# acceptance-mutation-manifest-begin
# {"version":1,"tested_at":"2026-10-26T16:37:22.910326156Z","feature_name":"sf-js usage","feature_path":"swarmforge/tools/javascript/features/usage.feature","background_hash":"e623ff98a8de5b5f8ec8c6ccd0bbeb6eb4011d4c3252f9027eeec3bc67352124","implementation_hash":"sha256:ffe68807e0d5473498328619e608fb179e31cd274ccd6af4eab71e850be0a30c","scenarios":[{"index":0,"name":"sf-js usage 1: help lists every command","scenario_hash":"caf488621985f89a978046d5cfb51698707e89f9246417359822f0a85bb9c7ae","mutation_count":14,"result":{"Total":14,"Killed":14,"Survived":0,"Errors":0},"tested_at":"2026-10-26T16:10:46.513666855Z"},{"index":2,"name":"sf-js usage 3: a command that runs project tools needs package.json","scenario_hash":"361ddace81cd928cc7a3ecbe994202d6cf1be69ea5bc9247cc99704fe229851d","mutation_count":3,"result":{"Total":3,"Killed":3,"Survived":0,"Errors":0},"tested_at":"2026-10-26T16:10:46.513666855Z"}]}
# acceptance-mutation-manifest-end

Feature: sf-js usage

  sf-js exits 0 when clean, 1 on a finding, and 2 on a usage or input error,
  with the error on stderr prefixed "sf-js: ".

  Background:
    Given a task worktree branched from main
    And fake JavaScript tools on PATH

  # sf-js usage 1
  Scenario Outline: sf-js usage 1: help lists every command
    When I run sf-js with "<help>"
    Then the exit status is 0
    And the output has a line starting with "  <command>"

    Examples:
      | help   | command  |
      | help   | tools    |
      | --help | test     |
      | -h     | property |
      | help   | coverage |
      | help   | crap     |
      | help   | dry      |
      | help   | mutate   |

  # sf-js usage 2
  Scenario Outline: sf-js usage 2: an unknown command fails
    When I run sf-js with "<command>"
    Then the exit status is 2
    And the error output contains "sf-js: unknown command '<command>'"

    Examples:
      | command |
      | nope    |
      | lint    |

  # sf-js usage 3
  Scenario Outline: sf-js usage 3: a command that runs project tools needs package.json
    When I run sf-js with "<command>"
    Then the exit status is 2
    And the error output contains "sf-js: no package.json"

    Examples:
      | command  |
      | tools    |
      | test     |
      | coverage |

  # sf-js usage 4
  Scenario Outline: sf-js usage 4: a report path that looks like a boolean or a number is still a path
    Given the worktree has the lockfile "package-lock.json"
    And package.json lists "vitest" in devDependencies
    When I run sf-js with "crap <options>"
    Then the exit status is 2
    And the error output contains "<message>"

    Examples:
      | options              | message                            |
      | --all --report false | no coverage-final.json under false |
      | --all --report 42    | no coverage-final.json under 42    |

  # sf-js usage 5
  Scenario Outline: sf-js usage 5: a base ref that looks like a number is still a ref
    Given the jscpd report has a 64-token clone of "src/a.ts" lines 1 to 3 and "lib/old.js" lines 10 to 12
    When I run sf-js with "dry --report-only --base <base>"
    Then the exit status is 2
    And the error output contains "<base>"

    Examples:
      | base |
      | 123  |
      | 7    |

  # sf-js usage 6
  Scenario: sf-js usage 6: sf tool installs the sf-js wrapper
    When I run "sf tool ensure sf-js" and then "sf tool require sf-js"
    Then the exit status is 0
    And the output has a line starting with "OK: sf-js "
