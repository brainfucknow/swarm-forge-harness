Feature: sf-js crap

  If package.json lists eslint and not oxlint, sf-js crap runs ESLint's
  complexity rule (typescript-eslint parser, with sf-js's own ESLint config)
  through the package manager and joins it with
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
    And package.json lists "eslint" in devDependencies

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
      | 5  | 3       | 5          | 0      | 0    | ok   crap    6.6  cc   5  cov  60%  src/ledger.ts:10 withdraw |

  # sf-js crap 1
  Scenario: sf-js crap 1: a function at CRAP 10 is not over
    Given ESLint reports "withdraw" at line 10 of "src/ledger.ts" with complexity 10
    And Istanbul covers 4 of 4 statements of "withdraw" on lines 10 to 17 of "src/ledger.ts"
    And line 12 of "src/ledger.ts" changed on the task branch
    When I run sf-js with "crap --base main"
    Then the exit status is 0
    And the output has the line "ok   crap   10.0  cc  10  cov 100%  src/ledger.ts:10 withdraw"
    And the output ends with the line "1 changed function(s), 0 over CRAP 10"

  # sf-js crap 1
  Scenario: sf-js crap 1: a function none of whose statements ran has 0% coverage
    Given ESLint reports "withdraw" at line 10 of "src/ledger.ts" with complexity 2
    And Istanbul covers 0 of 3 statements of "withdraw" on lines 10 to 17 of "src/ledger.ts"
    And line 12 of "src/ledger.ts" changed on the task branch
    When I run sf-js with "crap --base main"
    Then the exit status is 0
    And the output has the line "ok   crap    6.0  cc   2  cov   0%  src/ledger.ts:10 withdraw"
    And the output ends with the line "1 changed function(s), 0 over CRAP 10"

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

  # sf-js crap 2
  Scenario: sf-js crap 2: a covered nested function is scored on its own statements
    Given ESLint reports "settle" at line 20 of "src/ledger.ts" with complexity 2
    And Istanbul covers 2 of 4 statements of "settle" on lines 20 to 30 of "src/ledger.ts"
    And ESLint reports "(anonymous)" at line 22 of "src/ledger.ts" with complexity 1
    And Istanbul covers 3 of 3 statements of "(anonymous)" on lines 22 to 25 of "src/ledger.ts"
    And line 23 of "src/ledger.ts" changed on the task branch
    When I run sf-js with "crap --base main"
    Then the exit status is 0
    And the output has the line "ok   crap    2.5  cc   2  cov  50%  src/ledger.ts:20 settle"
    And the output has the line "ok   crap    1.0  cc   1  cov 100%  src/ledger.ts:22 (anonymous)"
    And the output ends with the line "2 changed function(s), 0 over CRAP 10"

  # sf-js crap 2
  Scenario: sf-js crap 2: a fully covered outer function is scored apart from its uncovered nested function
    Given ESLint reports "settle" at line 20 of "src/ledger.ts" with complexity 2
    And Istanbul covers 4 of 4 statements of "settle" on lines 20 to 30 of "src/ledger.ts"
    And ESLint reports "(anonymous)" at line 22 of "src/ledger.ts" with complexity 1
    And Istanbul covers 0 of 3 statements of "(anonymous)" on lines 22 to 25 of "src/ledger.ts"
    And line 23 of "src/ledger.ts" changed on the task branch
    When I run sf-js with "crap --base main"
    Then the exit status is 0
    And the output has the line "ok   crap    2.0  cc   2  cov 100%  src/ledger.ts:20 settle"
    And the output has the line "ok   crap    2.0  cc   1  cov   0%  src/ledger.ts:22 (anonymous)"
    And the output ends with the line "2 changed function(s), 0 over CRAP 10"

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

  # sf-js crap 4
  Scenario: sf-js crap 4: a function with no statements that was called is covered
    Given ESLint reports "noop" at line 10 of "src/ledger.ts" with complexity 1
    And Istanbul counts 3 calls of "noop" on lines 10 to 10 of "src/ledger.ts" with no statements
    And line 10 of "src/ledger.ts" changed on the task branch
    When I run sf-js with "crap --base main"
    Then the exit status is 0
    And the output has the line "ok   crap    1.0  cc   1  cov 100%  src/ledger.ts:10 noop"

  # sf-js crap 5
  Scenario: sf-js crap 5: crap needs a coverage report in tmp/coverage
    Given line 12 of "src/ledger.ts" changed on the task branch
    When I run sf-js with "crap --base main"
    Then the exit status is 2
    And the error output contains "no coverage-final.json under tmp/coverage; run sf-js coverage first"

  # sf-js crap 5
  Scenario: sf-js crap 5: crap needs a coverage report in the directory --report names
    Given line 12 of "src/ledger.ts" changed on the task branch
    When I run sf-js with "crap --base main --report reports/cov"
    Then the exit status is 2
    And the error output contains "no coverage-final.json under reports/cov; run sf-js coverage first"

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
  Scenario: sf-js crap 7: ESLint runs through pnpm
    Given package.json declares packageManager "pnpm@9.1.0"
    And the worktree has the lockfile "pnpm-lock.yaml"
    And ESLint reports "withdraw" at line 10 of "src/ledger.ts" with complexity 4
    And Istanbul covers 4 of 4 statements of "withdraw" on lines 10 to 17 of "src/ledger.ts"
    And line 12 of "src/ledger.ts" changed on the task branch
    When I run sf-js with "crap --base main"
    Then the exit status is 0
    And sf-js ran "pnpm exec eslint"

  # sf-js crap 7
  Scenario: sf-js crap 7: ESLint runs through yarn
    Given package.json declares packageManager "yarn@4.5.0"
    And the worktree has the lockfile "yarn.lock"
    And ESLint reports "withdraw" at line 10 of "src/ledger.ts" with complexity 4
    And Istanbul covers 4 of 4 statements of "withdraw" on lines 10 to 17 of "src/ledger.ts"
    And line 12 of "src/ledger.ts" changed on the task branch
    When I run sf-js with "crap --base main"
    Then the exit status is 0
    And sf-js ran "yarn eslint"
