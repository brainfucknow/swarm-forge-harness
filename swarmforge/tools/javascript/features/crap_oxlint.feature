Feature: sf-js crap with oxlint

  With oxlint as the complexity source, sf-js crap scores and prints
  functions exactly as it does with ESLint. It reads oxlint's
  eslint(complexity) diagnostics and ignores every other one. The function's
  name is the one between backticks in the message, with "#" in front when
  the message starts with "private" (private method `x` is #x). A message with no name between backticks
  ("function has a complexity of 2.") is "(anonymous)". The function starts
  at the diagnostic's line; a function missing from the coverage report
  spans only that line.

  sf-js's oxlint config turns eslint-disable comments off, but oxlint always
  honors its own oxlint-disable comments, so one can hide a function from
  CRAP. The operator chose a warning over a failure: for each line of a
  linted file holding an oxlint-disable, oxlint-disable-line or
  oxlint-disable-next-line directive that names no rule, or names complexity
  or eslint/complexity among its rules, sf-js crap prints
  "sf-js: warning: FILE:LINE has an oxlint-disable directive that can hide a
  function from CRAP" on stderr. The warning does not change the exit
  status.

  Background:
    Given a main branch where "src/ledger.ts" has 40 lines
    And a task worktree branched from main
    And fake JavaScript tools on PATH
    And the worktree has the lockfile "package-lock.json"
    And package.json lists the devDependencies "vitest oxlint"
    And line 12 of "src/ledger.ts" changed on the task branch

  # sf-js crap with oxlint 1
  Scenario Outline: sf-js crap with oxlint 1: a changed function is scored and gated at 10
    Given oxlint reports "withdraw" at line 10 of "src/ledger.ts" with complexity <cc>
    And Istanbul covers <covered> of 4 statements of "withdraw" on lines 10 to 17 of "src/ledger.ts"
    When I run sf-js with "crap --base main"
    Then the exit status is <status>
    And the output has the line "<row>"
    And the output ends with the line "1 changed function(s), <over> over CRAP 10"

    Examples:
      | cc | covered | status | over | row                                                           |
      | 4  | 1       | 1      | 1    | OVER crap   10.8  cc   4  cov  25%  src/ledger.ts:10 withdraw |
      | 4  | 4       | 0      | 0    | ok   crap    4.0  cc   4  cov 100%  src/ledger.ts:10 withdraw |
      | 6  | 2       | 1      | 1    | OVER crap   10.5  cc   6  cov  50%  src/ledger.ts:10 withdraw |

  # sf-js crap with oxlint 2
  Scenario: sf-js crap with oxlint 2: oxlint's name for a function prints as withdraw
    Given oxlint reports "function `withdraw` has a complexity of 4. Maximum allowed is 0." with code "eslint(complexity)" at line 10 of "src/ledger.ts"
    And Istanbul covers 1 of 4 statements of "withdraw" on lines 10 to 17 of "src/ledger.ts"
    When I run sf-js with "crap --base main"
    Then the exit status is 1
    And the output has the line "OVER crap   10.8  cc   4  cov  25%  src/ledger.ts:10 withdraw"

  # sf-js crap with oxlint 2
  Scenario: sf-js crap with oxlint 2: oxlint's name for a method prints as withdraw
    Given oxlint reports "method `withdraw` has a complexity of 4. Maximum allowed is 0." with code "eslint(complexity)" at line 10 of "src/ledger.ts"
    And Istanbul covers 1 of 4 statements of "withdraw" on lines 10 to 17 of "src/ledger.ts"
    When I run sf-js with "crap --base main"
    Then the exit status is 1
    And the output has the line "OVER crap   10.8  cc   4  cov  25%  src/ledger.ts:10 withdraw"

  # sf-js crap with oxlint 2
  Scenario: sf-js crap with oxlint 2: oxlint's name for a getter prints as withdraw
    Given oxlint reports "getter `withdraw` has a complexity of 4. Maximum allowed is 0." with code "eslint(complexity)" at line 10 of "src/ledger.ts"
    And Istanbul covers 1 of 4 statements of "withdraw" on lines 10 to 17 of "src/ledger.ts"
    When I run sf-js with "crap --base main"
    Then the exit status is 1
    And the output has the line "OVER crap   10.8  cc   4  cov  25%  src/ledger.ts:10 withdraw"

  # sf-js crap with oxlint 2
  Scenario: sf-js crap with oxlint 2: oxlint's name for a private method prints as #withdraw
    Given oxlint reports "private method `withdraw` has a complexity of 4. Maximum allowed is 0." with code "eslint(complexity)" at line 10 of "src/ledger.ts"
    And Istanbul covers 1 of 4 statements of "withdraw" on lines 10 to 17 of "src/ledger.ts"
    When I run sf-js with "crap --base main"
    Then the exit status is 1
    And the output has the line "OVER crap   10.8  cc   4  cov  25%  src/ledger.ts:10 #withdraw"

  # sf-js crap with oxlint 2
  Scenario: sf-js crap with oxlint 2: oxlint's name for a function prints as (anonymous)
    Given oxlint reports "function has a complexity of 4. Maximum allowed is 0." with code "eslint(complexity)" at line 10 of "src/ledger.ts"
    And Istanbul covers 1 of 4 statements of "withdraw" on lines 10 to 17 of "src/ledger.ts"
    When I run sf-js with "crap --base main"
    Then the exit status is 1
    And the output has the line "OVER crap   10.8  cc   4  cov  25%  src/ledger.ts:10 (anonymous)"

  # sf-js crap with oxlint 2
  Scenario: sf-js crap with oxlint 2: oxlint's name for a class static block prints as (anonymous)
    Given oxlint reports "class static block has a complexity of 4. Maximum allowed is 0." with code "eslint(complexity)" at line 10 of "src/ledger.ts"
    And Istanbul covers 1 of 4 statements of "withdraw" on lines 10 to 17 of "src/ledger.ts"
    When I run sf-js with "crap --base main"
    Then the exit status is 1
    And the output has the line "OVER crap   10.8  cc   4  cov  25%  src/ledger.ts:10 (anonymous)"

  # sf-js crap with oxlint 3
  Scenario: sf-js crap with oxlint 3: diagnostics of other rules are not scored
    Given oxlint reports "`debugger` statement is not allowed" with code "eslint(no-debugger)" at line 12 of "src/ledger.ts"
    And Istanbul covers 1 of 4 statements of "withdraw" on lines 10 to 17 of "src/ledger.ts"
    When I run sf-js with "crap --base main"
    Then the exit status is 0
    And the output ends with the line "0 changed function(s), 0 over CRAP 10"

  # sf-js crap with oxlint 4
  Scenario: sf-js crap with oxlint 4: a function missing from the coverage report starting on a changed line is listed
    Given oxlint reports "refund" at line 12 of "src/ledger.ts" with complexity 3
    And Istanbul covers 2 of 2 statements of "deposit" on lines 3 to 8 of "src/ledger.ts"
    When I run sf-js with "crap --base main"
    Then the exit status is 1
    And the output ends with the line "1 changed function(s), 1 over CRAP 10"

  # sf-js crap with oxlint 4
  Scenario: sf-js crap with oxlint 4: a function missing from the coverage report starting above a changed line is not listed
    Given oxlint reports "refund" at line 11 of "src/ledger.ts" with complexity 3
    And Istanbul covers 2 of 2 statements of "deposit" on lines 3 to 8 of "src/ledger.ts"
    When I run sf-js with "crap --base main"
    Then the exit status is 0
    And the output ends with the line "0 changed function(s), 0 over CRAP 10"

  # sf-js crap with oxlint 5
  Scenario Outline: sf-js crap with oxlint 5: an oxlint-disable directive that covers complexity is warned about
    Given line 11 of "src/ledger.ts" reads "<directive>" on the task branch
    And oxlint reports "withdraw" at line 10 of "src/ledger.ts" with complexity 4
    And Istanbul covers 4 of 4 statements of "withdraw" on lines 10 to 17 of "src/ledger.ts"
    When I run sf-js with "crap --base main"
    Then the exit status is 0
    And the error output contains "sf-js: warning: src/ledger.ts:11 has an oxlint-disable directive that can hide a function from CRAP"

    Examples:
      | directive                                     |
      | // oxlint-disable-next-line complexity        |
      | // oxlint-disable-next-line eslint/complexity |
      | /* oxlint-disable */                          |
      | // oxlint-disable-line                        |
      | /* oxlint-disable no-debugger, complexity */  |

  # sf-js crap with oxlint 6
  Scenario: sf-js crap with oxlint 6: a directive for another rule is not warned about
    Given line 11 of "src/ledger.ts" reads "// oxlint-disable-next-line no-debugger" on the task branch
    And oxlint reports "withdraw" at line 10 of "src/ledger.ts" with complexity 4
    And Istanbul covers 4 of 4 statements of "withdraw" on lines 10 to 17 of "src/ledger.ts"
    When I run sf-js with "crap --base main"
    Then the exit status is 0
    And the error output does not contain "warning"

  # sf-js crap with oxlint 6
  Scenario: sf-js crap with oxlint 6: an eslint-disable directive is not warned about
    Given line 11 of "src/ledger.ts" reads "// eslint-disable-next-line complexity" on the task branch
    And oxlint reports "withdraw" at line 10 of "src/ledger.ts" with complexity 4
    And Istanbul covers 4 of 4 statements of "withdraw" on lines 10 to 17 of "src/ledger.ts"
    When I run sf-js with "crap --base main"
    Then the exit status is 0
    And the error output does not contain "warning"

  # sf-js crap with oxlint 6
  Scenario: sf-js crap with oxlint 6: an oxlint-enable directive is not warned about
    Given line 11 of "src/ledger.ts" reads "// oxlint-enable complexity" on the task branch
    And oxlint reports "withdraw" at line 10 of "src/ledger.ts" with complexity 4
    And Istanbul covers 4 of 4 statements of "withdraw" on lines 10 to 17 of "src/ledger.ts"
    When I run sf-js with "crap --base main"
    Then the exit status is 0
    And the error output does not contain "warning"
