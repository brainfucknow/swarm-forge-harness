# SwarmForge

This directory is SwarmForge installed into the project. It runs AI roles as
harness agents in Kilo, Claude Code, Codex, or Copilot CLI. Each task takes a
route through the role catalogue, and roles pass work through task branches in
this repository.

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
| `bin/sf-vb`, `languages/vbnet.md` | VB.NET tests, coverage, compiled-method CRAP and DRY; mutation unsupported. |
| `languages/clojure.md` | How to run clj-mutate, crap4clj and dry4clj on Babashka code. |
| `bin/sf-js`, `languages/javascript.md` | JavaScript and TypeScript tool wrapper: the same commands for Vitest or Jest, oxlint or ESLint complexity with Istanbul coverage, jscpd, and StrykerJS. |
| `bin/sf-rb`, `languages/ruby.md` | Ruby tool wrapper: SimpleCov coverage, CRAP from RuboCop or flog, DRY from jscpd or flay, and optional mutant. |
| `bin/sf-java`, `languages/java.md` | Maven Java 17+ / JUnit 5 tests, JaCoCo coverage, pinned Java CRAP, DRY and differential mutation. |
| `tools/core/` | Report readers shared by the language tool wrappers: changed lines, CRAP, jscpd, Stryker. |
| `handoff-protocol.md` | The task branch, record commits, and lifecycle. |
| `verify.conf` | Optional, written by the project: one shell command per line that `sf finish` runs on the merge before it merges, for example `make test`. |

The [handoff protocol](handoff-protocol.md) covers task state, approval, merge
recovery, and sharing the mutation cache on `swarmforge/metrics`.

Prerequisites: `git`, `bash`, and [Babashka](https://babashka.org) (`bb`). `sf`
needs `bb` 1.12.200 or newer and refuses an older one.
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

## Configuration

After editing `roles.conf`, `routes.conf`, or `tiers.conf`, run `sf adapters`
to regenerate the agent files under `.claude/agents/`, `.codex/agents/`,
`.github/agents/`, and `.kilo/agent/`. `sf check` validates the configuration
files and the role prompts; `sf init` and `sf adapters` run it first and refuse
a broken setup. `sf check`, `sf adapters`, `sf roles`, `sf routes` and `sf tool`
read the checkout they run in; `sf init` and task bookkeeping read the main
checkout's.

Adapter ownership is recorded in ignored `.swarmforge/adapters.edn` in the
checkout where `sf adapters` runs. Regeneration preserves unrelated agents and
refuses conflicting or locally edited adapters before changing agent files.

Catalogue settings:

- `session` makes the orchestrator play a role in chat; `subagent` delegates it.
- `audit` makes the first handoff or review at each commit request self-review
  without recording anything. Complete that review before retrying.
- `gate` makes a read-only subagent that audits each specification after
  `sf review` and before approval; it is not a stage and cannot be on a route.
  The orchestrator shows its report at the gate. `sf run` dispatches the gates
  when a specification it produced waits for approval. `sf dispatch` starts a
  gate without edit permissions: claude in its default permission mode with
  Edit, Write and NotebookEdit disallowed, codex in its read-only sandbox,
  copilot with the write tool denied. The Claude and Copilot gate adapters
  list only reading, search and shell tools. The shell can still write, so
  `sf run` also stops when a gate records, commits or changes the task
  worktree. With the operator's
  consent, the orchestrator sends CRITICAL and HIGH findings back to the
  specifier once per task with `sf reject`. The shipped
  [falsifier](roles/falsifier.md) is a gate; delete its line to turn it off.
- `optional` excludes a role from fallback routes. Include it explicitly in a
  named route, `--route`, or `SWARMFORGE_ROUTE` to enable it; it can also use `audit`.

Optional reviewers keep the usual approval and handoff flow. The `feature`
route includes the security reviewer; drop it per task with `sf route`:

| Role | When to include it | Position |
|---|---|---|
| [Architecture design reviewer](roles/architecture-design-reviewer.md) | Migrations, public APIs, major dependency changes. | After specification approval, before coding; architect still reviews implementation. |
| [Security reviewer](roles/security-reviewer.md) | Authentication, authorization, secrets, untrusted input, external execution. | After hardener, before QA; hardener retains mutation and quality gates. |

```sh
sf new "Migrate public API" -m "..." --route specifier,architecture-design-reviewer,coder,architect,qa
sf route <id> specifier,coder,cleaner,architect,hardener,qa
```
