Feature: sf tool crap4clj directory

  The crap4clj wrapper runs crap4clj in D, the directory that holds the first
  --source-root or -s value the caller passed (its parent directory). It
  replaces each --source-root and -s value with that path relative to D, and
  passes every other argument unchanged. The coverage rewrite then looks for
  spec, test and deps.edn in D, so the result, target/ and .metrics/ are
  exactly as when crap4clj runs from D. Without a source root, or when the
  first one sits in the current directory, D is the current directory. When
  D does not exist the wrapper exits 1 without running crap4clj.

  Background:
    Given a project with swarmforge initialized
    And a fake bb on PATH
    And a tool source whose bb.edn defines the tasks "crap"
    And "sf tool ensure crap4clj" ran in the main checkout
    And clj is present on PATH
    And deps.edn is present

  # sf tool crap4clj directory 1
  Scenario Outline: sf tool crap4clj directory 1: the wrapper runs crap4clj in the directory of the first source root
    Given the directories "<dirs>" exist
    When I run the crap4clj wrapper with "<args>"
    Then bb ran in "MAIN/tools/x"
    And bb received "--config SRC/bb.edn crap <received>" and the coverage command "<command>"

    Examples:
      | dirs                                    | args                                               | received                                | command                                                                 |
      | test tools/x/src tools/x/spec           | --source-root tools/x/src                          | --source-root src                       | bb --config CFG coverage -r speclj -p src -s spec --lcov                |
      | spec tools/x/src tools/x/test           | -s tools/x/src                                     | -s src                                  | bb --config CFG coverage -p src -s test --lcov                          |
      | tools/x/src tools/x/spec tools/core/src | --source-root tools/x/src -s tools/core/src orders | --source-root src -s ../core/src orders | bb --config CFG coverage -r speclj -p src -p ../core/src -s spec --lcov |
      | tools/x/src                             | --source-root MAIN/tools/x/src                     | --source-root src                       | bb --config CFG coverage -p src --lcov                                  |

  # sf tool crap4clj directory 2
  Scenario Outline: sf tool crap4clj directory 2: the wrapper leaves coverage to the caller in that directory
    Given the directories "tools/x/src tools/x/test" exist
    And the directory "tools/x" has <deps>
    When I run the crap4clj wrapper with "<args>"
    Then bb ran in "MAIN/tools/x"
    And bb received "--config SRC/bb.edn crap <received>"

    Examples:
      | deps        | args                                                                          | received                                                              |
      | deps.edn    | --source-root tools/x/src                                                     | --source-root src                                                     |
      | no deps.edn | --source-root tools/x/src --use-existing-coverage --lcov target/coverage.info | --source-root src --use-existing-coverage --lcov target/coverage.info |

  # sf tool crap4clj directory 3
  Scenario Outline: sf tool crap4clj directory 3: the wrapper stays in the current directory
    Given the directories "src lib tools/x/src" exist
    When I run the crap4clj wrapper with "<args>"
    Then bb ran in "MAIN"
    And bb received "--config SRC/bb.edn crap <args>"

    Examples:
      | args                             |
      |                                  |
      | --source-root lib -s tools/x/src |

  # sf tool crap4clj directory 4
  Scenario: sf tool crap4clj directory 4: the wrapper fails when the directory is missing
    When I run the crap4clj wrapper with "--source-root missing/src"
    Then the exit status is 1
    And bb did not run
    And the error output contains "missing"
