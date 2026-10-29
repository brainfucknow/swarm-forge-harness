# swarm-forge-harness

A harness-integrated [SwarmForge](https://github.com/brainfucknow/swarm-forge).
The roles are agents you run directly in Claude Code, Codex, or Copilot CLI.
There is no tmux, no daemon, and no web dashboard. Roles talk to each other
through task branches in the project repository, and they talk to you in
chat.

## Prerequisites

- `git`, and `bash` for `install.sh`, the commit-msg hook, and the tool wrappers
- [Babashka](https://babashka.org) (`bb`) for `sf`, the C# and JavaScript tool
  wrappers, and the Clojure constitution tools
- One of Claude Code, Codex CLI, or Copilot CLI

## Install into a project

```sh
git clone https://github.com/brainfucknow/swarm-forge-harness
cd your-project
../swarm-forge-harness/install.sh
```

The installer copies `swarmforge/` and the `swarm` skill into the project,
adds one line to `AGENTS.md`, `CLAUDE.md`, and `.github/copilot-instructions.md`,
generates the agent files, and installs the commit-msg hook. Commit the result.

Then set the project language in `swarmforge/constitution/articles/project.md`.

## Run

Open the project in your harness and ask:

```text
/swarm Users can log in with email and password; lock the account after five failures.
```

For the default `feature` route, the session becomes the orchestrator:

1. The orchestrator picks a route for the request from `swarmforge/routes.conf`
   (`feature`, `bugfix`, `refactor`, `harden`, `spike`, or any subset of the
   roles in order) and `sf new --route` creates the task and its branch with
   that route on it. The `specifier` subagent, on your
   session's model, commits concise criteria and QA, using durable Gherkin
   files when they provide a useful executable contract in `.worktrees/<id>`,
   runs `sf review`, and returns. If the request is ambiguous it runs
   `sf ask` with its questions first and returns; you answer in chat.
2. The orchestrator shows you the committed spec and the route. You say
   approve, ask for changes, or change the route (`sf route <id> ...`).
3. `sf approve` hands the task to the `coder` subagent. Each subagent reads the
   constitution and its role, works on the task branch, commits, and runs
   `sf handoff`. The orchestrator dispatches the next role.
4. A role that is blocked runs `sf ask` and stops. The question appears in chat.
   Your answer goes back with `sf answer` and the same role resumes.
5. After the last role on the route, the task is at stage `done` and its task
   file moves to `tasks/done/`. `sf finish` merges the branch into your base
   branch and removes it.

`sf ls` shows every open task with its stage and whether it is waiting on you.
`sf show <id>` prints the whole history, also after the merge. Both work in
any clone that has the task branch (`git push origin swarm/<id>` and a fetch
move it).

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

## Reference

- [Installed guide](swarmforge/README.md): commands and configuration.
- [Handoff protocol](swarmforge/handoff-protocol.md): task branches and records.
- Language tools: [Clojure](swarmforge/languages/clojure.md),
  [C#](swarmforge/languages/csharp.md),
  [JavaScript/TypeScript](swarmforge/languages/javascript.md).

## Tests

```sh
bash test/sf_test.sh
bash test/sf_tool_test.sh
bb --config swarmforge/tools/sf/bb.edn spec
bb --config swarmforge/tools/sf/bb.edn property
swarmforge/bin/sf tool ensure gherkin-parser
bash test/acceptance.sh
bb --config swarmforge/tools/csharp/bb.edn test
bb --config swarmforge/tools/javascript/bb.edn test
bb --config swarmforge/tools/javascript/bb.edn property
bb --config swarmforge/tools/javascript/bb.edn acceptance
```

These suites exercise the CLI with scratch Git repositories and test the
language wrappers against fixture reports and fake external tools.
