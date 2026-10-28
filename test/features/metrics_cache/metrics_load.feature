Feature: sf metrics cache load

  Mutation manifests are a cache kept on the branch swarmforge/metrics, never
  in the tree of a task or of main. The tree of that branch holds each
  manifest at its path under .metrics/: clj-mutate manifests under mutate/
  and gherkin-mutator blocks under gherkin/.

  sf start, after it finds or makes the task worktree, copies each file under
  mutate/ and gherkin/ of the tip of swarmforge/metrics to .metrics/<path> in
  the task worktree. A file the worktree already has at that path stays as it
  is. Other files of the branch are not copied. Without the branch, nothing is
  copied. The output of sf start does not change.

  Background:
    Given a project with swarmforge initialized and .metrics/ ignored

  # sf metrics cache load 1
  Scenario Outline: sf metrics cache load 1: sf start copies the cached manifests into the task worktree
    Given the metrics branch holds the file "<dir>/x.edn" with "v1"
    And task "A" titled "Add login" was created on route "coder"
    When I run "sf start $A"
    Then the exit status is 0
    And the output has no line containing "metrics"
    And the file ".metrics/<dir>/x.edn" in the worktree of task "A" holds "v1"
    And the worktree of task "A" has no uncommitted change

    Examples:
      | dir     |
      | mutate  |
      | gherkin |

  # sf metrics cache load 2
  Scenario: sf metrics cache load 2: a file the worktree has stays and a deleted file comes back
    Given the metrics branch holds the file "mutate/a.edn" with "cached a"
    And the metrics branch holds the file "mutate/b.edn" with "cached b"
    And task "A" titled "Add login" was started on route "coder"
    And the file ".metrics/mutate/a.edn" in the worktree of task "A" is written with "local a"
    And the file ".metrics/mutate/b.edn" in the worktree of task "A" was deleted
    When I run "sf start $A"
    Then the exit status is 0
    And the file ".metrics/mutate/a.edn" in the worktree of task "A" holds "local a"
    And the file ".metrics/mutate/b.edn" in the worktree of task "A" holds "cached b"

  # sf metrics cache load 3
  Scenario: sf metrics cache load 3: files outside mutate/ and gherkin/ are not copied
    Given the metrics branch holds the file "crap.edn" with "v1"
    And the metrics branch holds the file "other/x.edn" with "v1"
    And task "A" titled "Add login" was created on route "coder"
    When I run "sf start $A"
    Then the exit status is 0
    And the worktree of task "A" has no file ".metrics/crap.edn"
    And the worktree of task "A" has no file ".metrics/other/x.edn"

  # sf metrics cache load 4
  Scenario: sf metrics cache load 4: without the metrics branch sf start copies nothing
    Given task "A" titled "Add login" was created on route "coder"
    When I run "sf start $A"
    Then the exit status is 0
    And the worktree of task "A" has no file ".metrics"
    And the branch "swarmforge/metrics" does not exist

  # sf metrics cache load 5
  Scenario: sf metrics cache load 5: a later task gets the manifests a finished task saved
    Given task "A" titled "Add login" was started on route "coder"
    And the file ".metrics/mutate/sf/task.edn" in the worktree of task "A" is written with "v1"
    And task "A" was finished
    And task "B" titled "Add logout" was created on route "coder"
    When I run "sf start $B"
    Then the exit status is 0
    And the file ".metrics/mutate/sf/task.edn" in the worktree of task "B" holds "v1"
