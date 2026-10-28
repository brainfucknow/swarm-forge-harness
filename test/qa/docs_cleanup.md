# QA procedure: Clean up the docs in this repo

This task changes documents only, so it has no Gherkin. The items below
give the end state the operator approved, one per item of the scope
question. The coder makes the edits. QA checks the end state: run the
checks in "Scripted checks" from the task worktree, then do the reading
check. Record each result as a `Tested:` or `Not-tested:` trailer on the QA
handoff commit. Do not commit a script.

The task "Keep mutation manifests on an orphan metrics branch" owns these,
and this task leaves them unchanged: the manifest rules in
`swarmforge/constitution/articles/engineering.md`, `swarmforge/roles/hardener.md`,
`swarmforge/roles/cleaner.md`, `install.sh`, and the section "Mutation
manifests: the cache branch" of the design note. This task also leaves
`swarmforge/roles/orchestrator.md` unchanged.

## Old task names

The git-bug ids no longer resolve with `sf`. Where a document names one of
these tasks, it uses the title in quotes, not the id:

| Old id | Title |
|---|---|
| 01d767f | "Spike: replace git-bug with Markdown task files and commit trailers" |
| e6b686e | "Build the git-native task store" |
| df7e1a7 | "QA records verification as Lore commit trailers instead of committed scripts" |
| aca0457 | "Port the tool-wrapper fix to the Babashka sf tool" |
| 8414dd5 | "Fix the dry4clj and clj-mutate tool wrappers" |
| 0306e88 | "Add JavaScript and TypeScript support (sf-js)" |
| 5680706 | "sf-js crap: oxlint as complexity source, ESLint fallback" |
| 5f9a812 | "sf-js crap: exit 2 when oxlint cannot parse a file" |
| 5bbff27 | "Tighten sf-js Gherkin examples to kill mutation survivors" |

## End state

1. **`docs/spikes/git-task-store/design.md`** holds only what is still
   pending.
   - It opens with a short status paragraph. The local store is built, and
     `swarmforge/handoff-protocol.md` and `test/features/task_store/`
     describe it. Sharing is not built. The metrics section belongs to
     "Keep mutation manifests on an orphan metrics branch". The spike is
     named by its title.
   - These sections stay: Sharing, the section "Mutation manifests: the
     cache branch" (byte for byte), and Open questions. The "One remote"
     and "Local or remote" rules from "Reading the state" are about
     sharing, so they move into Sharing. Sharing no longer lists `migrate`
     among the commands that push, and it no longer says that
     `handoff-protocol.md` drops the git-bug section.
   - These sections are gone: Build plan, Decisions, Model and all its
     subsections, The commit-msg hook, Migration with its rollout, "What
     else changes in the build task", and "Backlog.md and Beads".
2. **`docs/spikes/git-task-store/features/`** stays where it is. The
   README's first sentence names "Build the git-native task store" by its
   title. The two `.feature` files do not change.
3. **Old task ids.** These name tasks by title, per the table above:
   `test/features/task_store/README.md`, `test/features/sf_tool/README.md`,
   `test/features/lore_trailers/README.md`,
   `swarmforge/tools/javascript/features/README.md`,
   `test/qa/task_store.md`, `test/qa/lore_trailers.md`, `test/qa/sf_tool.md`,
   `swarmforge/tools/javascript/qa/sf-js.md` and the features README of
   item 2.
4. **`test/qa/sf_tool.md`**, setup step 1, clones the task worktree's
   current branch, not `swarm/aca0457`:
   `git clone -q --branch "$(git -C "$WT" branch --show-current)" "$WT" "$WT/tmp/qa/project"`.
