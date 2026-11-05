Feature: sf task done

  The handoff that moves a task to stage done (sf handoff to the last role's
  successor, sf handoff --to done, or sf approve on a route whose last role
  is the specifier) is a record commit like any other and changes no file.
  For a task created before this change, it removes every file named
  <id>.md from the branch, so no task file reaches the merge.

  sf finish merges the task branch into the target with --no-ff. The merge
  commit is the finished record: "sf finished: <id>" and the snapshot
  trailers, then SwarmForge-Merged-Into. It brings no task file into the
  target. sf finish removes the worktree and the local branch; it never
  touches a remote. Without swarmforge/verify.conf it prints "VERIFY: none
  configured" before "FINISHED: <id> merged into <target>" (see sf finish
  verify).

  Background:
    Given a project with swarmforge initialized

  # sf task done 1
  Scenario Outline: sf task done 1: a handoff that reaches done is a record that changes no file
    Given task "A" titled "Add login" was started on route "<route>"
    When I run "<command>"
    Then the exit status is 0
    And the tip commit of task "A" changes no file
    And the tip commit of task "A" has the message "sf handoff: $A\n\ncoder work complete\n\nSwarmForge-Task: $A\nSwarmForge-Record: handoff\nSwarmForge-From: coder\nSwarmForge-Stage: done\nSwarmForge-Route: <route>\nSwarmForge-Needs: none"
    And the worktree of task "A" has no uncommitted change

    Examples:
      | route    | command                 |
      | coder    | sf handoff $A           |
      | coder,qa | sf handoff $A --to done |

  # sf task done 2
  Scenario: sf task done 2: sf approve on a route that ends at the specifier changes no file
    Given task "A" titled "Add login" was started on route "specifier"
    And I ran "sf review $A"
    When I run "sf approve $A"
    Then the exit status is 0
    And the output matches "^HANDOFF: $A specifier -> done at "
    And the tip commit of task "A" changes no file

  # sf task done 3
  Scenario: sf task done 3: sf finish merges the task with a finished record and leaves no task file
    Given task "A" titled "Add login" was started on route "coder"
    And role "coder" committed the file "src/a.clj" with "Code" in the worktree of task "A" as C1
    And I ran "sf handoff $A"
    And the tip of the branch of task "A" is named T1
    When I run "sf finish $A"
    Then the exit status is 0
    And the output is "VERIFY: none configured\nFINISHED: $A merged into main"
    And the HEAD of the main checkout is a merge whose second parent is T1
    And the HEAD of the main checkout has the message "sf finished: $A\n\nSwarmForge-Task: $A\nSwarmForge-Record: finished\nSwarmForge-From: operator\nSwarmForge-Stage: done\nSwarmForge-Route: coder\nSwarmForge-Needs: none\nSwarmForge-Merged-Into: main"
    And the main checkout has the file "src/a.clj" and no file "tasks/$A.md"
    And the main checkout has no file "tasks/done/$A.md" and no uncommitted change
    And the branch "swarm/$A" does not exist
    And the worktree of task "A" does not exist
