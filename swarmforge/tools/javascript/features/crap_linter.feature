# mutation-stamp: sha256=ebdd49704c3a52c2913cbad5478d64d8a6b9806778a0da4d131317d127f01cc5
# acceptance-mutation-manifest-begin
# {"version":1,"tested_at":"2026-10-27T15:11:13.435103556Z","feature_name":"sf-js crap complexity source","feature_path":"swarmforge/tools/javascript/features/crap_linter.feature","background_hash":"ff8b6fe3e7b6fbd332cb25d5737a57cc6801079f0eab42d4a63c638c75627cbd","implementation_hash":"sha256:d44955bf02e6c64fd90ec5d37943cd7dc5c56f1405191a5e561e4a2672b830e9","scenarios":[{"index":4,"name":"sf-js crap complexity source 2: a linter listed in dependencies counts as listed","scenario_hash":"acdf46df5dafb808ed66706293e123651897daed596b19632cf5bb9a060647d6","mutation_count":2,"result":{"Total":2,"Killed":2,"Survived":0,"Errors":0},"tested_at":"2026-10-27T15:11:13.435103556Z"},{"index":8,"name":"sf-js crap complexity source 4: oxlint runs with sf-js's own config and a JSON report","scenario_hash":"7f87b470ead1ea6d70024a0ef295c4488680e92da36bf492da1ae09114f83cf8","mutation_count":4,"result":{"Total":4,"Killed":4,"Survived":0,"Errors":0},"tested_at":"2026-10-27T15:11:13.435103556Z"}]}
# acceptance-mutation-manifest-end

