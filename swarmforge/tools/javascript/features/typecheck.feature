Feature: sf-js typecheck

  With a tsconfig.json, sf-js test runs "tsc --noEmit" before the tests.
  A type error stops sf-js test with exit status 1 before the tests run.

  Background:
    Given a task worktree branched from main
    And fake JavaScript tools on PATH
    And the worktree has the lockfile "package-lock.json"
    And package.json lists "vitest" in devDependencies

  # sf-js typecheck 1
  Scenario: sf-js typecheck 1: tsc runs before the tests
    Given tsconfig.json is present
    When I run sf-js with "test"
    Then the exit status is 0
    And sf-js ran "npx --no-install tsc --noEmit" before "npx --no-install vitest run"

  # sf-js typecheck 2
  Scenario Outline: sf-js typecheck 2: a type error stops the tests
    Given tsconfig.json is present
    And the fake tsc exits with status <tsc_status>
    When I run sf-js with "test"
    Then the exit status is 1
    And sf-js did not run "vitest"

    Examples:
      | tsc_status |
      | 1          |
      | 2          |

  # sf-js typecheck 3
  Scenario Outline: sf-js typecheck 3: only test typechecks, and only with a tsconfig.json
    Given tsconfig.json is <tsconfig>
    When I run sf-js with "<command>"
    Then the exit status is 0
    And sf-js did not run "tsc"

    Examples:
      | tsconfig | command  |
      | absent   | test     |
      | present  | property |
      | present  | coverage |
