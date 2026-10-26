# acceptance-mutation-manifest-begin
# {"version":1,"tested_at":"2026-10-26T16:36:36.812353307Z","feature_name":"sf-js tests","feature_path":"swarmforge/tools/javascript/features/tests.feature","background_hash":"e80ae66b77880d9562e2e1cf312f5b4c2e793f43500870531deafd3e85f2419e","implementation_hash":"sha256:aa0be03771959c81efd25c5ccbda4ab4e682bac3da3fc9184c3c283c737d3746","scenarios":[{"index":0,"name":"sf-js tests 1: package.json picks the runner","scenario_hash":"3751e24f56d0998791b498fe782ba0334a9e98363a8272ab0a0aadbd3054fe9c","mutation_count":12,"result":{"Total":12,"Killed":12,"Survived":0,"Errors":0},"tested_at":"2026-10-26T16:24:45.780917077Z"},{"index":3,"name":"sf-js tests 4: test leaves out property tests","scenario_hash":"ff423c12ebfc9df23aaedadc233482c6c474972ee51c8b0bb6aebe0781521a92","mutation_count":6,"result":{"Total":6,"Killed":6,"Survived":0,"Errors":0},"tested_at":"2026-10-26T16:24:45.780917077Z"}]}
# acceptance-mutation-manifest-end

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
    Given package.json lists "<package>" in devDependencies
    When I run sf-js with "<command>"
    Then the exit status is 2
    And the error output contains "no test runner (vitest or jest) in package.json"

    Examples:
      | package | command  |
      | mocha   | test     |
      | ava     | property |
      | mocha   | coverage |

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
    And the command line that runs "<runner>" ends with "<extra>"

    Examples:
      | runner | extra          |
      | vitest | --reporter=dot |
      | jest   | --ci --silent  |

  # sf-js tests 6
  Scenario Outline: sf-js tests 6: property runs only property tests
    Given package.json lists "<runner>" in devDependencies
    When I run sf-js with "property"
    Then the exit status is 0
    And sf-js ran "<command>"
    And the command line that runs "<runner>" contains "<pattern>"
    And the command line that runs "<runner>" does not contain "<flag>"

    Examples:
      | runner | command                     | pattern            | flag                     |
      | vitest | npx --no-install vitest run | .property.test.    | --exclude                |
      | jest   | npx --no-install jest       | \.property\.test\. | --testPathIgnorePatterns |

  # sf-js tests 7
  Scenario Outline: sf-js tests 7: a failing runner exits 1
    Given package.json lists "<runner>" in devDependencies
    And the fake <runner> exits with status <runner_status>
    When I run sf-js with "<command>"
    Then the exit status is <status>

    Examples:
      | runner | command  | runner_status | status |
      | vitest | test     | 1             | 1      |
      | vitest | property | 1             | 1      |
      | jest   | test     | 1             | 1      |
      | jest   | property | 0             | 0      |
