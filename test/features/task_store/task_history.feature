Feature: sf task history

  sf start prints the lines TASK, TITLE, STAGE, ROUTE, BRANCH and DIR; sf show
  prints TASK, TITLE, STATUS (open or finished), STAGE and ROUTE. Then both
  print a "---" line and these sections, separated by one blank line: the
  request (the task file below its title heading); the records, oldest first,
  each a block of "key: value" lines as in handoff-protocol.md; and the LORE:
  lines. In a value of several lines, each later line is indented by two
  spaces. The commit of a review or handoff record is the role's commit that
  the record was made on; files lists what changed since the commit of the
  previous created, review or handoff record, or "-".

  Records and LORE: lines come from the first-parent history of the task
  branch only, so the commits that a merge of the base branch brings in are
  left out. The LORE: lines are those of the commits after the created record.

  sf show also shows a finished task (tasks/done/<id>.md is in the HEAD of
  the main checkout). When HEAD's first-parent history has the task's
  finished record, sf show reads the records and LORE: lines from that
  merge's second parent and ends the records with the finished block. When it
  has none (a host merge or a squash merge), the records are those of the
  "## Records" section of tasks/done/<id>.md without their fences, with no
  finished block, and LORE: lines are printed only while the task branch
  still exists. sf start refuses a finished task.

  Background:
    Given a project with swarmforge initialized

  # sf task history 1
  Scenario Outline: sf task history 1: start and show print the request, the records and the LORE lines
    Given task "A" titled "Add login" with the request "Users log in.\nWith email." was started on route "specifier,coder"
    And I ran "sf ask $A Which hash?"
    And I ran "sf answer $A argon2id"
    And role "specifier" committed the file "features/a.feature" with "Spec\n\nConstraint: keep it short" in the worktree of task "A" as C1
    And I ran "sf review $A spec ready"
    When I run "sf <command> $A"
    Then the exit status is 0
    And the output has the lines "<header>" before the line "---"
    And the output after the line "---" is "Users log in.\nWith email.\n\ntype: created\nfrom: operator\nbranch: swarm/$A\ncommit: $A:created\nroute: specifier,coder\n\ntype: question\nfrom: specifier\nquestion: Which hash?\n\ntype: answer\nfrom: operator\nanswer: argon2id\n\ntype: review\nfrom: specifier\ncommit: C1\nfiles: features/a.feature\nsummary: spec ready\n\nLORE: C1 specifier Lore-id: L1\nLORE: C1 specifier Constraint: keep it short"

    Examples:
      | command | header                                                                                                  |
      | start   | TASK: $A\nTITLE: Add login\nSTAGE: specifier\nROUTE: specifier,coder\nBRANCH: swarm/$A\nDIR: ROOT/.worktrees/$A |
      | show    | TASK: $A\nTITLE: Add login\nSTATUS: open\nSTAGE: specifier\nROUTE: specifier,coder                          |

  # sf task history 2
  Scenario: sf task history 2: a value of several lines goes on indented lines
    Given task "A" titled "Add login" was started on route "specifier"
    And I ran "sf ask $A" with the text "Which hash?\n1) bcrypt\n2) argon2id"
    When I run "sf show $A"
    Then the exit status is 0
    And the output has the lines "type: question\nfrom: specifier\nquestion: Which hash?\n  1) bcrypt\n  2) argon2id"

  # sf task history 3
  Scenario: sf task history 3: a merge of the base branch adds no records and no LORE lines of other tasks
    Given task "B" titled "Fix bug" was started on route "coder"
    And task "A" titled "Add login" was started on route "specifier"
    And role "specifier" committed "Spec\n\nTested: from A" in the worktree of task "A" as C2
    And I ran "sf review $A"
    And I ran "sf approve $A"
    And task "A" was finished
    And role "coder" committed "Work\n\nTested: from B" in the worktree of task "B" as C1
    And I ran "git merge --no-edit main" in the worktree of task "B"
    When I run "sf show $B"
    Then the exit status is 0
    And the output has no line containing "$A"
    And the output has no line containing "Tested: from A"
    And the output ends with the lines "LORE: C1 coder Lore-id: L1\nLORE: C1 coder Tested: from B"

  # sf task history 4
  Scenario Outline: sf task history 4: sf show prints a finished task with its LORE lines
    Given task "A" titled "Add login" was started on route "specifier"
    And role "specifier" committed "Spec\n\nTested: x" in the worktree of task "A" as C1
    And I ran "sf review $A"
    And I ran "sf approve $A"
    And task "A" was finished
    When I run "sf show <argument>"
    Then the exit status is 0
    And the output has the lines "TASK: $A\nTITLE: Add login\nSTATUS: finished\nSTAGE: done\nROUTE: specifier" before the line "---"
    And the output has the lines "type: finished\nfrom: operator\nmerged-into: main"
    And the output ends with the lines "LORE: C1 specifier Lore-id: L1\nLORE: C1 specifier Tested: x"

    Examples:
      | argument |
      | $A       |
      | $A:hex   |

  # sf task history 5
  Scenario: sf task history 5: sf start refuses a finished task
    Given task "A" titled "Add login" was started on route "specifier"
    And I ran "sf review $A"
    And I ran "sf approve $A"
    And task "A" was finished
    When I run "sf start $A"
    Then the exit status is 1
    And the error output has the line "sf: task $A is finished"

  # sf task history 6
  Scenario: sf task history 6: sf show of a squash-merged task prints the archived records and the LORE lines of its branch
    Given task "A" titled "Add login" was started on route "specifier"
    And I ran "sf ask $A Which?"
    And I ran "sf answer $A yes"
    And role "specifier" committed "Spec\n\nTested: x" in the worktree of task "A" as C1
    And I ran "sf review $A"
    And I ran "sf approve $A"
    And task "A" was handed off to done and squash-merged into main
    When I run "sf show $A"
    Then the exit status is 0
    And the output has the lines "TASK: $A\nTITLE: Add login\nSTATUS: finished\nSTAGE: done\nROUTE: specifier" before the line "---"
    And the output has the lines "type: question\nfrom: specifier\nquestion: Which?"
    And the output has no line containing "type: finished"
    And the output has no line containing "```"
    And the output ends with the lines "LORE: C1 specifier Lore-id: L1\nLORE: C1 specifier Tested: x"

  # sf task history 7
  Scenario: sf task history 7: once the branch of a squash-merged task is gone, sf show prints no LORE lines
    Given task "A" titled "Add login" was started on route "specifier"
    And role "specifier" committed "Spec\n\nTested: x" in the worktree of task "A" as C1
    And I ran "sf review $A"
    And I ran "sf approve $A"
    And task "A" was handed off to done and squash-merged into main
    And the worktree and the branch of task "A" were removed
    When I run "sf show $A"
    Then the exit status is 0
    And the output has the line "STATUS: finished"
    And the output ends with the lines "type: handoff\nfrom: specifier\nto: done\nbranch: swarm/$A\ncommit: C1\nfiles: -\nsummary: specifier work complete"
