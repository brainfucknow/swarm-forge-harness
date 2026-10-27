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
    When I commit "<message>" in the worktree of task "A" with SWARMFORGE_ROLE "<role>"
    Then the exit status is 1
    And the error output has the line "sf: <key> is a SwarmForge record trailer; only sf writes those"
    And the branch of task "A" did not move

    Examples:
      | message                                     | role  | key              |
      | Fix\n\nSwarmForge-Stage: done               | coder | SwarmForge-Stage |
      | Fix\n\nTested: x\nswarmforge-needs: none    | coder | swarmforge-needs |
      | Fix\n\nSwarmForge-Task: x                   | done  | SwarmForge-Task  |

  # sf hook record trailers 2
  Scenario Outline: sf hook record trailers 2: SwarmForge lines outside the trailer block and main checkout commits are allowed
    When I commit "<message>" in the <place>
    Then the exit status is 0

    Examples:
      | message                                         | place                   |
      | Fix\n\nSwarmForge-Stage: done\n\nClosing text   | worktree of task "A"    |
      | Fix\n\nSwarmForge-Stage: done                   | main checkout           |

  # sf hook record trailers 3
  Scenario: sf hook record trailers 3: sf commit-msg refuses the message file and leaves it unchanged
    Given the file "tmp/msg" in the worktree of task "A" is written with "Fix\n\nSwarmForge-Stage: done"
    When I run "sf commit-msg coder tmp/msg" in the worktree of task "A"
    Then the exit status is 1
    And the error output has the line "sf: SwarmForge-Stage is a SwarmForge record trailer; only sf writes those"
    And the file "tmp/msg" in the worktree of task "A" still holds "Fix\n\nSwarmForge-Stage: done"
