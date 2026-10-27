# mutation-stamp: sha256=6652a4b78a2b5d15b896f1e7b1c9d5193ab019321ba32cd2fdef8a2bb3cbfef5
# acceptance-mutation-manifest-begin
# {"version":1,"tested_at":"2026-10-27T15:17:34.419792714Z","feature_name":"sf-js package manager","feature_path":"swarmforge/tools/javascript/features/package_manager.feature","background_hash":"607aa3bceb07c08cfc83cde86938518306ba29ede7b0432895c3a8e7326f5b86","implementation_hash":"sha256:0c18a042fa4813ffb9abfd2f187bfb15331c06c2fac10c113124e5cdc759800e","scenarios":[{"index":0,"name":"sf-js package manager 1: the packageManager field names the manager","scenario_hash":"b7cb8718c649e78f2c349127d9fa83c04a7c3cd470c6fdd7eee3df17be6ee32d","mutation_count":12,"result":{"Total":12,"Killed":12,"Survived":0,"Errors":0},"tested_at":"2026-10-27T15:17:34.419792714Z"},{"index":1,"name":"sf-js package manager 2: without the field the lockfile names the manager","scenario_hash":"1f0b4b756331f02d29a9cdaec7bf364cb2f91538e78c1676e2a660851b2accae","mutation_count":12,"result":{"Total":12,"Killed":12,"Survived":0,"Errors":0},"tested_at":"2026-10-27T15:17:34.419792714Z"},{"index":2,"name":"sf-js package manager 3: the declared manager's lockfile wins over other lockfiles","scenario_hash":"0707bcc2478f8a429051f312013e4c182dd09ea3b1bedca454396fdf9a817f4c","mutation_count":6,"result":{"Total":6,"Killed":6,"Survived":0,"Errors":0},"tested_at":"2026-10-27T15:17:34.419792714Z"},{"index":3,"name":"sf-js package manager 4: a missing lockfile fails","scenario_hash":"7d270ae94be4c87713bcd6f85c959b7cca6400facb4662f0bf13259c98318539","mutation_count":9,"result":{"Total":9,"Killed":9,"Survived":0,"Errors":0},"tested_at":"2026-10-27T15:17:34.419792714Z"},{"index":5,"name":"sf-js package manager 6: a lockfile of another manager fails","scenario_hash":"3e4d86d55fe0c62592195eab9f02001a8f7133735adf3d9214663e18e90964aa","mutation_count":9,"result":{"Total":9,"Killed":9,"Survived":0,"Errors":0},"tested_at":"2026-10-27T15:17:34.419792714Z"},{"index":6,"name":"sf-js package manager 7: lockfiles of several managers without the field fail","scenario_hash":"d8e0c3c04f54402cdf71f3db0ae31287264c705fedb49323835e1068c39ab96b","mutation_count":4,"result":{"Total":4,"Killed":4,"Survived":0,"Errors":0},"tested_at":"2026-10-27T15:17:34.419792714Z"},{"index":7,"name":"sf-js package manager 8: an unknown packageManager fails","scenario_hash":"b4153da1aed0294184c0d3d011bc56d5f8db63f71869523f5198efeb51f65a01","mutation_count":4,"result":{"Total":4,"Killed":4,"Survived":0,"Errors":0},"tested_at":"2026-10-27T15:17:34.419792714Z"}]}
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
