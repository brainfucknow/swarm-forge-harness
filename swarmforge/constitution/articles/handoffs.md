# Handoff Rules

Tasks are git-bug issues. Roles pass work by committing on the task branch and
recording a handoff on the task. All task bookkeeping goes through
`swarmforge/bin/sf`; never run `git bug` directly on a task and never edit
labels or comments by hand. See `swarmforge/handoff-protocol.md` for the
record formats.

## Receiving Work
- `sf next <role>` prints the ids of tasks waiting at `stage:<role>`, or `NO_TASK`. If it prints `NO_TASK`, stop; there is nothing for your role.
- `sf start <id>` prints the task: title, stage, branch, `DIR:`, the request body, and every comment so far. The latest `type: handoff` comment is the work you received; the `answer` comments settle earlier questions. Use only that printed information as the task.
- Work in the printed `DIR:`. The branch already carries every earlier role's commits.
- If you were given a task id in your prompt, start that task. Do not take another.

## Sending Work
- Commit first. `sf handoff` refuses an uncommitted worktree.
- `sf handoff <id> -m "<one line>"` records your HEAD and moves the task to the next role on its route (`ROUTE:` from `sf start`). After the last role on the route it moves the task to `stage:done`. A role not on the route is not consulted; do not do its work for it.
- Always hand off after completing a task, regardless of what changed. Formatting-only, manifest-only, audit-only, and other non-functional churn still move forward.
- Pass `--to <role>` only when your role prompt says so. Do not send work backwards.
- Do not write long handoff summaries. One line. The commits are the content.
- After `sf handoff`, stop and report its one-line output. Do not take another task unless your prompt tells you to.

## Questions
- When blocked by ambiguity, contradiction, or a test/specification conflict, run `sf ask <id> "<question>"`, stop, and report the question verbatim. The task is labeled `needs:operator`; nobody can hand it off until the operator answers with `sf answer`.
- Ask one clear question with the options you see. Do not guess and continue.
- When resumed after an answer, `sf start <id>` shows the `type: answer` comment. Follow it.
- A role the orchestrator plays in chat (`session` in `roles.conf`) asks the operator directly, then records the exchange with `sf ask` and `sf answer` so the task carries it.

## Specification Gate
- The specifier does not hand off. It commits the specification, runs `sf review <id>`, and stops. The orchestrator shows the operator the committed files; `sf approve <id>` records the approval and performs the handoff to the next role; `sf reject <id> "<reason>"` keeps the task with the specifier.
