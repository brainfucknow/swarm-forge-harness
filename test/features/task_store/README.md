# Task store acceptance features

Gherkin for the task "Build the git-native task store", which built the
local part of the design from the task "Spike: replace git-bug with
Markdown task files and commit trailers". It takes `sf` off git-bug and
keeps every task on its local branch: task files under `tasks/`, record
commits with `SwarmForge-*` trailers, the `tasks/done/` archive, slug plus
hash ids, and the hook's refusal of `SwarmForge-*` trailers on role
commits. The test script here runs one check per scenario and example row,
labelled with the scenario name (for example `sf task ids 3: ...`). Each
check runs the real `sf` and real git in a scratch repository that holds a
copy of `swarmforge/`. git-bug is never on PATH. The current contract is
[the handoff protocol](../../../swarmforge/handoff-protocol.md).

## Settled with the operator

- The spike's design and Gherkin were adopted as approved.
- No `sf migrate`: the template had no users yet.
- Sharing is not built: the push to `SWARMFORGE_REMOTE`, the check for
  unseen remote records, remote-tracking task branches, the one considered
  remote, and hex uniqueness against remote branches. Its draft Gherkin is
  in `docs/spikes/git-task-store/features/`. Here, a task is a local
  `swarm/<id>` branch only.

## Settled with the operator: sf finish resume

For the task "Let sf finish resume after a merge conflict"
(`finish_resume.feature`):

- After a conflict the operator resolves and commits the merge, with any
  message, then runs `sf finish <id>` again. sf finish then removes the
  worktree and the branch, and rewrites the merge commit's message to the
  finished record when it lacks it. Nobody writes `SwarmForge-*` trailers by
  hand.
- A merge still in progress is refused with "commit the merge first"; sf
  finish does not commit it.
- The conflict message is `sf: merge of swarm/<id> into <target> stopped on
  conflicts: <files>; resolve them, commit, then run: sf finish <id>`.
- crap4clj run from the repository root is a separate task, "Run crap4clj
  from the repository root".

## Not covered by these features

Per `AGENTS.md`, prompt and document text is not pinned by tests. QA reads
it by hand against the protocol and role guidance. `sf --help` does not
list `SWARMFORGE_GIT_BUG`, `migrate` or `SWARMFORGE_REMOTE`.

## Placeholders

| Text | Meaning |
|---|---|
| `\n` in a quoted value or a table cell | A newline. |
| `\t` in a quoted value or a table cell | A tab. |
| `$A`, `$B` | The id that `sf new` printed for task "A" or "B". |
| `$A:hex` | The 7 hex digits at the end of `$A`. |
| `$A:created` | The abbreviated sha (`git rev-parse --short`) of the created record commit of task "A", the first commit on its branch. |
| `NEW` | The id that the step under test (`sf new`) printed. "task NEW" is that task. |
| `C1`, `C2`, `T1`, `M1` | The abbreviated sha of the commit that a step named so. |
| `L1` | The `Lore-id` that the hook gave commit `C1`. |
| `DATE` | Any date in the form `YYYY-MM-DD`. |
| `ROOT` | The absolute path of the main checkout. |

## Step meanings

