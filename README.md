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

- `git`, `bash`
- [`git-bug`](https://github.com/git-bug/git-bug) on PATH (release binaries are
  single files)
- One of Claude Code, Codex CLI, or Copilot CLI

## Install into a project

```sh
git clone https://github.com/brainfucknow/swarm-forge-harness
cd your-project
../swarm-forge-harness/install.sh six-pack     # or four-pack, two-pack
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

1. `sf new` creates the task and its branch. The `specifier` subagent, on your
   session's model, writes Gherkin and a QA procedure in `.worktrees/<id>`,
   commits, runs `sf review`, and returns. If the request is ambiguous it runs
   `sf ask` with its questions first and returns; you answer in chat.
2. The orchestrator shows you the committed spec. You say approve, or ask for
   changes.
3. `sf approve` hands the task to the `coder` subagent. Each subagent reads the
   constitution and its role, works on the task branch, commits, and runs
   `sf handoff`. The orchestrator dispatches the next role.
4. A role that is blocked runs `sf ask` and stops. The question appears in chat.
   Your answer goes back with `sf answer` and the same role resumes.
5. After `qa`, the task is `stage:done`. `sf finish` merges the branch into
   your base branch and closes the task.

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

## Model tiers

`swarmforge/roles.conf` (six-pack default):

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
turn at the cost of a fuller session context.

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
`swarmforge/languages/csharp.md` for the output formats. The report readers run
on Node, which jscpd needs anyway.

## Layout

```text
install.sh                       copy the pack into a project
swarmforge/
  bin/sf                         git-bug task helper
  bin/sf-cs  tools/csharp/       C# tool wrapper and its report readers
  languages/csharp.md            what each sf-cs command runs and prints
  roles.conf  tiers.conf  packs/ pipeline and models
  constitution.md  constitution/articles/
  roles/                         specifier, coder, cleaner, architect, hardener, qa, orchestrator
  handoff-protocol.md            labels, records, lifecycle
.claude/agents  .codex/agents  .github/agents     generated role agents
.claude/skills/swarm  .agents/skills/swarm        the /swarm entry point
test/sf_test.sh                  behavioral tests for sf (needs git-bug)
```

## Tests

```sh
bash test/sf_test.sh
node --test swarmforge/tools/csharp/csharp.test.mjs
```

The first drives `sf` against a real git-bug in a scratch repository. The
second drives `sf-cs` against fixture reports and a fake `dotnet`. Neither
pins prompt wording.
