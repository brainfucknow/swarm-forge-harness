# acceptance-mutation-manifest-begin
# {"version":1,"tested_at":"2026-10-26T16:32:07.058140096Z","feature_name":"sf-js dry","feature_path":"swarmforge/tools/javascript/features/dry.feature","background_hash":"5d2123b12eaf3ad0f317f89bdfa37ce12217b21f338f3cb49911179be913d719","implementation_hash":"sha256:4d409f244c07e5dac9c1fb0d35e1dadec81943db84bbc4fa06e6024f94462e03","scenarios":[{"index":1,"name":"sf-js dry 2: each listed clone names both sides and marks the changed one","scenario_hash":"19e2a3eb19b7058700b99ac05b71f0e9e4fccd702eae1efc6a6ce683bbe9c489","mutation_count":10,"result":{"Total":10,"Killed":10,"Survived":0,"Errors":0},"tested_at":"2026-10-26T16:17:02.296813096Z"},{"index":3,"name":"sf-js dry 4: jscpd runs on JavaScript and TypeScript with a JSON report in tmp/jscpd","scenario_hash":"a6544e70cd957b100c034701aa3746718a69f5e3a661dcfc500223dbd78261e0","mutation_count":11,"result":{"Total":11,"Killed":11,"Survived":0,"Errors":0},"tested_at":"2026-10-26T16:17:02.296813096Z"}]}
# acceptance-mutation-manifest-end

Feature: sf-js dry

  sf-js dry runs jscpd over JavaScript and TypeScript with a JSON report in
  tmp/jscpd/ and lists the clones that touch a changed line. Exit 1 when it
  lists any. --report-only re-reads the last report.

  Background:
    Given a main branch where "src/ledger.ts" has 40 lines
    And a task worktree branched from main
    And fake JavaScript tools on PATH

  # sf-js dry 1
  Scenario Outline: sf-js dry 1: a clone is listed when a changed line falls inside it
    Given the jscpd report has a 64-token clone of "src/ledger.ts" lines 13 to 15 and "src/legacy.ts" lines 30 to 32
    And line <changed> of "src/ledger.ts" changed on the task branch
    When I run sf-js with "dry --report-only --base main"
    Then the exit status is <status>
    And the output ends with the line "<summary>"

    Examples:
      | changed | status | summary                             |
      | 13      | 1      | 1 of 1 clone(s) touch changed lines |
      | 15      | 1      | 1 of 1 clone(s) touch changed lines |
      | 12      | 0      | 0 of 1 clone(s) touch changed lines |
      | 16      | 0      | 0 of 1 clone(s) touch changed lines |

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
    Then the command line that runs "jscpd" contains "--min-tokens <tokens>"

    Examples:
      | tokens |
      | 30     |
      | 120    |

  # sf-js dry 6
  Scenario: sf-js dry 6: --report-only needs an earlier report
    When I run sf-js with "dry --report-only --base main"
    Then the exit status is 2
    And the error output contains "tmp/jscpd/jscpd-report.json not found"
    And sf-js did not run "jscpd"
