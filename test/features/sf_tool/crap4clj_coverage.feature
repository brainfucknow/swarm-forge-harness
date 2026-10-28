Feature: sf tool crap4clj coverage

  sf tool ensure crap4clj also writes a Babashka coverage config, CFG, at
  .swarmforge/tools/crap4clj-coverage/bb.edn under the main checkout, outside
  the crap4clj clone. Its coverage task puts the crap4clj clone's src and the
  -p and -s directories it is given (relative to the current directory) on the
  classpath, enables crap4clj.bb-coverage, loads the speclj Cloverage runner
  when it gets "-r speclj", and runs Cloverage with its arguments. CFG pins
  cloverage at git sha 61e3cac426e9907a9dd01c37597f85c71a57ff90 with deps root
  "cloverage" (the Babashka-compatible commit crap4clj documents) and speclj
  at 3.12.2.

  The crap4clj wrapper appends --coverage-command and one argument,
  "bb --config CFG coverage [-r speclj] -p R ... -s T ... --lcov", when the
  Clojure CLI clj is not on PATH or the directory crap4clj runs in (see
  crap4clj_directory.feature) has no deps.edn. R is each --source-root or -s
  value crap4clj gets, in order, else src. T is spec and then test, each only
  when that directory exists in the directory crap4clj runs in. "-r speclj"
  is there only when spec exists. The wrapper
  adds nothing when the caller passes --coverage-command or
  --use-existing-coverage, or when clj is on PATH and deps.edn exists.

  Background:
    Given a project with swarmforge initialized
    And a fake bb on PATH
    And a tool source whose bb.edn defines the tasks "crap"
    And "sf tool ensure crap4clj" ran in the main checkout

  # sf tool crap4clj coverage 1
  Scenario Outline: sf tool crap4clj coverage 1: ensure writes the coverage config
    Then the file "CFG" exists
    And the file "CFG" mentions "<text>"

    Examples:
      | text                                     |
      | 61e3cac426e9907a9dd01c37597f85c71a57ff90 |
      | crap4clj.bb-coverage                     |
      | 3.12.2                                   |

  # sf tool crap4clj coverage 2
  Scenario Outline: sf tool crap4clj coverage 2: the wrapper adds a Babashka coverage command
    Given clj is <clj> on PATH
    And deps.edn is <deps>
    And the directories "<dirs>" exist
    When I run the crap4clj wrapper with "<args>"
    Then bb received "--config SRC/bb.edn crap <args>" and the coverage command "<command>"

    Examples:
      | clj     | deps    | dirs          | args                         | command                                                                     |
      | absent  | absent  | src test      |                              | bb --config CFG coverage -p src -s test --lcov                              |
      | absent  | absent  | src spec      |                              | bb --config CFG coverage -r speclj -p src -s spec --lcov                    |
      | absent  | absent  | lib spec test | --source-root lib -s scripts | bb --config CFG coverage -r speclj -p lib -p scripts -s spec -s test --lcov |
      | absent  | present | src           |                              | bb --config CFG coverage -p src --lcov                                      |
      | present | absent  | src test      |                              | bb --config CFG coverage -p src -s test --lcov                              |

  # sf tool crap4clj coverage 3
  Scenario Outline: sf tool crap4clj coverage 3: the wrapper adds nothing when coverage is settled
    Given clj is <clj> on PATH
    And deps.edn is <deps>
    And the directories "src test" exist
    When I run the crap4clj wrapper with "<args>"
    Then bb received "--config SRC/bb.edn crap <args>"

    Examples:
      | clj     | deps    | args                         |
      | present | present |                              |
      | absent  | absent  | --coverage-command make-lcov |
      | absent  | absent  | --use-existing-coverage      |
