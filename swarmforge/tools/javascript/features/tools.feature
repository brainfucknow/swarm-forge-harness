Feature: sf-js tools

  sf-js tools checks that node and the package manager are on PATH, adds the
  missing tool packages to devDependencies with the package manager, and
  checks that jscpd runs. The tool packages are: the runner (vitest unless
  package.json lists jest), @vitest/coverage-v8 for vitest, typescript,
  eslint, typescript-eslint, @stryker-mutator/core, the Stryker runner plugin
  for the runner, @stryker-mutator/typescript-checker when tsconfig.json
  exists, and fast-check. Packages are added in one command, sorted.

  Background:
    Given a task worktree branched from main
    And fake JavaScript tools on PATH
    And the worktree has the lockfile "package-lock.json"

  # sf-js tools 1
  Scenario Outline: sf-js tools 1: missing tool packages are added as devDependencies
    Given package.json lists the devDependencies "<listed>"
    And tsconfig.json is <tsconfig>
    When I run sf-js with "tools"
    Then the exit status is 0
    And sf-js added the devDependencies "<added>"

    Examples:
      | listed                 | tsconfig | added                                                                                                                                                       |
      |                        | absent   | @stryker-mutator/core @stryker-mutator/vitest-runner @vitest/coverage-v8 eslint fast-check typescript typescript-eslint vitest                              |
      | vitest                 | present  | @stryker-mutator/core @stryker-mutator/typescript-checker @stryker-mutator/vitest-runner @vitest/coverage-v8 eslint fast-check typescript typescript-eslint |
      | jest                   | absent   | @stryker-mutator/core @stryker-mutator/jest-runner eslint fast-check typescript typescript-eslint                                                           |
      | jest typescript eslint | present  | @stryker-mutator/core @stryker-mutator/jest-runner @stryker-mutator/typescript-checker fast-check typescript-eslint                                         |
      | vitest fast-check      | absent   | @stryker-mutator/core @stryker-mutator/vitest-runner @vitest/coverage-v8 eslint typescript typescript-eslint                                                |

  # sf-js tools 2
  Scenario Outline: sf-js tools 2: each package manager adds devDependencies its own way
    Given package.json declares packageManager "<declared>"
    And the worktree has the lockfile "<lockfile>"
    And package.json lists the devDependencies "vitest"
    When I run sf-js with "tools"
    Then the exit status is 0
    And sf-js ran "<add>"

    Examples:
      | declared   | lockfile          | add                    |
      | npm@10.8.2 | package-lock.json | npm install --save-dev |
      | pnpm@9.1.0 | pnpm-lock.yaml    | pnpm add --save-dev    |
      | yarn@4.5.0 | yarn.lock         | yarn add --dev         |
      | bun@1.1.30 | bun.lock          | bun add --dev          |

  # sf-js tools 3
  Scenario: sf-js tools 3: nothing is added when every tool package is listed
    Given package.json lists the devDependencies "vitest @vitest/coverage-v8 typescript eslint typescript-eslint @stryker-mutator/core @stryker-mutator/vitest-runner fast-check"
    And tsconfig.json is absent
    When I run sf-js with "tools"
    Then the exit status is 0
    And the output has the line "all JavaScript tools present"
    And sf-js did not run "npm install"

  # sf-js tools 4
  Scenario: sf-js tools 4: jscpd is checked
    When I run sf-js with "tools"
    Then the exit status is 0
    And sf-js ran "npx --yes jscpd@4 --version"

  # sf-js tools 5
  Scenario Outline: sf-js tools 5: a missing command fails before anything is installed
    Given package.json declares packageManager "<declared>"
    And the worktree has the lockfile "<lockfile>"
    And "<command>" is not on PATH
    When I run sf-js with "tools"
    Then the exit status is 2
    And the error output contains "missing on PATH: <command>"
    And sf-js ran nothing

    Examples:
      | declared   | lockfile       | command |
      | pnpm@9.1.0 | pnpm-lock.yaml | node    |
      | pnpm@9.1.0 | pnpm-lock.yaml | pnpm    |
      | bun@1.1.30 | bun.lock       | bun     |

  # sf-js tools 6
  Scenario: sf-js tools 6: a failed install fails
    Given the fake npm exits with status 1
    When I run sf-js with "tools"
    Then the exit status is 2
    And the error output contains "npm install --save-dev failed"
