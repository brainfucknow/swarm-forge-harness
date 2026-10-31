Feature: sf init without git-bug

  sf init needs git and Babashka, not git-bug. It prints the identity that sf
  gives its record commits: git's user.name and user.email, or, when git has
  none, SWARMFORGE_USER (default operator) and SWARMFORGE_EMAIL (default
  <user>@swarmforge.local).

  # sf init without git-bug 1
  Scenario Outline: sf init without git-bug 1: sf init succeeds without git-bug and prints the identity of record commits
    Given a git repository with a copy of swarmforge and no git-bug on PATH
    And the git identity of the project is "<identity>"
    When I run "sf init"
    Then the exit status is 0
    And the output has the line "identity: <printed>"
    And the output has the line "roles: specifier architecture-design-reviewer coder cleaner architect hardener security-reviewer qa"

    Examples:
      | identity                    | printed                     |
      | tester <tester@example.com> | tester <tester@example.com> |

  # sf init without git-bug 2
  Scenario: sf init without git-bug 2: sf init prints the default identity when git has none
    Given a git repository with a copy of swarmforge and no git-bug on PATH
    And the git identity of the project is "-"
    When I run "sf init"
    Then the exit status is 0
    And the output has the line "identity: operator <operator@swarmforge.local>"
    And the output has the line "roles: specifier architecture-design-reviewer coder cleaner architect hardener security-reviewer qa"
