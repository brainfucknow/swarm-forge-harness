# Lore trailer acceptance features

Roles record decision context and verification as
[Lore](https://github.com/Ian-stetsenko/lore-protocol) git trailers. These features specify the
`sf` side: the commit-msg hook and the `LORE:` lines of `sf start` and
`sf show`. A test script under `test/` runs them, one check per scenario and
example row, labelled with the scenario name (for example
`sf commit Lore-id 1: Tested`). Each check runs the real `sf` and real git in
a scratch git repository with `swarmforge/` copied in. The
[workflow article](../../../swarmforge/constitution/articles/workflow.md)
defines the current commit and verification conventions.

## Placeholders

| Text | Meaning |
|---|---|
| `\n` in a quoted value | A newline. A line that starts with a space after `\n` is a folded trailer line. |
| `ID` | The task id that `sf new` printed. |
| `LID` | The value of the `Lore-id` trailer in the commit under test. |
| `C1`, `C2`, `C3` | The abbreviated sha of the commit named so, as `git rev-parse --short` prints it in the scratch repository. |
| `L1`, `L3` | The `Lore-id` the hook gave commit `C1` / `C3`. |

## Step meanings

| Step | Meaning |
|---|---|
| `a project with swarmforge initialized` | A scratch git repository on `main` whose first commit holds a copy of `swarmforge/`, with `sf init` run in it. |
| `a task at stage "S" started with sf start` | `sf new "t" -m "t" --route S` ran, then `sf start ID`. The task's worktree is `.worktrees/ID`. |
| `the main checkout has the commit "M"` | `git commit --allow-empty -m M` ran in the main checkout before the task was created. |
| `I commit "M" in the task worktree` | `git commit --allow-empty -m M` in the task worktree, with the hook active and `SWARMFORGE_ROLE` unset. |
| `I commit "M" in the main checkout` | The same in the main checkout. |
| `I commit "M" in the task worktree with SWARMFORGE_ROLE "R"` | The same in the task worktree with `SWARMFORGE_ROLE=R`. |
| `I commit "M" in the task worktree twice` | Two such commits, one after the other. |
| `role "R" committed "M" in the task worktree as C` | `SWARMFORGE_ROLE=R git commit --allow-empty -m M` in the task worktree; the new commit is `C`. |
| `"M" was committed in the task worktree with the hook skipped as C` | `git commit --allow-empty --no-verify -m M` in the task worktree; the new commit is `C`. |
| `the task was finished with sf finish` | `sf review ID`, `sf approve ID`, then `sf finish ID` ran in the main checkout. The task branch is gone. |
| `I run "C" in the main checkout` | Runs command `C` (split on spaces, placeholders filled in) from the main checkout. |
| `the exit status is N` | Exit status of the step under test. |
| `the commit message is "M"` | `git log -1 --format=%B` of the new commit, with the trailing newline removed, equals `M` with placeholders read as patterns. |
| `LID is 8 lowercase hexadecimal digits` | `LID` matches `^[0-9a-f]{8}$`. |
| `git reads the trailers of the commit as "T"` | `git log -1 --format=%(trailers:only,unfold)`, trailing blank lines removed, equals `T`. |
| `the two commits have different Lore-ids` | The `Lore-id` values of the last two commits differ. |
| `the output ends with the lines "X"` | `X` split at each `\n` gives lines; the last stdout lines equal them, in order, with placeholders filled in. No other `LORE:` line is in stdout. |
| `the output has no line containing "X"` / `starting with "X"` | No stdout line contains / starts with `X`. |
