Feature: sf specification approval

  A task at the specifier stage leaves that stage only through operator
  approval of its reviewed commit. A role override, explicit destination,
  rejection or answered clarification does not grant approval. Routes that
  omit the specifier keep their normal handoffs. This is a workflow invariant,
  not authentication against someone who can edit Git directly.

  Background:
    Given a project with swarmforge initialized
    And task "A" titled "Add login" was started on route "specifier,coder,qa"
    And role "specifier" committed "Specify login" in the worktree of task "A" as C1

  # sf specification approval 1
  Scenario Outline: sf specification approval 1: an unreviewed specification cannot be handed off
    When I run "sf handoff $A <options>"
    Then the exit status is 1
    And the branch of task "A" did not move

    Examples:
      | options    |
      |            |
      | --to coder |
      | --to done  |

  # sf specification approval 2
  Scenario: sf specification approval 2: rejection requires another review and approval
    Given I ran "sf review $A"
    And I ran "sf reject $A revise"
    When I run "sf handoff $A"
    Then the exit status is 1
    And the branch of task "A" did not move

  # sf specification approval 3
  Scenario: sf specification approval 3: a role override cannot bypass the specification gate
    Given the command environment sets "SWARMFORGE_ROLE" to "coder"
    When I run "sf handoff $A --to coder"
    Then the exit status is 1
    And the branch of task "A" did not move

  # sf specification approval 4
  Scenario Outline: sf specification approval 4: approval hands the reviewed commit to the next stage
    Given task "B" titled "Review change" was started on route "<route>"
    And role "specifier" committed "Specify change" in the worktree of task "B" as C2
    And I ran "sf review $B"
    When I run "sf approve $B"
    Then the exit status is 0
    And the output has the line "HANDOFF: $B specifier -> <next> at C2"

    Examples:
      | route              | next  |
      | specifier,coder,qa | coder |
      | specifier,qa       | qa    |
      | specifier          | done  |

  # sf specification approval 5
  Scenario Outline: sf specification approval 5: routes without a specifier retain normal handoff
    Given task "B" titled "Maintain change" was started on route "<route>"
    And role "<role>" committed "Maintain change" in the worktree of task "B" as C2
    When I run "sf handoff $B"
    Then the exit status is 0
    And the output has the line "HANDOFF: $B <role> -> <next> at C2"

    Examples:
      | route    | role  | next |
      | coder,qa | coder | qa   |
      | qa       | qa    | done |

  # sf specification approval 6
  Scenario: sf specification approval 6: a newer specification needs a fresh review
    Given I ran "sf review $A"
    And role "specifier" committed "Revise login" in the worktree of task "A" as C2
    When I run "sf approve $A"
    Then the exit status is 1
    And the branch of task "A" did not move

  # sf specification approval 7
  Scenario: sf specification approval 7: answering a clarification does not grant approval
    Given I ran "sf review $A"
    And I ran "sf ask $A clarify"
    And I ran "sf answer $A clarified"
    When I run "sf handoff $A"
    Then the exit status is 1
    And the branch of task "A" did not move
