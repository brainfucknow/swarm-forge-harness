Feature: sf gherkin-mutator manifest block

  gherkin-mutator keeps its mutation stamp and scenario manifest as a block of
  comment lines at the top of the feature file: an optional
  "# mutation-stamp: ..." line, then the lines from
  "# acceptance-mutation-manifest-begin" to
  "# acceptance-mutation-manifest-end", then a blank line. The
  gherkin-mutator wrapper that sf tool ensure writes keeps that block out of
  the feature file between runs, in .metrics/gherkin/<path> at the root of the
  worktree, where <path> is the path of the --feature file from that root.
  The wrapper may run in any directory of the worktree. Without --feature it
  only runs the tool.

  Before the run: when .metrics/gherkin/<path> exists and the feature file
  has no block, the wrapper puts its lines and a blank line at the top of
  the feature file.

  After the run, whatever its exit status, when the feature file has a block:
  the wrapper writes the block lines to .metrics/gherkin/<path> in place of
  what was there, then removes them and the blank lines that then come first
  from the feature file. The wrapper exits with the exit status of the run.
  Its argument rewriting (--level, --workers) does not change.

  Background:
    Given a project with swarmforge initialized and .metrics/ ignored
    And task "A" titled "Add login" was started on route "hardener"
    And the worktree of task "A" has the committed file "features/x.feature" that holds "Feature: x"

  # sf gherkin-mutator manifest block 1
  Scenario Outline: sf gherkin-mutator manifest block 1: the block of a run moves to .metrics/gherkin/
    Given gherkin-mutator is a fake that writes the block "<block>" and exits with status 0
    When I run the gherkin-mutator wrapper with "--feature features/x.feature" in the worktree of task "A"
    Then the exit status is 0
    And the fake saw the feature file holding "Feature: x"
    And the file "features/x.feature" in the worktree of task "A" is as committed
    And the file ".metrics/gherkin/features/x.feature" in the worktree of task "A" holds "<block>"

    Examples:
      | block                                                                                                       |
      | # mutation-stamp: sha256=ab\n# acceptance-mutation-manifest-begin\n# {}\n# acceptance-mutation-manifest-end |
      | # acceptance-mutation-manifest-begin\n# {}\n# acceptance-mutation-manifest-end                              |

  # sf gherkin-mutator manifest block 2
  Scenario: sf gherkin-mutator manifest block 2: the cached block goes into the feature file for the run
    Given the file ".metrics/gherkin/features/x.feature" in the worktree of task "A" is written with "# acceptance-mutation-manifest-begin\n# {old}\n# acceptance-mutation-manifest-end"
    And gherkin-mutator is a fake that writes the block "# acceptance-mutation-manifest-begin\n# {new}\n# acceptance-mutation-manifest-end" and exits with status 0
    When I run the gherkin-mutator wrapper with "--feature features/x.feature" in the worktree of task "A"
    Then the exit status is 0
    And the fake saw the feature file holding "# acceptance-mutation-manifest-begin\n# {old}\n# acceptance-mutation-manifest-end\n\nFeature: x"
    And the file "features/x.feature" in the worktree of task "A" is as committed
    And the file ".metrics/gherkin/features/x.feature" in the worktree of task "A" holds "# acceptance-mutation-manifest-begin\n# {new}\n# acceptance-mutation-manifest-end"

  # sf gherkin-mutator manifest block 3
  Scenario: sf gherkin-mutator manifest block 3: a failed run still moves its block and the wrapper exits with its status
    Given gherkin-mutator is a fake that writes the block "# acceptance-mutation-manifest-begin\n# {}\n# acceptance-mutation-manifest-end" and exits with status 3
    When I run the gherkin-mutator wrapper with "--feature features/x.feature" in the worktree of task "A"
    Then the exit status is 3
    And the file "features/x.feature" in the worktree of task "A" is as committed
    And the file ".metrics/gherkin/features/x.feature" in the worktree of task "A" holds "# acceptance-mutation-manifest-begin\n# {}\n# acceptance-mutation-manifest-end"

  # sf gherkin-mutator manifest block 4
  Scenario: sf gherkin-mutator manifest block 4: the cache path is from the worktree root when the wrapper runs in a subdirectory
    Given gherkin-mutator is a fake that writes the block "# acceptance-mutation-manifest-begin\n# {}\n# acceptance-mutation-manifest-end" and exits with status 0
    When I run the gherkin-mutator wrapper with "--feature x.feature" in the directory "features" of the worktree of task "A"
    Then the exit status is 0
    And the file "features/x.feature" in the worktree of task "A" is as committed
    And the file ".metrics/gherkin/features/x.feature" in the worktree of task "A" holds "# acceptance-mutation-manifest-begin\n# {}\n# acceptance-mutation-manifest-end"
