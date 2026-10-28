Feature: sf finish resume

  If the merge of sf finish stops on conflicts, sf finish exits 1 with
  "sf: merge of swarm/<id> into <target> stopped on conflicts: <files>;
  resolve them, commit, then run: sf finish <id>", where files are the
  conflicted paths as git lists them, separated by spaces. The merge stays in
  progress on the target, with the worktree and the task branch in place.

  While a merge is in progress in the main checkout, sf finish exits 1 with
  "sf: a merge is in progress; commit the merge first" and changes nothing.

  Once the operator has committed the merge, sf finish <id> completes the
  task although tasks/done/<id>.md is in HEAD. If HEAD's first-parent history
  has the task's finished record, sf finish leaves HEAD as it is. If it has
  none and HEAD is a merge whose second parent is the tip of the task branch,
  sf finish rewrites HEAD's message to the finished record, with the same
  parents and tree. Either way it then removes the worktree and the task
  branch and prints "FINISHED: <id> merged into <target>". In any other case
  a task with tasks/done/<id>.md in HEAD is refused as before with
  "sf: task <id> is finished".

  Background:
    Given a project with swarmforge initialized
    And task "A" titled "Add login" was started on route "coder"

  # sf finish resume 1
  Scenario Outline: sf finish resume 1: a conflicting merge stops with the files and the next step
    Given the worktree of task "A" and the branch "<target>" changed the files "<files>" differently
    And I ran "sf handoff $A"
    And the tip of the branch of task "A" is named T1
    When I run "sf finish $A --into <target>"
    Then the exit status is 1
    And the error output has the line "sf: merge of swarm/$A into <target> stopped on conflicts: <files>; resolve them, commit, then run: sf finish $A"
    And the main checkout is on the branch "<target>" merging T1 with the conflicted files "<files>"
    And the branch "swarm/$A" exists
    And the worktree of task "A" exists

    Examples:
      | target  | files               |
      | main    | src/a.clj           |
      | release | src/a.clj src/b.clj |

  # sf finish resume 2
  Scenario: sf finish resume 2: a merge in progress is refused
    Given the worktree of task "A" and the branch "main" changed the files "src/a.clj" differently
    And I ran "sf handoff $A"
    And I ran "sf finish $A"
    And the operator resolved the conflicts and staged them
    And the tip of the branch of task "A" is named T1
    When I run "sf finish $A"
    Then the exit status is 1
    And the error output has the line "sf: a merge is in progress; commit the merge first"
    And the main checkout is on the branch "main" merging T1 with no conflicted file
    And the branch "swarm/$A" exists
    And the worktree of task "A" exists

  # sf finish resume 3
  Scenario Outline: sf finish resume 3: a merge committed with another message gets the finished record
    Given the worktree of task "A" and the branch "<target>" changed the files "src/a.clj" differently
    And I ran "sf handoff $A"
    And the tip of the branch of task "A" is named T1
    And I ran "sf finish $A --into <target>"
    And the operator resolved the conflicts and committed the merge with the message "Resolve conflict"
    And the HEAD of the main checkout is named M1
    When I run "sf finish $A"
    Then the exit status is 0
    And the output is "FINISHED: $A merged into <target>"
    And the HEAD of the main checkout has the parents and the tree of M1
    And the HEAD of the main checkout is a merge whose second parent is T1
    And the HEAD of the main checkout has the message "sf finished: $A\n\nSwarmForge-Task: $A\nSwarmForge-Record: finished\nSwarmForge-From: operator\nSwarmForge-Stage: done\nSwarmForge-Route: coder\nSwarmForge-Needs: none\nSwarmForge-Merged-Into: <target>"
    And the branch "swarm/$A" does not exist
    And the worktree of task "A" does not exist

    Examples:
      | target  |
      | main    |
      | release |

  # sf finish resume 4
  Scenario: sf finish resume 4: a merge committed with git's prepared message stays as it is
    Given the worktree of task "A" and the branch "main" changed the files "src/a.clj" differently
    And I ran "sf handoff $A"
    And I ran "sf finish $A"
    And the operator resolved the conflicts and committed the merge with git's prepared message
    And the HEAD of the main checkout is named M1
    When I run "sf finish $A"
    Then the exit status is 0
    And the output is "FINISHED: $A merged into main"
    And the HEAD of the main checkout is M1
    And the branch "swarm/$A" does not exist
    And the worktree of task "A" does not exist

  # sf finish resume 5
  Scenario Outline: sf finish resume 5: a task finished without a merge of its tip is still refused
    Given task "A" <how>
    When I run "sf finish $A"
    Then the exit status is 1
    And the error output has the line "sf: task $A is finished"

    Examples:
      | how                                                 |
      | was finished                                        |
      | was handed off to done and squash-merged into main  |
