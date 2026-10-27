Feature: sf manifest save

  sf manifest save <id> writes a note in refs/notes/mutate on the HEAD commit
  of the task worktree. The note holds each manifest file whose content is not
  the same as the notes' copy of its path (see sf manifest load), and each
  manifest file whose path has no notes' copy. When HEAD already has a note,
  that note keeps its other paths. A file that was removed from .metrics/
  stays in the notes.

  The command prints "MANIFESTS: <n> saved to refs/notes/mutate on <commit>",
  where <n> counts the files it saved and <commit> is the abbreviated sha of
  HEAD. When no file must be saved, it writes no note and prints
  "MANIFESTS: nothing to save". The exit status is 0 in both cases.

  sf handoff saves in the same way before it records the handoff, and prints
  only its HANDOFF: line. sf approve also saves, because it hands off. When sf
  handoff records nothing (for example AUDIT_REQUIRED), it writes no note.

  Background:
    Given a project with swarmforge initialized and .metrics/ ignored

  # sf manifest save 1
  Scenario: sf manifest save 1: the manifest files go into a note on HEAD
    Given task "A" was started
    And the worktree of task "A" has 3 manifest files
    When I run "sf manifest save A"
    Then the exit status is 0
    And the output is "MANIFESTS: 3 saved to refs/notes/mutate on HEAD"
    And the HEAD commit of task "A" has a note in refs/notes/mutate

  # sf manifest save 2
  Scenario: sf manifest save 2: no manifest files write no note
    Given task "A" was started
    When I run "sf manifest save A"
    Then the exit status is 0
    And the output is "MANIFESTS: nothing to save"
    And the HEAD commit of task "A" has no note in refs/notes/mutate

  # sf manifest save 3
  Scenario: sf manifest save 3: only files that are not the same as the notes' copy are saved
    Given task "A" was started
    And the worktree file ".metrics/mutate/a.edn" of task "A" is written with "v1"
    And the worktree file ".metrics/mutate/b.edn" of task "A" is written with "w1"
    And the manifests of task "A" were saved at a new commit
    And a commit was made in the worktree of task "A"
    And the worktree file ".metrics/mutate/b.edn" of task "A" is written with "w2"
    When I run "sf manifest save A"
    Then the exit status is 0
    And the output is "MANIFESTS: 1 saved to refs/notes/mutate on HEAD"

  # sf manifest save 4
  Scenario: sf manifest save 4: a second save on the same commit keeps the paths of the first
    Given task "A" was started
    And the worktree file ".metrics/mutate/a.edn" of task "A" is written with "v1"
    And I ran "sf manifest save A"
    And the worktree file ".metrics/mutate/b.edn" of task "A" is written with "w1"
    And I ran "sf manifest save A"
    And the worktree file ".metrics/mutate/a.edn" of task "A" was deleted
    And the worktree file ".metrics/mutate/b.edn" of task "A" was deleted
    When I run "sf manifest load A"
    Then the exit status is 0
    And the output is "MANIFESTS: 2 loaded from refs/notes/mutate"
    And the worktree file ".metrics/mutate/a.edn" of task "A" holds "v1"
    And the worktree file ".metrics/mutate/b.edn" of task "A" holds "w1"

  # sf manifest save 5
  Scenario: sf manifest save 5: sf handoff saves the manifests on the commit it records
    Given task "A" on route "coder,qa" was started
    And the worktree file ".metrics/mutate/a.edn" of task "A" is written with "v1"
    And a commit was made in the worktree of task "A"
    When I run "sf handoff A"
    Then the exit status is 0
    And the output is "HANDOFF: A coder -> qa at HEAD"
    And the HEAD commit of task "A" has a note in refs/notes/mutate
    And the notes' copy of ".metrics/mutate/a.edn" for task "A" is "v1"

  # sf manifest save 6
  Scenario: sf manifest save 6: sf handoff without manifest changes writes no note
    Given task "A" on route "coder,qa" was started
    And a commit was made in the worktree of task "A"
    When I run "sf handoff A"
    Then the exit status is 0
    And the HEAD commit of task "A" has no note in refs/notes/mutate

  # sf manifest save 7
  Scenario: sf manifest save 7: sf approve saves the manifests as it hands off
    Given task "A" on route "specifier,coder" was started
    And the worktree file ".metrics/mutate/a.edn" of task "A" is written with "v1"
    And a commit was made in the worktree of task "A"
    And I ran "sf review A"
    When I run "sf approve A"
    Then the exit status is 0
    And the HEAD commit of task "A" has a note in refs/notes/mutate

  # sf manifest save 8
  Scenario: sf manifest save 8: a handoff that the audit gate refuses writes no note
    Given role "coder" has the audit flag in roles.conf
    And task "A" on route "coder,qa" was started
    And the worktree file ".metrics/mutate/a.edn" of task "A" is written with "v1"
    And a commit was made in the worktree of task "A"
    When I run "sf handoff A"
    Then the exit status is 3
    And the HEAD commit of task "A" has no note in refs/notes/mutate

  # sf manifest save 9
  Scenario: sf manifest save 9: files that are the same as the notes' copy are not saved
    Given task "A" was started
    And the worktree file ".metrics/mutate/a.edn" of task "A" is written with "v1"
    And the worktree file ".metrics/mutate/b.edn" of task "A" is written with "w1"
    And the manifests of task "A" were saved at a new commit
    And a commit was made in the worktree of task "A"
    When I run "sf manifest save A"
    Then the exit status is 0
    And the output is "MANIFESTS: nothing to save"
