Feature: sf gherkin-mutator manifest block

  gherkin-mutator keeps its mutation stamp and scenario manifest as a block of
  comment lines at the top of the feature file. The block is an optional
  "# mutation-stamp: ..." line, then the lines from
  "# acceptance-mutation-manifest-begin" to
  "# acceptance-mutation-manifest-end". A blank line comes after it.

  The gherkin-mutator wrapper that sf tool ensure gherkin-mutator writes keeps
  the block out of the feature file between runs. The wrapper is run from the
  root of the task worktree with --feature <path>.

  Before the run: when .metrics/gherkin/<path> exists and the feature file has
  no block, the wrapper puts the lines of .metrics/gherkin/<path> and a blank
  line at the top of the feature file.

  After the run, whatever its exit status: the wrapper writes the block that
  the feature file has to .metrics/gherkin/<path>, one line per block line,
  and replaces what was there. Then it removes the block lines from the
  feature file, and the blank lines that then come first. The wrapper exits
  with the exit status of the run. The argument rewriting of the wrapper
  (--level, --workers) does not change.

  Background:
    Given a project with swarmforge initialized and .metrics/ ignored
    And task "A" was started
    And the worktree of task "A" has the committed file "features/x.feature" that holds "Feature: x"

  # sf gherkin-mutator manifest block 1
  Scenario Outline: sf gherkin-mutator manifest block 1: the block of a run moves to .metrics/gherkin/
    Given gherkin-mutator is a fake that writes the block "<block>" and exits with status 0
    When I run ".swarmforge/bin/gherkin-mutator --runner-worker w --feature features/x.feature" in the worktree of task "A"
    Then the exit status is 0
    And the fake saw the feature file holding "Feature: x"
    And the file "features/x.feature" of task "A" is as committed
    And the worktree file ".metrics/gherkin/features/x.feature" of task "A" holds "<block>"

    Examples:
      | block                                                                                                       |
      | # mutation-stamp: sha256=ab\n# acceptance-mutation-manifest-begin\n# {}\n# acceptance-mutation-manifest-end |
      | # acceptance-mutation-manifest-begin\n# {}\n# acceptance-mutation-manifest-end                              |

  # sf gherkin-mutator manifest block 2
  Scenario: sf gherkin-mutator manifest block 2: the saved block goes into the feature file before the run
    Given the worktree file ".metrics/gherkin/features/x.feature" of task "A" is written with "# acceptance-mutation-manifest-begin\n# {old}\n# acceptance-mutation-manifest-end"
    And gherkin-mutator is a fake that writes the block "# acceptance-mutation-manifest-begin\n# {new}\n# acceptance-mutation-manifest-end" and exits with status 0
    When I run ".swarmforge/bin/gherkin-mutator --runner-worker w --feature features/x.feature" in the worktree of task "A"
    Then the exit status is 0
    And the fake saw the feature file holding "# acceptance-mutation-manifest-begin\n# {old}\n# acceptance-mutation-manifest-end\n\nFeature: x"
    And the file "features/x.feature" of task "A" is as committed
    And the worktree file ".metrics/gherkin/features/x.feature" of task "A" holds "# acceptance-mutation-manifest-begin\n# {new}\n# acceptance-mutation-manifest-end"

  # sf gherkin-mutator manifest block 3
  Scenario: sf gherkin-mutator manifest block 3: a run that fails still moves its block and the wrapper exits with its status
    Given gherkin-mutator is a fake that writes the block "# acceptance-mutation-manifest-begin\n# {}\n# acceptance-mutation-manifest-end" and exits with status 3
    When I run ".swarmforge/bin/gherkin-mutator --runner-worker w --feature features/x.feature" in the worktree of task "A"
    Then the exit status is 3
    And the file "features/x.feature" of task "A" is as committed
    And the worktree file ".metrics/gherkin/features/x.feature" of task "A" holds "# acceptance-mutation-manifest-begin\n# {}\n# acceptance-mutation-manifest-end"
