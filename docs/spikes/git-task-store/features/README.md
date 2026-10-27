# Task store acceptance features

These features are the Gherkin for spike 01d767f, which takes the task
store out of git-bug. They specify the `sf` behavior that `../design.md`
settles. They are kept here and not under `test/features/`, which CI runs,
because nothing is built yet. The build task moves them to
`test/features/task_store/`. The test script there runs one check per
scenario and example row, labelled with the scenario name (for example
`sf task ids 3: ...`). Each check runs the real `sf` and real git in a
scratch repository that holds a copy of `swarmforge/`. git-bug is not on
PATH, except in `migrate.feature`. The end-to-end procedure is `../qa.md`.

## Changes to existing specifications

- `test/features/lore_trailers/lore_lines.feature` scenario 4 changes.
  `sf show` of a finished task now ends with its `LORE:` lines.
  `task_history.feature` scenario 4 states the new behavior.
- The Lore features' step meaning for "a project with swarmforge
  initialized" no longer puts git-bug on PATH.
- `test/sf_test.sh` checks the git-bug labels and comments. It is rewritten
  against the branches. Outputs and refusals that these features do not
  restate (`ASKED:`, `ANSWERED:`, `REVIEW:`, `REJECTED:`, `ROUTE:`,
  `AUDIT_REQUIRED`, `NO_TASK`, the `--to` and route checks) stay as they
  are.

## Placeholders

| Text | Meaning |
|---|---|
| `\n` in a quoted value or a table cell | A newline. |
| `\t` in a quoted value or a table cell | A tab. |
| `$A`, `$B`, `$C` | The id that `sf new` printed for task "A", "B" or "C". |
| `$A:hex` | The 7 hex digits at the end of `$A`. |
| `$A:created` | The abbreviated sha (`git rev-parse --short`) of the created record commit of task "A", the first commit on its branch. |
| `NEW` | The id that the step under test (`sf new`) printed. "task NEW" is that task. |
| `$G1`, `$G2` | The git-bug human id (7 hex digits) of git-bug task G1 or G2. |
| `C1`, `C2`, `T1`, `B0`, `B2` | The abbreviated sha of the commit that a step named so. |
| `B0:full` | The full 40-digit sha of `B0`. |
| `L1` | The `Lore-id` that the hook gave commit `C1`. |
| `DATE` | Any date in the form `YYYY-MM-DD`. |
| `ROOT` | The absolute path of the main checkout. |
| A task name in quotes in a step (`task "A"`, `task "fix-login-$G1"`) | `"A"` is task A. A value with a `-` is a task id. |

## Step meanings

