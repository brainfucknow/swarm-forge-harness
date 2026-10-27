Feature: sf manifest load

  Mutation manifests are kept in the git notes ref refs/notes/mutate, not in
  tracked files. A manifest file is any file under .metrics/ at the root of a
  task worktree. Its path is relative to that root.

  The notes' copy of a path is read from the notes on the commits that HEAD of
  the task worktree reaches, in the order that git rev-list HEAD prints them.
  The first note that holds the path gives its content. Thus the note of a
  commit wins over the notes of its ancestors. Notes on commits that HEAD does
  not reach are not read.

  sf manifest load <id> writes the notes' copy of each path into the task
  worktree. A file that is already in the worktree stays as it is and is not
  counted. The command prints "MANIFESTS: <n> loaded from refs/notes/mutate",
  where <n> counts the files it wrote. sf start loads in the same way after it
  finds or makes the worktree, and prints the same line before the "---" line.
  If git does not ignore .metrics/ in the task worktree, nothing is loaded
  and the line is "MANIFESTS: .metrics/ is not ignored by git; nothing loaded".
  The exit status is 0 in both cases.

  sf manifest load, save and tested need the task worktree.

  Background:
    Given a project with swarmforge initialized and .metrics/ ignored

  # sf manifest load 1
  Scenario Outline: sf manifest load 1: a later task inherits the manifests of a finished task
    Given task "A" finished with "<path>" saved as "<content>"
    And task "B" was created
    When I run "sf start B"
    Then the exit status is 0
    And the output has the line "MANIFESTS: 1 loaded from refs/notes/mutate" before the line "---"
    And the worktree file "<path>" of task "B" holds "<content>"

    Examples:
      | path                                         | content |
      | .metrics/mutate/sf/task.edn                  | v1      |
      | .metrics/gherkin/test/features/login.feature | w1      |

  # sf manifest load 2
  Scenario: sf manifest load 2: the note of a later commit wins and a deleted file comes back
    Given task "A" was started
    And the worktree file ".metrics/mutate/a.edn" of task "A" is written with "v1"
    And the manifests of task "A" were saved at a new commit
    And the worktree file ".metrics/mutate/a.edn" of task "A" is written with "v2"
    And the manifests of task "A" were saved at a new commit
    And the worktree file ".metrics/mutate/a.edn" of task "A" was deleted
    When I run "sf manifest load A"
    Then the exit status is 0
    And the output is "MANIFESTS: 1 loaded from refs/notes/mutate"
    And the worktree file ".metrics/mutate/a.edn" of task "A" holds "v2"

  # sf manifest load 3
  Scenario: sf manifest load 3: the manifests of two merged tasks are both loaded
    Given task "A" was started
    And task "B" was started
    And the worktree file ".metrics/mutate/a.edn" of task "A" is written with "v1"
    And the manifests of task "A" were saved at a new commit
    And the worktree file ".metrics/mutate/b.edn" of task "B" is written with "w1"
    And the manifests of task "B" were saved at a new commit
    And task "A" was finished
    And task "B" was finished
    And task "C" was created
    When I run "sf start C"
    Then the exit status is 0
    And the output has the line "MANIFESTS: 2 loaded from refs/notes/mutate" before the line "---"
    And the worktree file ".metrics/mutate/a.edn" of task "C" holds "v1"
    And the worktree file ".metrics/mutate/b.edn" of task "C" holds "w1"

  # sf manifest load 4
  Scenario: sf manifest load 4: notes on commits that HEAD does not reach are not read
    Given task "A" was started
    And the worktree file ".metrics/mutate/a.edn" of task "A" is written with "v1"
    And the manifests of task "A" were saved at a new commit
    And task "B" was created
    When I run "sf start B"
    Then the exit status is 0
    And the output has the line "MANIFESTS: 0 loaded from refs/notes/mutate" before the line "---"
    And the worktree file ".metrics/mutate/a.edn" of task "B" does not exist

  # sf manifest load 5
  Scenario: sf manifest load 5: a file already in the worktree stays as it is
    Given task "A" finished with ".metrics/mutate/a.edn" saved as "v1"
    And task "B" was started
    And the worktree file ".metrics/mutate/a.edn" of task "B" is written with "local"
    When I run "sf manifest load B"
    Then the exit status is 0
    And the output is "MANIFESTS: 0 loaded from refs/notes/mutate"
    And the worktree file ".metrics/mutate/a.edn" of task "B" holds "local"

  # sf manifest load 6
  Scenario Outline: sf manifest load 6: nothing is loaded when git does not ignore .metrics/
    Given task "A" finished with ".metrics/mutate/a.edn" saved as "v1"
    And the main checkout committed a .gitignore without .metrics/
    And task "B" was started
    When I run "sf <command> B"
    Then the exit status is 0
    And the output has the line "MANIFESTS: .metrics/ is not ignored by git; nothing loaded"
    And the worktree file ".metrics/mutate/a.edn" of task "B" does not exist

    Examples:
      | command       |
      | start         |
      | manifest load |

  # sf manifest load 7
  Scenario Outline: sf manifest load 7: the manifest commands need the task worktree
    Given task "A" was created
    When I run "sf manifest <subcommand> A"
    Then the exit status is 1
    And the error output has the line "sf: task worktree missing; run: sf start A"

    Examples:
      | subcommand |
      | load       |
      | save       |
      | tested     |
