Feature: sf tool entry point guard

  Before it writes a wrapper, sf tool ensure checks that the tool's source has
  the entry point the catalogue names. For kind bb, the source must have a
  bb.edn whose :tasks map has the task. For kind bbmain, src/ in the source
  must hold a file for the namespace: dots become slashes, dashes become
  underscores, and the extension is .clj, .cljc or .bb. When the entry point
  is missing, ensure prints an error naming the tool on stderr, exits 1, and
  writes no wrapper for that tool. A wrapper left by an earlier install stays
  byte-for-byte as it was, so require still reports the tool; a failing ensure
  never replaces or removes a wrapper. Tools the tool needs that were
  installed first stay installed. ensure does not try another way to install
  the tool. A source directory that already exists under .swarmforge/tools is
  used as it is and is not cloned again.

  Background:
    Given a project with swarmforge initialized
    And a fake bb on PATH

  # sf tool entry point guard 1
  Scenario Outline: sf tool entry point guard 1: a missing entry point fails the install and keeps the earlier wrapper
    Given a tool source whose bb.edn defines the tasks "<tasks>"
    And an earlier install left a wrapper for "<tool>"
    When I run "sf tool ensure <tool>" in the main checkout
    Then the exit status is 1
    And the error output contains "<error>"
    And the wrapper for "<tool>" is unchanged
    And "sf tool require <tool>" in the main checkout prints "OK: <tool> MAIN/.swarmforge/bin/<tool>"

    Examples:
      | tool       | tasks           | error                                                   |
      | clj-mutate | spec clj-mutate | sf tool: clj-mutate: SRC/bb.edn has no task 'mutate'    |
      | crap4clj   | crap4clj int    | sf tool: crap4clj: SRC/bb.edn has no task 'crap'        |
      | dry4clj    | deintroverter   | sf tool: dry4clj: SRC/src has no namespace dry4clj.core |

  # sf tool entry point guard 2
  Scenario Outline: sf tool entry point guard 2: a source without bb.edn fails the install
    Given a tool source without bb.edn
    When I run "sf tool ensure <tool>" in the main checkout
    Then the exit status is 1
    And the error output contains "sf tool: <tool>: no bb.edn in SRC"
    And there is no wrapper for "<tool>"

    Examples:
      | tool        |
      | crap4go     |
      | mutate4java |

  # sf tool entry point guard 3
  Scenario: sf tool entry point guard 3: an existing clone is not cloned again
    Given no tool source override
    And the directory "MAIN/.swarmforge/tools/dry4go" exists without bb.edn
    When I run "sf tool ensure dry4go" in the main checkout
    Then the exit status is 1
    And the error output contains "sf tool: dry4go: no bb.edn in MAIN/.swarmforge/tools/dry4go"
    And the error output does not mention "clone"

  # sf tool entry point guard 4
  Scenario: sf tool entry point guard 4: a bb.edn that cannot be read fails the install and keeps the earlier wrapper
    Given a tool source whose bb.edn is "{:tasks {crap {}"
    And an earlier install left a wrapper for "crap4clj"
    When I run "sf tool ensure crap4clj" in the main checkout
    Then the exit status is 1
    And the error output contains "sf tool: crap4clj: SRC/bb.edn could not be read: "
    And the wrapper for "crap4clj" is unchanged
    And "sf tool require crap4clj" in the main checkout prints "OK: crap4clj MAIN/.swarmforge/bin/crap4clj"
