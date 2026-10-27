# mutation-stamp: sha256=5313ffb56aad904409d01df8d8c22b5e7a5e5731d58668cd27a7e53e7ecc50fd
# acceptance-mutation-manifest-begin
# {"version":1,"tested_at":"2026-10-27T15:18:26.012435362Z","feature_name":"sf-js typecheck","feature_path":"swarmforge/tools/javascript/features/typecheck.feature","background_hash":"9318ee40a2565fae6d1fa410d1e4bab0659cfbdcc1bbf09c0c82fd401e6270c4","implementation_hash":"sha256:c0c1dfaebd1d772a2608ba570a3f7d6c594baf1edff19a701682716fb1a4ecd8","scenarios":[{"index":1,"name":"sf-js typecheck 2: a type error stops the tests","scenario_hash":"ab809ab0fb14e8e4d00713d47041ca15565c2c5a1e8a986d8ad0ac74f3db86f3","mutation_count":2,"result":{"Total":2,"Killed":2,"Survived":0,"Errors":0},"tested_at":"2026-10-27T15:18:26.012435362Z"},{"index":2,"name":"sf-js typecheck 3: only test typechecks, and only with a tsconfig.json","scenario_hash":"0ffe65286099c06937642189e4d1080349a01640e23441724e3a935ed2b493da","mutation_count":6,"result":{"Total":6,"Killed":6,"Survived":0,"Errors":0},"tested_at":"2026-10-27T15:18:26.012435362Z"}]}
# acceptance-mutation-manifest-end

Feature: sf-js typecheck

  With a tsconfig.json, sf-js test runs "tsc --noEmit" before the tests.
  A type error stops sf-js test with exit status 1 before the tests run.

  Background:
    Given a task worktree branched from main
    And fake JavaScript tools on PATH
    And the worktree has the lockfile "package-lock.json"
    And package.json lists "vitest" in devDependencies

  # sf-js typecheck 1
  Scenario: sf-js typecheck 1: tsc runs before the tests
    Given tsconfig.json is present
    When I run sf-js with "test"
    Then the exit status is 0
    And sf-js ran "npx --no-install tsc --noEmit" before "npx --no-install vitest run"

  # sf-js typecheck 2
  Scenario Outline: sf-js typecheck 2: a type error stops the tests
    Given tsconfig.json is present
    And the fake tsc exits with status <tsc_status>
    When I run sf-js with "test"
    Then the exit status is 1
    And sf-js did not run "vitest"

    Examples:
      | tsc_status |
      | 1          |
      | 2          |

  # sf-js typecheck 3
  Scenario Outline: sf-js typecheck 3: only test typechecks, and only with a tsconfig.json
    Given tsconfig.json is <tsconfig>
    When I run sf-js with "<command>"
    Then the exit status is 0
    And sf-js did not run "tsc"

    Examples:
      | tsconfig | command  |
      | absent   | test     |
      | present  | property |
      | present  | coverage |
