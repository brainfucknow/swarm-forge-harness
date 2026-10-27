# swarm-forge-harness

A harness-integrated [SwarmForge](https://github.com/brainfucknow/swarm-forge).
The roles are agents you run directly in Claude Code, Codex, or Copilot CLI.
There is no tmux, no daemon, and no web dashboard. Roles talk to each other
through [git-bug](https://github.com/git-bug/git-bug) issues stored in the
project repository, and they talk to you in chat.

## What changed from SwarmForge

| SwarmForge | This harness |
|---|---|
| One tmux session per role, each a long-running CLI. | Roles are harness subagents. The session only orchestrates. |
| Handoff daemon, inbox and outbox files, `roles.tsv`. | A git-bug task per feature: `stage:<role>` label, `type: handoff` comments, one branch. |
| Dashboard approval gate (Attention). | The specifier commits, runs `sf review`, and stops. You approve or reject in chat. |
| Dashboard clarifications. | A role runs `sf ask`, stops, and the question reaches you in chat. `sf answer` resumes it. |
| Per-role worktrees with back-propagation merges. | One worktree per task; roles work in sequence on the same branch, so nothing propagates backwards. |
| `grok`, `codex`, `claude`, `copilot` backends chosen per role. | Model tiers per role: `frontier` (the session's model) for the specifier, `strong` (Opus) and `fast` (Sonnet) for the rest. |

## Prerequisites

- `git`, and `bash` for `install.sh`, the commit-msg hook, and the tool wrappers
- [`git-bug`](https://github.com/git-bug/git-bug) on PATH (release binaries are
  single files)
- [Babashka](https://babashka.org) (`bb`) for `sf`, the C# tool wrapper, and
  the Clojure constitution tools
- One of Claude Code, Codex CLI, or Copilot CLI

## Install into a project

```sh
git clone https://github.com/brainfucknow/swarm-forge-harness
cd your-project
../swarm-forge-harness/install.sh
```

The installer copies `swarmforge/` and the `swarm` skill into the project,
adds one line to `AGENTS.md`, `CLAUDE.md`, and `.github/copilot-instructions.md`,
generates the agent files, and creates the git-bug identity. Commit the result.

Then set the project language in `swarmforge/constitution/articles/project.md`.

## Run

Open the project in your harness and ask:

```text
/swarm Users can log in with email and password; lock the account after five failures.
```

The session becomes the orchestrator:

1. The orchestrator picks a route for the request from `swarmforge/routes.conf`
   (`feature`, `bugfix`, `refactor`, `harden`, `spike`, or any subset of the
   roles in order) and `sf new --route` creates the task and its branch with
   that route on it. The `specifier` subagent, on your
   session's model, writes Gherkin and a QA procedure in `.worktrees/<id>`,
   commits, runs `sf review`, and returns. If the request is ambiguous it runs
   `sf ask` with its questions first and returns; you answer in chat.
2. The orchestrator shows you the committed spec and the route. You say
   approve, ask for changes, or change the route (`sf route <id> ...`).
3. `sf approve` hands the task to the `coder` subagent. Each subagent reads the
   constitution and its role, works on the task branch, commits, and runs
   `sf handoff`. The orchestrator dispatches the next role.
4. A role that is blocked runs `sf ask` and stops. The question appears in chat.
   Your answer goes back with `sf answer` and the same role resumes.
5. After the last role on the route, the task is `stage:done`. `sf finish`
   merges the branch into your base branch and closes the task.

`sf ls` shows every open task with its stage and whether it is waiting on you.
`sf show <id>` prints the whole history. Both work in any clone that has
pulled the git-bug refs (`git bug pull`).

## Harness integration

| Harness | Session entry | Subagent roles | Model tier |
|---|---|---|---|
| Claude Code | `/swarm` from `.claude/skills/swarm/` | `.claude/agents/<role>.md` | `model: opus` or `sonnet` |
| Codex CLI | `$swarm` from `.agents/skills/swarm/` | `.codex/agents/<role>.toml` | `model_reasoning_effort` high / medium / low |
| Copilot CLI | `/swarm` from `.agents/skills/swarm/` | `.github/agents/<role>.agent.md` | `model: claude-opus-5.5` or `claude-sonnet-5.5` |

The agent files are generated from `swarmforge/roles.conf` and
`swarmforge/tiers.conf` by `swarmforge/bin/sf adapters`. Each one is a thin
wrapper that tells the agent to read the constitution and its role prompt, so
prompts live in one place.

Codex: custom agent roles need the multi-agent feature. If `codex` does not
offer the roles, enable it in `~/.codex/config.toml` per the Codex subagents
documentation, or fall back to `sf run <id>` below.

If a harness cannot delegate, `sf dispatch <role> <id>` runs the role in a
headless `claude -p`, `codex exec`, or `copilot -p` session, and
`sf run <id>` loops through the stages until the task is done or waiting on
you. Headless runs need tool permissions you grant yourself; pass them in
`SWARMFORGE_CLAUDE_ARGS`, `SWARMFORGE_CODEX_ARGS`, or `SWARMFORGE_COPILOT_ARGS`.

## Roles, routes, and model tiers

There is no pack to choose. `swarmforge/roles.conf` is the catalogue of roles
in canonical order; each task picks a route through it, so a bug fix can skip
the cleaner, architect, and hardener while a feature visits all six. Named
routes live in `swarmforge/routes.conf`:

| Route | Roles |
|---|---|
| `feature` (default) | specifier, coder, cleaner, architect, hardener, qa |
| `bugfix` | specifier, coder, qa |
| `refactor` | cleaner, architect, hardener |
| `harden` | hardener, qa |
| `spike` | specifier |

The catalogue:

| Role | Tier | Runs as |
|---|---|---|
| specifier | frontier | subagent (your session's model) |
| coder | strong | subagent |
| cleaner | fast | subagent |
| architect | strong | subagent |
| hardener | fast | subagent |
| qa | strong | subagent |

`swarmforge/tiers.conf` maps `strong` to Opus and `fast` to Sonnet for Claude
Code and Copilot, and to reasoning effort for Codex. Edit either file and run
`sf adapters`. Setting a role to `session` instead of `subagent` makes the
orchestrator play it in chat, which suits co-writing a specification turn by
turn at the cost of a fuller session context. Adding `audit` to a role's line
makes its first `sf handoff` at any commit come back with a self-review
checklist instead of a record; the second attempt at the same commit records
it. It is off by default.

## Languages

The constitution's tool table covers Go, Clojure, Java, and C#. For C#,
`swarmforge/bin/sf-cs` wraps the tools so each role runs one command:

| Command | Tool |
|---|---|
| `sf-cs test`, `sf-cs property` | `dotnet test`, split by the `Property` trait |
| `sf-cs coverage` | coverlet Cobertura under `tmp/coverage/` |
| `sf-cs crap` | CRAP per changed method from coverlet's complexity and coverage |
| `sf-cs dry` | jscpd clones touching changed lines |
| `sf-cs mutate` | Stryker.NET on changed files, survivors listed |

Each exits 0 when clean, 1 on a finding, 2 on a usage error. See
`swarmforge/languages/csharp.md` for the output formats. The report readers are
Babashka, like the rest of the SwarmForge family; jscpd itself still runs on
Node.

## Layout

```text
install.sh                       copy SwarmForge into a project
swarmforge/
  bin/sf  tools/sf/              git-bug task helper (Babashka)
  bin/sf-tool  tools.conf        constitution tool installer and catalogue (sf tool ...)
  bin/sf-cs  tools/csharp/       C# tool wrapper and its report readers (Babashka)
  languages/csharp.md            what each sf-cs command runs and prints
  roles.conf  routes.conf  tiers.conf   catalogue, routes, models
  constitution.md  constitution/articles/
  roles/                         specifier, coder, cleaner, architect, hardener, qa, orchestrator
  handoff-protocol.md            labels, records, lifecycle
.claude/agents  .codex/agents  .github/agents     generated role agents
.claude/skills/swarm  .agents/skills/swarm        the /swarm entry point
swarmforge/tools/sf/spec/        Speclj unit specs for sf
test/sf_test.sh                  behavioral tests for sf (needs git-bug)
test/features/  test/qa/         Gherkin acceptance features and QA procedures
test/acceptance.sh  test/acceptance/   acceptance pipeline: generator, runtime, step handlers
test/probe_gitbug.clj            checks that git-bug reads its own label writes
```

## Tests

```sh
bash test/sf_test.sh
bash test/sf_tool_test.sh
bb --config swarmforge/tools/sf/bb.edn spec
bash test/acceptance.sh
bb --config swarmforge/tools/csharp/bb.edn test
```

The first drives `sf` against a real git-bug in a scratch repository, the
second drives `sf tool` against a local tool source and a fake `bb`, the third
runs the Speclj unit specs of `sf`, the fourth runs the Gherkin features in
`test/features/` through `gherkin-parser` (`swarmforge/bin/sf tool ensure
gherkin-parser`) and the generated acceptance tests, and the last drives
`sf-cs` against fixture reports and a fake `dotnet`. None pins prompt wording.
