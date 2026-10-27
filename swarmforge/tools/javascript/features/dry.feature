Feature: sf-js dry

  sf-js dry runs jscpd over JavaScript and TypeScript with a JSON report in
  tmp/jscpd/ and lists the clones that touch a changed line. Exit 1 when it
  lists any. --report-only re-reads the last report.

  Background:
    Given a main branch where "src/ledger.ts" has 40 lines
    And a task worktree branched from main
    And fake JavaScript tools on PATH

  # sf-js dry 1
  Scenario: sf-js dry 1: a change on the clone's first line lists the clone
    Given the jscpd report has a 64-token clone of "src/ledger.ts" lines 13 to 15 and "src/legacy.ts" lines 30 to 32
    And line 13 of "src/ledger.ts" changed on the task branch
    When I run sf-js with "dry --report-only --base main"
    Then the exit status is 1
    And the output ends with the line "1 of 1 clone(s) touch changed lines"

  # sf-js dry 1
  Scenario: sf-js dry 1: a change on the clone's last line lists the clone
    Given the jscpd report has a 64-token clone of "src/ledger.ts" lines 13 to 15 and "src/legacy.ts" lines 30 to 32
    And line 15 of "src/ledger.ts" changed on the task branch
    When I run sf-js with "dry --report-only --base main"
    Then the exit status is 1
    And the output ends with the line "1 of 1 clone(s) touch changed lines"

  # sf-js dry 1
  Scenario: sf-js dry 1: a change on the line before the clone lists nothing
    Given the jscpd report has a 64-token clone of "src/ledger.ts" lines 13 to 15 and "src/legacy.ts" lines 30 to 32
    And line 12 of "src/ledger.ts" changed on the task branch
    When I run sf-js with "dry --report-only --base main"
    Then the exit status is 0
    And the output ends with the line "0 of 1 clone(s) touch changed lines"

  # sf-js dry 1
  Scenario: sf-js dry 1: a change on the line after the clone lists nothing
    Given the jscpd report has a 64-token clone of "src/ledger.ts" lines 13 to 15 and "src/legacy.ts" lines 30 to 32
    And line 16 of "src/ledger.ts" changed on the task branch
    When I run sf-js with "dry --report-only --base main"
    Then the exit status is 0
    And the output ends with the line "0 of 1 clone(s) touch changed lines"

  # sf-js dry 2
  Scenario Outline: sf-js dry 2: each listed clone names both sides and marks the changed one
    Given the jscpd report has a <tokens>-token clone of "<first>" lines 13 to 15 and "<second>" lines 30 to 32
    And line <changed> of "src/ledger.ts" changed on the task branch
    When I run sf-js with "dry --report-only --base main"
    Then the exit status is 1
    And the output has the line "<row>"

    Examples:
      | tokens | first         | second        | changed | row                                                               |
      | 64     | src/ledger.ts | src/legacy.ts | 14      | 64 tokens  src/ledger.ts:13-15 (changed)  ==  src/legacy.ts:30-32 |
      | 52     | src/legacy.ts | src/ledger.ts | 31      | 52 tokens  src/legacy.ts:13-15  ==  src/ledger.ts:30-32 (changed) |

  # sf-js dry 3
  Scenario: sf-js dry 3: --all lists clones on unchanged lines too
    Given the jscpd report has a 64-token clone of "src/ledger.ts" lines 13 to 15 and "src/legacy.ts" lines 30 to 32
    When I run sf-js with "dry --report-only --all"
    Then the exit status is 1
    And the output has the line "64 tokens  src/ledger.ts:13-15  ==  src/legacy.ts:30-32"
    And the output ends with the line "1 of 1 clone(s)"

  # sf-js dry 4
  Scenario Outline: sf-js dry 4: jscpd runs on JavaScript and TypeScript with a JSON report in tmp/jscpd
    When I run sf-js with "dry --base main"
    Then sf-js ran "npx --yes jscpd@4"
    And the command line that runs "jscpd" contains "<argument>"

    Examples:
      | argument                               |
      | --format javascript,typescript,jsx,tsx |
      | --min-tokens 50                        |
      | --reporters json                       |
      | --output tmp/jscpd                     |
      | **/node_modules/**                     |
      | **/dist/**                             |
      | **/build/**                            |
      | **/coverage/**                         |
      | **/tmp/**                              |
      | **/*.d.ts                              |
      | **/*.min.js                            |

  # sf-js dry 5
  Scenario Outline: sf-js dry 5: --min-tokens sets the clone size
    When I run sf-js with "dry --base main --min-tokens <tokens>"
    Then the command line that runs "jscpd" contains "--min-tokens <passed> "

    Examples:
      | tokens | passed |
      | 30     | 30     |
      | 120    | 120    |

  # sf-js dry 6
  Scenario: sf-js dry 6: --report-only needs an earlier report
    When I run sf-js with "dry --report-only --base main"
    Then the exit status is 2
    And the error output contains "tmp/jscpd/jscpd-report.json not found"
    And sf-js did not run "jscpd"
