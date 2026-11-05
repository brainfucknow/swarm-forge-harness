Feature: sf finish verify

  sf finish first checks the task worktree, when it exists. If git status
  --porcelain prints anything there (staged, unstaged or untracked files;
  ignored files do not count), sf finish exits 1 with "sf: uncommitted
  changes in <worktree>; commit first", where worktree is its absolute path,
  and changes nothing: no merge, no record, the target, the task branch and
  the worktree stay as they were. This holds for a first finish and for a
  rerun after a conflict.

  The verification commands are the lines of swarmforge/verify.conf in the
  checkout being verified, in file order, without blank lines and lines whose
  first non-blank character is "#". sf runs each one with bash -c from the
  root of that checkout, prints "VERIFY: passed: <command>" after each one
  that exits 0, and stops at the first one that does not. The commands'
  own stdout and stderr go to sf's stderr. When the file is missing or has no
  command, sf prints "VERIFY: none configured" and verification passes. A
  failed command makes sf finish exit 1 with "sf: verification failed on the
  merge of swarm/<id> into <target>: <command> exited <status>" and record
  nothing.

  On a first finish, sf builds the merge candidate in the task worktree
  before it touches the target: it detaches the worktree at the target and
  merges the task branch there without committing, with the target as the
  current side as in the real merge (when the branch already contains the
  target, the candidate has the tree of the branch tip), and verifies that
  checkout. Then it returns
  the worktree to the tip of the task branch with no uncommitted change,
  discarding what the commands created or changed there (ignored files may
  stay). Only when verification passed does sf merge into the target as
  before. A failure leaves the target, the task branch and the worktree as
  they were. When the candidate conflicts, sf skips verification, returns the
  worktree to the branch tip, and merges into the target as before, which
  stops on the conflicts (see sf finish resume).

  On a rerun after the operator committed a conflicting merge on the target,
  sf finish verifies the main checkout at that HEAD before it changes HEAD's
  message or removes anything. A failure leaves HEAD, the task branch
  and the worktree as they were.

  After the merge and verification, sf finish removes the worktree without
  forcing it, then deletes the task branch, and prints the VERIFY lines
  followed by "FINISHED: <id> merged into <target>". If the cleanup stops,
  the task stays recoverable: a rerun of sf finish completes it.

  Background:
    Given a project with swarmforge initialized
    And task "A" titled "Add login" was started on route "coder"

  # sf finish verify 1
  Scenario Outline: sf finish verify 1: a merge that fails verification is not finished
    Given the main checkout committed the file "a.txt" with "a"
    And the main checkout committed the file "swarmforge/verify.conf" with "<conf>"
    And role "coder" committed the file "b.txt" with "Add b" in the worktree of task "A" as C1
    And I ran "sf handoff $A"
    And the HEAD of the main checkout is named M1
    When I run "sf finish $A"
    Then the exit status is 1
    And the error output has the line "sf: verification failed on the merge of swarm/$A into main: <failing> exited <status>"
    And the output is "<passed>"
    And the output has no line containing "touch never"
    And the HEAD of the main checkout is M1
    And the main checkout has no file "b.txt" and no uncommitted change
    And the branch of task "A" did not move
    And the worktree of task "A" exists
    And the worktree of task "A" has no uncommitted change

    Examples:
      | conf                                   | failing                       | status | passed               |
      | test ! -e a.txt -o ! -e b.txt          | test ! -e a.txt -o ! -e b.txt | 1      |                      |
      | # checks\n\ntrue\nexit 3\ntouch never  | exit 3                        | 3      | VERIFY: passed: true |

  # sf finish verify 2
  Scenario: sf finish verify 2: a candidate that passes is merged and its leftovers are discarded
    Given the file "swarmforge/verify.conf" in the worktree of task "A" is written with "touch made-by-verify\ntest -e b.txt"
    And the file "b.txt" in the worktree of task "A" is written with "b"
    And I ran "git add swarmforge/verify.conf b.txt" in the worktree of task "A"
    And I ran "git commit -q -m Verify" in the worktree of task "A"
    And I ran "sf handoff $A"
    When I run "sf finish $A"
    Then the exit status is 0
    And the output is "VERIFY: passed: touch made-by-verify\nVERIFY: passed: test -e b.txt\nFINISHED: $A merged into main"
    And the main checkout has the file "b.txt" and no file "made-by-verify"
    And the branch "swarm/$A" does not exist
    And the worktree of task "A" does not exist

  # sf finish verify 3
  Scenario: sf finish verify 3: a verify.conf without commands finishes unverified
    Given the main checkout committed the file "swarmforge/verify.conf" with "# no commands yet\n"
    And I ran "sf handoff $A"
    When I run "sf finish $A"
    Then the exit status is 0
    And the output is "VERIFY: none configured\nFINISHED: $A merged into main"
    And the branch "swarm/$A" does not exist
    And the worktree of task "A" does not exist

  # sf finish verify 4
  Scenario: sf finish verify 4: staged and unstaged changes in the worktree refuse the finish
    Given I ran "sf handoff $A"
    And the worktree of task "A" has the staged new file "S" and the unstaged change "U"
    And the HEAD of the main checkout is named M1
    When I run "sf finish $A"
    Then the exit status is 1
    And the error output has the line "sf: uncommitted changes in ROOT/.worktrees/$A; commit first"
    And the worktree of task "A" still has the staged new file "S" and the unstaged change "U"
    And the HEAD of the main checkout is M1
    And the branch of task "A" did not move

  # sf finish verify 5
  Scenario: sf finish verify 5: an untracked file in the worktree refuses the finish
    Given I ran "sf handoff $A"
    And the file "notes.txt" in the worktree of task "A" is written with "draft"
    And the HEAD of the main checkout is named M1
    When I run "sf finish $A"
    Then the exit status is 1
    And the error output has the line "sf: uncommitted changes in ROOT/.worktrees/$A; commit first"
    And the file "notes.txt" in the worktree of task "A" still holds "draft"
    And the HEAD of the main checkout is M1
    And the branch of task "A" did not move

  # sf finish verify 6
  Scenario: sf finish verify 6: a committed conflict resolution that fails verification is not finished
    Given the main checkout committed the file "swarmforge/verify.conf" with "grep -qv resolved src/a.clj"
    And the worktree of task "A" and the branch "main" changed the files "src/a.clj" differently
    And I ran "sf handoff $A"
    And I ran "sf finish $A"
    And the operator resolved the conflicts and committed the merge with the message "Resolve conflict"
    And the HEAD of the main checkout is named M1
    When I run "sf finish $A"
    Then the exit status is 1
    And the error output has the line "sf: verification failed on the merge of swarm/$A into main: grep -qv resolved src/a.clj exited 1"
    And the HEAD of the main checkout is M1
    And the branch of task "A" did not move
    And the worktree of task "A" exists
