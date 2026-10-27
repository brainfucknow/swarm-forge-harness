Feature: sf hook record trailers

  Only sf writes SwarmForge-* trailers. In a task worktree, at every stage,
  the commit-msg hook (sf commit-msg) refuses a commit whose trailer block has
  a key that starts with "SwarmForge-" in any letter case. It prints "sf:
  <key> is a SwarmForge record trailer; only sf writes those" on stderr,
  exits 1, and the commit is not made. A "SwarmForge-" line outside the
  trailer block is allowed. Commits in the main checkout are not checked.

  Background:
    Given a project with swarmforge initialized
    And task "A" titled "Add login" was started on route "coder"

  # sf hook record trailers 1
  Scenario Outline: sf hook record trailers 1: a task worktree commit with a SwarmForge trailer is refused
    When I commit "Fix\n\n<key>: done" in the worktree of task "A" with SWARMFORGE_ROLE "coder"
    Then the exit status is 1
    And the error output has the line "sf: <printed> is a SwarmForge record trailer; only sf writes those"
    And the branch of task "A" did not move

    Examples:
      | key              | printed          |
      | SwarmForge-Stage | SwarmForge-Stage |
      | swarmforge-needs | swarmforge-needs |
      | SWARMFORGE-TASK  | SWARMFORGE-TASK  |

  # sf hook record trailers 2
  Scenario: sf hook record trailers 2: a SwarmForge line outside the trailer block is allowed
    When I commit "Fix\n\nSwarmForge-Stage: done\n\nClosing text" in the worktree of task "A"
    Then the exit status is 0

  # sf hook record trailers 3
  Scenario: sf hook record trailers 3: sf commit-msg refuses the message file and leaves it unchanged
    Given the file "tmp/msg" in the worktree of task "A" is written with "Fix\n\nSwarmForge-Stage: done"
    When I run "sf commit-msg coder tmp/msg" in the worktree of task "A"
    Then the exit status is 1
    And the error output has the line "sf: SwarmForge-Stage is a SwarmForge record trailer; only sf writes those"
    And the file "tmp/msg" in the worktree of task "A" still holds "Fix\n\nSwarmForge-Stage: done"

  # sf hook record trailers 4
  Scenario: sf hook record trailers 4: a SwarmForge trailer after another trailer is refused
    When I commit "Fix\n\nTested: x\nswarmforge-needs: none" in the worktree of task "A" with SWARMFORGE_ROLE "coder"
    Then the exit status is 1
    And the error output has the line "sf: swarmforge-needs is a SwarmForge record trailer; only sf writes those"
    And the branch of task "A" did not move

  # sf hook record trailers 5
  Scenario: sf hook record trailers 5: the refusal holds at stage done
    When I commit "Fix\n\nSwarmForge-Task: x" in the worktree of task "A" with SWARMFORGE_ROLE "done"
    Then the exit status is 1
    And the error output has the line "sf: SwarmForge-Task is a SwarmForge record trailer; only sf writes those"
    And the branch of task "A" did not move

  # sf hook record trailers 6
  Scenario: sf hook record trailers 6: a commit in the main checkout is not checked
    When I commit "Fix\n\nSwarmForge-Stage: done" in the main checkout
    Then the exit status is 0
