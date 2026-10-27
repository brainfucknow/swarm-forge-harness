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
    Given package.json declares packageManager "<manager>@1.0.0"
    And the worktree has the lockfile "<lockfile>"
    When I run sf-js with "test"
    Then the exit status is 0
    And sf-js ran "<command>"

    Examples:
      | manager | lockfile          | command                     |
      | npm     | package-lock.json | npx --no-install vitest run |
      | pnpm    | pnpm-lock.yaml    | pnpm exec vitest run        |
      | yarn    | yarn.lock         | yarn vitest run             |
      | bun     | bun.lock          | bunx vitest run             |

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
    Given package.json declares packageManager "<manager>@1.0.0"
    And the worktree has the lockfile "<lockfile>"
    And the worktree has the lockfile "package-lock.json"
    When I run sf-js with "test"
    Then the exit status is 0
    And sf-js ran "<command>"

    Examples:
      | manager | lockfile       | command              |
      | pnpm    | pnpm-lock.yaml | pnpm exec vitest run |
      | yarn    | yarn.lock      | yarn vitest run      |

  # sf-js package manager 4
  Scenario Outline: sf-js package manager 4: a missing lockfile fails
    Given package.json declares packageManager "<manager>@1.0.0"
    When I run sf-js with "<command>"
    Then the exit status is 2
    And the error output contains "<message>"
    And sf-js ran nothing

    Examples:
      | manager | command  | message                                              |
      | pnpm    | test     | no lockfile (pnpm-lock.yaml)                         |
      | npm     | property | no lockfile (npm-shrinkwrap.json, package-lock.json) |
      | bun     | mutate   | no lockfile (bun.lock, bun.lockb)                    |

  # sf-js package manager 5
  Scenario: sf-js package manager 5: no field and no lockfile fails
    When I run sf-js with "test"
    Then the exit status is 2
    And the error output contains "no lockfile"
    And sf-js ran nothing

  # sf-js package manager 6
  Scenario Outline: sf-js package manager 6: a lockfile of another manager fails
    Given package.json declares packageManager "<manager>@1.0.0"
    And the worktree has the lockfile "<lockfile>"
    When I run sf-js with "test"
    Then the exit status is 2
    And the error output contains "<message>"
    And sf-js ran nothing

    Examples:
      | manager | lockfile          | message                                                         |
      | pnpm    | yarn.lock         | packageManager names pnpm but the lockfile is yarn.lock         |
      | npm     | bun.lock          | packageManager names npm but the lockfile is bun.lock           |
      | yarn    | package-lock.json | packageManager names yarn but the lockfile is package-lock.json |

  # sf-js package manager 7
  Scenario Outline: sf-js package manager 7: lockfiles of several managers without the field fail
    Given the worktree has the lockfile "<first>"
    And the worktree has the lockfile "<second>"
    When I run sf-js with "test"
    Then the exit status is 2
    And the error output contains "lockfiles for several package managers: <first>, <second>"
    And sf-js ran nothing

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
    And the error output contains "<message>"
    And sf-js ran nothing

    Examples:
      | declared   | message                             |
      | deno@2.0.0 | unknown packageManager 'deno@2.0.0' |
      | cnpm@9.0.0 | unknown packageManager 'cnpm@9.0.0' |
