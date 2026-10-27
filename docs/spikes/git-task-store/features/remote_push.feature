Feature: sf remote push

  With SWARMFORGE_REMOTE set to a remote, every command that writes a record
  on a task branch then pushes that branch to the remote, with the role
  commits under the record. When the push fails, the record stays local:
  the command prints its usual output, warns on stderr "sf: push of
  swarm/<id> to <remote> failed; the record is only local", and exits 0.
  Without SWARMFORGE_REMOTE, sf pushes nothing.

  With SWARMFORGE_REMOTE set, sf first fetches the task branch from the
  remote before it writes a record. When the fetched branch has a record
  commit of the task that the local branch does not contain, sf refuses:
  "sf: <remote>/swarm/<id> has records that swarm/<id> lacks; pull first:
  git pull --rebase <remote> swarm/<id>", exit status 1, and no commit. Records
  change no files, so that rebase puts the local records on top of the
  remote ones without conflict, and the newest record gives the state. When
  the fetch fails, there is no check. Without SWARMFORGE_REMOTE, sf fetches
  nothing and checks nothing.

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

  # sf remote push 6
  Scenario: sf remote push 6: a record is refused while the remote branch has records the local one lacks
    Given task "A" titled "Add login" was started on route "coder,qa"
    And I ran "git push origin swarm/$A"
    And another clone of "origin" pushed the record "sf ask $A Other" on task "A"
    When I run "sf answer $A yes" with SWARMFORGE_REMOTE "origin"
    Then the exit status is 1
    And the error output has the line "sf: origin/swarm/$A has records that swarm/$A lacks; pull first: git pull --rebase origin swarm/$A"
    And the branch of task "A" did not move

  # sf remote push 7
  Scenario: sf remote push 7: after a pull with rebase the record goes on top of the remote records
    Given task "A" titled "Add login" was started on route "coder,qa"
    And I ran "git push origin swarm/$A"
    And I ran "sf ask $A Mine"
    And another clone of "origin" pushed the record "sf ask $A Other" on task "A"
    And I ran "git pull --rebase origin swarm/$A" in the worktree of task "A"
    When I run "sf answer $A yes" with SWARMFORGE_REMOTE "origin"
    Then the exit status is 0
    And the branch "swarm/$A" of "origin" is at the tip of task "A"
    And the tip commit of task "A" has the trailer "SwarmForge-Needs: none"
    And the records of task "A" are, oldest first, "created\nquestion\nquestion\nanswer"

  # sf remote push 8
  Scenario: sf remote push 8: without SWARMFORGE_REMOTE nothing is fetched or checked
    Given task "A" titled "Add login" was started on route "coder,qa"
    And I ran "git push origin swarm/$A"
    And another clone of "origin" pushed the record "sf ask $A Other" on task "A"
    When I run "sf answer $A yes"
    Then the exit status is 0
    And the tip commit of task "A" has the trailer "SwarmForge-Record: answer"
