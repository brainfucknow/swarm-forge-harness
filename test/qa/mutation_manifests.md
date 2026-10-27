# QA procedure: mutation manifests in git notes (task ec13ecd)

Do this end to end through the command line only. Use real `git`, real
`git-bug`, `swarmforge/bin/sf` and the wrappers that `sf tool ensure` writes,
as an operator and the roles use them. Do not call `sf` internals.

Turn sections 1-7 into a throwaway script under `./tmp/` and do not commit it.
Section 8 is a reading check and is not scripted. In the steps, "write F = X"
means `mkdir -p` the directory, then `printf '%s\n' X > F`.

## Setup

1. `export PATH=$HOME/.local/bin:$PATH` (git-bug, bb).
2. Make a scratch directory `S` under `./tmp/`. In `S`:
   - `git init -b main`.
   - Copy in the `swarmforge/` of the task worktree.
   - Write a `.gitignore` with `.worktrees/`, `.swarmforge/`, `tmp/` and
     `.metrics/`.
   - Commit, then run `swarmforge/bin/sf init`.
   - Expect exit 0.
3. `sf new "First" -m x --route specifier`. Record the id as `A`.
4. `sf start A`. Expect the line `MANIFESTS: 0 loaded from refs/notes/mutate`
   before the `---` line. Record `DIR:` as `DA`.

## 1. Save

1. In `DA`, run `sf manifest save A`. Expect `MANIFESTS: nothing to save` and
   `git notes --ref=mutate list` to print nothing.
2. Write these two files:
   - `DA/.metrics/mutate/sf/task.edn` =
     `{:version 2 :source "src/sf/task.clj" :forms [{:killed 40 :survived 0 :uncovered 0} {:killed 1 :survived 0 :uncovered 0}]}`
   - `DA/.metrics/gherkin/features/x.feature` = the three lines
     `# acceptance-mutation-manifest-begin`,
     `# {"feature_path":"features/x.feature","scenarios":[{"result":{"Total":12,"Killed":12,"Survived":0,"Errors":0}}]}`,
     `# acceptance-mutation-manifest-end`.
3. Check that `git -C DA status --porcelain` prints nothing: `.metrics/` is
   ignored.
4. Run `sf manifest tested A`. Expect exactly these two lines, in this order:
   - `Tested: gherkin-mutator features/x.feature | 12 killed, 0 survived`
   - `Tested: clj-mutate src/sf/task.clj | 41 killed, 0 survived`
5. Commit `git commit --allow-empty -m "Harden"` in `DA`, with the two
   lines from step 4 as trailers. Run `sf manifest save A` and expect
   `MANIFESTS: 2 saved to refs/notes/mutate on <short HEAD>`. Then check:
   - `git notes --ref=mutate list HEAD` (in `DA`) exits 0.
   - `git log -1 --format=%B` shows the `Tested:` trailers.
   - `git show --stat HEAD` lists no `.metrics/` file.
6. Run `sf manifest save A` again. Expect `MANIFESTS: nothing to save`.
   Run `sf manifest tested A`. Expect no output.
7. Change one form count in `task.edn` and run `sf manifest tested A`. Expect
   only the clj-mutate line, with the new sum.

## 2. Load in the same task

1. Delete `DA/.metrics`. Run `sf manifest load A`. Expect
   `MANIFESTS: 2 loaded from refs/notes/mutate`. Expect both files back, with
   the content they had when they were saved.
2. Write `task.edn` = `local`. Run `sf manifest load A`. Expect
   `MANIFESTS: 0 loaded …`, and `task.edn` still holds `local`. Restore the
   saved content (delete the file and load again).

## 3. Handoff and approval save; the audit gate does not

1. Write `DA/.metrics/mutate/extra.edn` = `e1`. Run `sf review A`, then
   `sf approve A`. Expect the `HANDOFF:` line as the only stdout line.
   Expect `git notes --ref=mutate show HEAD` (in `DA`) to now also cover
   `extra.edn`: delete the file, then `sf manifest load A` brings it back.
2. `sf new "Audited" -m x --route coder,qa`, giving `D`. In the main
   checkout, add the field `audit` to the `coder` line of
   `swarmforge/roles.conf`. Then:
   - `sf start D`, write a manifest file in its worktree, and make an empty
     commit.
   - Run `sf handoff D`. Expect exit 3 and `AUDIT_REQUIRED`, and no note on
     that HEAD.
   - Run `sf handoff D` again. Expect `HANDOFF: D coder -> qa`, and now a note
     on that HEAD.
   - Restore `roles.conf`.

