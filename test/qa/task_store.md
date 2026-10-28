# QA procedure: the local task store

For the task "Build the git-native task store".

This procedure runs end to end through the command line only, with real
`git` and `swarmforge/bin/sf` used as the operator and the roles use them.
Do not call `sf` internals. Turn sections 1–8 into a throwaway script under
`./tmp/` and do not commit it. Section 9 is a reading check and is not
scripted.

Design: `swarmforge/handoff-protocol.md` describes the store. Sharing is
not part of this task. Gherkin: `test/features/task_store/`.

## Setup

1. Remove git-bug from PATH. Expect `command -v git-bug` to fail.
2. Make a scratch directory `S` under `./tmp/`, and in it `S/main`:
   `git init -b main`. Copy the task worktree's `swarmforge/` and a
   `.gitignore` with `.worktrees/`, `.swarmforge/` and `tmp/` into it, and
   commit them.
3. Run `swarmforge/bin/sf init`. Expect exit 0, an `identity:` line with
   git's identity, and a `roles:` line. Check that `git for-each-ref` shows
   nothing under `refs/bugs` or `refs/identities`.

## 1. Ids

1. Run `sf new "Ändra inloggningen för användare" -m x --route specifier`.
   Expect `andra-inloggningen-for-` and 7 hex digits.
2. Run `sf new "Add login" -m "Users log in.\nWith email." --route specifier,coder,qa`,
   with a real newline in `-m`. Note the id `A`. Run `sf new "Add logout" -m x --route coder`
   and note `B`.
3. Run `sf stage` with the full id of `A`, its 7 hex digits, `add-logi`,
   and `add-logo`. Expect `specifier`, `specifier`, `specifier` and `coder`.
   Then run `sf stage add-log`. Expect exit 1 and
   `task id 'add-log' is ambiguous: A B`. Run `sf stage nothing`. Expect
   exit 1 and `no task 'nothing'`.

## 2. The created record and the task file

1. Check `git log --format=%H swarm/A`: one commit on top of `main`. The
   main checkout has no new commit and no `tasks/` folder.
2. Check that `git show --stat swarm/A` adds only `tasks/A.md`. The file is
   the front matter (`id`, `created` as today's UTC date), `# Add login`, and
   the two request lines.
3. Check `git log -1 --format=%B swarm/A`. Expect the subject
   `sf created: A`, the title, and the six `SwarmForge-*` trailers
   (`Stage: specifier`, `Route: specifier,coder,qa`, `Needs: none`).
   `git log -1 --format='%(trailers:key=SwarmForge-Stage,valueonly)' swarm/A`
   must print `specifier`.

## 3. Records from start to finish (route specifier,coder,qa)

Run this as the operator in `S/main`, and as the roles in the worktree.
After each step, check `git log -1 --format=%B swarm/A` against the record
table in the design, and check that `git diff-tree -r swarm/A` prints
nothing (the record changes no file).

1. Run `sf start A`. Expect `TASK`, `TITLE`, `STAGE`, `ROUTE`, `BRANCH` and
   `DIR` lines, `---`, the two request lines, and the `type: created` block.
2. In the worktree, stage a new file and leave an unstaged change in a
   tracked file. Then run `sf ask A` with a question of three lines, and
   `sf answer A argon2id`. After each one, `git status --porcelain` in the
   worktree must be the same as before. The question commit's body keeps
   the three lines.
3. Commit the specifier's work with a Lore trailer
   (`Constraint: keep it short`). Run `sf review A -m "spec ready"`, then
   `sf reject A "split it"`. Commit again, and run `sf review A` and
   `sf approve A`. Expect two commits: `sf approval: A`, then
   `sf handoff: A` with `SwarmForge-Stage: coder`.
4. Commit as the coder. Run `sf handoff A -m "code done"`. Expect
   `HANDOFF: A coder -> qa at <coder commit>`.
5. Commit as QA. Run `sf handoff A`. Expect that the tip commit moves
   `tasks/A.md` to `tasks/done/A.md`. The moved file's front matter has
   `stage: done` and `route: specifier,coder,qa`, and its `## Records`
   section has every record, in order and fenced. The worktree is clean.
6. Run `sf show A`. Expect `STATUS: open`, `STAGE: done`, every record as a
   `key: value` block (the three-line question indented by two spaces), and
   the `LORE:` lines of the role commits only.
7. Run `sf finish A`. Expect `FINISHED: A merged into main`. `HEAD` is a
   merge with the `sf finished: A` message and trailers.
   `git log --first-parent --oneline main` shows one commit for the task.
   The branch and the worktree are gone, and `main` has `tasks/done/A.md`
   and no `tasks/A.md`.
8. Run `sf show A` and `sf show <hex of A>`. Expect `STATUS: finished`, the
   `type: finished` block with `merged-into: main`, and the same `LORE:`
   lines as in step 6. `sf start A` must exit 1 with `task A is finished`.

## 4. ls, next, refusals

1. Make three tasks: one plain, one waiting on a question, and one waiting
   for approval. Check that `sf ls`, `sf ls --needs`, `sf ls --stage coder`
   and `sf next <role>` match the state of each one.
2. Check that these each exit 1 and leave the branch tip as it was:
   `sf approve` without a review, `sf handoff` with `needs operator`,
   `sf handoff` with uncommitted changes, and `sf route` without the
   current stage.

## 5. First-parent history

1. Run task B (route `coder`) with a coder commit `Work\n\nTested: from B`.
2. Finish another task, C, with a commit `Spec\n\nTested: from C`.
3. In B's worktree, run `git merge --no-edit main`. Expect that `sf show B`
   and `sf start B` show no record of C, no `Tested: from C` line, and B's
   own `LORE:` lines. `sf stage B` stays `coder`.

## 6. A squash-merged task

1. Make a task D on route `specifier`. Run `sf ask D Which?` and
   `sf answer D yes`, commit as the specifier with `Spec\n\nTested: x`, and
   run `sf handoff D --to done`.
2. In `S/main`, run `git merge --squash swarm/D` and
   `git commit -m "Squash D"`. Keep D's branch and worktree.
3. Expect `sf ls` and `sf next specifier` not to list D, and `sf start D`,
   `sf ask D x` and `sf handoff D` to exit 1 with `task D is finished` and
   leave `swarm/D` where it was.
4. Run `sf show D`. Expect `STATUS: finished`, the archived records without
   fences and with no `type: finished` block, and D's `LORE:` lines.
5. Remove D's worktree and branch (`git worktree remove --force`,
   `git branch -D`). Run `sf show D` again. Expect the same records and no
   `LORE:` lines.

