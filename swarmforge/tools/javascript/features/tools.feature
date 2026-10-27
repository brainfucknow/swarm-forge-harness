# mutation-stamp: sha256=9cfb4f414725cd4b3cbec2dcf303c9c44bc00a4dc9a800b65eeb0b04fb4ccede
# acceptance-mutation-manifest-begin
# {"version":1,"tested_at":"2026-10-27T15:18:23.531469822Z","feature_name":"sf-js tools","feature_path":"swarmforge/tools/javascript/features/tools.feature","background_hash":"e80ae66b77880d9562e2e1cf312f5b4c2e793f43500870531deafd3e85f2419e","implementation_hash":"sha256:e7a79043fb1b62c6ff7ff9c33f029b0ac07890a382fb25e4187a1e9fdcc99cd8","scenarios":[{"index":2,"name":"sf-js tools 1: missing tool packages are added as devDependencies","scenario_hash":"d1af68e205cc9f6d6ac1cdd3f402d3ffc7f0c0325bec26cc1a70185668292dea","mutation_count":12,"result":{"Total":12,"Killed":12,"Survived":0,"Errors":0},"tested_at":"2026-10-27T15:18:23.531469822Z"},{"index":7,"name":"sf-js tools 3: nothing is added when every tool package is listed","scenario_hash":"0c590d0bd8235cad1b819c1bb02a139e3614c973b750b19a89873f8933ceeead","mutation_count":2,"result":{"Total":2,"Killed":2,"Survived":0,"Errors":0},"tested_at":"2026-10-27T15:18:23.531469822Z"}]}
# acceptance-mutation-manifest-end

