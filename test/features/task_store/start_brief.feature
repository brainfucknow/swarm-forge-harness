Feature: sf start brief

  sf start gives a role a bounded brief; sf show keeps printing the whole
  history, every record and every LORE: line.

  Records: sf start always prints every question, answer and
  changes-requested record. Of the other records it prints only the last 10.
  It prints them all oldest first, as sf show does. When it leaves out other
  records, the line
  "RECORDS: <k> earlier records omitted; sf show <id> prints them" comes
  after the request, as its own section, before the first printed record;
  <k> is the number of records left out and <id> is the full task id. With 10
  other records or fewer, sf start prints every record and no RECORDS: line.

  LORE: lines: sf start leaves out two kinds of LORE: lines.
  - Evidence keys: Tested, Not-tested, Confidence and Lore-id.
  - Superseded commits: a "Supersedes: <Lore-id>" trailer on any commit of the
    task branch removes every LORE: line of the commit whose Lore-id equals
    the value. The Supersedes line itself stays unless its own commit is
    superseded. A value that matches no commit of the branch removes nothing.
    sf start reads every commit's Lore-id for this even though it does not
    print Lore-id lines.
  It keeps Constraint, Rejected, Directive, Scope-risk, Reversibility,
  Supersedes, Depends-on and Related, in the order sf show prints them. When
  it leaves out at least one LORE: line, the line
  "LORE-OMITTED: <k> superseded or evidence lines; sf show <id> prints them"
  comes first in the LORE: section, before the printed LORE: lines; <k> is
  the number of LORE: lines that sf show prints and sf start leaves out. When
  it leaves none out, there is no LORE-OMITTED: line.

  Background:
    Given a project with swarmforge initialized
    And task "A" titled "Add login" with the request "Users log in." was started on route "specifier,coder,qa"

  # sf start brief 1
  Scenario Outline: sf start brief 1: sf start keeps every question, answer and changes-requested record and the last 10 others
    Given role "specifier" committed "Spec" in the worktree of task "A" as C1
    And I ran "sf review $A"
    And I ran "sf reject $A revise"
    And the operator asked and answered 2 questions on task "A"
    And the route of task "A" was set to "specifier,coder,qa" <routes> times
    When I run "sf start $A"
    Then the exit status is 0
    And the output has the lines "Users log in.\n\nRECORDS: <omitted> earlier records omitted; sf show $A prints them\n\ntype: <first>"
    And the output has 15 lines starting with "type: "
    And the output has <shown> lines starting with "type: route"
    And the output has the lines "type: changes-requested\nfrom: operator\nreason: revise"
    And the output has the lines "type: question\nfrom: specifier\nquestion: q1\n\ntype: answer\nfrom: operator\nanswer: a1"
    And the output has the line "answer: a2"
    And the output has no line containing "type: created"

    Examples:
      | routes | omitted | first             | shown |
      | 9      | 1       | review            | 9     |
      | 11     | 3       | changes-requested | 10    |

  # sf start brief 2
  Scenario: sf start brief 2: with 10 other records sf start prints every record and no RECORDS line
    Given the operator asked and answered 2 questions on task "A"
    And the route of task "A" was set to "specifier,coder,qa" 9 times
    When I run "sf start $A"
    Then the exit status is 0
    And the output has 14 lines starting with "type: "
    And the output has the lines "Users log in.\n\ntype: created"
    And the output has no line containing "RECORDS:"

  # sf start brief 3
  Scenario: sf start brief 3: sf show prints every record
    Given the operator asked and answered 2 questions on task "A"
    And the route of task "A" was set to "specifier,coder,qa" 11 times
    When I run "sf show $A"
    Then the exit status is 0
    And the output has 16 lines starting with "type: "
    And the output has the lines "Users log in.\n\ntype: created"
    And the output has no line containing "RECORDS:"

  # sf start brief 4
  Scenario Outline: sf start brief 4: sf start keeps the decision keys
    Given role "specifier" committed "Spec\n\n<key>: v" in the worktree of task "A" as C1
    When I run "sf start $A"
    Then the exit status is 0
    And the output ends with the lines "LORE-OMITTED: 1 superseded or evidence lines; sf show $A prints them\nLORE: C1 specifier <key>: v"

    Examples:
      | key           |
      | Constraint    |
      | Rejected      |
      | Directive     |
      | Scope-risk    |
      | Reversibility |
      | Supersedes    |
      | Depends-on    |
      | Related       |

  # sf start brief 5
  Scenario Outline: sf start brief 5: sf start leaves out the evidence keys
    Given role "specifier" committed "Spec\n\n<key>: v" in the worktree of task "A" as C1
    When I run "sf start $A"
    Then the exit status is 0
    And the output ends with the lines "LORE-OMITTED: 2 superseded or evidence lines; sf show $A prints them"
    And the output has no line containing "<key>: v"

    Examples:
      | key        |
      | Tested     |
      | Not-tested |
      | Confidence |

  # sf start brief 6
  Scenario: sf start brief 6: sf start leaves out every LORE line of a superseded commit
    Given role "specifier" committed "Spec\n\nDirective: use A\nConstraint: keep B" in the worktree of task "A" as C1
    And role "coder" committed "Code\n\nDirective: use C\nSupersedes: L1" in the worktree of task "A" as C2
    When I run "sf start $A"
    Then the exit status is 0
    And the output ends with the lines "LORE-OMITTED: 4 superseded or evidence lines; sf show $A prints them\nLORE: C2 coder Directive: use C\nLORE: C2 coder Supersedes: L1"
    And the output has no line containing "keep B"

  # sf start brief 7
  Scenario: sf start brief 7: superseding chains
    Given role "specifier" committed "Spec\n\nDirective: one" in the worktree of task "A" as C1
    And role "coder" committed "Code\n\nDirective: two\nSupersedes: L1" in the worktree of task "A" as C2
    And role "qa" committed "Verify\n\nDirective: three\nSupersedes: L2" in the worktree of task "A" as C3
    When I run "sf start $A"
    Then the exit status is 0
    And the output ends with the lines "LORE-OMITTED: 6 superseded or evidence lines; sf show $A prints them\nLORE: C3 qa Directive: three\nLORE: C3 qa Supersedes: L2"
    And the output has no line containing "Directive: one"

  # sf start brief 8
  Scenario: sf start brief 8: sf show prints superseded and evidence LORE lines
    Given role "specifier" committed "Spec\n\nDirective: use A\nTested: a" in the worktree of task "A" as C1
    And role "coder" committed "Code\n\nDirective: use C\nSupersedes: L1" in the worktree of task "A" as C2
    When I run "sf show $A"
    Then the exit status is 0
    And the output ends with the lines "LORE: C1 specifier Lore-id: L1\nLORE: C1 specifier Directive: use A\nLORE: C1 specifier Tested: a\nLORE: C2 coder Lore-id: L2\nLORE: C2 coder Directive: use C\nLORE: C2 coder Supersedes: L1"
    And the output has no line containing "LORE-OMITTED:"

  # sf start brief 9
  Scenario: sf start brief 9: nothing left out prints no LORE-OMITTED line
    Given "Fix\n\nConstraint: x" was committed in the worktree of task "A" with the hook skipped as C1
    When I run "sf start $A"
    Then the exit status is 0
    And the output ends with the lines "LORE: C1 - Constraint: x"
    And the output has no line containing "LORE-OMITTED:"
