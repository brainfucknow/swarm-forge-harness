Feature: sf-js crap oxlint analysis errors

  With oxlint as the complexity source, sf-js crap fails with exit 2 when
  oxlint could not analyze a linted file, as it does with ESLint. oxlint
  reports a file it could not parse (a syntax error, or a semantic error such
  as a redeclared identifier) as a diagnostic with no code, and reports no
  complexity for any function in that file. Every diagnostic with no code is
  reported on stderr as "oxlint could not analyze FILE:LINE: MESSAGE", once
  per diagnostic, with the line of its first label. oxlint reports a file it
  could not open (one that is not valid UTF-8, for example) as a diagnostic
  with no code, no file and no labels; that one is reported as "oxlint could
  not analyze: MESSAGE". Either way no CRAP table is printed. Diagnostics
  with a code are read as before.

  Background:
    Given a main branch where "src/ledger.ts" has 40 lines
    And a task worktree branched from main
    And fake JavaScript tools on PATH
    And the worktree has the lockfile "package-lock.json"
    And package.json lists the devDependencies "vitest oxlint"
    And oxlint reports "withdraw" at line 10 of "src/ledger.ts" with complexity 4
    And Istanbul covers 4 of 4 statements of "withdraw" on lines 10 to 17 of "src/ledger.ts"
    And line 12 of "src/ledger.ts" changed on the task branch

  # sf-js crap oxlint analysis errors 1
  Scenario Outline: sf-js crap oxlint analysis errors 1: an oxlint diagnostic with no code fails the run
    Given oxlint reports an error "<message>" at line <line> of "src/ledger.ts"
    When I run sf-js with "crap --base main"
    Then the exit status is 2
    And the error output contains "<error>"
    And the output does not mention "function(s)"

    Examples:
      | line | message                                  | error                                                                               |
      | 3    | Expected `:` but found `decimal`         | oxlint could not analyze src/ledger.ts:3: Expected `:` but found `decimal`          |
      | 21   | Identifier `a` has already been declared | oxlint could not analyze src/ledger.ts:21: Identifier `a` has already been declared |

  # sf-js crap oxlint analysis errors 1
  Scenario: sf-js crap oxlint analysis errors 1: crap --all fails the same way
    Given oxlint reports an error "Identifier `a` has already been declared" at line 21 of "src/ledger.ts"
    When I run sf-js with "crap --all"
    Then the exit status is 2
    And the error output contains "oxlint could not analyze src/ledger.ts:21: Identifier `a` has already been declared"
    And the output does not mention "function(s)"

  # sf-js crap oxlint analysis errors 2
  Scenario: sf-js crap oxlint analysis errors 2: each oxlint diagnostic with no code is reported
    Given "src/audit.ts" is added on the task branch
    And oxlint reports an error "Unexpected token" at line 3 of "src/ledger.ts"
    And oxlint reports an error "Expected `,` or `)` but found `const`" at line 5 of "src/audit.ts"
    When I run sf-js with "crap --base main"
    Then the exit status is 2
    And the error output contains "oxlint could not analyze src/ledger.ts:3: Unexpected token"
    And the error output contains "oxlint could not analyze src/audit.ts:5: Expected `,` or `)` but found `const`"
    And the output does not mention "function(s)"

  # sf-js crap oxlint analysis errors 3
  Scenario: sf-js crap oxlint analysis errors 3: an oxlint diagnostic with no code and no file fails the run
    Given oxlint reports an error "Failed to open file src/ledger.ts with error stream did not contain valid UTF-8" with no file
    When I run sf-js with "crap --base main"
    Then the exit status is 2
    And the error output contains "oxlint could not analyze: Failed to open file src/ledger.ts with error stream did not contain valid UTF-8"
    And the output does not mention "function(s)"
