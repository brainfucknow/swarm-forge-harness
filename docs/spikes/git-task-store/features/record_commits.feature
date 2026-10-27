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
    Given I ran "<setup>"
    When I run "<command>"
    Then the exit status is 0
    And the tip commit of task "A" changes no file
    And the tip commit of task "A" has the message "sf <record>: $A<text>\n\nSwarmForge-Task: $A\nSwarmForge-Record: <record>\nSwarmForge-From: <from>\nSwarmForge-Stage: specifier\nSwarmForge-Route: <route>\nSwarmForge-Needs: <needs>"

    Examples:
      | setup           | command                   | record            | text                 | from      | route              | needs    |
      | sf stage $A     | sf ask $A Which hash?     | question          | \n\nWhich hash?      | specifier | specifier,coder,qa | operator |
      | sf ask $A Which | sf answer $A argon2id     | answer            | \n\nargon2id         | operator  | specifier,coder,qa | none     |
      | sf stage $A     | sf review $A spec ready   | review            | \n\nspec ready       | specifier | specifier,coder,qa | approval |
      | sf review $A    | sf reject $A split it     | changes-requested | \n\nsplit it         | operator  | specifier,coder,qa | none     |
      | sf review $A    | sf route $A specifier,qa  | route             |                      | operator  | specifier,qa       | approval |

  # sf record commits 2
  Scenario: sf record commits 2: free text keeps its newlines
    When I run "sf ask $A" with the text "Which hash?\n\n1) bcrypt\n2) argon2id"
    Then the exit status is 0
    And the tip commit of task "A" has the message "sf question: $A\n\nWhich hash?\n\n1) bcrypt\n2) argon2id\n\nSwarmForge-Task: $A\nSwarmForge-Record: question\nSwarmForge-From: specifier\nSwarmForge-Stage: specifier\nSwarmForge-Route: specifier,coder,qa\nSwarmForge-Needs: operator"

  # sf record commits 3
  Scenario: sf record commits 3: sf handoff adds a handoff record on the role's commit
    Given role "specifier" committed "Spec" in the worktree of task "A" as C1
    When I run "sf handoff $A -m spec done"
    Then the exit status is 0
    And the output is "HANDOFF: $A specifier -> coder at C1"
    And the parent of the tip commit of task "A" is C1
    And the tip commit of task "A" changes no file
    And the tip commit of task "A" has the message "sf handoff: $A\n\nspec done\n\nSwarmForge-Task: $A\nSwarmForge-Record: handoff\nSwarmForge-From: specifier\nSwarmForge-Stage: coder\nSwarmForge-Route: specifier,coder,qa\nSwarmForge-Needs: none"

  # sf record commits 4
  Scenario: sf record commits 4: sf approve adds an approval record and then a handoff record
    Given role "specifier" committed "Spec" in the worktree of task "A" as C1
    And I ran "sf review $A spec ready"
    When I run "sf approve $A"
    Then the exit status is 0
    And the parent of the tip commit of task "A" has the message "sf approval: $A\n\nSwarmForge-Task: $A\nSwarmForge-Record: approval\nSwarmForge-From: operator\nSwarmForge-Stage: specifier\nSwarmForge-Route: specifier,coder,qa\nSwarmForge-Needs: none"
    And the tip commit of task "A" has the message "sf handoff: $A\n\nspecifier work complete\n\nSwarmForge-Task: $A\nSwarmForge-Record: handoff\nSwarmForge-From: specifier\nSwarmForge-Stage: coder\nSwarmForge-Route: specifier,coder,qa\nSwarmForge-Needs: none"

  # sf record commits 5
  Scenario Outline: sf record commits 5: a record leaves the staged and unstaged changes of the worktree as they were
    Given I ran "<setup>"
    And the worktree of task "A" has the staged new file "s.txt" and the unstaged change "t.txt"
    When I run "<command>"
    Then the exit status is 0
    And the worktree of task "A" still has the staged new file "s.txt" and the unstaged change "t.txt"

    Examples:
      | setup           | command                  |
      | sf stage $A     | sf ask $A Which hash?    |
      | sf ask $A Which | sf answer $A argon2id    |
      | sf stage $A     | sf route $A specifier,qa |

  # sf record commits 6
  Scenario Outline: sf record commits 6: a refused command adds no commit
    Given I ran "<setup>"
    When I run "<command>"
    Then the exit status is 1
    And the branch of task "A" did not move

    Examples:
      | setup                | command                |
      | sf stage $A          | sf approve $A          |
      | sf stage $A          | sf reject $A no        |
      | sf ask $A Which      | sf handoff $A          |
      | sf stage $A          | sf route $A coder,qa   |
