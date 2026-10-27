Feature: sf tool catalogue location

  sf tool list, require and ensure read swarmforge/tools.conf of the checkout
  they run in: the task worktree when run in one, the main checkout
  otherwise. Tool sources and wrappers stay shared under the main checkout's
  .swarmforge/. A wrapper for a tool of kind local runs the script from the
  checkout the wrapper is run in (git rev-parse --show-toplevel) when that
  checkout has it and shares the main checkout's git directory (git
  rev-parse --git-common-dir), and the main checkout's copy otherwise,
  including in another git repository and outside any git repository. sf check still reads the main checkout's
  configuration.

  Background:
    Given a project with swarmforge initialized
    And a task worktree
    And the task worktree's tools.conf adds the line "probe local swarmforge/bin/probe - - -"
    And the task worktree's "swarmforge/bin/probe" prints "probe"

  # sf tool catalogue location 1
  Scenario Outline: sf tool catalogue location 1: list shows the catalogue of the checkout it runs in
    When I run "sf tool list" in the <checkout>
    Then the exit status is 0
    And the tool "probe" is listed: <listed>

    Examples:
      | checkout      | listed |
      | task worktree | yes    |
      | main checkout | no     |

  # sf tool catalogue location 2
  Scenario: sf tool catalogue location 2: ensure and require in a task worktree use its catalogue
    When I run "sf tool ensure probe" in the task worktree
    Then the exit status is 0
    And the output has the line "INSTALLED: probe MAIN/.swarmforge/bin/probe"
    And "sf tool require probe" in the task worktree prints "OK: probe MAIN/.swarmforge/bin/probe"

  # sf tool catalogue location 3
  Scenario: sf tool catalogue location 3: the main checkout does not know a worktree-only tool
    When I run "sf tool ensure probe" in the main checkout
    Then the exit status is 1
    And the error output contains "unknown tool 'probe'"

  # sf tool catalogue location 4
  Scenario Outline: sf tool catalogue location 4: a local wrapper runs the script of the caller's checkout
    Given the main checkout's "swarmforge/bin/sf-cs" prints "main"
    And the task worktree's "swarmforge/bin/sf-cs" prints "task"
    And a second task worktree without "swarmforge/bin/sf-cs"
    And "sf tool ensure sf-cs" ran in the main checkout
    When I run the sf-cs wrapper in the <place>
    Then the output is "<printed>"

    Examples:
      | place                  | printed |
      | task worktree          | task    |
      | main checkout          | main    |
      | second task worktree   | main    |
      | directory outside git  | main    |
      | another git repository | main    |
