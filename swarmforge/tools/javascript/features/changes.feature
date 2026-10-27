Feature: sf-js changes

  "Changed" means the task: every line changed since the merge base with the
  base branch, committed or not, plus whole untracked files, in files ending
  .js .jsx .ts .tsx .mjs .cjs .mts .cts but not .d.ts. Deleting lines
  changes the line that now follows the deletion (line 1 when the deletion
  is at the top of the file), so a function whose change only deletes lines
  still counts as changed. sf-cs shares this rule. --base defaults to
  SWARMFORGE_BASE, then main, then master. An empty SWARMFORGE_BASE cell
  means the variable is unset. sf-js dry --report-only shows the
  scoping because it lists only clones that touch a changed line.

  # sf-js changes 1
  Scenario Outline: sf-js changes 1: only JavaScript and TypeScript sources count as changed
    Given a task worktree branched from main
    And fake JavaScript tools on PATH
    And "<file>" is added on the task branch
    And the jscpd report has a 64-token clone of "<file>" lines 1 to 3 and "lib/old.js" lines 10 to 12
    When I run sf-js with "dry --report-only --base main"
    Then the exit status is <status>
    And the output ends with the line "<summary>"

    Examples:
      | file       | status | summary                             |
      | src/a.js   | 1      | 1 of 1 clone(s) touch changed lines |
      | src/a.jsx  | 1      | 1 of 1 clone(s) touch changed lines |
      | src/a.ts   | 1      | 1 of 1 clone(s) touch changed lines |
      | src/a.tsx  | 1      | 1 of 1 clone(s) touch changed lines |
      | src/a.mjs  | 1      | 1 of 1 clone(s) touch changed lines |
      | src/a.cjs  | 1      | 1 of 1 clone(s) touch changed lines |
      | src/a.mts  | 1      | 1 of 1 clone(s) touch changed lines |
      | src/a.cts  | 1      | 1 of 1 clone(s) touch changed lines |
      | src/a.d.ts | 0      | 0 of 1 clone(s) touch changed lines |
      | src/a.json | 0      | 0 of 1 clone(s) touch changed lines |
      | src/a.cs   | 0      | 0 of 1 clone(s) touch changed lines |

  # sf-js changes 2
  Scenario Outline: sf-js changes 2: committed, uncommitted, and untracked work all count
    Given a main branch where "src/ledger.ts" has 40 lines
    And a task worktree branched from main
    And fake JavaScript tools on PATH
    And line 14 of "src/ledger.ts" is <state>
    And the jscpd report has a 64-token clone of "src/ledger.ts" lines 13 to 15 and "lib/old.js" lines 10 to 12
    When I run sf-js with "dry --report-only --base main"
    Then the exit status is <status>
    And the output ends with the line "<summary>"

    Examples:
      | state                                | status | summary                             |
      | changed on the task branch           | 1      | 1 of 1 clone(s) touch changed lines |
      | changed but not committed            | 1      | 1 of 1 clone(s) touch changed lines |
      | changed on main after the task began | 0      | 0 of 1 clone(s) touch changed lines |

  # sf-js changes 3
  Scenario: sf-js changes 3: an untracked file counts as changed throughout
    Given a task worktree branched from main
    And fake JavaScript tools on PATH
    And "src/fresh.ts" is untracked with 20 lines
    And the jscpd report has a 64-token clone of "src/fresh.ts" lines 18 to 20 and "lib/old.js" lines 10 to 12
    When I run sf-js with "dry --report-only --base main"
    Then the exit status is 1
    And the output ends with the line "1 of 1 clone(s) touch changed lines"

  # sf-js changes 4
  Scenario Outline: sf-js changes 4: the base branch defaults to SWARMFORGE_BASE, then main, then master
    Given a task worktree branched from "<base>"
    And fake JavaScript tools on PATH
    And SWARMFORGE_BASE is "<env>"
    And "src/a.ts" is added on the task branch
    And the jscpd report has a 64-token clone of "src/a.ts" lines 1 to 3 and "lib/old.js" lines 10 to 12
    When I run sf-js with "dry --report-only"
    Then the exit status is <status>

    Examples:
      | base   | env   | status |
      | main   |       | 1      |
      | master |       | 1      |
      | trunk  | trunk | 1      |
      | trunk  |       | 2      |

  # sf-js changes 5
  Scenario: sf-js changes 5: no base branch fails
    Given a task worktree branched from "trunk"
    And fake JavaScript tools on PATH
    And the jscpd report has a 64-token clone of "src/a.ts" lines 1 to 3 and "lib/old.js" lines 10 to 12
    When I run sf-js with "dry --report-only"
    Then the exit status is 2
    And the error output contains "no base branch found"

  # sf-js changes 6
  Scenario Outline: sf-js changes 6: deleting lines changes the line after them
    Given a main branch where "src/ledger.ts" has 40 lines
    And a task worktree branched from main
    And fake JavaScript tools on PATH
    And lines <first> to <last> of "src/ledger.ts" are deleted on the task branch
    And the jscpd report has a 64-token clone of "src/ledger.ts" lines <from> to <to> and "lib/old.js" lines 10 to 12
    When I run sf-js with "dry --report-only --base main"
    Then the exit status is <status>
    And the output ends with the line "<summary>"

    Examples:
      | first | last | from | to | status | summary                             |
      | 12    | 13   | 12   | 14 | 1      | 1 of 1 clone(s) touch changed lines |
      | 12    | 13   | 9    | 11 | 0      | 0 of 1 clone(s) touch changed lines |
      | 12    | 13   | 13   | 15 | 0      | 0 of 1 clone(s) touch changed lines |
      | 1     | 2    | 1    | 3  | 1      | 1 of 1 clone(s) touch changed lines |
