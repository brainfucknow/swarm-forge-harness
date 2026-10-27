Feature: sf Lore lines

  sf start and sf show print the Lore trailers of the task branch after the
  task's records, one line per trailer:
  "LORE: <short sha> <role> <Key>: <value>". The commits are those on the
  first-parent history of the task branch after the task's created record,
  oldest first, and each commit's trailers keep their order in the message.
  <short sha> is the commit's abbreviated sha as git rev-parse --short prints
  it. <role> is the role of the commit's last "By <role>." line, or "-" when
  it has none. Only trailers that git reads from the trailer block count, with
  folded values joined to one line; trailers that are not Lore trailers are
  left out. When there are no Lore trailers, nothing is added to the output.
  sf show of a task finished with sf finish reads them from the merge's
  second parent.

  Background:
    Given a project with swarmforge initialized
    And the main checkout has the commit "Base\n\nTested: base"
    And a task at stage "specifier" started with sf start

  # sf Lore lines 1
  Scenario Outline: sf Lore lines 1: start and show print the Lore trailers of the branch, oldest first
    Given role "specifier" committed "Spec\n\nConstraint: keep the\n byline" in the task worktree as C1
    And role "coder" committed "Code\n\nPlain body" in the task worktree as C2
    And role "qa" committed "Verify\n\nTested: placement\nNot-tested: Windows\nCo-Authored-By: A <a@b>" in the task worktree as C3
    When I run "sf <command> ID" in the main checkout
    Then the exit status is 0
    And the output ends with the lines "LORE: C1 specifier Lore-id: L1\nLORE: C1 specifier Constraint: keep the byline\nLORE: C3 qa Lore-id: L3\nLORE: C3 qa Tested: placement\nLORE: C3 qa Not-tested: Windows"
    And the output has no line containing "Tested: base"

    Examples:
      | command |
      | start   |
      | show    |

  # sf Lore lines 2
  Scenario: sf Lore lines 2: a commit without a byline shows the role as -
    Given "Fix\n\nTested: x" was committed in the task worktree with the hook skipped as C1
    When I run "sf start ID" in the main checkout
    Then the exit status is 0
    And the output ends with the lines "LORE: C1 - Tested: x"

  # sf Lore lines 3
  Scenario Outline: sf Lore lines 3: no Lore trailers add nothing
    Given role "specifier" committed "Spec\n\nCo-Authored-By: A <a@b>" in the task worktree as C1
    When I run "sf <command> ID" in the main checkout
    Then the exit status is 0
    And the output has no line starting with "LORE:"

    Examples:
      | command |
      | start   |
      | show    |

  # sf Lore lines 4
  Scenario: sf Lore lines 4: show prints the Lore lines of a finished task
    Given role "specifier" committed "Spec\n\nTested: x" in the task worktree as C1
    And the task was finished with sf finish
    When I run "sf show ID" in the main checkout
    Then the exit status is 0
    And the output ends with the lines "LORE: C1 specifier Lore-id: L1\nLORE: C1 specifier Tested: x"
