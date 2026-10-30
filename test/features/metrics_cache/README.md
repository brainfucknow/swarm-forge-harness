# Metrics cache acceptance features

Mutation manifests are a cache outside the tracked tree and every PR, on the
orphan branch
`swarmforge/metrics` (see [Sharing](../../../swarmforge/handoff-protocol.md#sharing)).
The manifests are the clj-mutate files under `.metrics/mutate/` and the
gherkin-mutator stamp and manifest
block, which the wrapper keeps in `.metrics/gherkin/<feature path>`
instead of the feature file.

- `metrics_load.feature`: `sf start` copies the cache into the worktree.
- `metrics_save.feature`: `sf handoff` (and `sf approve`) saves to the cache.
- `gherkin_block.feature`: the gherkin-mutator wrapper moves the block.
- `install_ignore.feature`: `install.sh` ignores `.metrics/`.

`test/acceptance.sh` runs them, one check per scenario and example row, with
the real `sf`, real git and a real `bb` in a scratch repository that holds a
copy of `swarmforge/`.

## Placeholders

| Text | Meaning |
|---|---|
| `\n` in a quoted value or a table cell | A newline. |
| `$A`, `$B` | The id that `sf new` printed for task "A" or "B". |
| `C1` | The abbreviated sha of the commit that a step named so. |
| `M1` | The tip of `swarmforge/metrics` that a step named so. |

## Step meanings

Steps not listed here mean what `../task_store/README.md` says.

| Step | Meaning |
|---|---|
| `a project with swarmforge initialized and .metrics/ ignored` | The task store step `a project with swarmforge initialized`, with the line `.metrics/` added to the `.gitignore` of its first commit. |
| `the metrics branch holds the file "P" with "X"` | In the main checkout, without a checkout of the branch: a commit on `swarmforge/metrics` whose tree is the tip's tree with `P` holding `X` and a newline. When the branch does not exist, the commit has no parent. |
| `the tip of the metrics branch is named M1` | `M1` is the current tip of `swarmforge/metrics`. |
| `the file "P" in the worktree of task "A" was deleted` | Removes `P` from that worktree. |
| `the file "P" in the worktree of task "A" holds "X"` | `P` in that worktree holds `X` and a newline. |
| `the worktree of task "A" has no file "P"` | There is no file or directory `P` in that worktree. |
| `the worktree of task "A" has the committed file "P" that holds "X"` | Writes `X` and a newline to `P` in that worktree, then `git add P` and commits it as the task's stage role. |
| `the file "P" in the worktree of task "A" is as committed` | `git status --porcelain -- P` in that worktree prints nothing. |
| `gherkin-mutator is a fake that writes the block "B" and exits with status N` | `SWARMFORGE_TOOL_SRC` names a scratch tool source whose `bb.edn` task `gherkin-mutator` copies the `--feature` file to `seen.feature` in a scratch directory outside the worktree, removes any block from the file, writes `B`, a newline, a blank line and the rest of the file, and exits with `N`. `sf tool ensure gherkin-mutator` then ran in the main checkout. |
| `I run the gherkin-mutator wrapper with "ARGS" in the worktree of task "A"` | Runs the main checkout's `.swarmforge/bin/gherkin-mutator` with `ARGS`, split on spaces, from the root of that worktree. This is the step under test. |
| `I run the gherkin-mutator wrapper with "ARGS" in the directory "D" of the worktree of task "A"` | The same, from directory `D` inside that worktree. |
| `the fake saw the feature file holding "X"` | The fake's `seen.feature` holds `X` and a newline. |
| `the file "P" on the metrics branch is "X"` | `git show swarmforge/metrics:P`, without its last newline, equals `X`. |
| `the metrics branch is one commit with no parent` | `git rev-list swarmforge/metrics` prints one commit, and `git rev-list --parents -n 1 swarmforge/metrics` prints only that sha. So `git merge-base main swarmforge/metrics` fails. |
| `the tip commit of the metrics branch has the message "M"` | `git log -1 --format=%B swarmforge/metrics`, without its trailing newlines, equals `M`. |
| `the tip commit of the metrics branch changes the files "X"` | `git diff-tree --root --no-commit-id -r --name-status --no-renames swarmforge/metrics` prints exactly the lines of `X`, with the tab between status and path written as a space in `X`. |
| `the parent of the tip commit of the metrics branch is M1` | `git rev-parse swarmforge/metrics^` is the sha of `M1`. |
| `the metrics branch did not move` | `swarmforge/metrics` names the same commit as before the step under test. |
| `a git repository with one commit and no swarmforge` | A scratch git repository on `main` with one commit, no `swarmforge/` and no `.gitignore`. |
| `I run install.sh into it N times` | Runs this repository's `install.sh <scratch repository>` N times. The exit status is the status of the last run. |
| `the .gitignore of the repository has the line ".metrics/" exactly once` | `grep -cxF .metrics/ .gitignore` in the scratch repository prints `1`. |
