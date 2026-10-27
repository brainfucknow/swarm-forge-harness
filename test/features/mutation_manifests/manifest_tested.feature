Feature: sf manifest tested

  sf manifest tested <id> prints one Lore "Tested:" trailer line for each
  manifest file that sf manifest save would save now, in the order of the
  file paths. The hardener puts these lines on its last commit.

  A file under .metrics/mutate/ is a clj-mutate manifest: an EDN map whose
  :source names the source file and whose :forms each have :killed, :survived
  and :uncovered counts. Its line is
  "Tested: clj-mutate <source> | <killed> killed, <survived> survived", where
  each count is the sum over :forms. When the sum of :uncovered is not 0, the
  line ends with ", <uncovered> uncovered".

  A file .metrics/gherkin/<feature path> holds a gherkin-mutator block (see
  the feature sf gherkin-mutator manifest block). Its line is
  "Tested: gherkin-mutator <feature path> | <killed> killed, <survived> survived",
  where the counts are the sums of Killed and Survived over the scenarios of
  the manifest JSON.

  Other files under .metrics/, and files that cannot be read as their kind of
  manifest, give no line. When no file would be saved, the command prints
  nothing. The exit status is 0.

  Background:
    Given a project with swarmforge initialized and .metrics/ ignored
    And task "A" was started

  # sf manifest tested 1
  Scenario Outline: sf manifest tested 1: a clj-mutate manifest gives the sums over its forms
    Given the clj-mutate manifest ".metrics/mutate/sf/x.edn" of task "A" names source "src/sf/task.clj" with forms killed "<killed>", survived "<survived>" and uncovered "<uncovered>"
    When I run "sf manifest tested A"
    Then the exit status is 0
    And the output is "Tested: clj-mutate src/sf/task.clj | <counts>"

    Examples:
      | killed | survived | uncovered | counts                            |
      | 40,1   | 0,0      | 0,0       | 41 killed, 0 survived             |
      | 3,0    | 1,2      | 0,2       | 3 killed, 3 survived, 2 uncovered |

  # sf manifest tested 2
  Scenario Outline: sf manifest tested 2: a gherkin-mutator manifest gives the sums over its scenarios
    Given the gherkin-mutator manifest of feature "test/features/x.feature" in task "A" has scenarios killed "<killed>" and survived "<survived>"
    When I run "sf manifest tested A"
    Then the exit status is 0
    And the output is "Tested: gherkin-mutator test/features/x.feature | <counts>"

    Examples:
      | killed | survived | counts                |
      | 9,3    | 0,0      | 12 killed, 0 survived |
      | 2      | 1        | 2 killed, 1 survived  |

  # sf manifest tested 3
  Scenario: sf manifest tested 3: only files that save would save give lines, in path order
    Given the clj-mutate manifest ".metrics/mutate/b.edn" of task "A" names source "src/b.clj" with forms killed "1", survived "0" and uncovered "0"
    And the manifests of task "A" were saved at a new commit
    And the clj-mutate manifest ".metrics/mutate/c.edn" of task "A" names source "src/c.clj" with forms killed "2", survived "0" and uncovered "0"
    And the gherkin-mutator manifest of feature "features/a.feature" in task "A" has scenarios killed "4" and survived "0"
    And the worktree file ".metrics/other.txt" of task "A" is written with "x"
    And the worktree file ".metrics/mutate/bad.edn" of task "A" is written with "{:source"
    When I run "sf manifest tested A"
    Then the exit status is 0
    And the output is "Tested: gherkin-mutator features/a.feature | 4 killed, 0 survived\nTested: clj-mutate src/c.clj | 2 killed, 0 survived"

  # sf manifest tested 4
  Scenario: sf manifest tested 4: no manifest to save prints nothing
    When I run "sf manifest tested A"
    Then the exit status is 0
    And the output is empty
