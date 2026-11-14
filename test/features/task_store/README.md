# Task store acceptance features

Executable contracts for task IDs, requests, records, approval, history, startup
briefs, hooks, and safe finishing. Usage and recovery belong in the
[handoff protocol](../../../swarmforge/handoff-protocol.md).

Run `bash test/acceptance.sh` from the repository root, optionally followed
by feature paths. It parses Gherkin, generates entrypoints, and runs every
scenario/example in a fresh scratch Git repository with the real `sf` and
Babashka. See the [step handlers](../../acceptance/src/acceptance/store_steps.clj)
for fixture operations and assertions.

## Placeholders and fixtures

| Text | Meaning |
|---|---|
| `\n`, `\t` | Newline and tab in quoted values or table cells. |
| `$A`, `$B` | Task IDs returned by `sf new`; `$A:hex` is its seven-digit suffix. |
| `$A:created` | Abbreviated SHA of the task's creation record. |
| `$A:created-sha` | Full SHA of the task's creation record. |
| `NEW` | The task created by the command under test. |
| `C1`, `C2`, `T1`, `M1` | Commits named by fixture steps. |
| `L1`, `L2`, `L3` | Hook-generated Lore IDs for the corresponding commits. |
| `ROOT` | Absolute path of the main checkout. |

The initialized fixture starts on `main`, with `swarmforge/`, ignored scratch
directories, no verification commands, and Git identity
`tester <tester@example.com>`. `I ran` sets up prior work; `I run` is the
command whose output and status are checked. Command steps split on spaces
after placeholder expansion; the `with the text` form appends one argument.
Output comparisons omit the final newline. `task "B" is the task that command
created` names the task whose id the last `I ran` printed.

Conflict fixtures commit different contents on both branches; resolution
steps stage or commit the resolved files as named. Squash-merge fixtures
retain the task branch and worktree until an explicit removal step. These
operations test recovery; they are not instructions for bypassing `sf`.
