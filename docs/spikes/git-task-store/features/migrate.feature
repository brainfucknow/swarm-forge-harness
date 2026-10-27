Feature: sf migrate

  sf migrate converts each open git-bug task that has a stage: label, and
  needs git-bug on PATH. The new id is the slug of the title, "-", and the
  git-bug human id. The branch swarm/<hex> is renamed to swarm/<new id>, or,
  when it is gone, made at the commit of the task's "type: created" record.
  The worktree .worktrees/<hex> moves to .worktrees/<new id>. One commit is
  added that adds tasks/<new id>.md: the front matter, the title, the request
  (the first git-bug comment), and "## Imported records" with each later
  comment verbatim in its own "```text" block. It is a created record whose
  snapshot comes from the stage:, route: and needs: labels, with
  SwarmForge-Base: <full sha of the created record's commit>. The commit of
  the created record, for files and LORE: lines, is that base. The command
  prints "MIGRATED: <hex> -> <new id>" per task.

  A task already migrated is skipped with "SKIPPED: <hex> already migrated as
  <new id>". Closed git-bug tasks and all git-bug data are left as they are.

  Background:
    Given a project with swarmforge initialized and git-bug on PATH
    And the old sf made the open git-bug task G1 titled "Fix login" with the request "Users cannot log in." on route "coder,qa" at stage "coder" on commit B0

  # sf migrate 1
  Scenario Outline: sf migrate 1: an open git-bug task becomes a task branch with a task file
    Given the git-bug task G1 has the needs label "<label>"
    And the git-bug task G1 has the comment "type: question\nfrom: coder\nquestion: Which hash?"
    And the old sf started G1
    And role "coder" committed "Code\n\nTested: x" in the old worktree of G1 as C1
    When I run "sf migrate"
    Then the exit status is 0
    And the output is "MIGRATED: $G1 -> fix-login-$G1"
    And the branch "swarm/$G1" does not exist
    And the worktree ".worktrees/fix-login-$G1" is on the branch "swarm/fix-login-$G1"
    And the parent of the tip commit of task "fix-login-$G1" is C1
    And the tip commit of task "fix-login-$G1" adds only the file "tasks/fix-login-$G1.md"
    And the tip commit of task "fix-login-$G1" has the message "sf created: fix-login-$G1\n\nFix login\n\nSwarmForge-Task: fix-login-$G1\nSwarmForge-Record: created\nSwarmForge-From: operator\nSwarmForge-Stage: coder\nSwarmForge-Route: coder,qa\nSwarmForge-Needs: <needs>\nSwarmForge-Base: B0:full"
    And the file "tasks/fix-login-$G1.md" on the branch of task "fix-login-$G1" is "---\nid: fix-login-$G1\ncreated: DATE\n---\n\n# Fix login\n\nUsers cannot log in.\n\n## Imported records\n\n```text\ntype: created\nfrom: operator\nbranch: swarm/$G1\ncommit: B0\nroute: coder,qa\n```\n\n```text\ntype: question\nfrom: coder\nquestion: Which hash?\n```"

    Examples:
      | label          | needs    |
      | -              | none     |
      | needs:operator | operator |
      | needs:approval | approval |

  # sf migrate 2
  Scenario: sf migrate 2: a migrated task keeps its LORE lines and answers to its hex id
    Given the old sf started G1
    And role "coder" committed "Code\n\nTested: x" in the old worktree of G1 as C1
    And I ran "sf migrate"
    When I run "sf start $G1"
    Then the exit status is 0
    And the output has the line "TASK: fix-login-$G1"
    And the output has the line "STAGE: coder"
    And the output ends with the lines "LORE: C1 coder Lore-id: L1\nLORE: C1 coder Tested: x"

  # sf migrate 3
  Scenario: sf migrate 3: an open task whose branch is gone gets a branch at its created record's commit
    Given the branch "swarm/$G1" was deleted
    When I run "sf migrate"
    Then the exit status is 0
    And the output is "MIGRATED: $G1 -> fix-login-$G1"
    And the parent of the tip commit of task "fix-login-$G1" is B0

  # sf migrate 4
  Scenario: sf migrate 4: a second run skips the migrated task
    Given I ran "sf migrate"
    And the tip of the branch of task "fix-login-$G1" is named T1
    When I run "sf migrate"
    Then the exit status is 0
    And the output is "SKIPPED: $G1 already migrated as fix-login-$G1"
    And the branch "swarm/fix-login-$G1" is at T1

  # sf migrate 5
  Scenario: sf migrate 5: closed git-bug tasks and the git-bug data are left alone
    Given the old sf made the open git-bug task G2 titled "Old work" with the request "Done." on route "coder" at stage "done" on commit B2
    And the git-bug task G2 is closed
    When I run "sf migrate"
    Then the exit status is 0
    And the output is "MIGRATED: $G1 -> fix-login-$G1"
    And the branch "swarm/old-work-$G2" does not exist
    And git-bug still lists the tasks G1 and G2

  # sf migrate 6
  Scenario: sf migrate 6: sf migrate needs git-bug on PATH
    Given git-bug is not on PATH
    When I run "sf migrate"
    Then the exit status is 1
    And the error output has the line "sf: migrate needs git-bug on PATH (https://github.com/git-bug/git-bug)"
