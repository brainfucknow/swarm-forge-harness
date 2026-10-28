# Historical QA: documentation cleanup

This records the completed task "Clean up the docs in this repo". Its
one-time checks of changed files, exact headings and wording are retained
in Git history, rather than treated as current requirements.

The cleanup replaced obsolete git-bug task ids with task titles, removed
completed design plans, and made QA clone the current task branch.

Current references:

- [Task protocol](../../swarmforge/handoff-protocol.md)
- [Pending sharing design](../../docs/spikes/git-task-store/design.md)
- [Task-store acceptance contract](../features/task_store/README.md)
- [Task-store QA](task_store.md)
- [Tool-wrapper QA](sf_tool.md)

For further documentation edits, check that links resolve, current behavior
matches the protocol, and installed guidance remains self-contained.
Do not pin prompt wording with automated tests.
