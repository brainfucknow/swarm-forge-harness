# Sharing features (draft, for the sharing task)

Task e6b686e built the local store and moved the spike's other features to
`test/features/task_store/`. These two features are what is left: the third
task of the build, sharing, adopts them, moves them to
`test/features/task_store/`, and adds a QA section for them (the spike's
section 6, "Sharing and SWARMFORGE_REMOTE":
`git show c7091d5:docs/spikes/git-task-store/qa.md`). They are kept here
because CI runs every feature under `test/features/`.

- `remote_push.feature`: the push to `SWARMFORGE_REMOTE` after each record,
  the warning when it fails, and the refusal while the remote branch has
  records the local one lacks.
- `remote_tasks.feature`: remote-tracking task branches, the one considered
  remote, and finished tasks whose remote-tracking branch remains (the
  spike's `task_state` scenarios 4, 5, 6, 9 and the remote rows of 7).

The placeholders and step meanings are those of
`test/features/task_store/README.md`, plus these.

| Text | Meaning |
|---|---|
| `$C` | The id that `sf new` printed for task "C". |

| Step | Meaning |
|---|---|
| `a project with swarmforge initialized and a remote "R"` | The project, plus a bare repository added as remote `R`. |
| `a second remote "R"` | Adds another bare repository as remote `R`. |
| `another clone of "R" created task "C" titled "T" on route "Q" and pushed its branch` | Clones `R` into a second scratch directory, runs `sf init` and `sf new "T" -m "T" --route Q` there, then `git push R swarm/$C`. |
| `another clone of "R" pushed the record "C" on task "A"` | Clones `R` into a second scratch directory and runs `sf init` and `sf start $A` there. Then it runs `C` (placeholders filled in, split on spaces) with `SWARMFORGE_REMOTE=R`, so the record is pushed. |
| `I run "C" with SWARMFORGE_REMOTE "R"` | The step under test with `SWARMFORGE_REMOTE=R`. `-` leaves it unset. |
| `I run sf new with the title "T", the request "Q" and the route "R" with SWARMFORGE_REMOTE "X"` | `sf new` as in the store's README, with that variable. |
| `the branch "B" is at the tip of "R"` | `refs/heads/B` and `refs/remotes/R` name the same commit. |
| `the branch "B" of "R" is at the tip of task "A"` | `git ls-remote R refs/heads/B` names the tip of `swarm/$A`. |
| `the branch "B" of "R" contains C1` | `C1` is an ancestor of that remote branch's commit (fetched into a scratch ref). |
| `the remote "R" has no branch "B"` | `git ls-remote R refs/heads/B` prints nothing. |
| `the tip commit of task "A" has the trailer "T"` | `git log -1 --format=%(trailers:only,unfold) swarm/$A` has the line `T`. |
| `the records of task "A" are, oldest first, "X"` | The `SwarmForge-Record` values of the commits in `git log --first-parent --reverse swarm/$A` that have `SwarmForge-Task: $A`, one per line, equal the lines of `X`. |
| `task "B" was finished` / `… was handed off to done and squash-merged into main` | As in the store's README; in `remote_tasks.feature` scenario 3 the example column `finish` holds the words after `task "B"`. |
