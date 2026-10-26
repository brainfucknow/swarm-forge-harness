# acceptance-mutation-manifest-begin
# {"version":1,"tested_at":"2026-10-26T16:31:39.023834292Z","feature_name":"sf-js crap selection","feature_path":"swarmforge/tools/javascript/features/crap_selection.feature","background_hash":"c269d7fec536e35e08c592be7c2c24d21d5e938a789def25a03c9622e5acf5a5","implementation_hash":"sha256:ca24ce3dddfcf931a00b043159ad8fc8ec4b5e1257fd13e738b67dbbdd21cecc","scenarios":[{"index":1,"name":"sf-js crap selection 2: a change outside every function lists nothing","scenario_hash":"5ac6feac9a7cef56bc0e8dd4f907bcf52ccf00b9ec1a381b7f10809b49a355db","mutation_count":2,"result":{"Total":2,"Killed":2,"Survived":0,"Errors":0},"tested_at":"2026-10-26T16:15:52.774788024Z"}]}
# acceptance-mutation-manifest-end

Feature: sf-js crap selection

  sf-js crap lists the functions that span a changed line, from their first
  to their last line; --all lists every function, worst CRAP first.

  Background:
    Given a main branch where "src/ledger.ts" has 40 lines
    And a task worktree branched from main
    And fake JavaScript tools on PATH
    And the worktree has the lockfile "package-lock.json"
    And package.json lists "vitest" in devDependencies
    And ESLint reports "deposit" at line 3 of "src/ledger.ts" with complexity 1
    And Istanbul covers 2 of 2 statements of "deposit" on lines 3 to 8 of "src/ledger.ts"
    And ESLint reports "withdraw" at line 10 of "src/ledger.ts" with complexity 4
    And Istanbul covers 1 of 4 statements of "withdraw" on lines 10 to 17 of "src/ledger.ts"

  # sf-js crap selection 1
  Scenario Outline: sf-js crap selection 1: only functions spanning a changed line are listed
    Given line <changed> of "src/ledger.ts" changed on the task branch
    When I run sf-js with "crap --base main"
    Then the exit status is <status>
    And the output mentions "<listed>"
    And the output does not mention "<absent>"
    And the output ends with the line "1 changed function(s), <over> over CRAP 10"

    Examples:
      | changed | listed   | absent   | status | over |
      | 3       | deposit  | withdraw | 0      | 0    |
      | 8       | deposit  | withdraw | 0      | 0    |
      | 10      | withdraw | deposit  | 1      | 1    |
      | 17      | withdraw | deposit  | 1      | 1    |

  # sf-js crap selection 2
  Scenario Outline: sf-js crap selection 2: a change outside every function lists nothing
    Given line <changed> of "src/ledger.ts" changed on the task branch
    When I run sf-js with "crap --base main"
    Then the exit status is 0
    And the output ends with the line "0 changed function(s), 0 over CRAP 10"

    Examples:
      | changed |
      | 9       |
      | 18      |

  # sf-js crap selection 3
  Scenario: sf-js crap selection 3: --all lists every function, worst first
    When I run sf-js with "crap --all"
    Then the exit status is 1
    And output line 1 is "OVER crap   10.8  cc   4  cov  25%  src/ledger.ts:10 withdraw"
    And output line 2 is "ok   crap    1.0  cc   1  cov 100%  src/ledger.ts:3 deposit"
    And the output ends with the line "2 function(s), 1 over CRAP 10"
