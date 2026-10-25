# SwarmForge pack

This directory is a SwarmForge pack installed into the project. It runs a
pipeline of AI roles as harness agents in Claude Code, Codex, or Copilot CLI.
Roles pass work through git-bug tasks stored in this repository.

```text
operator ──► session: orchestrator + specifier ──► subagents: coder → cleaner → architect → hardener → qa
                        │ git-bug task, branch swarm/<id>, worktree .worktrees/<id>
                        └─ sf review ─► operator approves ─► sf approve ─► … ─► sf finish
```

| File | Purpose |
|---|---|
| `roles.conf` | Active roles in pipeline order, each with a model tier and whether it runs in the session or as a subagent. |
| `tiers.conf` | Model per tier per harness. |
| `packs/*.conf` | Presets for `roles.conf`. |
| `constitution.md`, `constitution/articles/` | Rules every role obeys. |
| `roles/*.md` | What each role owns, verifies, and hands off. `orchestrator.md` is the session's own role. |
| `bin/sf` | The only writer of task state. `sf --help` lists the commands. |
| `bin/sf-cs`, `languages/csharp.md` | C# tool wrapper: tests, coverage, CRAP, DRY, mutation, each scoped to the task's changes. |
| `handoff-protocol.md` | Task labels, records, and lifecycle. |

Prerequisites: `git`, `bash`, and [`git-bug`](https://github.com/git-bug/git-bug)
on PATH. Run `swarmforge/bin/sf init` once per clone.

Start a task from the harness with `/swarm <feature request>`, or by hand:

```sh
sf new "Add login" -m "Users log in with email and password."   # prints the id
sf start <id>      # worktree and task text; the session plays the specifier
sf review <id>     # specifier stops for approval
sf approve <id>    # operator; moves the task to the coder
sf ls              # id, stage, needs, title
sf finish <id>     # after stage:done: merge, close
```

Without harness subagents, `sf run <id>` dispatches each stage to a headless
`claude`, `codex`, or `copilot` process until the task is done or needs the
operator.

After editing `roles.conf` or `tiers.conf`, run `sf adapters` to regenerate the
agent files under `.claude/agents/`, `.codex/agents/`, and `.github/agents/`.
