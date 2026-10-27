Feature: sf task archive

  The handoff that moves a task to stage done (sf handoff to the last role's
  successor, sf handoff --to done, or sf approve on a route whose last role
  is the specifier) is one commit that moves tasks/<id>.md to
  tasks/done/<id>.md. The moved file gets "stage: done" and "route: <route>"
  at the end of its front matter, and a "## Records" section after the
  request with each record, the handoff itself included, rendered as in sf
  show and fenced in its own "```text" block. The worktree is clean after it.

  sf finish merges the task branch into the target with --no-ff. The merge
  commit is the finished record: "sf finished: <id>" and the snapshot
  trailers, then SwarmForge-Merged-Into. sf finish removes the worktree and
  the branch.

  Background:
    Given a project with swarmforge initialized

  # sf task archive 1
  Scenario: sf task archive 1: the handoff to done moves the task file to tasks/done/ with the records
    Given task "A" titled "Add login" with the request "Users log in." was started on route "coder"
    And role "coder" committed the file "src/a.clj" with "Code" in the worktree of task "A" as C1
    When I run "sf handoff $A -m code done"
    Then the exit status is 0
    And the output is "HANDOFF: $A coder -> done at C1"
    And the tip commit of task "A" changes the files "D tasks/$A.md\nA tasks/done/$A.md"
    And the tip commit of task "A" has the message "sf handoff: $A\n\ncode done\n\nSwarmForge-Task: $A\nSwarmForge-Record: handoff\nSwarmForge-From: coder\nSwarmForge-Stage: done\nSwarmForge-Route: coder\nSwarmForge-Needs: none"
    And the file "tasks/done/$A.md" on the branch of task "A" is "---\nid: $A\ncreated: DATE\nstage: done\nroute: coder\n---\n\n# Add login\n\nUsers log in.\n\n## Records\n\n```text\ntype: created\nfrom: operator\nbranch: swarm/$A\ncommit: $A:created\nroute: coder\n```\n\n```text\ntype: handoff\nfrom: coder\nto: done\nbranch: swarm/$A\ncommit: C1\nfiles: src/a.clj\nsummary: code done\n```"
    And the worktree of task "A" has no uncommitted change

  # sf task archive 2
  Scenario Outline: sf task archive 2: every handoff that reaches done archives the task file
    Given task "A" titled "Add login" was started on route "<route>"
    When I run "<command>"
    Then the exit status is 0
    And the file "tasks/done/$A.md" on the branch of task "A" has the line "stage: done"
    And the file "tasks/$A.md" does not exist on the branch of task "A"

    Examples:
      | route    | command                 |
      | coder    | sf handoff $A           |
      | coder,qa | sf handoff $A --to done |

  # sf task archive 3
  Scenario: sf task archive 3: sf approve on a route that ends at the specifier archives the task file
    Given task "A" titled "Add login" was started on route "specifier"
    And I ran "sf review $A"
    When I run "sf approve $A"
    Then the exit status is 0
    And the file "tasks/done/$A.md" on the branch of task "A" has the line "stage: done"
    And the file "tasks/$A.md" does not exist on the branch of task "A"

  # sf task archive 4
  Scenario: sf task archive 4: a handoff that does not reach done leaves the task file in place
    Given task "A" titled "Add login" was started on route "coder,qa"
    When I run "sf handoff $A"
    Then the exit status is 0
    And the tip commit of task "A" changes no file
    And the file "tasks/done/$A.md" does not exist on the branch of task "A"

  # sf task archive 5
  Scenario: sf task archive 5: sf finish merges the task with a finished record and removes its branch
    Given task "A" titled "Add login" was started on route "coder"
    And I ran "sf handoff $A"
    And the tip of the branch of task "A" is named T1
    When I run "sf finish $A"
    Then the exit status is 0
    And the output is "FINISHED: $A merged into main"
    And the HEAD of the main checkout is a merge whose second parent is T1
    And the HEAD of the main checkout has the message "sf finished: $A\n\nSwarmForge-Task: $A\nSwarmForge-Record: finished\nSwarmForge-From: operator\nSwarmForge-Stage: done\nSwarmForge-Route: coder\nSwarmForge-Needs: none\nSwarmForge-Merged-Into: main"
    And the main checkout has the file "tasks/done/$A.md" and no file "tasks/$A.md"
    And the branch "swarm/$A" does not exist
    And the worktree of task "A" does not exist
