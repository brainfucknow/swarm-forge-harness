Feature: sf-java quality

  sf-java uses the pinned crap4java, dry4java and mutate4java tools through
  sf tool wrappers, including the approved compatibility patches. Quality
  commands exclude property tests. Default source scope is production Java
  changed since the merge base with --base (SWARMFORGE_BASE, then main,
  then master when omitted), including committed, uncommitted and untracked
  files. Deleted files are not passed to tools. DRY compares the production
  source set but gates only clones touching changed code. Mutation is
  differential against tool-maintained manifests and uses four workers.

  Background:
    Given a single-module Maven Java project in a task worktree
    And controllable pinned Java quality tools are installed through sf tool

  # sf-java quality 1
  Scenario Outline: sf-java quality 1: CRAP is gated at ten
    Given a changed production method has tool-reported CRAP <score>
    When I run sf-java with "crap --base main"
    Then the exit status is <status>
    And the output identifies the method and its CRAP score
    And the coverage run uses the dedicated runner without property tests
    And Maven does not execute tests

    Examples:
      | score | status |
      | 8.5   | 0      |
      | 10.0  | 0      |
      | 10.1  | 1      |

  # sf-java quality 2
  Scenario Outline: sf-java quality 2: changed source scope includes local edits
    Given a production Java file has a <change> change from main
    And the tool reports a CRAP score above ten for that file
    When I run sf-java with "crap --base main"
    Then the exit status is 1
    And the output identifies that production Java file

    Examples:
      | change      |
      | committed   |
      | uncommitted |
      | untracked   |

  # sf-java quality 3
  Scenario Outline: sf-java quality 3: irrelevant changes do not start mutation
    Given only "<file>" changed from main
    When I run sf-java with "mutate --base main"
    Then the exit status is 0
    And mutate4java was not started
    And the output states that no production Java files require mutation

    Examples:
      | file                                 |
      | src/test/java/demo/LedgerTest.java    |
      | README.md                            |

  # sf-java quality 4
  Scenario Outline: sf-java quality 4: DRY gates duplicates touching changes
    Given dry4java reports a duplicate whose left declaration is <left>
    And its right declaration is <right>
    When I run sf-java with "dry --base main"
    Then the exit status is <status>
    And the output reports <findings> relevant duplicates

    Examples:
      | left      | right     | status | findings |
      | changed   | unchanged | 1      | 1        |
      | unchanged | changed   | 1      | 1        |
      | unchanged | unchanged | 0      | 0        |

  # sf-java quality 5
  Scenario Outline: sf-java quality 5: mutation uses the dedicated runner and differential state
    Given a production Java file changed from main
    And mutate4java reports <survivors> surviving mutants
    When I run sf-java with "mutate --base main"
    Then the exit status is <status>
    And mutate4java uses differential manifest selection with four workers
    And mutant tests recompile the mutated source and use the dedicated runner
    And property tests are excluded
    And Maven does not execute tests
    And the output reports <reported> surviving mutants
    And no embedded mutation manifest remains in tracked Java source

    Examples:
      | survivors | status | reported |
      | 0         | 0      | 0        |
      | 1         | 1      | 1        |

  # sf-java quality 7
  Scenario: sf-java quality 7: failed mutation restores application sources
    Given a production Java file changed from main
    And mutation encounters a failing baseline
    When I run sf-java with "mutate --base main"
    Then the exit status is nonzero
    And production source content is unchanged from before the command
    And the application pom.xml is unchanged
    And no embedded mutation manifest remains in tracked Java source

  # sf-java quality 6
  Scenario Outline: sf-java quality 6: tool failures do not look clean
    Given the quality tool cannot produce valid analysis because of "<problem>"
    When I run sf-java with "<command> --base main"
    Then the exit status is 2
    And the error output identifies the analysis failure

    Examples:
      | command | problem                   |
      | crap    | missing coverage data     |
      | dry     | malformed tool report     |
      | mutate  | missing tool executable   |