## 4. A later task inherits

1. `sf finish A` (the task is at `stage:done` after the approval on route
   `specifier`).
2. `sf new "Second" -m x --route specifier`, giving `B`. Run `sf start B`.
   Expect `MANIFESTS: 3 loaded from refs/notes/mutate` before `---`. Expect
   the three files from task A in the worktree of `B`, with their saved
   content.
3. Make two tasks `P` and `Q` from `main`, and start both. In `P`, write
   `.metrics/mutate/p.edn` = `p1`, commit, and save. In `Q`, write
   `.metrics/mutate/q.edn` = `q1`, commit, and save. Finish both (review,
   approve, finish). Start a new task `R`. Expect `p.edn` and `q.edn` in its
   worktree.
4. Make a task `V`. Write `.metrics/mutate/u.edn` = `u1` in its worktree,
   commit, and save, but do not finish `V`. Start a new task `U`. Expect no
   `u.edn` in the worktree of `U`: notes on unmerged commits are not read.
5. In task `B`, overwrite `.metrics/mutate/sf/task.edn` with `v2`, commit,
   save, and finish. Start a new task. Expect `task.edn` to hold `v2`: the
   newer note wins.

## 5. Not ignored

1. Remove the `.metrics/` line from `.gitignore` in the main checkout and
   commit.
2. `sf new "Unignored" -m x --route specifier`, then `sf start` it. Expect exit
   0 and the line
   `MANIFESTS: .metrics/ is not ignored by git; nothing loaded`. Expect no
   `.metrics/` directory in its worktree. `sf manifest load` on it prints
   the same line.
3. Put the `.metrics/` line back and commit.

## 6. gherkin-mutator wrapper

1. In a task worktree `DW`, write `features/x.feature` = `Feature: x` and
   commit it. Run `sf tool ensure gherkin-mutator` (use the real APS source,
   or `SWARMFORGE_TOOL_SRC` with a fake whose `bb.edn` task
   `gherkin-mutator` does this: copies `--feature` to `tmp/seen.feature`;
   puts `# mutation-stamp: sha256=ab`, the begin line, `# {}` and the end line
   at the top; exits 1).
2. From `DW`, run
   `.swarmforge/bin/gherkin-mutator --runner-worker w --feature features/x.feature --level full --workers 9`.
   Expect:
   - The exit status of the run (1 with the fake).
   - `git status --porcelain` prints nothing: the feature is as committed.
   - `.metrics/gherkin/features/x.feature` holds the four block lines.
   - With the fake: `tmp/seen.feature` holds only `Feature: x`, and the fake
     got `--level hard` and `--workers 4`.
3. Change the fake to write `# {new}` instead of `# {}` and to exit 0. Run it
   again. Expect:
   - `tmp/seen.feature` starts with the four old block lines, then a blank
     line, then `Feature: x`.
   - The feature file is as committed again.
   - `.metrics/gherkin/features/x.feature` holds the new block.
4. With the real APS tools and a real feature and runner, if the environment
   has them: run the wrapper twice at `--level hard`. Expect the second run
   to skip the clean scenarios (the status line counts skipped scenarios).
   This shows that the moved block still makes the mutation differential.

## 7. install.sh

1. Make a fresh scratch repository with one commit. Run
   `install.sh <repo>` twice. Expect exit 0 and `grep -cxF .metrics/ .gitignore`
   = 1.

## 8. Reading check (manual, not scripted)

Read the changed documents once. Confirm each item by judgement, not by
string search:

- `engineering.md` says that mutation manifests are kept in
  `refs/notes/mutate`, never in tracked files. It also says that `sf start`
  loads them and `sf handoff` saves them, and that clj-mutate and
  gherkin-mutator run from the worktree root through the wrappers. It still
  forbids hand edits and full mutation runs.
- `hardener.md` tells the hardener to put the lines of
  `sf manifest tested <id>` on its last commit as trailers.
- `hardener.md` and `cleaner.md` keep manifest state across a split without
  committing it.
- `handoff-protocol.md` tells how to push and fetch `refs/notes/mutate`, and
  says what a squash merge does to them.
- The `.gitignore` of this repository ignores `.metrics/`, and `sf --help`
  lists the manifest commands.

Record the outcome as `Tested:` and `Not-tested:` trailers on the QA handoff
commit.
