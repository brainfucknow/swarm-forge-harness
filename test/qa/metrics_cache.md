# QA procedure: mutation manifests on the metrics cache branch

Task keep-mutation-manifests-on-an-2a5978d. Gherkin:
`test/features/metrics_cache/`. Run this end to end through the command line
only. Use real `git`, `bb`, `install.sh`, `swarmforge/bin/sf` and the
wrappers that `sf tool ensure` writes, as the operator and the roles use
them. Do not call `sf` internals. Turn sections 1-5 into a throwaway script
under `./tmp/` and do not commit it. Section 6 is a reading check and is not
scripted. "Write F = X" means `mkdir -p` the directory, then
`printf '%s\n' X > F`.

## Setup

1. Make a scratch directory `S` under `./tmp/`. In `S/p`: `git init -b main`,
   commit one `README.md`, then run this task worktree's
   `install.sh S/p`. Expect exit 0.
2. Check `grep -cxF .metrics/ S/p/.gitignore` prints `1`. Run `install.sh S/p`
   again and check it still prints `1`. Commit what install wrote.
3. Check `git -C S/p rev-parse --verify swarmforge/metrics` fails: install
   makes no cache branch.

## 1. Save on handoff

1. In `S/p`: `sf new "First" -m x --route coder,qa`; note the id `A`.
   `sf start A`; note `DIR:` as `DA`. Expect the same lines as before this
   task (`TASK`, `TITLE`, `STAGE`, `ROUTE`, `BRANCH`, `DIR`, `---`, ...) and no
   line that mentions `metrics`.
2. Write `DA/.metrics/mutate/sf/task.edn` = `v1`,
   `DA/.metrics/gherkin/features/x.feature` = `g1` and
   `DA/.metrics/crap.edn` = `c1`. Check `git -C DA status --porcelain`
   prints nothing.
3. Commit an empty change in `DA` and run `sf handoff A` there. Expect the
   single line `HANDOFF: A coder -> qa at <sha>` and no other output.
4. Check the branch:
   - `git ls-tree -r --name-only swarmforge/metrics` prints exactly
     `gherkin/features/x.feature` and `mutate/sf/task.edn`.
   - `git show swarmforge/metrics:mutate/sf/task.edn` prints `v1`.
   - `git rev-list --count swarmforge/metrics` prints `1`, and
     `git merge-base main swarmforge/metrics` fails.
   - `git log -1 --format=%B swarmforge/metrics` is `sf metrics: A`, a blank
     line, and `SwarmForge-Task: A`.
   - `git diff-tree -r swarm/A` prints nothing (the record changes no file),
     and `git -C S/p status --porcelain` and `git -C DA status --porcelain`
     print nothing.
   - `git worktree list` lists only the main checkout and `DA`, and the main
     checkout is still on `main`.
5. Note the tip as `M1`. Run `sf handoff A` again with nothing changed
   (stage `qa`, to `done`). Expect `swarmforge/metrics` still at `M1`.

## 2. Changed, new and deleted manifests

1. `sf new "Second" -m x --route coder,qa`; note `B`. `sf start B`; note
   `DB`. Expect `DB/.metrics/mutate/sf/task.edn` = `v1` and
   `DB/.metrics/gherkin/features/x.feature` = `g1`, and no
   `DB/.metrics/crap.edn`.
2. Write `DB/.metrics/mutate/sf/task.edn` = `local`. Run `sf start B` again.
   Expect the file still holds `local`.
3. Delete `DB/.metrics/gherkin/features/x.feature`. Run `sf start B`. Expect
   it back, holding `g1`.
4. Delete it again, and write `DB/.metrics/mutate/other.edn` = `o1`. Run
   `sf handoff B`. Check:
   - `git diff-tree --no-commit-id -r --name-status swarmforge/metrics` prints
     `M mutate/sf/task.edn` and `A mutate/other.edn` (tab-separated), and
     nothing for `gherkin/`.
   - `git rev-parse swarmforge/metrics^` is `M1`.
   - `git show swarmforge/metrics:gherkin/features/x.feature` still prints
     `g1`.
5. In `DB`, run `sf ask B "Which?"`, write `DB/.metrics/mutate/late.edn` =
   `l1` and run `sf handoff B`. Expect exit 1 and the refusal on stderr. Check
   `swarmforge/metrics` did not move.

## 3. Approve saves, and a finished task passes its manifests on

1. `sf answer B ok`. `sf new "Third" -m x --route specifier,coder`; note `C`
   and start it as `DC`. Write `DC/.metrics/mutate/c.edn` = `c1`. Run
   `sf review C` and, in `S/p`, `sf approve C`. Expect
   `git show swarmforge/metrics:mutate/c.edn` prints `c1`.
2. Hand `C` to done (`sf handoff C --to done`) and run `sf finish C`. Expect
   `git ls-tree -r --name-only main` lists nothing under `.metrics/`, and
   `swarmforge/metrics` still has `mutate/c.edn`.
3. `sf new "Fourth" -m x --route coder`; start it as `DD`. Expect
   `DD/.metrics/mutate/c.edn` = `c1` and `DD/.metrics/mutate/late.edn` absent.

## 4. The gherkin-mutator block

Use the real tools: `sf tool ensure gherkin-mutator` and
`sf tool ensure gherkin-parser` in `S/p`. Wrappers land in
`S/p/.swarmforge/bin/`.

1. In `DD`, add `features/x.feature` with one scenario whose step has a
   quoted value, and a runner adapter as the tool needs. Commit them.
2. Run `S/p/.swarmforge/bin/gherkin-mutator` with `--feature
   features/x.feature` and the runner arguments, from `DD`. Whatever its
   result, check:
   - `git -C DD status --porcelain` prints nothing: the feature file is as
     committed and has no `# acceptance-mutation-manifest-begin` line.
   - `DD/.metrics/gherkin/features/x.feature` starts with
     `# acceptance-mutation-manifest-begin` (after an optional
     `# mutation-stamp:` line) and ends with
     `# acceptance-mutation-manifest-end`.
   - The wrapper's exit status is the tool's.
3. Run it again from `DD/features` with `--feature x.feature`. Check the
   same two things for the same paths, and that the progress output shows
   the second run reused the manifest (scenarios skipped as already tested).
4. Run `sf handoff D` and check
   `git show swarmforge/metrics:gherkin/features/x.feature` matches the file
   in `DD/.metrics/`.
5. Make a block appear in a run that fails: point the runner at a command
   that exits non-zero. Expect the wrapper's exit status to be non-zero, the
   feature file as committed, and the block in `.metrics/gherkin/`.

## 5. clj-mutate

1. `sf tool ensure clj-mutate`. In a fresh task worktree with a small
   Babashka namespace under `src/` and a spec, run the clj-mutate wrapper on
   the source file. Expect `.metrics/mutate/<path>.edn` in the worktree and a
   clean `git status --porcelain`.
2. Hand the task off, start a new task, and run clj-mutate on the same,
   unchanged file there. Expect it to report a differential run that skips
   the already-killed forms.

## 6. Reading check

Read the changed prompts and documents listed under "Not covered by these
features" in `test/features/metrics_cache/README.md`. Check each says what is
listed there, and that none still tells a role to commit `.metrics/` or a
feature's mutation block. Check `sf --help` lists no new command.