| Step | Meaning |
|---|---|
| `a project with swarmforge initialized` | A scratch git repository on `main`. Its first commit has a copy of `swarmforge/` and a `.gitignore` with `.worktrees/`, `.swarmforge/` and `tmp/`. git's identity is `tester <tester@example.com>`. git-bug is not on PATH. `sf init` ran in it and exited 0. |
| `… and a remote "R"` | The same, plus a bare repository added as remote `R`. |
| `… and git-bug on PATH` | The same, with git-bug on PATH and a git-bug identity. |
| `a git repository with a copy of swarmforge and no git-bug on PATH` | The project without `sf init`. |
| `git-bug is not on PATH` | Removes git-bug from PATH for the step under test. |
| `the git identity of the project is "I"` | Sets `user.name` and `user.email` from `name <email>`. `-` unsets both, in the repository and in HOME. |
| `task "A" titled "T" was created on route "R"` | `sf new "T" -m "T" --route R` in the main checkout. |
| `task "A" titled "T" was started on route "R"` | The same, then `sf start $A`. |
| `task "A" titled "T" with the request "Q" was started on route "R"` | `sf new "T" -m "Q" --route R`, then `sf start $A`. |
| `task "A" was finished` | `sf handoff $A --to done` with `SWARMFORGE_ROLE` unset, then `sf finish $A`, in the main checkout. |
| `another clone of "R" created task "C" titled "T" on route "Q" and pushed its branch` | Clones `R` into a second scratch directory, runs `sf init` and `sf new "T" -m "T" --route Q` there, then `git push R swarm/$C`. |
| `another clone of "R" pushed the record "C" on task "A"` | Clones `R` into a second scratch directory and runs `sf init` and `sf start $A` there. Then it runs `C` (placeholders filled in, split on spaces) with `SWARMFORGE_REMOTE=R`, so the record is pushed. |
| `role "R" committed "M" in the worktree of task "A" as C1` | `SWARMFORGE_ROLE=R git commit --allow-empty -m M` in the worktree of A. The commit is `C1`. |
| `role "R" committed the file "P" with "M" in the worktree of task "A" as C1` | Writes `P` with the line `x`, then `git add P` and commits as above. |
| `the worktree of task "A" has the staged new file "S" and the unstaged change "U"` | Commits `U` with the line `1` (as the stage's role), appends the line `2` to `U` without staging it, then writes `S` with the line `s` and stages it with `git add S`. |
| `the file "P" in the worktree of task "A" is written with "X"` | Writes `X` and a newline to `P` in that worktree. Makes directories as needed. |
| `the tip of the branch of task "A" is named T1` | `T1` is the current tip of `swarm/$A`. |
| `I ran "C"` / `I ran "C" in the worktree of task "A"` | Runs `C` (placeholders filled in, split on spaces) in the main checkout or that worktree. Output and status are not checked. |
| `I ran "C" with the text "X"` | The same, with `X` (newlines expanded) added as one last argument. |
| `I run …` | The same forms. This is the step under test, whose output and status the `Then` steps check. |
| `I run "C" with SWARMFORGE_REMOTE "R"` | The step under test with `SWARMFORGE_REMOTE=R`. |
| `I run sf new with the title "T"` | `sf new "T"` in the main checkout. |
| `I run sf new with the title "T", the request "Q" and the route "R"` | `sf new "T" -m "Q" --route R`, with `\n` in Q expanded. `… with SWARMFORGE_REMOTE "X"` adds that variable. |
| `I commit "M" in the worktree of task "A" with SWARMFORGE_ROLE "R"` | `SWARMFORGE_ROLE=R git commit --allow-empty -m M` in that worktree, with the hook active. |
| `I commit "M" in the worktree of task "A"` / `… in the main checkout` | `git commit --allow-empty -m M` there, with `SWARMFORGE_ROLE` unset. |
| `the exit status is N` | The exit status of the step under test. |
| `the output is "X"` / `is not "X"` | Stdout without its last newline equals / does not equal `X`. |
| `the output matches "RE"` | Stdout without its last newline matches the regular expression `RE` (placeholders filled in first). |
| `the output has the line "X"` | One stdout line equals `X`. |
| `the output has the lines "X"` | The lines of `X` appear in stdout as consecutive lines, in order. |
| `the output has the lines "X" before the line "L"` | The same, and all of them come before the first line equal to `L`. |
| `the output after the line "L" is "X"` | The stdout lines after the first line equal to `L`, joined by newlines without the last newline, equal `X`. |
| `the output ends with the lines "X"` | The last stdout lines equal the lines of `X`. |
| `the output has no line containing "X"` | No stdout line contains `X`. |
| `the error output has the line "X"` | One stderr line equals `X`. |
| `the branch "B" exists` / `does not exist` / `is at T1` | `git rev-parse --verify refs/heads/B` succeeds / fails / prints the sha of `T1`. |
| `the branch "B" is at the tip of "R"` | `refs/heads/B` and `refs/remotes/R` name the same commit. |
| `the branch "B" was deleted` | `git branch -D B` in the main checkout. |
| `the branch "B" of "R" is at the tip of task "A"` | `git ls-remote R refs/heads/B` names the tip of `swarm/$A`. |
| `the branch "B" of "R" contains C1` | `C1` is an ancestor of that remote branch's commit (fetched into a scratch ref). |
| `the remote "R" has no branch "B"` | `git ls-remote R refs/heads/B` prints nothing. |
| `the branch of task "A" did not move` | `swarm/$A` names the same commit as before the step under test. |
| `the tip commit of task "A" has the message "M"` | `git log -1 --format=%B swarm/$A`, without its trailing newlines, equals `M`. |
| `the parent of the tip commit of task "A" has the message "M"` | The same for `swarm/$A^`. |
| `the tip commit of task "A" has the trailer "T"` | `git log -1 --format=%(trailers:only,unfold) swarm/$A` has the line `T`. |
| `the tip commit of task NEW has the author "X"` | `git log -1 --format='%an <%ae>'` equals `X`. |
| `the records of task "A" are, oldest first, "X"` | The `SwarmForge-Record` values of the commits in `git log --first-parent --reverse swarm/$A` that have `SwarmForge-Task: $A`, one per line, equal the lines of `X`. |
| `the parent of the tip commit of task "A" is C1` | `git rev-parse swarm/$A^` is the sha of `C1`. |
| `the parent of the tip commit of task NEW is the HEAD of the main checkout` | `swarm/NEW^` and the main checkout's HEAD name the same commit. |
| `the tip commit of task "A" changes no file` | `git diff-tree --no-commit-id -r swarm/$A` prints nothing. |
| `the tip commit of task "A" adds only the file "P"` | `git diff-tree --no-commit-id -r --name-status --no-renames swarm/$A` prints exactly `A\tP`. |
| `the tip commit of task "A" changes the files "X"` | The same command prints exactly the lines of `X`, with the tab between status and path written as a space in `X`. |
| `the file "P" on the branch of task "A" is "X"` | `git show swarm/$A:P`, without its last newline, equals `X` (`DATE` matches any date). |
| `the file "P" on the branch of task "A" has the line "X"` | One line of `git show swarm/$A:P` equals `X`. |
| `the file "P" does not exist on the branch of task "A"` | `git cat-file -e swarm/$A:P` fails. |
| `the file "P" in the worktree of task "A" still holds "X"` | The file holds `X` and a newline. |
| `the main checkout has no file "P" and no uncommitted change` | There is no `P` in the main checkout, and `git status --porcelain` prints nothing. |
| `the main checkout has the file "P" and no file "Q"` | `P` is in the main checkout, and `Q` is not. |
| `the HEAD of the main checkout is a merge whose second parent is T1` | `HEAD^2` is `T1`. |
| `the HEAD of the main checkout has the message "M"` | As for the tip commit, for `HEAD` of the main checkout. |
| `the worktree of task "A" has no uncommitted change` | `git status --porcelain` in it prints nothing. |
| `the worktree of task "A" does not exist` | There is no `.worktrees/$A` directory. |
| `the worktree "W" is on the branch "B"` | `git -C W rev-parse --abbrev-ref HEAD` prints `B`. |
| `the worktree of task "A" still has the staged new file "S" and the unstaged change "U"` | In that worktree, `git status --porcelain` prints exactly `A  S` and ` M U`. `S` holds `s`, and `U` holds `1` and `2`. |
| `the old sf made the open git-bug task G1 titled "T" with the request "Q" on route "R" at stage "S" on commit B0` | Does what the git-bug sf's `sf new` did. `git bug bug new -t T -m Q` (`$G1` is its human id). Labels `route:R` and `stage:S`. The branch `swarm/$G1` at the main checkout's HEAD, which is `B0`. A comment `type: created`, `from: operator`, `branch: swarm/$G1`, `commit: B0`, `route: R`, one per line. |
| `the git-bug task G1 has the needs label "L"` | Adds the label `L`. `-` adds none. |
| `the git-bug task G1 has the comment "X"` | `git bug bug comment new $G1 -m X`. |
| `the git-bug task G2 is closed` | `git bug bug status close $G2`. |
| `the old sf started G1` | `git worktree add .worktrees/$G1 swarm/$G1`. |
| `role "R" committed "M" in the old worktree of G1 as C1` | `SWARMFORGE_ROLE=R git commit --allow-empty -m M` in `.worktrees/$G1`. |
| `git-bug still lists the tasks G1 and G2` | `git bug bug` lists both human ids. |
