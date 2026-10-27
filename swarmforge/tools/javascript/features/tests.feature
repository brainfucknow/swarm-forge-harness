# mutation-stamp: sha256=fb6306b7f8dcbbbfccd95314ab4b59aefaad60470f160eea68c503cccfdf5115
# acceptance-mutation-manifest-begin
# {"version":1,"tested_at":"2026-10-27T15:18:10.001108682Z","feature_name":"sf-js tests","feature_path":"swarmforge/tools/javascript/features/tests.feature","background_hash":"e80ae66b77880d9562e2e1cf312f5b4c2e793f43500870531deafd3e85f2419e","implementation_hash":"sha256:809bca8571ff3650633a26f2646fecf8223ebdf348d8a9ac3c71d549903c82e5","scenarios":[{"index":0,"name":"sf-js tests 1: package.json picks the runner","scenario_hash":"3751e24f56d0998791b498fe782ba0334a9e98363a8272ab0a0aadbd3054fe9c","mutation_count":12,"result":{"Total":12,"Killed":12,"Survived":0,"Errors":0},"tested_at":"2026-10-27T15:18:10.001108682Z"},{"index":2,"name":"sf-js tests 3: no runner in package.json fails","scenario_hash":"71efb49747dffb573bbfce6c8f57a5478424cd9796af224341eaedfc147bde63","mutation_count":3,"result":{"Total":3,"Killed":3,"Survived":0,"Errors":0},"tested_at":"2026-10-27T15:18:10.001108682Z"},{"index":3,"name":"sf-js tests 4: test leaves out property tests","scenario_hash":"ff423c12ebfc9df23aaedadc233482c6c474972ee51c8b0bb6aebe0781521a92","mutation_count":6,"result":{"Total":6,"Killed":6,"Survived":0,"Errors":0},"tested_at":"2026-10-27T15:18:10.001108682Z"},{"index":4,"name":"sf-js tests 5: test passes extra arguments to the runner","scenario_hash":"9c0c2463ac19e2102d1af46465646ce1ed4d94c0bfd2a269eaa06eb57dc0ce99","mutation_count":6,"result":{"Total":6,"Killed":6,"Survived":0,"Errors":0},"tested_at":"2026-10-27T15:18:10.001108682Z"},{"index":5,"name":"sf-js tests 6: property runs only property tests","scenario_hash":"58409cd7307c162acbd31f22bb6aa5f5a5531b44680a1bfeb5a372c2813935dc","mutation_count":6,"result":{"Total":6,"Killed":6,"Survived":0,"Errors":0},"tested_at":"2026-10-27T15:18:10.001108682Z"},{"index":6,"name":"sf-js tests 7: a failing runner exits 1","scenario_hash":"850b1638263bf9af6417c8b98d139030e50a93aa7fc8423c68bbebdd69da8f0c","mutation_count":8,"result":{"Total":8,"Killed":8,"Survived":0,"Errors":0},"tested_at":"2026-10-27T15:18:10.001108682Z"}]}
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
