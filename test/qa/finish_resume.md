# QA procedure: sf finish resume after a merge conflict

For the task "Let sf finish resume after a merge conflict". Gherkin:
`test/features/task_store/finish_resume.feature`.

This procedure runs end to end through the command line only, with real
`git` and `swarmforge/bin/sf`, as the operator uses them. Do not call `sf`
internals or read its source. Check only exit statuses, stdout, stderr, git
state and files on disk.

Turn sections 1 to 5 into a throwaway script under `./tmp/` and do not
commit it. Record the result of each check in the QA handoff commit's Lore
trailers: `Tested:` for a check that passed, `Not-tested:` with the reason
for a check that could not run. A failing check is a defect to fix, not a
trailer.

## Setup

`WT` is the absolute path of the task worktree. Start with an empty
`$WT/tmp/qa/`.

1. Make `S=$WT/tmp/qa/repo`: `git init -q -b main "$S"`. Copy the task
   worktree's `swarmforge/` into it, write a `.gitignore` with `.worktrees/`,
   `.swarmforge/` and `tmp/`, write `notes.txt` with the line `base`, and
   commit everything.
2. Run every command below in `$S` (the main checkout) unless a step names a
   task worktree, with `swarmforge/bin/sf` as `sf`.
3. `sf init` exits 0.

A "conflicting task" below is made like this, with title `T`, file `F` and
target branch `B`:

- `id=$(sf new "T" -m "T" --route coder)`, then `sf start $id`.
- In `.worktrees/$id`: write `F` with the line `ours`, `git add F`,
  `git commit -m "Change F"`.
- `sf handoff $id -m done`. Expect exit 0 and `HANDOFF: $id coder -> done at …`.
  Note `tip=$(git rev-parse swarm/$id)`.
- In the main checkout, on branch `B` (create it from `main` with
  `git branch B main` first when it does not exist): write `F` with the line
  `theirs` and commit it, then `git checkout -q main`.

## 1. The conflict is reported

1. Make a conflicting task "Replay finish" with file `notes.txt` and target
   `release`. This replays the incident: `sf finish --into <branch>` on a
   conflict.
2. Run `sf finish $id --into release`.
   - Exit 1. Stdout is empty.
   - Stderr is the one line
     `sf: merge of swarm/$id into release stopped on conflicts: notes.txt; resolve them, commit, then run: sf finish $id`.
   - `git branch --show-current` prints `release`.
     `git rev-parse MERGE_HEAD` equals `$tip`.
     `git diff --name-only --diff-filter=U` prints `notes.txt`.
   - `swarm/$id` exists and `.worktrees/$id` is a directory.

## 2. A merge in progress is refused

1. Write `notes.txt` with the line `resolved` and `git add notes.txt`. Do not
   commit.
2. Run `sf finish $id`.
   - Exit 1. Stderr is `sf: a merge is in progress; commit the merge first`.
   - `git rev-parse MERGE_HEAD` still equals `$tip`; `git status` still
     reports the merge in progress; `swarm/$id` and `.worktrees/$id` still
     exist; `git log -1 --format=%H` is the same as before the step.

## 3. A merge committed with another message is completed

1. `git commit -q -m "Resolve conflict"`. Note `m1=$(git rev-parse HEAD)`,
   its parents `git rev-parse $m1^1 $m1^2` and its tree
   `git rev-parse $m1^{tree}`.
2. Run `sf finish $id`.
   - Exit 0. Stdout is `FINISHED: $id merged into release`.
   - `git branch --show-current` prints `release`.
   - HEAD has the same two parents, in order, and the same tree as `$m1`.
     `HEAD^2` is `$tip`. `notes.txt` holds `resolved`.
   - `git log -1 --format=%B` is exactly
     `sf finished: $id`, a blank line, then the trailers
     `SwarmForge-Task: $id`, `SwarmForge-Record: finished`,
     `SwarmForge-From: operator`, `SwarmForge-Stage: done`,
     `SwarmForge-Route: coder`, `SwarmForge-Needs: none`,
     `SwarmForge-Merged-Into: release`, one per line.
     `git log -1 --format='%(trailers:key=SwarmForge-Record,valueonly)'`
     prints `finished`.
   - `git branch --list "swarm/$id"` prints nothing. `.worktrees/$id` does
     not exist, and `git worktree list` does not list it.
   - `git status --porcelain` prints nothing.
3. Run `sf show $id`. Expect exit 0, `STATUS: finished`, and the records
   ending with the block `type: finished`, `from: operator`,
   `merged-into: release`.
4. Run `sf finish $id` again. Expect exit 1 and stderr
   `sf: task $id is finished`. HEAD did not move.
5. `git checkout -q main`.

## 4. A merge committed with git's prepared message is kept

1. Make a conflicting task "Prepared message" with file `other.txt` and
   target `main`.
2. Run `sf finish $id`. Expect exit 1 and the conflict line naming
   `other.txt` and `main`.
3. Write `other.txt` with the line `resolved`, `git add other.txt`, then
   `git commit -q --no-edit`. Note `m1=$(git rev-parse HEAD)`.
4. Run `sf finish $id`.
   - Exit 0. Stdout is `FINISHED: $id merged into main`.
   - `git rev-parse HEAD` equals `$m1`: the commit was not rewritten.
   - `swarm/$id` and `.worktrees/$id` are gone.
   - `sf show $id` shows `STATUS: finished` and the `type: finished` block
     with `merged-into: main`.

## 5. Unchanged paths

1. Two conflicting files: make a conflicting task "Two files" with file
   `a.txt` and target `main`, and in its worktree also change `b.txt` to
   `ours` (commit before the handoff), with `b.txt` committed as `theirs` on
   `main`. Run `sf finish $id`. Expect exit 1 and the conflict line ending
   `stopped on conflicts: a.txt b.txt; resolve them, commit, then run: sf finish $id`.
   Then run `git merge --abort`, so the checkout is clean again.
2. A clean finish is as before: make a task "Clean" with `sf new` and
   `sf start`, commit a new file `clean.txt` in its worktree, `sf handoff`,
   then `sf finish $id`. Expect exit 0, `FINISHED: $id merged into main`, a
   merge commit with the `sf finished: $id` message and trailers, and the
   branch and worktree gone.
3. A squash-merged task stays refused: make a task "Squash" with `sf new`
   and `sf start`, commit a file in its worktree, `sf handoff`, then
   `git merge --squash swarm/$id` and `git commit -q -m "Squash"`. Run
   `sf finish $id`. Expect exit 1 and stderr `sf: task $id is finished`.
   `swarm/$id` still exists.
