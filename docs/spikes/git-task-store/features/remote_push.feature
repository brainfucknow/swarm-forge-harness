Feature: sf remote push

  With SWARMFORGE_REMOTE set to a remote, every command that writes a record
  on a task branch then pushes that branch to the remote, with the role
  commits under the record. When the push fails, the record stays local:
  the command prints its usual output, warns on stderr "sf: push of
  swarm/<id> to <remote> failed; the record is only local", and exits 0.
  Without SWARMFORGE_REMOTE, sf pushes nothing.

  Background:
    Given a project with swarmforge initialized and a remote "origin"

  # sf remote push 1
  Scenario: sf remote push 1: sf new pushes the new task branch
    When I run sf new with the title "Add login", the request "x" and the route "coder" with SWARMFORGE_REMOTE "origin"
    Then the exit status is 0
    And the branch "swarm/NEW" of "origin" is at the tip of task NEW

  # sf remote push 2
  Scenario Outline: sf remote push 2: a record command pushes the task branch
    Given task "A" titled "Add login" was started on route "specifier,qa"
    And I ran "<setup>"
    When I run "<command>" with SWARMFORGE_REMOTE "origin"
    Then the exit status is 0
    And the branch "swarm/$A" of "origin" is at the tip of task "A"

    Examples:
      | setup           | command                |
      | sf stage $A     | sf ask $A Which        |
      | sf ask $A Which | sf answer $A a         |
      | sf stage $A     | sf route $A specifier  |
      | sf stage $A     | sf review $A           |
      | sf review $A    | sf approve $A          |
      | sf review $A    | sf reject $A no        |

  # sf remote push 3
  Scenario: sf remote push 3: sf handoff pushes the role's commits with the record
    Given task "A" titled "Add login" was started on route "coder,qa"
    And role "coder" committed "Code" in the worktree of task "A" as C1
    When I run "sf handoff $A" with SWARMFORGE_REMOTE "origin"
    Then the exit status is 0
    And the branch "swarm/$A" of "origin" is at the tip of task "A"
    And the branch "swarm/$A" of "origin" contains C1

  # sf remote push 4
  Scenario: sf remote push 4: without SWARMFORGE_REMOTE nothing is pushed
    Given task "A" titled "Add login" was started on route "coder,qa"
    When I run "sf ask $A Which"
    Then the exit status is 0
    And the remote "origin" has no branch "swarm/$A"

  # sf remote push 5
  Scenario: sf remote push 5: a failed push keeps the record and warns
    Given task "A" titled "Add login" was started on route "coder,qa"
    When I run "sf ask $A Which" with SWARMFORGE_REMOTE "nowhere"
    Then the exit status is 0
    And the output is "ASKED: $A waits for the operator"
    And the error output has the line "sf: push of swarm/$A to nowhere failed; the record is only local"
    And the tip commit of task "A" has the trailer "SwarmForge-Record: question"
