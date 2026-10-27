# QA procedure: tasks in plain git (spike 01d767f, for the build task)

This procedure runs end to end through the command line only, with real
`git` and `swarmforge/bin/sf` used as the operator and the roles use them.
Do not call `sf` internals. Turn sections 1–9 into a throwaway script under
`./tmp/` and do not commit it. Section 10 is done by hand on this
repository. Section 11 is a reading check and is not scripted.

Design: `design.md`. Gherkin: `features/` (moved to
`test/features/task_store/` by the build).

## Setup

1. Remove git-bug from PATH for sections 1–8. Expect `command -v git-bug`
   to fail.
2. Make a scratch directory `S` under `./tmp/`, and in it a bare repository
   `S/origin.git`. Give it a `pre-receive` hook that rejects any ref that is
   not under `refs/heads/`. This is the same rule as this environment's
   proxy.
3. Make `S/main`: `git init -b main`. Copy the task worktree's `swarmforge/`
   and a `.gitignore` with `.worktrees/`, `.swarmforge/` and `tmp/` into it,
   and commit them. Then run `git remote add origin ../origin.git` and
   `git push origin main`.
4. Run `swarmforge/bin/sf init`. Expect exit 0, an `identity:` line with
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
   `task id 'add-log' is ambiguous: A B`.

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
table in `design.md`, and check that `git diff-tree -r swarm/A` prints
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
   The branch and the worktree are gone, and `main` has `tasks/done/A.md`.
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

## 6. Sharing and SWARMFORGE_REMOTE

1. With `SWARMFORGE_REMOTE=origin`, run `sf new`, `sf ask`, `sf answer`,
   `sf route`, `sf review`, `sf reject`, `sf review`, `sf approve` and
   `sf handoff` on a new task D. After each one,
   `git ls-remote origin refs/heads/swarm/D` must name the local tip. The
   pre-receive hook must refuse nothing.
2. Without the variable, run a record on D. Expect `origin` to stay at the
   old tip.
3. With `SWARMFORGE_REMOTE=nowhere`, run `sf ask D x`. Expect exit 0, the
   `ASKED:` line, and on stderr
   `sf: push of swarm/D to nowhere failed; the record is only local`. The
   local tip has the question record.
4. Clone `origin` to `S/other`, and run `sf init` and `sf new "Port tool"
   --route qa` there with `SWARMFORGE_REMOTE=origin`. In `S/main`, run
   `git fetch origin` and `sf ls`. Expect the task to be listed. Run
   `sf start <id>`. Expect a local branch at the remote tip and a worktree.
5. Push a task, finish it locally, and fetch. Expect `sf ls` not to list it,
   although `origin/swarm/<id>` still exists.

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
5. A normal role commit still gets `By <role>.` and a `Lore-id`. The Lore
   procedure in `test/qa/lore_trailers.md`, sections 1–3, still passes
   without git-bug on PATH.

## 8. Identity

Unset git's `user.name` and `user.email`, in the repository and in HOME, and
run `sf init` and `sf new`. Expect `identity: operator
<operator@swarmforge.local>` and that author on the created commit. Run them
again with `SWARMFORGE_USER=ops`. Expect `ops <ops@swarmforge.local>`.

## 9. Migration (git-bug on PATH)

1. Without git-bug on PATH, `sf migrate` must exit 1 with
   `migrate needs git-bug on PATH`.
2. Put git-bug on PATH. In a fresh scratch repository, rebuild what the
   git-bug sf left: run the git-bug sf from `main` before the build (check
   it out with `git worktree add`) to make three tasks:
   - an open task with a worktree, role commits with Lore trailers, a
     question and `needs:operator`;
   - an open task whose branch was deleted;
   - a closed task.
3. Run the new `sf migrate`. Expect one `MIGRATED: <hex> -> <slug>-<hex>`
   line per open task and nothing for the closed task. The branches and
   the worktree are renamed. The task file has the request and every
   comment under `## Imported records`. The tip trailers match the labels
   and carry `SwarmForge-Base`. `sf start <hex>` prints the old `LORE:`
   lines. `sf ls` lists both open tasks with the right needs.
4. Run `sf migrate` again. Expect `SKIPPED:` lines and no new commits.
   `git bug bug` still lists all three tasks.
5. Make a clone of this repository under `./tmp/`, and fetch the git-bug
   mirror: `git fetch origin 'refs/heads/git-bug/bugs/*:refs/bugs/*'
   'refs/heads/git-bug/identities/*:refs/identities/*'`. Fetch the open
   `swarm/*` branches too. Run `sf migrate` in that clone. Expect a
   `MIGRATED:` line for each task that `git bug bug -s open` lists, and a
   `sf show` that reads right for each one. Record the result as `Tested:`
   trailers.

## 10. Rollout on this repository (operator, after the build is merged)

Follow "Rollout on this repository" in `design.md`. Run `sf migrate` in the
main checkout, check `sf ls` and `sf show` of each migrated task, push the
renamed branches, and only then delete the old `swarm/<hex>` and
`git-bug/*` branches on origin. QA does not do this. It is listed so that
the build's handoff can point to it.

## 11. Reading check (manual, not scripted)

Read the changed documents once and confirm by judgement, not by string
search:

- `swarmforge/handoff-protocol.md` describes the task as a branch, the task
  file, the record commits and their trailers, the archive in
  `tasks/done/`, sharing by branch push and `SWARMFORGE_REMOTE`, and no
  git-bug.
- `swarmforge/README.md`, `AGENTS.md`, the adapter bodies,
  `constitution/articles/project.md`, `handoffs.md`, `workflow.md` and
  `roles/orchestrator.md` no longer require git-bug or name `refs/bugs`.
- `install.sh` and `.github/workflows/test.yml` no longer install or
  probe git-bug. `test/probe_gitbug.clj` is gone.
- `sf --help` lists `migrate` and `SWARMFORGE_REMOTE`, and no longer lists
  `SWARMFORGE_GIT_BUG`.

Record the outcome as trailers on the QA handoff commit.
