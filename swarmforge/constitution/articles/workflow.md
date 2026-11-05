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

- `sf init` and `sf start` install the commit-msg hook. In task worktrees it supplies a missing byline from the current stage (`SWARMFORGE_ROLE` overrides), placing it before the final Git trailer block or appending it when no block exists. It refuses commits with an unknown role or reserved `SwarmForge-*` trailers. Do not skip hooks.
- If `sf start` prints `HOOK:`, prepare the message under `./tmp/`, run `sf commit-msg <role> <file>`, then `git commit -F <file>`. This supplies the byline and Lore ID without the hook.

## Lore Trailers
- Roles record decision context and verification as [Lore](https://github.com/Ian-stetsenko/lore-protocol) git trailers in the last paragraph of a commit message: `Constraint`, `Rejected` (`<alternative> | <reason>`), `Confidence`, `Scope-risk`, `Reversibility`, `Directive`, `Tested`, `Not-tested`, `Supersedes`, `Depends-on`, `Related`. Keep the byline above that block. Write one trailer per line; fold a long value onto a next line that starts with a space.
- When that block has a Lore trailer and no `Lore-id`, the hook adds `Lore-id: <8 random hex digits>` as its first line. Do not write a `Lore-id` yourself; without the hook, `sf commit-msg` adds it.
- Record every specification departure and non-obvious trade-off on the commit making it: `Constraint: <limit>`, `Rejected: <alternative> | <reason>`, or `Directive: <instruction for later roles>`. Record verification with `Tested` and `Not-tested`. Chat alone does not preserve these decisions; `sf` does not enforce their content.

## Temporary Files
- Use `./tmp/` in the task worktree for temporary files; do not use `/tmp`.
- Parse and dry-check into `./tmp/…`.
- Do not commit `./tmp/`.

## Repository Footprint
- Keep task plans, manual QA procedures, review reports, and verification detail in commits or ignored scratch files. Track documentation only when it serves a lasting user or maintainer need. Preserve required `sf` task files and history.
- Reuse existing code and test structures. Add a framework, generator, runner, abstraction, or permanent file only for behavior that existing structures cannot express clearly.
- A review may conclude with no changes. Record its findings and evidence in a commit (`--allow-empty` when needed) and handoff; do not invent edits or reports to justify a stage.

## Announcements
- Do not add role bylines to chat messages or task records; `sf` records the role.

## Failure Conditions
- If `sf` reports an error, read it and repair the cause. Do not work around `sf` by editing task files, record commits, or task branches directly.
- If a required tool cannot be installed in this environment, say so in your report and stop; do not substitute a homegrown check.