| Step | Meaning |
|---|---|
| `a project with swarmforge initialized` | A scratch git repository on `main`. Its first commit has a copy of `swarmforge/` and a `.gitignore` with `.worktrees/`, `.swarmforge/` and `tmp/`. git's identity is `tester <tester@example.com>`. git-bug is not on PATH. `sf init` ran in it and exited 0. |
| `a git repository with a copy of swarmforge and no git-bug on PATH` | The project without `sf init`. |
| `the git identity of the project is "I"` | Sets `user.name` and `user.email` from `name <email>`. `-` unsets both, in the repository and in HOME. |
| `task "A" titled "T" was created on route "R"` | `sf new "T" -m "T" --route R` in the main checkout. |
| `task "A" titled "T" was started on route "R"` | The same, then `sf start $A`. |
| `task "A" titled "T" with the request "Q" was started on route "R"` | `sf new "T" -m "Q" --route R`, then `sf start $A`. |
| `task "A" was finished` | `sf handoff $A --to done` with `SWARMFORGE_ROLE` unset, then `sf finish $A`, in the main checkout. |
| `task "A" was handed off to done and squash-merged into main` | `sf handoff $A --to done` with `SWARMFORGE_ROLE` unset, then, in the main checkout, `git merge --squash swarm/$A` and `git commit -m "Squash A"`. The branch and the worktree of A stay. |
| `the worktree and the branch of task "A" were removed` | `git worktree remove --force .worktrees/$A`, then `git branch -D swarm/$A`, in the main checkout. |
| `role "R" committed "M" in the worktree of task "A" as C1` | `SWARMFORGE_ROLE=R git commit --allow-empty -m M` in the worktree of A. The commit is `C1`. |
| `role "R" committed the file "P" with "M" in the worktree of task "A" as C1` | Writes `P` with the line `x`, then `git add P` and commits as above. |
| `the worktree of task "A" has the staged new file "S" and the unstaged change "U"` | Commits `U` with the line `1` (as the stage's role), appends the line `2` to `U` without staging it, then writes `S` with the line `s` and stages it with `git add S`. |
| `the file "P" in the worktree of task "A" is written with "X"` | Writes `X` and a newline to `P` in that worktree. Makes directories as needed. |
| `the tip of the branch of task "A" is named T1` | `T1` is the current tip of `swarm/$A`. |
| `the worktree of task "A" and the branch "B" changed the files "F" differently` | For each path in `F` (separated by spaces): in the worktree of A, writes it with the line `ours` and commits it as the stage's role; on branch `B` (made at the main checkout's HEAD when missing), writes it with the line `theirs` and commits it. The main checkout is on `main` afterwards. |
| `the operator resolved the conflicts and staged them` | In the main checkout, writes every conflicted file with the line `resolved` and runs `git add` on it. |
| `the operator resolved the conflicts and committed the merge with the message "M"` | The same, then `git commit -m M` in the main checkout. |
| `the operator resolved the conflicts and committed the merge with git's prepared message` | The same, then `git commit --no-edit` in the main checkout, which keeps the message sf finish prepared for the merge. |
| `the HEAD of the main checkout is named M1` | `M1` is the current HEAD of the main checkout. |
| `I ran "C"` / `I ran "C" in the worktree of task "A"` | Runs `C` (placeholders filled in, split on spaces) in the main checkout or that worktree. Output and status are not checked. |
| `I ran "C" with the text "X"` | The same, with `X` (newlines expanded) added as one last argument. |
| `I run …` | The same forms. This is the step under test, whose output and status the `Then` steps check. |
| `I run sf new with the title "T"` | `sf new "T"` in the main checkout. |
| `I run sf new with the title "T", the request "Q" and the route "R"` | `sf new "T" -m "Q" --route R`, with `\n` in Q expanded. |
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
| `the branch "B" exists` / `does not exist` | `git rev-parse --verify refs/heads/B` succeeds / fails. |
| `the branch of task "A" did not move` | `swarm/$A` names the same commit as before the step under test. |
| `the tip commit of task "A" has the message "M"` | `git log -1 --format=%B swarm/$A`, without its trailing newlines, equals `M`. |
| `the parent of the tip commit of task "A" has the message "M"` | The same for `swarm/$A^`. |
| `the tip commit of task NEW has the author "X"` | `git log -1 --format='%an <%ae>'` equals `X`. |
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
| `the HEAD of the main checkout is M1` | `HEAD` is the commit `M1`. |
| `the HEAD of the main checkout has the parents and the tree of M1` | `HEAD` has the same parents, in order, and the same tree as `M1`. |
| `the main checkout is on the branch "B" merging T1 with the conflicted files "F"` | In the main checkout, `git branch --show-current` prints `B`, `MERGE_HEAD` is `T1`, and `git diff --name-only --diff-filter=U` prints exactly the paths of `F` (separated by spaces in `F`), one per line. |
| `the main checkout is on the branch "B" merging T1 with no conflicted file` | The same, and `git diff --name-only --diff-filter=U` prints nothing. |
| `the worktree of task "A" has no uncommitted change` | `git status --porcelain` in it prints nothing. |
| `the worktree of task "A" does not exist` | There is no `.worktrees/$A` directory. |
| `the worktree of task "A" exists` | `.worktrees/$A` is a directory. |
| `the worktree of task "A" still has the staged new file "S" and the unstaged change "U"` | In that worktree, `git status --porcelain` prints exactly `A  S` and ` M U`. `S` holds `s`, and `U` holds `1` and `2`. |
