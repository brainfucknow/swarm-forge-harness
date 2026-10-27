Feature: sf-js crap selection

  sf-js crap lists the functions that span a changed line, from their first
  to their last line; --all lists every function, worst CRAP first. Deleting
  lines changes the line that now follows the deletion, so a function whose
  change only deletes lines is listed. ESLint and Istanbul positions are
  those of the changed file.

  Background:
    Given a main branch where "src/ledger.ts" has 40 lines
    And a task worktree branched from main
    And fake JavaScript tools on PATH
    And the worktree has the lockfile "package-lock.json"
    And package.json lists "vitest" in devDependencies
    And package.json lists "eslint" in devDependencies
    And ESLint reports "deposit" at line 3 of "src/ledger.ts" with complexity 1
    And Istanbul covers 2 of 2 statements of "deposit" on lines 3 to 8 of "src/ledger.ts"
    And ESLint reports "withdraw" at line 10 of "src/ledger.ts" with complexity 4
    And Istanbul covers 1 of 4 statements of "withdraw" on lines 10 to 17 of "src/ledger.ts"

  # sf-js crap selection 1
  Scenario: sf-js crap selection 1: a change on its first line lists only deposit
    Given line 3 of "src/ledger.ts" changed on the task branch
    When I run sf-js with "crap --base main"
    Then the exit status is 0
    And the output mentions "deposit"
    And the output does not mention "withdraw"
    And the output ends with the line "1 changed function(s), 0 over CRAP 10"

  # sf-js crap selection 1
  Scenario: sf-js crap selection 1: a change on its last line lists only deposit
    Given line 8 of "src/ledger.ts" changed on the task branch
    When I run sf-js with "crap --base main"
    Then the exit status is 0
    And the output mentions "deposit"
    And the output does not mention "withdraw"
    And the output ends with the line "1 changed function(s), 0 over CRAP 10"

  # sf-js crap selection 1
  Scenario: sf-js crap selection 1: a change on its first line lists only withdraw
    Given line 10 of "src/ledger.ts" changed on the task branch
    When I run sf-js with "crap --base main"
    Then the exit status is 1
    And the output mentions "withdraw"
    And the output does not mention "deposit"
    And the output ends with the line "1 changed function(s), 1 over CRAP 10"

  # sf-js crap selection 1
  Scenario: sf-js crap selection 1: a change on its last line lists only withdraw
    Given line 17 of "src/ledger.ts" changed on the task branch
    When I run sf-js with "crap --base main"
    Then the exit status is 1
    And the output mentions "withdraw"
    And the output does not mention "deposit"
    And the output ends with the line "1 changed function(s), 1 over CRAP 10"

  # sf-js crap selection 2
  Scenario: sf-js crap selection 2: a change on line 9, between two functions, lists nothing
    Given line 9 of "src/ledger.ts" changed on the task branch
    When I run sf-js with "crap --base main"
    Then the exit status is 0
    And the output ends with the line "0 changed function(s), 0 over CRAP 10"

  # sf-js crap selection 2
  Scenario: sf-js crap selection 2: a change on line 18, after the last function, lists nothing
    Given line 18 of "src/ledger.ts" changed on the task branch
    When I run sf-js with "crap --base main"
    Then the exit status is 0
    And the output ends with the line "0 changed function(s), 0 over CRAP 10"

  # sf-js crap selection 3
  Scenario: sf-js crap selection 3: --all lists every function, worst first
    When I run sf-js with "crap --all"
    Then the exit status is 1
    And output line 1 is "OVER crap   10.8  cc   4  cov  25%  src/ledger.ts:10 withdraw"
    And output line 2 is "ok   crap    1.0  cc   1  cov 100%  src/ledger.ts:3 deposit"
    And the output ends with the line "2 function(s), 1 over CRAP 10"

  # sf-js crap selection 4
  Scenario: sf-js crap selection 4: deleting lines 5 to 6, inside deposit, lists only deposit
    Given lines 5 to 6 of "src/ledger.ts" are deleted on the task branch
    When I run sf-js with "crap --base main"
    Then the exit status is 0
    And the output mentions "deposit"
    And the output does not mention "withdraw"
    And the output ends with the line "1 changed function(s), 0 over CRAP 10"

  # sf-js crap selection 4
  Scenario: sf-js crap selection 4: deleting line 10, the first line of withdraw, lists only withdraw
    Given lines 10 to 10 of "src/ledger.ts" are deleted on the task branch
    When I run sf-js with "crap --base main"
    Then the exit status is 1
    And the output mentions "withdraw"
    And the output does not mention "deposit"
    And the output ends with the line "1 changed function(s), 1 over CRAP 10"

  # sf-js crap selection 4
  Scenario: sf-js crap selection 4: deleting lines 12 to 13, inside withdraw, lists only withdraw
    Given lines 12 to 13 of "src/ledger.ts" are deleted on the task branch
    When I run sf-js with "crap --base main"
    Then the exit status is 1
    And the output mentions "withdraw"
    And the output does not mention "deposit"
    And the output ends with the line "1 changed function(s), 1 over CRAP 10"

  # sf-js crap selection 5
  Scenario: sf-js crap selection 5: deleting line 9, between two functions, lists nothing
    Given lines 9 to 9 of "src/ledger.ts" are deleted on the task branch
    When I run sf-js with "crap --base main"
    Then the exit status is 0
    And the output ends with the line "0 changed function(s), 0 over CRAP 10"

  # sf-js crap selection 5
  Scenario: sf-js crap selection 5: deleting lines 18 to 19, after the last function, lists nothing
    Given lines 18 to 19 of "src/ledger.ts" are deleted on the task branch
    When I run sf-js with "crap --base main"
    Then the exit status is 0
    And the output ends with the line "0 changed function(s), 0 over CRAP 10"
