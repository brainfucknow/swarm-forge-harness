# QA procedure: CI manifest guard

Task fail-ci-when-mutation-7b7d47b. Gherkin:
`test/features/manifest_guard/`. Run this end to end from the command line:
`bash`, real `git`, and the task's `test/no_manifests.sh` as CI runs it. Do
not read the script to decide a result. Turn sections 1 and 2 into a
throwaway script under `./tmp/` and do not commit it. Section 3 is a reading
check and is not scripted.

`WT` is the absolute path of the task worktree, and `G` is
`bash $WT/test/no_manifests.sh`. Work under `$WT/tmp/qa/`, which starts
empty. "Write F = X" means `mkdir -p` the directory, then
`printf '%s\n' X > F`. For each run, record stdout, stderr and the exit
status separately.

Record each check in the QA handoff commit's Lore trailers: `Tested:` for a
check that passed, `Not-tested:` with the reason for one that could not run.
A failing check is a defect to fix, not a trailer.

## 1. A scratch repository

1. `S=$WT/tmp/qa/s`. `git init -b main S`, set `user.name`/`user.email`,
   write `S/.gitignore` = `.metrics/`, write `S/features/x.feature` =
   `Feature: x`, commit both. Run `G` in `S`. Expect exit 0 and no output
   on stdout or stderr.
2. Prepend the block to `S/features/x.feature`, so it starts with the lines
   `# mutation-stamp: sha256=ab`, `# acceptance-mutation-manifest-begin`,
   `# {}`, `# acceptance-mutation-manifest-end`, a blank line, then
   `Feature: x`. Do not stage it. Run `G`. Expect exit 0 and no output.
3. `git add features/x.feature`, no commit. Run `G`. Expect exit 1, empty
   stdout, and stderr exactly
   `mutation manifest block in a tracked feature file: features/x.feature`.
4. Commit it. Run `G` from `S/features`. Expect the same exit and stderr.
5. Write `S/.metrics/mutate/a.edn` = `x` (ignored, untracked). Run `G`.
   Expect the same as step 4: the ignored file is not reported.
6. `git add -f .metrics/mutate/a.edn` and commit. Write
   `S/tools/sf/.metrics/crap.edn` = `x`, `git add -f` and commit. Run `G`.
   Expect exit 1 and stderr exactly these three lines, in this order:
   - `mutation manifest block in a tracked feature file: features/x.feature`
   - `tracked file under .metrics/: .metrics/mutate/a.edn`
   - `tracked file under .metrics/: tools/sf/.metrics/crap.edn`
7. `git rm -r -q --cached .metrics tools`, restore `features/x.feature` to
   `Feature: x`, and commit. Write `S/docs/block.md` with the three manifest
   marker lines, and append to `S/features/x.feature` the line
   `  Given the block # acceptance-mutation-manifest-begin` (indented).
   Commit both. Run `G`. Expect exit 0 and no output.

## 2. This repository and its history

1. Run `G` in `$WT`. Expect exit 0 and no output: the tree of the task,
   including its own feature file and the specs that quote the markers, has
   no manifest.
2. `git -C $WT worktree add --detach $WT/tmp/qa/old 14c03ea` (the parent of
   20fbee7, "Drop the mutation manifests from the sf-js features and
   .metrics/"). Run `G` in `$WT/tmp/qa/old`. Expect exit 1, empty stdout,
   and 17 stderr lines: 11 that start
   `mutation manifest block in a tracked feature file: `, then 6 that start
   `tracked file under .metrics/: `. Each named path exists in that
   checkout, and each feature path's first line starts with
   `# mutation-stamp:` or `# acceptance-mutation-manifest-begin`.
3. Repeat step 2 at `b1555ae`, the commit that showed the problem. Expect
   exit 1 and 15 lines, all
   `mutation manifest block in a tracked feature file: swarmforge/tools/javascript/features/<name>.feature`.
4. `git -C $WT worktree remove --force` both checkouts.

## 3. CI wiring (read by hand)

1. In `$WT/.github/workflows/test.yml`, the job `sf` has a step that runs
   `bash test/no_manifests.sh`, after `actions/checkout` and before the
   Babashka install.
2. If the task branch has been pushed and CI ran on it, the step passed.
   Otherwise record `Not-tested:` for this item.
