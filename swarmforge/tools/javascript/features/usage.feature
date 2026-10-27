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
    And the error output contains "<message>"

    Examples:
      | command | message                       |
      | nope    | sf-js: unknown command 'nope' |
      | lint    | sf-js: unknown command 'lint' |

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
    When I run sf-js with "crap --all --report <report>"
    Then the exit status is 2
    And the error output contains "<message>"

    Examples:
      | report | message                            |
      | false  | no coverage-final.json under false |
      | 42     | no coverage-final.json under 42    |

  # sf-js usage 5
  Scenario Outline: sf-js usage 5: a base ref that looks like a number is still a ref
    Given the jscpd report has a 64-token clone of "src/a.ts" lines 1 to 3 and "lib/old.js" lines 10 to 12
    When I run sf-js with "dry --report-only --base <base>"
    Then the exit status is 2
    And the error output contains "<message>"

    Examples:
      | base | message                        |
      | 123  | sf-js: git merge-base 123 HEAD |
      | 7    | sf-js: git merge-base 7 HEAD   |

  # sf-js usage 6
  Scenario: sf-js usage 6: sf tool installs the sf-js wrapper
    When I run "sf tool ensure sf-js" and then "sf tool require sf-js"
    Then the exit status is 0
    And the output has a line starting with "OK: sf-js "
