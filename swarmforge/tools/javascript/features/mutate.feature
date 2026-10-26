# acceptance-mutation-manifest-begin
# {"version":1,"tested_at":"2026-10-26T16:34:46.596399759Z","feature_name":"sf-js mutate","feature_path":"swarmforge/tools/javascript/features/mutate.feature","background_hash":"9318ee40a2565fae6d1fa410d1e4bab0659cfbdcc1bbf09c0c82fd401e6270c4","implementation_hash":"sha256:f200c3be7cb602ed0ad36e23c5610e62fc16c2d1c32c23fad46046150f71c544","scenarios":[{"index":10,"name":"sf-js mutate 11: the score leaves out Ignored, CompileError and RuntimeError mutants","scenario_hash":"ffdf3813cf0edd66a6bea9e3106e1624f66315cac45e9e54ec5fa32e6e4050c7","mutation_count":36,"result":{"Total":36,"Killed":36,"Survived":0,"Errors":0},"tested_at":"2026-10-26T16:22:00.060803841Z"}]}
# acceptance-mutation-manifest-end

Feature: sf-js mutate

  sf-js mutate runs StrykerJS through the package manager on the task's
  changed source files (test files and .d.ts files left out), incrementally,
  with the JSON report at tmp/stryker/mutation.json. It lists every Survived
  or NoCoverage mutant and exits 1 when there is any. --report-only re-reads
  the last report.

  Background:
    Given a task worktree branched from main
    And fake JavaScript tools on PATH
    And the worktree has the lockfile "package-lock.json"
    And package.json lists "vitest" in devDependencies

  # sf-js mutate 1
  Scenario Outline: sf-js mutate 1: a changed source file is mutated
    Given "<file>" is added on the task branch
    When I run sf-js with "mutate --base main"
    Then sf-js ran "npx --no-install stryker run"
    And the command line that runs "stryker" contains "--mutate <file>"

    Examples:
      | file          |
      | src/ledger.ts |
      | src/ledger.js |
      | src/view.tsx  |
      | src/view.jsx  |
      | src/tool.mjs  |
      | src/tool.cts  |

  # sf-js mutate 2
  Scenario Outline: sf-js mutate 2: tests and declarations are not mutated
    Given "<file>" is added on the task branch
    When I run sf-js with "mutate --base main"
    Then the exit status is 0
    And the output has the line "no changed source files to mutate"
    And sf-js did not run "stryker"

    Examples:
      | file                        |
      | src/ledger.test.ts          |
      | src/ledger.spec.js          |
      | src/ledger.property.test.ts |
      | src/__tests__/ledger.ts     |
      | src/types.d.ts              |
      | README.md                   |

  # sf-js mutate 3
  Scenario: sf-js mutate 3: several changed files go to one --mutate, sorted
    Given "src/b.ts" is added on the task branch
    And "src/a.ts" is added on the task branch
    And "src/a.test.ts" is added on the task branch
    When I run sf-js with "mutate --base main"
    Then the command line that runs "stryker" contains "--mutate src/a.ts,src/b.ts"

  # sf-js mutate 4
  Scenario Outline: sf-js mutate 4: Stryker runs incrementally with the project's runner
    Given package.json lists "<runner>" in devDependencies
    And "src/ledger.ts" is added on the task branch
    When I run sf-js with "mutate --base main"
    Then the command line that runs "stryker" contains "<argument>"

    Examples:
      | runner | argument                                       |
      | vitest | --testRunner vitest                            |
      | jest   | --testRunner jest                              |
      | vitest | --incremental                                  |
      | vitest | --incrementalFile tmp/stryker/incremental.json |
      | vitest | --reporters json,progress                      |
      | vitest | --concurrency 4                                |

  # sf-js mutate 5
  Scenario: sf-js mutate 5: the TypeScript checker runs with a tsconfig.json
    Given tsconfig.json is present
    And "src/ledger.ts" is added on the task branch
    When I run sf-js with "mutate --base main"
    Then the command line that runs "stryker" contains "--checkers typescript"

  # sf-js mutate 6
  Scenario: sf-js mutate 6: no TypeScript checker without a tsconfig.json
    Given tsconfig.json is absent
    And "src/ledger.ts" is added on the task branch
    When I run sf-js with "mutate --base main"
    Then the command line that runs "stryker" does not contain "--checkers"

  # sf-js mutate 7
  Scenario: sf-js mutate 7: --all mutates what Stryker's own configuration names
    Given "src/ledger.ts" is added on the task branch
    When I run sf-js with "mutate --all"
    Then sf-js ran "npx --no-install stryker run"
    And the command line that runs "stryker" does not contain "--mutate"

  # sf-js mutate 8
  Scenario Outline: sf-js mutate 8: arguments after -- go to Stryker
    Given "src/ledger.ts" is added on the task branch
    When I run sf-js with "mutate --base main -- <extra>"
    Then the command line that runs "stryker" ends with "<extra>"
    And the command line that runs "stryker" contains "--concurrency 4"

    Examples:
      | extra            |
      | --timeoutMS 9000 |
      | --logLevel debug |

  # sf-js mutate 9
  Scenario Outline: sf-js mutate 9: a caller's --concurrency replaces the default
    Given "src/ledger.ts" is added on the task branch
    When I run sf-js with "mutate --base main -- --concurrency <workers>"
    Then the command line that runs "stryker" ends with "--concurrency <workers>"
    And the command line that runs "stryker" does not contain "--concurrency 4"

    Examples:
      | workers |
      | 2       |
      | 1       |

  # sf-js mutate 10
  Scenario Outline: sf-js mutate 10: survivors are listed and fail the run
    Given the Stryker report has a <status> "<mutator>" mutant in "src/ledger.ts" at line <line> replacing it with "<replacement>"
    When I run sf-js with "mutate --report-only"
    Then the exit status is <exit>
    And the output has the line "<row>"

    Examples:
      | status     | mutator          | line | replacement     | exit | row                                                            |
      | Survived   | EqualityOperator | 14   | amount >= total | 1    | Survived   src/ledger.ts:14  EqualityOperator  amount >= total |
      | NoCoverage | BooleanLiteral   | 21   | true            | 1    | NoCoverage src/ledger.ts:21  BooleanLiteral  true              |
      | Killed     | EqualityOperator | 14   | amount >= total | 0    | mutation score 100.0% (Killed 1)                               |

  # sf-js mutate 11
  Scenario Outline: sf-js mutate 11: the score leaves out Ignored, CompileError and RuntimeError mutants
    Given the Stryker report has <killed> mutants with status "Killed"
    And the Stryker report has <survived> mutants with status "Survived"
    And the Stryker report has <nocoverage> mutants with status "NoCoverage"
    And the Stryker report has <timeout> mutants with status "Timeout"
    And the Stryker report has <runtime> mutants with status "RuntimeError"
    And the Stryker report has <compile> mutants with status "CompileError"
    And the Stryker report has <ignored> mutants with status "Ignored"
    When I run sf-js with "mutate --report-only"
    Then the exit status is <exit>
    And the output ends with the line "<summary>"

    Examples:
      | killed | survived | nocoverage | timeout | runtime | compile | ignored | exit | summary                                                                                |
      | 2      | 1        | 1          | 0       | 0       | 0       | 0       | 1    | mutation score 50.0% (Killed 2, NoCoverage 1, Survived 1)                              |
      | 1      | 2        | 0          | 0       | 0       | 0       | 0       | 1    | mutation score 33.3% (Killed 1, Survived 2)                                            |
      | 1      | 0        | 0          | 1       | 1       | 1       | 1       | 0    | mutation score 100.0% (CompileError 1, Ignored 1, Killed 1, RuntimeError 1, Timeout 1) |
      | 0      | 0        | 0          | 0       | 0       | 0       | 2       | 0    | mutation score 100.0% (Ignored 2)                                                      |

  # sf-js mutate 12
  Scenario: sf-js mutate 12: --report-only needs an earlier report
    When I run sf-js with "mutate --report-only"
    Then the exit status is 2
    And the error output contains "tmp/stryker/mutation.json not found"
    And sf-js did not run "stryker"

  # sf-js mutate 13
  Scenario Outline: sf-js mutate 13: Stryker that leaves no report fails
    Given "src/ledger.ts" is added on the task branch
    And the fake stryker exits with status <stryker_status> without a report
    When I run sf-js with "mutate --base main"
    Then the exit status is 2
    And the error output contains "stryker exited <stryker_status> without tmp/stryker/mutation.json"

    Examples:
      | stryker_status |
      | 1              |
      | 3              |
