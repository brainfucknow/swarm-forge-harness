# mutation-stamp: sha256=77f3c744a8828d01a83e21d4fceec45be552fd79b997066a2f2b6e068ade72dc
# acceptance-mutation-manifest-begin
# {"version":1,"tested_at":"2026-10-27T15:11:04.618082952Z","feature_name":"sf-js crap analysis errors","feature_path":"swarmforge/tools/javascript/features/crap_analysis.feature","background_hash":"22b488197f7bd48476b9bcf1eece623637b117b0ff274972ff3147fe9e82a8e5","implementation_hash":"sha256:e34bc18f4442fc183c9bfbfda4f7c7660a99d70a361870c0f6928d507f243f1b","scenarios":[{"index":0,"name":"sf-js crap analysis errors 1: a fatal ESLint message fails the run","scenario_hash":"a93fe292cb7050b570f54bde849dbb096a835695e51e550a4e93d84799d704b5","mutation_count":6,"result":{"Total":6,"Killed":6,"Survived":0,"Errors":0},"tested_at":"2026-10-27T15:11:04.618082952Z"}]}
# acceptance-mutation-manifest-end

Feature: sf-js crap analysis errors

  If package.json lists eslint and not oxlint, sf-js crap fails with exit 2
  when ESLint could not analyze the code. A fatal message in the ESLint
  report (a parse error, for example) is reported on stderr as "eslint could
  not analyze FILE:LINE: MESSAGE", once per fatal message. ESLint's own exit status 2 (a configuration or internal
  error) fails as "eslint exited 2", even when ESLint wrote a report. Either
  way no CRAP table is printed. ESLint exit 1 is normal, because every
  complexity message is an error at maximum 0, and a message that is neither
  fatal nor a complexity message is ignored.

  Background:
    Given a main branch where "src/ledger.ts" has 40 lines
    And a task worktree branched from main
    And fake JavaScript tools on PATH
    And the worktree has the lockfile "package-lock.json"
    And package.json lists "vitest" in devDependencies
    And package.json lists "eslint" in devDependencies
    And ESLint reports "withdraw" at line 10 of "src/ledger.ts" with complexity 4
    And Istanbul covers 4 of 4 statements of "withdraw" on lines 10 to 17 of "src/ledger.ts"
    And line 12 of "src/ledger.ts" changed on the task branch

  # sf-js crap analysis errors 1
  Scenario Outline: sf-js crap analysis errors 1: a fatal ESLint message fails the run
    Given ESLint reports a fatal "<message>" at line <line> of "src/ledger.ts"
    When I run sf-js with "crap --base main"
    Then the exit status is 2
    And the error output contains "<error>"
    And the output does not mention "function(s)"

    Examples:
      | line | message                                           | error                                                                                        |
      | 3    | Parsing error: Unexpected token )                 | eslint could not analyze src/ledger.ts:3: Parsing error: Unexpected token )                  |
      | 21   | Parsing error: Declaration or statement expected. | eslint could not analyze src/ledger.ts:21: Parsing error: Declaration or statement expected. |

  # sf-js crap analysis errors 1
  Scenario: sf-js crap analysis errors 1: crap --all fails the same way
    Given ESLint reports a fatal "Parsing error: Declaration or statement expected." at line 21 of "src/ledger.ts"
    When I run sf-js with "crap --all"
    Then the exit status is 2
    And the error output contains "eslint could not analyze src/ledger.ts:21: Parsing error: Declaration or statement expected."
    And the output does not mention "function(s)"

  # sf-js crap analysis errors 2
  Scenario: sf-js crap analysis errors 2: ESLint exit status 2 fails the run
    Given the fake eslint exits with status 2
    When I run sf-js with "crap --base main"
    Then the exit status is 2
    And the error output contains "eslint exited 2"
    And the output does not mention "function(s)"

  # sf-js crap analysis errors 3
  Scenario: sf-js crap analysis errors 3: ESLint exit 0 with a non-fatal message is normal
    Given the fake eslint exits with status 0
    And ESLint reports a non-fatal "File ignored because of a matching ignore pattern." at line 1 of "src/ledger.ts"
    When I run sf-js with "crap --base main"
    Then the exit status is 0
    And the output ends with the line "1 changed function(s), 0 over CRAP 10"

  # sf-js crap analysis errors 3
  Scenario: sf-js crap analysis errors 3: ESLint exit 1 with a non-fatal message is normal
    Given the fake eslint exits with status 1
    And ESLint reports a non-fatal "File ignored because of a matching ignore pattern." at line 1 of "src/ledger.ts"
    When I run sf-js with "crap --base main"
    Then the exit status is 0
    And the output ends with the line "1 changed function(s), 0 over CRAP 10"
