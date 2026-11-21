Feature: sf finish default branch

  sf finish <id> without --into merges the task branch into the current
  branch of the main checkout, except when that branch is the repository's
  default branch: a done task must not land on the default branch without a
  pull request and human review, so sf finish refuses. The default branch is
  the branch that the remote's HEAD names (git symbolic-ref
  refs/remotes/<remote>/HEAD for the first remote of git remote, when that
  ref exists), else the init.defaultBranch configuration when set, else
  master, the built-in default of this project's git. The refusal exits 1
  with one stderr line naming the default branch, the --into alternative and
  the pull-request alternative, and changes nothing: no verification runs,
  no merge, no finished record, no checkout of another branch; the task
  branch and the worktree stay as they were. An explicit --into BRANCH always
  chooses the target, also when BRANCH is the default branch, but a blank
  --into, an empty string as from a quoted empty variable, counts as no
  --into and refuses the same way. The refusal
  applies only when sf finish would merge the task branch into the current
  branch, that is when HEAD has neither the task's finished record nor its
  tip; when HEAD already contains the tip, sf finish <id> without --into
  still verifies and records on the default branch and cleans up. The
  earlier refusals (unknown task, unclosed task, a merge in progress, a stage
  other than done, uncommitted worktree changes) keep precedence over the
  default-branch refusal. In these fixtures the scratch repository has no
  remote and the test HOME sets init.defaultBranch = main, so the default
  branch is main.

  Background:
    Given a project with swarmforge initialized
    And task "A" titled "Add login" was started on route "coder"
    And role "coder" committed the file "src/a.clj" with "Code" in the worktree of task "A" as C1
    And I ran "sf handoff $A"

  # sf finish default branch 1
  Scenario: sf finish default branch 1: a bare finish on the default branch refuses with the pull-request alternative
    Given the HEAD of the main checkout is named M1
    And the tip of the branch of task "A" is named T1
    When I run "sf finish $A"
    Then the exit status is 1
    And the output is ""
    And the error output has the line "sf: refusing to merge swarm/$A into main without --into: main is the default branch of this repository; run sf finish $A --into BRANCH to choose the target, or push swarm/$A and open a pull request into main to review and merge it there"
    And the HEAD of the main checkout is M1
    And the main checkout has no file "src/a.clj" and no uncommitted change
    And the branch of task "A" did not move
    And the branch "swarm/$A" exists
    And the worktree of task "A" exists
    And the worktree of task "A" has no uncommitted change

  # sf finish default branch 2
  Scenario: sf finish default branch 2: an explicit --into into the default branch still merges
    Given the tip of the branch of task "A" is named T1
    When I run "sf finish $A --into main"
    Then the exit status is 0
    And the output is "VERIFY: none configured\nFINISHED: $A merged into main"
    And the HEAD of the main checkout is a merge whose second parent is T1
    And the main checkout has the file "src/a.clj" and no uncommitted change
    And the branch "swarm/$A" does not exist
    And the worktree of task "A" does not exist

  # sf finish default branch 3
  Scenario: sf finish default branch 3: a bare finish on a non-default branch merges into the current branch
    Given I ran "git checkout -q -b release"
    And the tip of the branch of task "A" is named T1
    When I run "sf finish $A"
    Then the exit status is 0
    And the output is "VERIFY: none configured\nFINISHED: $A merged into release"
    And the HEAD of the main checkout is a merge whose second parent is T1
    And the branch "swarm/$A" does not exist
    And the worktree of task "A" does not exist

  # sf finish default branch 4
  Scenario: sf finish default branch 4: uncommitted worktree changes still refuse before the default-branch refusal
    Given the file "notes.txt" in the worktree of task "A" is written with "draft"
    And the HEAD of the main checkout is named M1
    When I run "sf finish $A"
    Then the exit status is 1
    And the error output has the line "sf: uncommitted changes in ROOT/.worktrees/$A; commit first"
    And the file "notes.txt" in the worktree of task "A" still holds "draft"
    And the HEAD of the main checkout is M1
    And the branch of task "A" did not move

  # sf finish default branch 5
  Scenario: sf finish default branch 5: a host merge on the default branch is still recorded by a bare finish
    Given I ran "git merge -q --no-ff --no-edit swarm/$A"
    And the tip of the branch of task "A" is named T1
    And the HEAD of the main checkout is named M1
    When I run "sf finish $A"
    Then the exit status is 0
    And the output is "VERIFY: none configured\nFINISHED: $A merged into main"
    And the HEAD of the main checkout has the first parent M1 and the tree of M1
    And the HEAD of the main checkout is a merge whose second parent is T1
    And the branch "swarm/$A" does not exist
    And the worktree of task "A" does not exist

  # sf finish default branch 6
  Scenario: sf finish default branch 6: the remote's HEAD names the default branch over init.defaultBranch
    Given I ran "git branch trunk"
    And I ran "git update-ref refs/remotes/origin/trunk HEAD"
    And I ran "git symbolic-ref refs/remotes/origin/HEAD refs/remotes/origin/trunk"
    And I ran "git remote add origin ."
    And the tip of the branch of task "A" is named T1
    When I run "sf finish $A"
    Then the exit status is 0
    And the output is "VERIFY: none configured\nFINISHED: $A merged into main"
    And the HEAD of the main checkout is a merge whose second parent is T1
    And the branch "swarm/$A" does not exist
    And the worktree of task "A" does not exist

  # sf finish default branch 7
  Scenario: sf finish default branch 7: init.defaultBranch names the default branch when there is no remote
    Given I ran "git config init.defaultBranch trunk"
    And I ran "git branch trunk"
    And the tip of the branch of task "A" is named T1
    When I run "sf finish $A"
    Then the exit status is 0
    And the output is "VERIFY: none configured\nFINISHED: $A merged into main"
    And the HEAD of the main checkout is a merge whose second parent is T1
    And the branch "swarm/$A" does not exist
    And the worktree of task "A" does not exist

  # sf finish default branch 8
  Scenario: sf finish default branch 8: a blank --into refuses like a bare finish
    Given the HEAD of the main checkout is named M1
    And the tip of the branch of task "A" is named T1
    When I run "sf finish $A --into" with the text ""
    Then the exit status is 1
    And the output is ""
    And the error output has the line "sf: refusing to merge swarm/$A into main without --into: main is the default branch of this repository; run sf finish $A --into BRANCH to choose the target, or push swarm/$A and open a pull request into main to review and merge it there"
    And the HEAD of the main checkout is M1
    And the main checkout has no file "src/a.clj" and no uncommitted change
    And the branch of task "A" did not move
    And the branch "swarm/$A" exists
    And the worktree of task "A" exists
    And the worktree of task "A" has no uncommitted change