Feature: sf-js tools

  sf-js tools checks that node and the package manager are on PATH, adds the
  missing tool packages to devDependencies with the package manager, and
  checks that jscpd runs. The tool packages are: the runner (vitest unless
  package.json lists jest), @vitest/coverage-v8 for vitest, typescript, the
  complexity linter, @stryker-mutator/core, the Stryker runner plugin for the
  runner, @stryker-mutator/typescript-checker when tsconfig.json exists, and
  fast-check. The complexity linter is eslint and typescript-eslint when
  package.json lists eslint and not oxlint, else oxlint. Packages are added
  in one command, sorted.

  Background:
    Given a task worktree branched from main
    And fake JavaScript tools on PATH
    And the worktree has the lockfile "package-lock.json"

  # sf-js tools 1
  Scenario: sf-js tools 1: a project listing both linters gets no linter package
    Given package.json lists the devDependencies "vitest eslint oxlint"
    And tsconfig.json is absent
    When I run sf-js with "tools"
    Then the exit status is 0
    And sf-js added the devDependencies "@stryker-mutator/core @stryker-mutator/vitest-runner @vitest/coverage-v8 fast-check typescript"

  # sf-js tools 1
  Scenario: sf-js tools 1: a project listing nothing gets every tool package
    Given package.json lists the devDependencies ""
    And tsconfig.json is absent
    When I run sf-js with "tools"
    Then the exit status is 0
    And sf-js added the devDependencies "@stryker-mutator/core @stryker-mutator/vitest-runner @vitest/coverage-v8 fast-check oxlint typescript vitest"

  # sf-js tools 1
  Scenario Outline: sf-js tools 1: missing tool packages are added as devDependencies
    Given package.json lists the devDependencies "<listed>"
    And tsconfig.json is <tsconfig>
    When I run sf-js with "tools"
    Then the exit status is 0
    And sf-js added the devDependencies "<added>"

    Examples:
      | listed                 | tsconfig | added                                                                                                                                     |
      | vitest                 | present  | @stryker-mutator/core @stryker-mutator/typescript-checker @stryker-mutator/vitest-runner @vitest/coverage-v8 fast-check oxlint typescript |
      | jest                   | absent   | @stryker-mutator/core @stryker-mutator/jest-runner fast-check oxlint typescript                                                           |
      | jest typescript eslint | present  | @stryker-mutator/core @stryker-mutator/jest-runner @stryker-mutator/typescript-checker fast-check typescript-eslint                       |
      | vitest fast-check      | absent   | @stryker-mutator/core @stryker-mutator/vitest-runner @vitest/coverage-v8 oxlint typescript                                                |

  # sf-js tools 2
  Scenario: sf-js tools 2: npm adds devDependencies its own way
    Given package.json lists the devDependencies "vitest"
    When I run sf-js with "tools"
    Then the exit status is 0
    And sf-js ran "npm install --save-dev"

  # sf-js tools 2
  Scenario: sf-js tools 2: pnpm adds devDependencies its own way
    Given package.json declares packageManager "pnpm@9.1.0"
    And the worktree has the lockfile "pnpm-lock.yaml"
    And package.json lists the devDependencies "vitest"
    When I run sf-js with "tools"
    Then the exit status is 0
    And sf-js ran "pnpm add --save-dev"

  # sf-js tools 2
  Scenario: sf-js tools 2: yarn adds devDependencies its own way
    Given package.json declares packageManager "yarn@4.5.0"
    And the worktree has the lockfile "yarn.lock"
    And package.json lists the devDependencies "vitest"
    When I run sf-js with "tools"
    Then the exit status is 0
    And sf-js ran "yarn add --dev"

  # sf-js tools 2
  Scenario: sf-js tools 2: bun adds devDependencies its own way
    Given package.json declares packageManager "bun@1.1.30"
    And the worktree has the lockfile "bun.lock"
    And package.json lists the devDependencies "vitest"
    When I run sf-js with "tools"
    Then the exit status is 0
    And sf-js ran "bun add --dev"

  # sf-js tools 3
  Scenario Outline: sf-js tools 3: nothing is added when every tool package is listed
    Given package.json lists the devDependencies "vitest @vitest/coverage-v8 typescript <linter> @stryker-mutator/core @stryker-mutator/vitest-runner fast-check"
    And tsconfig.json is absent
    When I run sf-js with "tools"
    Then the exit status is 0
    And the output has the line "all JavaScript tools present"
    And sf-js did not run "npm install"

    Examples:
      | linter                   |
      | oxlint                   |
      | eslint typescript-eslint |

  # sf-js tools 4
  Scenario: sf-js tools 4: jscpd is checked
    When I run sf-js with "tools"
    Then the exit status is 0
    And sf-js ran "npx --yes jscpd@4 --version"

  # sf-js tools 5
  Scenario: sf-js tools 5: node missing from PATH fails before anything is installed
    Given package.json declares packageManager "pnpm@9.1.0"
    And the worktree has the lockfile "pnpm-lock.yaml"
    And "node" is not on PATH
    When I run sf-js with "tools"
    Then the exit status is 2
    And the error output contains "missing on PATH: node"
    And sf-js ran nothing

  # sf-js tools 5
  Scenario: sf-js tools 5: pnpm missing from PATH fails before anything is installed
    Given package.json declares packageManager "pnpm@9.1.0"
    And the worktree has the lockfile "pnpm-lock.yaml"
    And "pnpm" is not on PATH
    When I run sf-js with "tools"
    Then the exit status is 2
    And the error output contains "missing on PATH: pnpm"
    And sf-js ran nothing

  # sf-js tools 5
  Scenario: sf-js tools 5: bun missing from PATH fails before anything is installed
    Given package.json declares packageManager "bun@1.1.30"
    And the worktree has the lockfile "bun.lock"
    And "bun" is not on PATH
    When I run sf-js with "tools"
    Then the exit status is 2
    And the error output contains "missing on PATH: bun"
    And sf-js ran nothing

  # sf-js tools 6
  Scenario: sf-js tools 6: a failed install fails
    Given the fake npm exits with status 1
    When I run sf-js with "tools"
    Then the exit status is 2
    And the error output contains "npm install --save-dev failed"
