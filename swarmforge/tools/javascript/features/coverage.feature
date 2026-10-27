# mutation-stamp: sha256=b19981a730347867a8c261d37d29d21121a928d2ee66d6e8dd426800dff1b54a
# acceptance-mutation-manifest-begin
# {"version":1,"tested_at":"2026-10-27T15:10:18.917670896Z","feature_name":"sf-js coverage","feature_path":"swarmforge/tools/javascript/features/coverage.feature","background_hash":"e80ae66b77880d9562e2e1cf312f5b4c2e793f43500870531deafd3e85f2419e","implementation_hash":"sha256:2c40f573896b8643fde62e6da50c6c5824348e3e15e572c12b7b19bf3181083a","scenarios":[{"index":0,"name":"sf-js coverage 1: the runner writes Istanbul JSON under tmp/coverage","scenario_hash":"97b912652bcbd4e4c657a4ccad3f0fd30ee9f73412f49567ee0d01a6fb5c9cdb","mutation_count":27,"result":{"Total":27,"Killed":27,"Survived":0,"Errors":0},"tested_at":"2026-10-27T15:10:18.917670896Z"},{"index":1,"name":"sf-js coverage 2: a run that writes no report fails, even over a stale one","scenario_hash":"63e0af163412a363c40aa4427a5e3c8cb7a261be8df0f5d2cdf90bb26bd3ff96","mutation_count":2,"result":{"Total":2,"Killed":2,"Survived":0,"Errors":0},"tested_at":"2026-10-27T15:10:18.917670896Z"},{"index":2,"name":"sf-js coverage 3: failing tests exit 1","scenario_hash":"249a36b702db32d8e648ced2ff9259620285011417c366cd3bf7cc2bcbd268dd","mutation_count":2,"result":{"Total":2,"Killed":2,"Survived":0,"Errors":0},"tested_at":"2026-10-27T15:10:18.917670896Z"}]}
# acceptance-mutation-manifest-end

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
