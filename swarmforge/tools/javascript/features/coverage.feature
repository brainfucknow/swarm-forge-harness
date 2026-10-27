Feature: sf-js coverage

  sf-js coverage runs the tests without property tests and writes Istanbul
  JSON coverage to tmp/coverage/coverage-final.json (vitest with the v8
  provider, jest with its default provider).

  Background:
    Given a task worktree branched from main
    And fake JavaScript tools on PATH
    And the worktree has the lockfile "package-lock.json"

  # sf-js coverage 1
  Scenario Outline: sf-js coverage 1: the runner writes Istanbul JSON under tmp/coverage
    Given package.json lists "<runner>" in devDependencies
    When I run sf-js with "coverage"
    Then the exit status is 0
    And sf-js ran "<command>"
    And the command line that runs "<runner>" contains "<argument>"
    And the output has the line "coverage written to tmp/coverage/coverage-final.json"

    Examples:
      | runner | command                     | argument                                 |
      | vitest | npx --no-install vitest run | --coverage.enabled                       |
      | vitest | npx --no-install vitest run | --coverage.provider=v8                   |
      | vitest | npx --no-install vitest run | --coverage.reporter=json                 |
      | vitest | npx --no-install vitest run | --coverage.reportsDirectory=tmp/coverage |
      | vitest | npx --no-install vitest run | **/*.property.test.*                     |
      | jest   | npx --no-install jest       | --coverage                               |
      | jest   | npx --no-install jest       | --coverageReporters=json                 |
      | jest   | npx --no-install jest       | --coverageDirectory=tmp/coverage         |
      | jest   | npx --no-install jest       | \.property\.test\.                       |

  # sf-js coverage 2
  Scenario Outline: sf-js coverage 2: a run that writes no report fails, even over a stale one
    Given package.json lists "vitest" in devDependencies
    And a stale coverage report is <stale> in tmp/coverage
    And the fake vitest writes no coverage report
    When I run sf-js with "coverage"
    Then the exit status is 2
    And the error output contains "no tmp/coverage/coverage-final.json after the coverage run"

    Examples:
      | stale   |
      | present |
      | absent  |

  # sf-js coverage 3
  Scenario Outline: sf-js coverage 3: failing tests exit 1
    Given package.json lists "<runner>" in devDependencies
    And the fake <runner> exits with status 1
    When I run sf-js with "coverage"
    Then the exit status is 1

    Examples:
      | runner |
      | vitest |
      | jest   |
