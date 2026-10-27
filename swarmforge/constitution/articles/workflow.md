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

- `sf init` and `sf start` install a `commit-msg` hook that adds `By <role>.` when it is missing, using the task's current stage as the role (`SWARMFORGE_ROLE` overrides it). When the message ends in a trailer block (the last paragraph, as `git interpret-trailers --parse` reads it), the hook puts the byline in its own paragraph just before that block, so git still reads the trailers; otherwise it appends the byline at the end. It acts only on commits in task worktrees, and refuses a task commit when it cannot find the role, or when its trailer block has a `SwarmForge-*` trailer (only `sf` writes those). Do not skip hooks (`--no-verify`). If `sf start` prints `HOOK:`, the hook could not be installed: write the message to a file under `./tmp/`, run `swarmforge/bin/sf commit-msg <role> <file>` on it, and commit with `git commit -F <file>`. That adds the byline and the `Lore-id` as the hook would.

## Lore Trailers
- Roles record decision context and verification as [Lore](https://github.com/Ian-stetsenko/lore-protocol) git trailers in the last paragraph of a commit message: `Constraint`, `Rejected` (`<alternative> | <reason>`), `Confidence`, `Scope-risk`, `Reversibility`, `Directive`, `Tested`, `Not-tested`, `Supersedes`, `Depends-on`, `Related`. Write one trailer per line; fold a long value onto a next line that starts with a space.
- When that block has a Lore trailer and no `Lore-id`, the hook adds `Lore-id: <8 random hex digits>` as its first line. Do not write a `Lore-id` yourself; without the hook, `sf commit-msg` adds it.
- Trailers are the record for what does not need to live as a file: departures from the specification, rejected alternatives, and what was and was not verified. `sf` does not check them; each role prompt says which ones it writes.

## Temporary Files
- Use `./tmp/` in the task worktree for temporary files; do not use `/tmp`.
- Parse and dry-check into `./tmp/…`.
- Do not commit `./tmp/`.

## Announcements
- Do not add role bylines to chat messages or task records; `sf` records the role.

## Failure Conditions
- If `sf` reports an error, read it and repair the cause. Do not work around `sf` by editing task files, record commits, or task branches directly.
- If a required tool cannot be installed in this environment, say so in your report and stop; do not substitute a homegrown check.
