# sf tool acceptance features

Gherkin for `sf tool` (task 8414dd5). These features are the specification.
`test/sf_tool_test.sh` runs them: one check per scenario and example row,
labelled with the scenario name, for example `sf tool wrappers 1: dry4clj`.
Each check runs the real `sf` in a scratch git repository with `swarmforge/`
copied in and a fake `bb` on PATH. The real tools and GitHub clones are used
only in the QA procedure (`../../qa/sf_tool.md`).

## Placeholders

| Text | Meaning |
|---|---|
| `MAIN` | The scratch project's main checkout (the repository root). |
| `SRC` | The tool source directory: `SWARMFORGE_TOOL_SRC` when set, else `MAIN/.swarmforge/tools/<repo>`. |
| `CFG` | `MAIN/.swarmforge/tools/crap4clj-coverage/bb.edn`. |

## Step meanings

| Step | Meaning |
|---|---|
| `a project with swarmforge installed` | A scratch git repository on `main` whose first commit holds a copy of `swarmforge/`. Commands run with `swarmforge/bin` on PATH. |
| `a fake bb on PATH` | A `bb` that prints each argument on its own line and exits 0. |
| `a tool source whose bb.edn defines the tasks "A B"` | `SWARMFORGE_TOOL_SRC` names a scratch directory whose `bb.edn` has a `:tasks` map with exactly those task keys. |
| `the tool source has the namespace "N"` | The tool source has `src/<N as a path>.clj`. |
| `a tool source without bb.edn` | `SWARMFORGE_TOOL_SRC` names a scratch directory with no `bb.edn`. |
| `no tool source override` | `SWARMFORGE_TOOL_SRC` is unset. |
| `the directory "D" exists without bb.edn` | `D` exists, holds a `README.md`, and has no `bb.edn`. |
| `an earlier install left a wrapper for "T"` | An executable `MAIN/.swarmforge/bin/T` exists before the step under test. |
| `"C" ran in the main checkout` | Command `C` ran from `MAIN` and exited 0. Its output is not checked. |
| `I run "C" in the <checkout>` | Runs command `C` (split on spaces) from the main checkout or the task worktree. |
| `I run the T wrapper with "ARGS"` | Runs `MAIN/.swarmforge/bin/T` from `MAIN` with `ARGS`, split on spaces. An empty value passes no arguments. |
| `I run the sf-cs wrapper in the <place>` | Runs `MAIN/.swarmforge/bin/sf-cs` with no arguments from that directory. `directory outside git` is a scratch directory that is not inside any git repository. |
| `bb received "ARGS"` | The fake bb's arguments, joined with single spaces, equal `ARGS` with the placeholders filled in. |
| `bb received "ARGS" and the coverage command "CMD"` | The fake bb's arguments are the words of `ARGS`, then `--coverage-command`, then one argument equal to `CMD` with the placeholders filled in. |
| `clj is present/absent on PATH` | A fake `clj` that exits 0 is, or is not, on PATH. No real `clj` can be found. |
| `deps.edn is present/absent` | Whether `MAIN/deps.edn` exists (it holds `{}`). |
| `the directories "A B" exist` | Those directories exist under `MAIN`. `spec` and `test` exist only when listed. |
| `the file "F" exists` / `mentions "X"` | `F` is a file; `X` is a substring of it. |
| `there is no wrapper for "T"` | `MAIN/.swarmforge/bin/T` does not exist. |
| `"C" in the <checkout> prints "X"` | Command `C` run from that checkout prints the line `X` on stdout. |
| `a task worktree` | `git worktree add` of branch `swarm/t1` at `MAIN/.worktrees/t1`. |
| `a second task worktree without "F"` | Another worktree, `MAIN/.worktrees/t2`, with `F` deleted and the deletion committed. |
| `the task worktree's tools.conf adds the line "L"` | `L` is appended to `swarmforge/tools.conf` in the task worktree and committed there. The main checkout's catalogue is unchanged. |
| `the <checkout>'s "F" prints "X"` | `F` in that checkout is an executable script that prints `X` and exits 0. |
| `the tool "T" is listed: yes/no` | Whether a stdout line starts with `T` followed by spaces. |
| `the exit status is N` | Exit status of the step under test. |
| `the output has the line "X"` / `the output is "X"` | `X` is a whole stdout line / stdout is exactly the line `X`. |
| `the output does not mention "X"` | `X` is not a substring of stdout or stderr. |
| `the error output contains "X"` / `does not mention "X"` | `X` is / is not a substring of stderr. |
