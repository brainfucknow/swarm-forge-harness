# SwarmForge

This directory is SwarmForge installed into the project. It runs AI roles as
harness agents in Claude Code, Codex, or Copilot CLI. Each task takes a route
through the role catalogue, and roles pass work through task branches in this
repository.

```text
operator ──► session: orchestrator ──► subagents on the task's route, e.g.
                        │                feature: specifier → coder → cleaner → architect → hardener → qa
                        │                bugfix:  specifier → coder → qa
                        │ task branch swarm/<id>, worktree .worktrees/<id>
                        └─ sf review ─► operator approves ─► sf approve ─► … ─► sf finish
```

| File | Purpose |
|---|---|
| `roles.conf` | The role catalogue in canonical order, each with a model tier. All run as subagents unless marked `session`. |
| `routes.conf` | Named routes: which roles a task visits. `feature` is the default. |
| `tiers.conf` | Model per tier per harness. |
| `constitution.md`, `constitution/articles/` | Rules every role obeys. |
| `roles/*.md` | What each role owns, verifies, and hands off. `orchestrator.md` is the session's own role. |
| `bin/sf`, `tools/sf/` | The only writer of task state. `sf --help` lists the commands. |
| `bin/sf-cs`, `languages/csharp.md` | C# tool wrapper: tests, coverage, CRAP, DRY, mutation, each scoped to the task's changes. |
| `bin/sf-fs`, `languages/fsharp.md` | F# .NET tests, coverage, compiled-method CRAP and DRY; mutation unsupported. |
| `languages/clojure.md` | How to run clj-mutate, crap4clj and dry4clj on Babashka code. |
| `bin/sf-js`, `languages/javascript.md` | JavaScript and TypeScript tool wrapper: the same commands for Vitest or Jest, oxlint or ESLint complexity with Istanbul coverage, jscpd, and StrykerJS. |
| `tools/core/` | Report readers shared by the language tool wrappers: changed lines, CRAP, jscpd, Stryker. |
| `handoff-protocol.md` | The task branch, task file, record commits, and lifecycle. |
| `verify.conf` | Optional, written by the project: one shell command per line that `sf finish` runs on the merge before it merges, for example `make test`. |

Mutation manifests are a cache on the orphan branch `swarmforge/metrics`, out
of the tree and of every PR. `sf start` copies them into the worktree's
ignored `.metrics/`, and `sf handoff` saves them back.

Prerequisites: `git`, `bash`, and [Babashka](https://babashka.org) (`bb`).
Run `swarmforge/bin/sf init` once per clone.

Start a task from the harness with `/swarm <feature request>`, or by hand:

```sh
sf new "Add login" -m "Users log in with email and password." --route feature   # prints the id
sf start <id>      # worktree and task text, run by the role that owns the stage
sf review <id>     # specifier stops for approval
sf route <id> specifier,coder,qa   # operator narrows the route at the gate
sf approve <id>    # operator; moves the task to the next role on the route
sf ls              # id, stage, needs, route, title
sf finish <id>     # at stage done: verify the merge, merge, remove the branch
```

Without harness subagents, `sf run <id>` dispatches each stage to a headless
`claude`, `codex`, or `copilot` process until the task is done or needs the
operator.

After editing `roles.conf`, `routes.conf`, or `tiers.conf`, run `sf adapters`
to regenerate the agent files under `.claude/agents/`, `.codex/agents/`, and
`.github/agents/`. `sf check` validates the configuration files and the role
prompts; `sf init` and `sf adapters` run it first and refuse a broken setup.

Adapter ownership is recorded in ignored `.swarmforge/adapters.edn` in the
main checkout. Regeneration preserves unrelated agents and
refuses conflicting or locally edited adapters before changing agent files.
