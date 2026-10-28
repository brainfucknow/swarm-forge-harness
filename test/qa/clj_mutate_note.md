# QA procedure: Note the clj-mutate test command for the hardener, and fix a stale QA step

This task changes documents only, so it has no Gherkin. The items below
give the end state the operator approved. The coder makes the edits. QA
checks the end state: run the checks in "Scripted checks" from the task
worktree, then do the reading check. Record each result as a `Tested:` or
`Not-tested:` trailer on the QA handoff commit. Do not commit a script.

The task "Run crap4clj from the repository root" owns running crap4clj from
the repository root. This task
says nothing about crap4clj in the hardener prompt.

## End state

1. **`swarmforge/roles/hardener.md`**, section "Mutation Work", has a short
   note about running clj-mutate on Babashka code. In plain words it says:
   - Run clj-mutate from the worktree root. clj-mutate keeps its manifest
     under `.metrics/mutate/` of the directory it runs from, and `sf start`
     and `sf handoff` load and save only the worktree root's.
   - Do not use the default test command `bb --config swarmforge/tools/sf/bb.edn spec`
     (or any `bb --config <path>/bb.edn` test command) with clj-mutate.
     It lets every mutant survive, because bb resolves the worker's
     symlinked `bb.edn` back to the real checkout and so tests the
     unmutated sources.
   - Instead, pass a `--test-command` that puts the worker's own copy of
     each `:paths` entry of the module's `bb.edn` on the classpath (source
     and `spec` or `test` directories, and any shared ones such as
     `../core/src`), together with `--no-coverage`, which clj-mutate
     requires for a custom test command.

   The note gives no example command. Nothing else in the file changes.
2. **`test/qa/sf_tool.md`**, check QA-4, expects a CRAP report whose rows
   include the function `parse-cobertura` in `sfcs.cobertura`, instead of
   `crap-score`. Nothing else in the file changes.

No file outside these two changes, except this procedure and `tasks/`,
which `sf` writes.

## Scripted checks

Run these from the task worktree. `BASE` is the merge base with `main`
(`git merge-base HEAD main`).

1. Changed files: `git diff --name-only BASE HEAD` lists only
   `swarmforge/roles/hardener.md`, `test/qa/sf_tool.md`,
   `test/qa/clj_mutate_note.md` and files under `tasks/`.
2. `grep -n 'crap-score' test/qa/sf_tool.md` prints nothing.
   `git diff BASE HEAD -- test/qa/sf_tool.md` changes only lines inside
   QA-4, and QA-4 names `parse-cobertura` and `sfcs.cobertura`.
3. QA-4 still matches the tool. Follow the setup of `test/qa/sf_tool.md`
   (clone into `$WT/tmp/qa/project`, `sf tool ensure crap4clj`), then run
   QA-4 as written. It exits 0 and the CRAP report has a row with
   `parse-cobertura` and `sfcs.cobertura`. Delete `$WT/tmp/qa/` afterwards.
4. The suites still pass: `bash test/sf_test.sh` and
   `bash test/acceptance.sh` (after `swarmforge/bin/sf tool ensure gherkin-parser`).

## Reading check (manual, not scripted)

The prompt text is not checked by script: AGENTS.md keeps prompt wording out
of tests. Read the diff of `swarmforge/roles/hardener.md` and its section
"Mutation Work" once and confirm by judgement:

- The change is the note in "Mutation Work" and nothing else in the file.
- A hardener who has not seen the earlier task can tell from the note where
  to run clj-mutate, which test command to avoid and why, and what to pass
  instead.
- The note is a few sentences of plain wording, fits the bullet style of
  the section, and adds nothing beyond the three points in "End state".