5. **`test/features/task_store/README.md`** no longer reads as a build log.
   - The section "Changes to existing specifications" is gone.
   - In "Settled with the operator", the bullets about migration, the
     rollout and git-bug leaving CI and the tests are gone. The sharing
     bullet says that sharing is not built and that its draft Gherkin is in
     `docs/spikes/git-task-store/features/`. The bullets that still explain
     behavior stay.
   - "Not covered by these features" no longer points at the design note's
     "What else changes in the build task". It points at section 9 of
     `../../qa/task_store.md`.
   - `test/qa/task_store.md`'s intro points at
     `swarmforge/handoff-protocol.md` instead of the design note's "Build
     plan". Its section 9 stays.
6. **`README.md`.**
   - In the Tests block, `swarmforge/bin/sf tool ensure gherkin-parser`
     comes right before `bash test/acceptance.sh`, which needs it. It
     appears once, and the order matches `.github/workflows/test.yml`. The
     prose under the block still counts the commands correctly.
   - The Layout block has a line for `tasks/`: task files of open tasks,
     and `tasks/done/` for finished ones.
7. **git-bug leftovers.** The words "git-bug is not needed." are gone from
   `test/features/lore_trailers/README.md`. Setup step 1 of
   `test/qa/lore_trailers.md` ("Remove git-bug from PATH") is gone, and
   the later steps are renumbered. The step "no git-bug on PATH" in
   `test/features/task_store/init.feature` stays.

No file outside the ones named above changes, except `tasks/`, which `sf`
writes.

## Scripted checks

Run these from the task worktree. `BASE` is the merge base with `main`
(`git merge-base HEAD main`).

1. Changed files: `git diff --name-only BASE HEAD` lists only the files
   named in "End state", plus `test/qa/docs_cleanup.md` and files under
   `tasks/`.
2. Old ids: `grep -rnE '01d767f|e6b686e|df7e1a7|aca0457|8414dd5|0306e88|5680706|5f9a812|5bbff27' --include='*.md' . | grep -v '^./tasks/' | grep -v '^./test/qa/docs_cleanup.md'`
   prints nothing. `ec13ecd` appears only inside the metrics section of
   the design note.
3. The metrics section is unchanged: the text from its heading up to the
   next `## ` heading is identical in `git show BASE:docs/spikes/git-task-store/design.md`
   and in the worktree copy.
4. The design note's `## ` headings are exactly the kept sections (Sharing,
   "Mutation manifests: the cache branch", Open questions), in that order.
   No `### ` heading is left from Model. `grep -n 'sf migrate\|Backlog.md\|Beads' docs/spikes/git-task-store/design.md`
   prints nothing.
5. `test/qa/sf_tool.md` has no `swarm/aca0457`. The clone command in its
   setup, run with `WT` set to the task worktree, makes a clone whose
   current branch is the task branch. Delete the clone afterwards.
6. `grep -n 'git-bug' test/features/lore_trailers/README.md test/qa/lore_trailers.md`
   prints nothing. `test/features/task_store/init.feature` is unchanged
   from `BASE`.
7. `grep -n 'Changes to existing specifications\|What else changes' test/features/task_store/README.md test/qa/task_store.md`
   prints nothing.
8. In `README.md`, the line `swarmforge/bin/sf tool ensure gherkin-parser`
   appears once in the Tests block, directly before `bash test/acceptance.sh`.
   The Layout block has a line that starts with `tasks/`.
9. Every relative Markdown link in the changed files resolves to an
   existing path: for each `](path)` and each backticked repository path
   the changed text mentions, the file or directory exists.
10. The suites still pass: `bash test/sf_test.sh`, `bash test/acceptance.sh`
    (after `swarmforge/bin/sf tool ensure gherkin-parser`), and
    `bb --config swarmforge/tools/javascript/bb.edn acceptance`.

## Reading check (manual, not scripted)

Read each changed document once and confirm by judgement:

- The design note reads as a note of pending work. A reader can tell what is
  built, where its description lives, and what remains.
- No changed document describes git-bug as current, describes a task that is
  already done as future work, or names a task by id alone.
- `test/features/task_store/README.md` still explains the store's
  behavior that its features leave out.
- Wording stays plain and short. Nothing new is added beyond the approved
  items.
