Feature: sf-js tests

  sf-js test runs the unit and acceptance tests without property tests;
  sf-js property runs only the property tests (files named *.property.test.*).
  The runner is vitest when package.json lists it, else jest.

  Background:
    Given a task worktree branched from main
    And fake JavaScript tools on PATH
    And the worktree has the lockfile "package-lock.json"

  # sf-js tests 1
  Scenario Outline: sf-js tests 1: package.json picks the runner
    Given package.json lists "<package>" in <section>
    When I run sf-js with "test"
    Then the exit status is 0
    And sf-js ran "<command>"

    Examples:
      | package | section         | command                     |
      | vitest  | devDependencies | npx --no-install vitest run |
      | vitest  | dependencies    | npx --no-install vitest run |
      | jest    | devDependencies | npx --no-install jest       |
      | jest    | dependencies    | npx --no-install jest       |

  # sf-js tests 2
  Scenario: sf-js tests 2: vitest wins when both runners are listed
    Given package.json lists "jest" in devDependencies
    And package.json lists "vitest" in devDependencies
    When I run sf-js with "test"
    Then the exit status is 0
    And sf-js ran "npx --no-install vitest run"
    And sf-js did not run "jest"

  # sf-js tests 3
  Scenario Outline: sf-js tests 3: no runner in package.json fails
    Given package.json lists "mocha" in devDependencies
    When I run sf-js with "<command>"
    Then the exit status is 2
    And the error output contains "no test runner (vitest or jest) in package.json"

    Examples:
      | command  |
      | test     |
      | property |
      | coverage |

  # sf-js tests 4
  Scenario Outline: sf-js tests 4: test leaves out property tests
    Given package.json lists "<runner>" in devDependencies
    When I run sf-js with "test"
    Then the command line that runs "<runner>" contains "<flag>"
    And the command line that runs "<runner>" contains "<pattern>"

    Examples:
      | runner | flag                     | pattern              |
      | vitest | --exclude                | **/*.property.test.* |
      | jest   | --testPathIgnorePatterns | \.property\.test\.   |

  # sf-js tests 5
  Scenario Outline: sf-js tests 5: test passes extra arguments to the runner
    Given package.json lists "<runner>" in devDependencies
    When I run sf-js with "test <extra>"
    Then the exit status is 0
    And the command line that runs "<runner>" ends with " <passed>"

    Examples:
      | runner | extra          | passed         |
      | vitest | --reporter=dot | --reporter=dot |
      | jest   | --ci --silent  | --ci --silent  |

  # sf-js tests 6
  Scenario Outline: sf-js tests 6: property runs only property tests
    Given package.json lists "<runner>" in devDependencies
    When I run sf-js with "property"
    Then the exit status is 0
    And sf-js ran "<command>"
    And the command line that runs "<runner>" contains "<pattern>"
    And the command line that runs "<runner>" does not contain "--exclude"
    And the command line that runs "<runner>" does not contain "--testPathIgnorePatterns"

    Examples:
      | runner | command                     | pattern            |
      | vitest | npx --no-install vitest run | .property.test.    |
      | jest   | npx --no-install jest       | \.property\.test\. |

  # sf-js tests 7
  Scenario Outline: sf-js tests 7: a failing runner exits 1
    Given package.json lists "<runner>" in devDependencies
    And the fake <runner> exits with status 1
    When I run sf-js with "<command>"
    Then the exit status is 1

    Examples:
      | runner | command  |
      | vitest | test     |
      | vitest | property |
      | jest   | test     |
      | jest   | property |
