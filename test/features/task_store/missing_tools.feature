Feature: sf missing tools

  sf handoff and sf review read the Not-tested trailers, as git parses them,
  of the role's commits since the newest created, review or handoff record:
  the range of the audit gate's diff. A tools.conf row of the catalogue that
  sf tool uses, other than a row of kind local, is named in a trailer when its
  name appears in the trailer's value, ignoring case, bounded on each side by
  the start or end of the value or by a character other than a letter, a
  digit, "-" or "_". A named tool is missing when sf tool require would exit
  1 for it: its wrapper, or the wrapper of a tool it needs, is not executable
  in .swarmforge/bin/ of the main checkout. The check catches tool names
  only; a trailer that names no tool passes. The prompt rule is the main
  control.

  If a named tool is missing, the command exits 4 and records nothing. It
  prints, for each missing tool once, in the order the commits (oldest first)
  and their trailers name them, the lines "MISSING_TOOL: <id> <tool> is named
  in Not-tested but is not installed; nothing was recorded" and "Run: sf tool
  ensure <tool>", then the line "Then run the check, or record Not-tested with
  the install or run error. If the install fails, run sf ask; after the
  operator answers, pass --allow-missing-tool." The check runs after the
  refusals sf handoff already has and before the audit gate, so a refusal
  leaves no audit state.

  --allow-missing-tool is found anywhere in the arguments and left out of the
  summary, as --to is. It lets the command record only when the task has an
  answer record from the operator after the newest created, review or handoff
  record; sf does not read the answer's text. Without that answer the command
  still exits 4, prints the lines above, and first prints the line
  "ALLOW_REFUSED: <id> no operator answer since the last record". When the
  flag lets a command record over a missing tool, the record ends with the
  trailer "SwarmForge-Missing-Tools: <tools>", the missing tools in the order
  above, separated by spaces; sf show and sf start print it as
  "missing-tools: <tools>". With no missing tool the flag adds nothing and
  needs no answer. sf approve does not run the check: the review it approves
  ran it at the same commit.

  Background:
    Given a project with swarmforge initialized
    And task "A" titled "Add login" was started on route "coder,qa"

  # sf missing tools 1
  Scenario Outline: sf missing tools 1: Not-tested naming a missing tool refuses the handoff
    Given role "coder" committed "Code\n\nNot-tested: <trailer>" in the worktree of task "A" as C1
    When I run "sf handoff $A"
    Then the exit status is 4
    And the output has the line "MISSING_TOOL: $A <first> is named in Not-tested but is not installed; nothing was recorded"
    And the output has the line "Run: sf tool ensure <first>"
    And the output has <count> lines starting with "MISSING_TOOL: "
    And the output has the line "Then run the check, or record Not-tested with the install or run error. If the install fails, run sf ask; after the operator answers, pass --allow-missing-tool."
    And the branch of task "A" did not move

    Examples:
      | trailer                                           | first      | count |
      | CRAP, DRY: crap4clj and dry4clj are not installed | crap4clj   | 2     |
      | DRY: dry4clj.                                     | dry4clj    | 1     |
      | mutation: (clj-mutate) not installed              | clj-mutate | 1     |
      | DRY: Dry4CLJ is not installed                     | dry4clj    | 1     |

  # sf missing tools 2
  Scenario Outline: sf missing tools 2: an installed tool, a local row or a non-matching name does not refuse
    Given the wrappers for "<installed>" are executable in the main checkout
    And role "coder" committed "Code\n\nNot-tested: <trailer>" in the worktree of task "A" as C1
    When I run "sf handoff $A"
    Then the exit status is 0
    And the output is "HANDOFF: $A coder -> qa at C1"

    Examples:
      | installed              | trailer                                                     |
      | crap4clj cloverage     | CRAP: crap4clj failed: Could not find namespace             |
      | speclj-structure-check | structure: speclj-structure-check failed with exit 2        |
      | dry4clj                | DRY: mydry4clj and dry4clj_old are not ours                 |
      | dry4clj                | mutation: the mutator has no target                         |
      | dry4clj                | mutation: sf-fs mutate is unsupported for F# (exit 2)       |
      | dry4clj                | CRAP, DRY: the Clojure quality tools are not installed here |

  # sf missing tools 3
  Scenario: sf missing tools 3: a missing tool that the named tool needs counts as missing
    Given the wrappers for "crap4clj" are executable in the main checkout
    And role "coder" committed "Code\n\nNot-tested: CRAP: crap4clj failed" in the worktree of task "A" as C1
    When I run "sf handoff $A"
    Then the exit status is 4
    And the output has the line "MISSING_TOOL: $A crap4clj is named in Not-tested but is not installed; nothing was recorded"
    And the branch of task "A" did not move

  # sf missing tools 4
  Scenario: sf missing tools 4: only Not-tested trailers count
    Given role "coder" committed "Code dry4clj\n\nSkipped dry4clj here.\n\nTested: dry4clj src -> clean\nConstraint: crap4clj later" in the worktree of task "A" as C1
    When I run "sf handoff $A"
    Then the exit status is 0
    And the output is "HANDOFF: $A coder -> qa at C1"

  # sf missing tools 5
  Scenario: sf missing tools 5: every commit since the previous record counts, each tool once
    Given role "coder" committed "First\n\nNot-tested: DRY: dry4clj" in the worktree of task "A" as C1
    And role "coder" committed "Second\n\nNot-tested: DRY again: dry4clj\nNot-tested: CRAP: crap4clj" in the worktree of task "A" as C2
    When I run "sf handoff $A"
    Then the exit status is 4
    And the output has 2 lines starting with "MISSING_TOOL: "
    And the output has the line "MISSING_TOOL: $A dry4clj is named in Not-tested but is not installed; nothing was recorded"
    And the output has the line "MISSING_TOOL: $A crap4clj is named in Not-tested but is not installed; nothing was recorded"
    And the branch of task "A" did not move

  # sf missing tools 6
  Scenario: sf missing tools 6: an answered question lets --allow-missing-tool record the missing tools
    Given role "coder" committed "Code\n\nNot-tested: DRY, CRAP: dry4clj, crap4clj" in the worktree of task "A" as C1
    And I ran "sf ask $A dry4clj will not install; accept?"
    And I ran "sf answer $A accept"
    When I run "sf handoff $A --allow-missing-tool -m code done"
    Then the exit status is 0
    And the output is "HANDOFF: $A coder -> qa at C1"
    And the tip commit of task "A" has the message "sf handoff: $A\n\ncode done\n\nSwarmForge-Task: $A\nSwarmForge-Record: handoff\nSwarmForge-From: coder\nSwarmForge-Stage: qa\nSwarmForge-Route: coder,qa\nSwarmForge-Needs: none\nSwarmForge-Missing-Tools: dry4clj crap4clj"

  # sf missing tools 7
  Scenario: sf missing tools 7: --allow-missing-tool without an operator answer is refused
    Given role "coder" committed "Code\n\nNot-tested: DRY: dry4clj" in the worktree of task "A" as C1
    When I run "sf handoff $A --allow-missing-tool"
    Then the exit status is 4
    And the output has the line "ALLOW_REFUSED: $A no operator answer since the last record"
    And the output has the line "MISSING_TOOL: $A dry4clj is named in Not-tested but is not installed; nothing was recorded"
    And the branch of task "A" did not move

  # sf missing tools 8
  Scenario: sf missing tools 8: an answer before the last handoff does not carry to the next role
    Given role "coder" committed "Code\n\nNot-tested: DRY: dry4clj" in the worktree of task "A" as C1
    And I ran "sf ask $A accept dry4clj?"
    And I ran "sf answer $A accept"
    And I ran "sf handoff $A --allow-missing-tool"
    And role "qa" committed "QA\n\nNot-tested: DRY: dry4clj" in the worktree of task "A" as C2
    When I run "sf handoff $A --allow-missing-tool"
    Then the exit status is 4
    And the output has the line "ALLOW_REFUSED: $A no operator answer since the last record"

  # sf missing tools 9
  Scenario: sf missing tools 9: the next role is not held to the previous role's commits
    Given role "coder" committed "Code\n\nNot-tested: DRY: dry4clj" in the worktree of task "A" as C1
    And I ran "sf ask $A accept dry4clj?"
    And I ran "sf answer $A accept"
    And I ran "sf handoff $A --allow-missing-tool"
    And role "qa" committed "QA" in the worktree of task "A" as C2
    When I run "sf handoff $A"
    Then the exit status is 0
    And the output is "HANDOFF: $A qa -> done at C2"

  # sf missing tools 10
  Scenario: sf missing tools 10: --allow-missing-tool with no missing tool adds nothing and needs no answer
    Given role "coder" committed "Code" in the worktree of task "A" as C1
    When I run "sf handoff $A --allow-missing-tool"
    Then the exit status is 0
    And the tip commit of task "A" has the message "sf handoff: $A\n\nSwarmForge-Task: $A\nSwarmForge-Record: handoff\nSwarmForge-From: coder\nSwarmForge-Stage: qa\nSwarmForge-Route: coder,qa\nSwarmForge-Needs: none"

  # sf missing tools 11
  Scenario: sf missing tools 11: sf review refuses the same way
    Given task "B" titled "Spec" was started on route "specifier,coder"
    And role "specifier" committed "Spec\n\nNot-tested: DRY: dry4clj" in the worktree of task "B" as C1
    When I run "sf review $B"
    Then the exit status is 4
    And the output has the line "MISSING_TOOL: $B dry4clj is named in Not-tested but is not installed; nothing was recorded"
    And the branch of task "B" did not move

  # sf missing tools 12
  Scenario: sf missing tools 12: an allowed review is approved without a second check
    Given task "B" titled "Spec" was started on route "specifier,coder"
    And role "specifier" committed "Spec\n\nNot-tested: DRY: dry4clj" in the worktree of task "B" as C1
    And I ran "sf ask $B accept dry4clj?"
    And I ran "sf answer $B accept"
    And I ran "sf review $B --allow-missing-tool"
    When I run "sf approve $B"
    Then the exit status is 0
    And the output has the line "HANDOFF: $B specifier -> coder at C1"

  # sf missing tools 13
  Scenario: sf missing tools 13: sf start shows the allowed missing tools
    Given role "coder" committed "Code\n\nNot-tested: DRY: dry4clj" in the worktree of task "A" as C1
    And I ran "sf ask $A accept dry4clj?"
    And I ran "sf answer $A accept"
    And I ran "sf handoff $A --allow-missing-tool"
    When I run "sf start $A"
    Then the exit status is 0
    And the output has the line "missing-tools: dry4clj"