Feature: sf-js crap complexity source

  sf-js crap takes per-function complexity from oxlint when package.json
  lists oxlint (in dependencies or devDependencies), else from ESLint when it
  lists eslint, else from oxlint. The linter runs through the package
  manager. oxlint runs with sf-js's own config, tmp/oxlint/oxlintrc.json,
  which turns on only the eslint/complexity rule, ignores eslint-disable
  comments and leaves out the files ESLint's config leaves out; the project's
  oxlint config files, nested ones included, are not read. oxlint prints its
  JSON report on stdout, and sf-js does not echo it. sf-js needs oxlint
  1.62.0 or later. An oxlint that runs no complexity rule, or that prints no
  report, is an input error, and sf-js does not fall back to ESLint.

  Background:
    Given a main branch where "src/ledger.ts" has 40 lines
    And a task worktree branched from main
    And fake JavaScript tools on PATH
    And the worktree has the lockfile "package-lock.json"
    And ESLint reports "withdraw" at line 10 of "src/ledger.ts" with complexity 3
    And oxlint reports "withdraw" at line 10 of "src/ledger.ts" with complexity 5
    And Istanbul covers 4 of 4 statements of "withdraw" on lines 10 to 17 of "src/ledger.ts"
    And line 12 of "src/ledger.ts" changed on the task branch

  # sf-js crap complexity source 1
  Scenario: sf-js crap complexity source 1: a project listing only oxlint takes its complexity from oxlint
    Given package.json lists the devDependencies "vitest oxlint"
    When I run sf-js with "crap --base main"
    Then the exit status is 0
    And sf-js ran "npx --no-install oxlint"
    And sf-js did not run "npx --no-install eslint"
    And the output has the line "ok   crap    5.0  cc   5  cov 100%  src/ledger.ts:10 withdraw"

  # sf-js crap complexity source 1
  Scenario: sf-js crap complexity source 1: a project listing only eslint takes its complexity from ESLint
    Given package.json lists the devDependencies "vitest eslint"
    When I run sf-js with "crap --base main"
    Then the exit status is 0
    And sf-js ran "npx --no-install eslint"
    And sf-js did not run "npx --no-install oxlint"
    And the output has the line "ok   crap    3.0  cc   3  cov 100%  src/ledger.ts:10 withdraw"

  # sf-js crap complexity source 1
  Scenario: sf-js crap complexity source 1: a project listing both takes its complexity from oxlint
    Given package.json lists the devDependencies "vitest eslint oxlint"
    When I run sf-js with "crap --base main"
    Then the exit status is 0
    And sf-js ran "npx --no-install oxlint"
    And sf-js did not run "npx --no-install eslint"
    And the output has the line "ok   crap    5.0  cc   5  cov 100%  src/ledger.ts:10 withdraw"

  # sf-js crap complexity source 1
  Scenario: sf-js crap complexity source 1: a project listing neither takes its complexity from oxlint
    Given package.json lists the devDependencies "vitest"
    When I run sf-js with "crap --base main"
    Then the exit status is 0
    And sf-js ran "npx --no-install oxlint"
    And sf-js did not run "npx --no-install eslint"
    And the output has the line "ok   crap    5.0  cc   5  cov 100%  src/ledger.ts:10 withdraw"

  # sf-js crap complexity source 2
  Scenario Outline: sf-js crap complexity source 2: a linter listed in dependencies counts as listed
    Given package.json lists "vitest" in devDependencies
    And package.json lists "<linter>" in dependencies
    When I run sf-js with "crap --base main"
    Then the exit status is 0
    And sf-js ran "npx --no-install <linter>"

    Examples:
      | linter  |
      | eslint  |
      | oxlint  |

  # sf-js crap complexity source 3
  Scenario: sf-js crap complexity source 3: oxlint runs through pnpm
    Given package.json declares packageManager "pnpm@9.1.0"
    And the worktree has the lockfile "pnpm-lock.yaml"
    And package.json lists "oxlint" in devDependencies
    When I run sf-js with "crap --base main"
    Then the exit status is 0
    And sf-js ran "pnpm exec oxlint"

  # sf-js crap complexity source 3
  Scenario: sf-js crap complexity source 3: oxlint runs through yarn
    Given package.json declares packageManager "yarn@4.5.0"
    And the worktree has the lockfile "yarn.lock"
    And package.json lists "oxlint" in devDependencies
    When I run sf-js with "crap --base main"
    Then the exit status is 0
    And sf-js ran "yarn oxlint"

  # sf-js crap complexity source 3
  Scenario: sf-js crap complexity source 3: oxlint runs through bun
    Given package.json declares packageManager "bun@1.1.30"
    And the worktree has the lockfile "bun.lock"
    And package.json lists "oxlint" in devDependencies
    When I run sf-js with "crap --base main"
    Then the exit status is 0
    And sf-js ran "bunx oxlint"

  # sf-js crap complexity source 4
  Scenario Outline: sf-js crap complexity source 4: oxlint runs with sf-js's own config and a JSON report
    Given package.json lists "oxlint" in devDependencies
    When I run sf-js with "crap --base main"
    Then the exit status is 0
    And the command line that runs "oxlint" contains "<option>"
    And the output does not mention "diagnostics"

    Examples:
      | option                             |
      | --config tmp/oxlint/oxlintrc.json  |
      | --disable-nested-config            |
      | --ignore-path tmp/oxlint/oxlintignore |
      | --format json                      |

  # sf-js crap complexity source 5
  Scenario: sf-js crap complexity source 5: oxlint lints the changed sources
    Given package.json lists "oxlint" in devDependencies
    And "src/notes.md" is added on the task branch
    When I run sf-js with "crap --base main"
    Then the command line that runs "oxlint" ends with " src/ledger.ts"
    And the command line that runs "oxlint" does not contain "src/notes.md"

  # sf-js crap complexity source 6
  Scenario: sf-js crap complexity source 6: an oxlint without the complexity rule is an error
    Given package.json lists the devDependencies "vitest eslint oxlint"
    And the fake oxlint runs no rules
    When I run sf-js with "crap --base main"
    Then the exit status is 2
    And the error output contains "oxlint ran no complexity rule; sf-js needs oxlint 1.62.0 or later"
    And sf-js did not run "npx --no-install eslint"

  # sf-js crap complexity source 7
  Scenario: sf-js crap complexity source 7: an oxlint that exits 1 without a report is an error
    Given package.json lists the devDependencies "vitest eslint oxlint"
    And the fake oxlint exits with status 1 without a report
    When I run sf-js with "crap --base main"
    Then the exit status is 2
    And the error output contains "oxlint exited 1 without a report; sf-js needs oxlint 1.62.0 or later"
    And sf-js did not run "npx --no-install eslint"

  # sf-js crap complexity source 7
  Scenario: sf-js crap complexity source 7: an oxlint that exits 127 without a report is an error
    Given package.json lists the devDependencies "vitest eslint oxlint"
    And the fake oxlint exits with status 127 without a report
    When I run sf-js with "crap --base main"
    Then the exit status is 2
    And the error output contains "oxlint exited 127 without a report; sf-js needs oxlint 1.62.0 or later"
    And sf-js did not run "npx --no-install eslint"
