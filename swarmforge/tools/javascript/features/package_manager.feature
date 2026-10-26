# acceptance-mutation-manifest-begin
# {"version":1,"tested_at":"2026-10-26T16:35:54.553707706Z","feature_name":"sf-js package manager","feature_path":"swarmforge/tools/javascript/features/package_manager.feature","background_hash":"607aa3bceb07c08cfc83cde86938518306ba29ede7b0432895c3a8e7326f5b86","implementation_hash":"sha256:64386aad40782efd3f079e444f17e5b0b7dbb7a8cd77ad64d9c4713a7b868466","scenarios":[{"index":1,"name":"sf-js package manager 2: without the field the lockfile names the manager","scenario_hash":"1f0b4b756331f02d29a9cdaec7bf364cb2f91538e78c1676e2a660851b2accae","mutation_count":12,"result":{"Total":12,"Killed":12,"Survived":0,"Errors":0},"tested_at":"2026-10-26T16:23:35.523896487Z"},{"index":6,"name":"sf-js package manager 7: lockfiles of several managers without the field fail","scenario_hash":"f73e66c76a9cfa6272722f2f122163a80853ae0290c2a123b4cbe9c815db72cb","mutation_count":4,"result":{"Total":4,"Killed":4,"Survived":0,"Errors":0},"tested_at":"2026-10-26T16:23:35.523896487Z"}]}
# acceptance-mutation-manifest-end

Feature: sf-js package manager

  sf-js picks the package manager from package.json's packageManager field,
  else from the lockfile, and runs project tools through that manager.
  "sf-js ran X" means sf-js printed a "$ " line whose command starts with X.

  Background:
    Given a task worktree branched from main
    And fake JavaScript tools on PATH
    And package.json lists "vitest" in devDependencies

  # sf-js package manager 1
  Scenario Outline: sf-js package manager 1: the packageManager field names the manager
    Given package.json declares packageManager "<declared>"
    And the worktree has the lockfile "<lockfile>"
    When I run sf-js with "test"
    Then the exit status is 0
    And sf-js ran "<command>"

    Examples:
      | declared   | lockfile          | command                     |
      | npm@10.8.2 | package-lock.json | npx --no-install vitest run |
      | pnpm@9.1.0 | pnpm-lock.yaml    | pnpm exec vitest run        |
      | yarn@4.5.0 | yarn.lock         | yarn vitest run             |
      | bun@1.1.30 | bun.lock          | bunx vitest run             |

  # sf-js package manager 2
  Scenario Outline: sf-js package manager 2: without the field the lockfile names the manager
    Given the worktree has the lockfile "<lockfile>"
    When I run sf-js with "test"
    Then the exit status is 0
    And sf-js ran "<command>"

    Examples:
      | lockfile            | command                     |
      | package-lock.json   | npx --no-install vitest run |
      | npm-shrinkwrap.json | npx --no-install vitest run |
      | pnpm-lock.yaml      | pnpm exec vitest run        |
      | yarn.lock           | yarn vitest run             |
      | bun.lock            | bunx vitest run             |
      | bun.lockb           | bunx vitest run             |

  # sf-js package manager 3
  Scenario Outline: sf-js package manager 3: the declared manager's lockfile wins over other lockfiles
    Given package.json declares packageManager "<declared>"
    And the worktree has the lockfile "<lockfile>"
    And the worktree has the lockfile "<other>"
    When I run sf-js with "test"
    Then the exit status is 0
    And sf-js ran "<command>"

    Examples:
      | declared   | lockfile       | other             | command              |
      | pnpm@9.1.0 | pnpm-lock.yaml | yarn.lock         | pnpm exec vitest run |
      | yarn@4.5.0 | yarn.lock      | package-lock.json | yarn vitest run      |

  # sf-js package manager 4
  Scenario Outline: sf-js package manager 4: a missing lockfile fails
    Given package.json declares packageManager "<declared>"
    When I run sf-js with "<command>"
    Then the exit status is 2
    And the error output contains "no lockfile"
    And sf-js did not run "<tool>"

    Examples:
      | declared   | command  | tool    |
      | pnpm@9.1.0 | test     | vitest  |
      | npm@10.8.2 | property | vitest  |
      | bun@1.1.30 | mutate   | stryker |

  # sf-js package manager 5
  Scenario: sf-js package manager 5: no field and no lockfile fails
    When I run sf-js with "test"
    Then the exit status is 2
    And the error output contains "no lockfile"
    And sf-js did not run "vitest"

  # sf-js package manager 6
  Scenario Outline: sf-js package manager 6: a lockfile of another manager fails
    Given package.json declares packageManager "<declared>"
    And the worktree has the lockfile "<lockfile>"
    When I run sf-js with "test"
    Then the exit status is 2
    And the error output contains "packageManager names <manager> but the lockfile is <lockfile>"
    And sf-js did not run "vitest"

    Examples:
      | declared   | manager | lockfile          |
      | pnpm@9.1.0 | pnpm    | yarn.lock         |
      | npm@10.8.2 | npm     | bun.lock          |
      | yarn@4.5.0 | yarn    | package-lock.json |

  # sf-js package manager 7
  Scenario Outline: sf-js package manager 7: lockfiles of several managers without the field fail
    Given the worktree has the lockfile "<first>"
    And the worktree has the lockfile "<second>"
    When I run sf-js with "test"
    Then the exit status is 2
    And the error output contains "lockfiles for several package managers: <first>, <second>"
    And sf-js did not run "vitest"

    Examples:
      | first             | second         |
      | package-lock.json | yarn.lock      |
      | bun.lockb         | pnpm-lock.yaml |

  # sf-js package manager 8
  Scenario Outline: sf-js package manager 8: an unknown packageManager fails
    Given package.json declares packageManager "<declared>"
    And the worktree has the lockfile "package-lock.json"
    When I run sf-js with "test"
    Then the exit status is 2
    And the error output contains "unknown packageManager '<declared>'"
    And sf-js did not run "vitest"

    Examples:
      | declared   |
      | deno@2.0.0 |
      | cnpm@9.0.0 |
