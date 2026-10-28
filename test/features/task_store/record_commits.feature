Feature: sf record commits

  Every sf command that changes a task, except sf new, sf finish and the
  handoff to done, adds commits to the task branch that change no file. Each
  is a record. Its message is "sf <record>: <id>", then a blank line and
  the free text when the record has any, then a blank line and the trailers:
  SwarmForge-Task, SwarmForge-Record, SwarmForge-From, and the state after
  the record as SwarmForge-Stage, SwarmForge-Route and SwarmForge-Needs (none,
  operator or approval). Free text keeps its newlines. Several arguments are
  joined with spaces.

  A record never touches the files or the index of a worktree: staged and
  unstaged changes stay as they were. A command that is refused adds no
  commit.

  Background:
    Given a project with swarmforge initialized
    And task "A" titled "Add login" was started on route "specifier,coder,qa"

  # sf record commits 1
  Scenario Outline: sf record commits 1: a command adds one record commit that changes no file
    When I run "<command>"
    Then the exit status is 0
    And the tip commit of task "A" changes no file
    And the tip commit of task "A" has the message "sf <record>: $A<text>\n\nSwarmForge-Task: $A\nSwarmForge-Record: <record>\nSwarmForge-From: specifier\nSwarmForge-Stage: specifier\nSwarmForge-Route: specifier,coder,qa\nSwarmForge-Needs: <needs>"

    Examples:
      | command                 | record   | text            | needs    |
      | sf ask $A Which hash?   | question | \n\nWhich hash? | operator |
      | sf review $A spec ready | review   | \n\nspec ready  | approval |

  # sf record commits 2
  Scenario: sf record commits 2: sf answer adds an answer record from the operator
    Given I ran "sf ask $A Which"
    When I run "sf answer $A argon2id"
    Then the exit status is 0
    And the tip commit of task "A" changes no file
    And the tip commit of task "A" has the message "sf answer: $A\n\nargon2id\n\nSwarmForge-Task: $A\nSwarmForge-Record: answer\nSwarmForge-From: operator\nSwarmForge-Stage: specifier\nSwarmForge-Route: specifier,coder,qa\nSwarmForge-Needs: none"

  # sf record commits 3
  Scenario Outline: sf record commits 3: a command after a review adds one record commit from the operator
    Given I ran "sf review $A"
    When I run "<command>"
    Then the exit status is 0
    And the tip commit of task "A" changes no file
    And the tip commit of task "A" has the message "sf <record>: $A<text>\n\nSwarmForge-Task: $A\nSwarmForge-Record: <record>\nSwarmForge-From: operator\nSwarmForge-Stage: specifier\nSwarmForge-Route: <route>\nSwarmForge-Needs: <needs>"

    Examples:
      | command                  | record            | text         | route              | needs    |
      | sf reject $A split it    | changes-requested | \n\nsplit it | specifier,coder,qa | none     |
      | sf route $A specifier,qa | route             |              | specifier,qa       | approval |

  # sf record commits 4
  Scenario: sf record commits 4: free text keeps its newlines
    When I run "sf ask $A" with the text "Which hash?\n\n1) bcrypt\n2) argon2id"
    Then the exit status is 0
    And the tip commit of task "A" has the message "sf question: $A\n\nWhich hash?\n\n1) bcrypt\n2) argon2id\n\nSwarmForge-Task: $A\nSwarmForge-Record: question\nSwarmForge-From: specifier\nSwarmForge-Stage: specifier\nSwarmForge-Route: specifier,coder,qa\nSwarmForge-Needs: operator"

  # sf record commits 5
  Scenario: sf record commits 5: sf handoff adds a handoff record on the role's commit
    Given role "specifier" committed "Spec" in the worktree of task "A" as C1
    When I run "sf handoff $A -m spec done"
    Then the exit status is 0
    And the output is "HANDOFF: $A specifier -> coder at C1"
    And the parent of the tip commit of task "A" is C1
    And the tip commit of task "A" changes no file
    And the tip commit of task "A" has the message "sf handoff: $A\n\nspec done\n\nSwarmForge-Task: $A\nSwarmForge-Record: handoff\nSwarmForge-From: specifier\nSwarmForge-Stage: coder\nSwarmForge-Route: specifier,coder,qa\nSwarmForge-Needs: none"

  # sf record commits 6
  Scenario: sf record commits 6: sf approve adds an approval record and then a handoff record
    Given role "specifier" committed "Spec" in the worktree of task "A" as C1
    And I ran "sf review $A spec ready"
    When I run "sf approve $A"
    Then the exit status is 0
    And the parent of the tip commit of task "A" has the message "sf approval: $A\n\nSwarmForge-Task: $A\nSwarmForge-Record: approval\nSwarmForge-From: operator\nSwarmForge-Stage: specifier\nSwarmForge-Route: specifier,coder,qa\nSwarmForge-Needs: none"
    And the tip commit of task "A" has the message "sf handoff: $A\n\nspecifier work complete\n\nSwarmForge-Task: $A\nSwarmForge-Record: handoff\nSwarmForge-From: specifier\nSwarmForge-Stage: coder\nSwarmForge-Route: specifier,coder,qa\nSwarmForge-Needs: none"

  # sf record commits 7
  Scenario: sf record commits 7: a record leaves the staged and unstaged changes of the worktree as they were
    Given the worktree of task "A" has the staged new file "s.txt" and the unstaged change "t.txt"
    And I ran "sf ask $A Which hash?"
    And I ran "sf answer $A argon2id"
    When I run "sf route $A specifier,qa"
    Then the exit status is 0
    And the worktree of task "A" still has the staged new file "s.txt" and the unstaged change "t.txt"

  # sf record commits 8
  Scenario Outline: sf record commits 8: a refused command adds no commit
    When I run "<command>"
    Then the exit status is 1
    And the error output has the line "sf: <error>"
    And the branch of task "A" did not move

    Examples:
      | command              | error                                                           |
      | sf approve $A        | task is not waiting for approval                                |
      | sf route $A coder,qa | route 'coder,qa' does not contain the current stage 'specifier' |

  # sf record commits 9
  Scenario: sf record commits 9: sf reject is refused while the task does not wait for approval
    When I run "sf reject $A no"
    Then the exit status is 1
    And the error output has the line "sf: task is not waiting for approval"
    And the branch of task "A" did not move

  # sf record commits 10
  Scenario: sf record commits 10: sf handoff is refused while the task needs the operator
    Given I ran "sf ask $A Which"
    When I run "sf handoff $A"
    Then the exit status is 1
    And the error output has the line "sf: task is waiting on the operator (needs:operator); handoff refused"
    And the branch of task "A" did not move
