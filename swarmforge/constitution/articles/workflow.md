# Workflow Rules

## Task Worktrees
- Every task has one branch, `swarm/<task-id>`, and one worktree, `.worktrees/<task-id>`. `sf start <id>` creates or finds it and prints `DIR:`. Work only in that directory.
- The main checkout stays on the project's base branch. Do not commit task work there; `sf finish` merges the task branch when the pipeline is done.
- Do not inspect, diff, merge, or base work on another task's branch unless the task or the operator names it.
- Every role on a task works on the same branch in sequence. Build on the commits already there; do not reset, rebase, or rewrite them.
- If the task worktree is missing or on the wrong branch, stop and report instead of working somewhere else.

## Commit Messages
- Include your role byline in every git commit message in this form: `By <role>.`
- Example:

```text
Implement handoff validation

By coder.
```

- `sf init` and `sf start` install a `commit-msg` hook that appends `By <role>.` when it is missing, using the task's current stage as the role. Do not skip hooks (`--no-verify`). If `sf start` prints `HOOK:`, the hook could not be installed and the byline is yours to write.

## Temporary Files
- Use `./tmp/` in the task worktree for temporary files; do not use `/tmp`.
- Parse and dry-check into `./tmp/…`.
- Do not commit `./tmp/`.

## Announcements
- Do not add role bylines to chat messages or task comments; `sf` records the role.

## Failure Conditions
- If `sf` reports an error, read it and repair the cause. Do not work around `sf` by editing git-bug data or labels directly.
- If a required tool cannot be installed in this environment, say so in your report and stop; do not substitute a homegrown check.
