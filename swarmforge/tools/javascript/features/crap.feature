# acceptance-mutation-manifest-begin
# {"version":1,"tested_at":"2026-10-26T16:31:25.220055529Z","feature_name":"sf-js crap","feature_path":"swarmforge/tools/javascript/features/crap.feature","background_hash":"a6be7b8e271949a5c9294cd79d75fbac3083ec9d2045cbb6b39e6fefaa1aeb2d","implementation_hash":"sha256:5c1133dc1e7293a7f4d3a3b78355d8ec0cb91a44bbe5f9dc4d942b3152289db4","scenarios":[{"index":2,"name":"sf-js crap 3: a function missing from the coverage report counts as uncovered","scenario_hash":"b59ca1065a5b9afcf5f90192ffdaa99254b923e53e2e5ca60d2e3d3fcf56fec8","mutation_count":6,"result":{"Total":6,"Killed":6,"Survived":0,"Errors":0},"tested_at":"2026-10-26T16:15:35.089169587Z"}]}
# acceptance-mutation-manifest-end

Feature: sf-js crap

  sf-js crap runs ESLint's complexity rule (typescript-eslint parser, with
  sf-js's own ESLint config) through the package manager and joins it with
  tmp/coverage/coverage-final.json. CRAP is comp^2 * (1 - coverage)^3 + comp;
  coverage is the share of the function's own Istanbul statements that ran,
  leaving out statements of nested functions. A function is changed when any
  line from its first to its last line changed. Exit 1 when any listed
  function is over CRAP 10. A function ESLint reports without a name (for
  example "Arrow function has a complexity of 1.") is "(anonymous)".

  Background:
    Given a main branch where "src/ledger.ts" has 40 lines
    And a task worktree branched from main
    And fake JavaScript tools on PATH
    And the worktree has the lockfile "package-lock.json"
    And package.json lists "vitest" in devDependencies

  # sf-js crap 1
  Scenario Outline: sf-js crap 1: a changed function is scored and gated at 10
    Given ESLint reports "withdraw" at line 10 of "src/ledger.ts" with complexity <cc>
    And Istanbul covers <covered> of <statements> statements of "withdraw" on lines 10 to 17 of "src/ledger.ts"
    And line 12 of "src/ledger.ts" changed on the task branch
    When I run sf-js with "crap --base main"
    Then the exit status is <status>
    And the output has the line "<row>"
    And the output ends with the line "1 changed function(s), <over> over CRAP 10"

    Examples:
      | cc | covered | statements | status | over | row                                                           |
      | 4  | 1       | 4          | 1      | 1    | OVER crap   10.8  cc   4  cov  25%  src/ledger.ts:10 withdraw |
      | 4  | 4       | 4          | 0      | 0    | ok   crap    4.0  cc   4  cov 100%  src/ledger.ts:10 withdraw |
      | 6  | 2       | 4          | 1      | 1    | OVER crap   10.5  cc   6  cov  50%  src/ledger.ts:10 withdraw |
      | 10 | 4       | 4          | 0      | 0    | ok   crap   10.0  cc  10  cov 100%  src/ledger.ts:10 withdraw |
      | 2  | 0       | 3          | 0      | 0    | ok   crap    6.0  cc   2  cov   0%  src/ledger.ts:10 withdraw |
      | 5  | 3       | 5          | 0      | 0    | ok   crap    6.6  cc   5  cov  60%  src/ledger.ts:10 withdraw |

  # sf-js crap 2
  Scenario Outline: sf-js crap 2: a nested function's statements do not count toward the outer function
    Given ESLint reports "settle" at line 20 of "src/ledger.ts" with complexity 2
    And Istanbul covers <outer_covered> of 4 statements of "settle" on lines 20 to 30 of "src/ledger.ts"
    And ESLint reports "(anonymous)" at line 22 of "src/ledger.ts" with complexity 1
    And Istanbul covers <inner_covered> of 3 statements of "(anonymous)" on lines 22 to 25 of "src/ledger.ts"
    And line 23 of "src/ledger.ts" changed on the task branch
    When I run sf-js with "crap --base main"
    Then the exit status is 0
    And the output has the line "<outer_row>"
    And the output has the line "<inner_row>"
    And the output ends with the line "2 changed function(s), 0 over CRAP 10"

    Examples:
      | outer_covered | inner_covered | outer_row                                                   | inner_row                                                        |
      | 2             | 0             | ok   crap    2.5  cc   2  cov  50%  src/ledger.ts:20 settle | ok   crap    2.0  cc   1  cov   0%  src/ledger.ts:22 (anonymous) |
      | 2             | 3             | ok   crap    2.5  cc   2  cov  50%  src/ledger.ts:20 settle | ok   crap    1.0  cc   1  cov 100%  src/ledger.ts:22 (anonymous) |
      | 4             | 0             | ok   crap    2.0  cc   2  cov 100%  src/ledger.ts:20 settle | ok   crap    2.0  cc   1  cov   0%  src/ledger.ts:22 (anonymous) |

  # sf-js crap 3
  Scenario Outline: sf-js crap 3: a function missing from the coverage report counts as uncovered
    Given ESLint reports "refund" at line 10 of "src/ledger.ts" with complexity <cc>
    And Istanbul covers 2 of 2 statements of "deposit" on lines 3 to 8 of "src/ledger.ts"
    And line 10 of "src/ledger.ts" changed on the task branch
    When I run sf-js with "crap --base main"
    Then the exit status is <status>
    And the output has the line "<row>"

    Examples:
      | cc | status | row                                                         |
      | 2  | 0      | ok   crap    6.0  cc   2  cov   0%  src/ledger.ts:10 refund |
      | 3  | 1      | OVER crap   12.0  cc   3  cov   0%  src/ledger.ts:10 refund |

  # sf-js crap 4
  Scenario Outline: sf-js crap 4: a function with no statements is covered when it was called
    Given ESLint reports "noop" at line 10 of "src/ledger.ts" with complexity 1
    And Istanbul counts <calls> calls of "noop" on lines 10 to 10 of "src/ledger.ts" with no statements
    And line 10 of "src/ledger.ts" changed on the task branch
    When I run sf-js with "crap --base main"
    Then the exit status is 0
    And the output has the line "<row>"

    Examples:
      | calls | row                                                       |
      | 0     | ok   crap    2.0  cc   1  cov   0%  src/ledger.ts:10 noop |
      | 3     | ok   crap    1.0  cc   1  cov 100%  src/ledger.ts:10 noop |

  # sf-js crap 5
  Scenario Outline: sf-js crap 5: crap needs a coverage report
    Given line 12 of "src/ledger.ts" changed on the task branch
    When I run sf-js with "<command>"
    Then the exit status is 2
    And the error output contains "no coverage-final.json under <dir>; run sf-js coverage first"

    Examples:
      | command                               | dir          |
      | crap --base main                      | tmp/coverage |
      | crap --base main --report reports/cov | reports/cov  |

  # sf-js crap 6
  Scenario: sf-js crap 6: --report reads coverage from another directory
    Given ESLint reports "withdraw" at line 10 of "src/ledger.ts" with complexity 4
    And Istanbul covers 1 of 4 statements of "withdraw" on lines 10 to 17 of "src/ledger.ts"
    And the coverage report is moved to "reports/cov"
    And line 12 of "src/ledger.ts" changed on the task branch
    When I run sf-js with "crap --base main --report reports/cov"
    Then the exit status is 1
    And the output has the line "OVER crap   10.8  cc   4  cov  25%  src/ledger.ts:10 withdraw"

  # sf-js crap 7
  Scenario Outline: sf-js crap 7: ESLint runs through the package manager
    Given package.json declares packageManager "<declared>"
    And the worktree has the lockfile "<lockfile>"
    And ESLint reports "withdraw" at line 10 of "src/ledger.ts" with complexity 4
    And Istanbul covers 4 of 4 statements of "withdraw" on lines 10 to 17 of "src/ledger.ts"
    And line 12 of "src/ledger.ts" changed on the task branch
    When I run sf-js with "crap --base main"
    Then the exit status is 0
    And sf-js ran "<command>"

    Examples:
      | declared   | lockfile       | command          |
      | pnpm@9.1.0 | pnpm-lock.yaml | pnpm exec eslint |
      | yarn@4.5.0 | yarn.lock      | yarn eslint      |