## 7. The commit-msg hook

In a task worktree:

1. `git commit --allow-empty -m "$(printf 'Fix\n\nSwarmForge-Stage: done')"`
   must exit 1, print `sf: SwarmForge-Stage is a SwarmForge record trailer;
   only sf writes those`, and make no commit. Do the same with
   `swarmforge-needs` in lower case, and with `SWARMFORGE_ROLE=done`.
2. The same line followed by a closing paragraph commits.
3. In the main checkout, the trailer commits.
4. With `git config core.hooksPath /dev/null`, run
   `sf commit-msg coder <file>` on such a message. Expect exit 1 and the
   file unchanged. Then reset `core.hooksPath`.
5. A normal role commit still gets `By <role>.` and a `Lore-id`, with the
   role read from the branch (no `SWARMFORGE_ROLE`). The Lore procedure in
   `test/qa/lore_trailers.md`, sections 1–5, passes without git-bug on PATH.

## 8. Identity

Unset git's `user.name` and `user.email`, in the repository and in HOME, and
run `sf init` and `sf new`. Expect `identity: operator
<operator@swarmforge.local>` and that author on the created commit. Run them
again with `SWARMFORGE_USER=ops`. Expect `ops <ops@swarmforge.local>`. Run
them with `SWARMFORGE_EMAIL=ops@example.com` as well. Expect
`ops <ops@example.com>`.

## 9. Reading check (manual, not scripted)

Read the changed documents once and confirm by judgement, not by string
search:

- `swarmforge/handoff-protocol.md` describes the task as a branch, the task
  file, the record commits and their trailers, the archive in
  `tasks/done/`, sharing by a plain `git push` of `swarm/<id>`, and no
  git-bug.
- `swarmforge/README.md`, `AGENTS.md`, the adapter bodies,
  `constitution/articles/project.md`, `handoffs.md`, `workflow.md` and
  `roles/orchestrator.md` no longer require git-bug or name `refs/bugs`.
- `install.sh` and `.github/workflows/test.yml` no longer install or
  probe git-bug. `test/probe_gitbug.clj` is gone. `test/acceptance.sh` and
  `test/sf_test.sh` run without git-bug.
- `sf --help` no longer lists `SWARMFORGE_GIT_BUG`, and does not list
  `migrate` or `SWARMFORGE_REMOTE`.
- The commit-msg hook's error text no longer mentions git-bug.

Record the outcome as trailers on the QA handoff commit. The rollout on this
repository (re-creating the tasks still in git-bug and removing the
`git-bug/*` branches) was a separate task, not QA's.
