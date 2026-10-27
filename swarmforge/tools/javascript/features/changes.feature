Feature: sf-js changes

  "Changed" means the task: every line changed since the merge base with the
  base branch, committed or not, plus whole untracked files, in files ending
  .js .jsx .ts .tsx .mjs .cjs .mts .cts but not .d.ts. Deleting lines
  changes the line that now follows the deletion (line 1 when the deletion
  is at the top of the file), so a function whose change only deletes lines
  still counts as changed. sf-cs shares this rule. --base defaults to the
  first of SWARMFORGE_BASE, main and master that names a branch. sf-js dry
  --report-only shows the scoping because it lists only clones that touch a
  changed line.

  # sf-js changes 1
  Scenario Outline: sf-js changes 1: JavaScript and TypeScript sources count as changed
    Given a task worktree branched from main
    And fake JavaScript tools on PATH
    And "<file>" is added on the task branch
    And the jscpd report has a 64-token clone of "<file>" lines 1 to 3 and "lib/old.js" lines 10 to 12
    When I run sf-js with "dry --report-only --base main"
    Then the exit status is 1
    And the output has the line "<row>"
    And the output ends with the line "1 of 1 clone(s) touch changed lines"

    Examples:
      | file      | row                                                      |
      | src/a.js  | 64 tokens  src/a.js:1-3 (changed)  ==  lib/old.js:10-12  |
      | src/a.jsx | 64 tokens  src/a.jsx:1-3 (changed)  ==  lib/old.js:10-12 |
      | src/a.ts  | 64 tokens  src/a.ts:1-3 (changed)  ==  lib/old.js:10-12  |
      | src/a.tsx | 64 tokens  src/a.tsx:1-3 (changed)  ==  lib/old.js:10-12 |
      | src/a.mjs | 64 tokens  src/a.mjs:1-3 (changed)  ==  lib/old.js:10-12 |
      | src/a.cjs | 64 tokens  src/a.cjs:1-3 (changed)  ==  lib/old.js:10-12 |
      | src/a.mts | 64 tokens  src/a.mts:1-3 (changed)  ==  lib/old.js:10-12 |
      | src/a.cts | 64 tokens  src/a.cts:1-3 (changed)  ==  lib/old.js:10-12 |

  # sf-js changes 1
  Scenario: sf-js changes 1: declarations and other files do not count as changed
    Given a task worktree branched from main
    And fake JavaScript tools on PATH
    And "src/a.d.ts" is added on the task branch
    And "src/a.json" is added on the task branch
    And "src/a.cs" is added on the task branch
    And the jscpd report has a 64-token clone of "src/a.d.ts" lines 1 to 3 and "lib/old.js" lines 10 to 12
    And the jscpd report has a 64-token clone of "src/a.json" lines 1 to 3 and "lib/old.js" lines 10 to 12
    And the jscpd report has a 64-token clone of "src/a.cs" lines 1 to 3 and "lib/old.js" lines 10 to 12
    When I run sf-js with "dry --report-only --base main"
    Then the exit status is 0
    And the output ends with the line "0 of 3 clone(s) touch changed lines"

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
  Scenario Outline: sf-js changes 4: without SWARMFORGE_BASE the base branch is main, then master
    Given a task worktree branched from "<base>"
    And fake JavaScript tools on PATH
    And "src/a.ts" is added on the task branch
    And the jscpd report has a 64-token clone of "src/a.ts" lines 1 to 3 and "lib/old.js" lines 10 to 12
    When I run sf-js with "dry --report-only"
    Then the exit status is 1

    Examples:
      | base   |
      | main   |
      | master |

  # sf-js changes 4
  Scenario Outline: sf-js changes 4: SWARMFORGE_BASE names the base branch
    Given a task worktree branched from "<base>"
    And fake JavaScript tools on PATH
    And SWARMFORGE_BASE is "<env>"
    And "src/a.ts" is added on the task branch
    And the jscpd report has a 64-token clone of "src/a.ts" lines 1 to 3 and "lib/old.js" lines 10 to 12
    When I run sf-js with "dry --report-only"
    Then the exit status is 1

    Examples:
      | base    | env     |
      | trunk   | trunk   |
      | develop | develop |

  # sf-js changes 4
  Scenario: sf-js changes 4: a SWARMFORGE_BASE that names no branch falls back to main
    Given a task worktree branched from main
    And fake JavaScript tools on PATH
    And SWARMFORGE_BASE is "trunk"
    And "src/a.ts" is added on the task branch
    And the jscpd report has a 64-token clone of "src/a.ts" lines 1 to 3 and "lib/old.js" lines 10 to 12
    When I run sf-js with "dry --report-only"
    Then the exit status is 1

  # sf-js changes 5
  Scenario: sf-js changes 5: no base branch fails
    Given a task worktree branched from "trunk"
    And fake JavaScript tools on PATH
    And the jscpd report has a 64-token clone of "src/a.ts" lines 1 to 3 and "lib/old.js" lines 10 to 12
    When I run sf-js with "dry --report-only"
    Then the exit status is 2
    And the error output contains "no base branch found"

  # sf-js changes 6
  Scenario: sf-js changes 6: deleting lines 12 to 13 changes line 12, the first line of a clone of lines 12 to 14
    Given a main branch where "src/ledger.ts" has 40 lines
    And a task worktree branched from main
    And fake JavaScript tools on PATH
    And lines 12 to 13 of "src/ledger.ts" are deleted on the task branch
    And the jscpd report has a 64-token clone of "src/ledger.ts" lines 12 to 14 and "lib/old.js" lines 10 to 12
    When I run sf-js with "dry --report-only --base main"
    Then the exit status is 1
    And the output ends with the line "1 of 1 clone(s) touch changed lines"

  # sf-js changes 6
  Scenario: sf-js changes 6: deleting lines 12 to 13 does not change a clone of lines 9 to 11, which ends before line 12
    Given a main branch where "src/ledger.ts" has 40 lines
    And a task worktree branched from main
    And fake JavaScript tools on PATH
    And lines 12 to 13 of "src/ledger.ts" are deleted on the task branch
    And the jscpd report has a 64-token clone of "src/ledger.ts" lines 9 to 11 and "lib/old.js" lines 10 to 12
    When I run sf-js with "dry --report-only --base main"
    Then the exit status is 0
    And the output ends with the line "0 of 1 clone(s) touch changed lines"

  # sf-js changes 6
  Scenario: sf-js changes 6: deleting lines 12 to 13 does not change a clone of lines 13 to 15, which starts after line 12
    Given a main branch where "src/ledger.ts" has 40 lines
    And a task worktree branched from main
    And fake JavaScript tools on PATH
    And lines 12 to 13 of "src/ledger.ts" are deleted on the task branch
    And the jscpd report has a 64-token clone of "src/ledger.ts" lines 13 to 15 and "lib/old.js" lines 10 to 12
    When I run sf-js with "dry --report-only --base main"
    Then the exit status is 0
    And the output ends with the line "0 of 1 clone(s) touch changed lines"

  # sf-js changes 6
  Scenario: sf-js changes 6: deleting lines 1 to 2 changes line 1, the first line of a clone of lines 1 to 3
    Given a main branch where "src/ledger.ts" has 40 lines
    And a task worktree branched from main
    And fake JavaScript tools on PATH
    And lines 1 to 2 of "src/ledger.ts" are deleted on the task branch
    And the jscpd report has a 64-token clone of "src/ledger.ts" lines 1 to 3 and "lib/old.js" lines 10 to 12
    When I run sf-js with "dry --report-only --base main"
    Then the exit status is 1
    And the output ends with the line "1 of 1 clone(s) touch changed lines"
