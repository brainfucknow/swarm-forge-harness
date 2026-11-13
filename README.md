# swarm-forge-harness

A harness-integrated [SwarmForge](https://github.com/brainfucknow/swarm-forge).
Run its roles in Kilo, Claude Code, Codex, or Copilot CLI. Roles pass work
through Git task branches; the operator handles questions and approvals in chat.

## Prerequisites

- `git`, and `bash` for `install.sh`, the commit-msg hook, and the tool wrappers
- [Babashka](https://babashka.org) (`bb`) for `sf`, which needs 1.12.200 or newer
  and refuses an older `bb`, and for the C#, F#, VB.NET and JavaScript tool
  wrappers and the Clojure constitution tools. `install.sh` checks `bb` before
  it changes the project.
- One of Kilo, Claude Code, Codex CLI, or Copilot CLI

## Install into a project

```sh
git clone https://github.com/brainfucknow/swarm-forge-harness
cd your-project
../swarm-forge-harness/install.sh
```

The installer copies the SwarmForge runtime, guidance, and `swarm` skill into
the project, adds instructions to `AGENTS.md`, `CLAUDE.md`, and
`.github/copilot-instructions.md`, generates the agents, and installs the
commit-msg hook. It also writes `swarmforge/.gitignore` so that a project
rule ignoring every `bin/` directory, such as Visual Studio's `[Bb]in/`, does
not drop `swarmforge/bin`. Commit the result.
Development assets stay in this source checkout.

Then set the project language in `swarmforge/constitution/articles/project.md`.

To update, rerun this source checkout's `install.sh`. Runtime, guidance and
skills are refreshed. The project owns `swarmforge/constitution/articles/project.md`
and the shipped top-level `swarmforge/*.conf` files: existing files are kept,
and only missing files receive defaults. `verify.conf` is never installed or
changed. This also applies to older installs without ownership metadata.

A runtime update can require configuration changes. Compare those files with
the defaults in the source checkout, merge the changes your project needs,
then run `swarmforge/bin/sf adapters` to regenerate agents. Invalid preserved
settings fail validation instead of being reset. The installer lists unshipped
payload files for manual review; they may be user files, so it never deletes
them or assumes they were retired upstream.

## Run

Open the project in your harness and ask:

```text
/swarm Users can log in with email and password; lock the account after five failures.
```

For the default `feature` route, the session orchestrates:

1. The specifier commits criteria and independent QA, then stops for your
   approval. You can request changes or adjust the route before approving.
2. Roles implement, review, and verify on the task branch, committing and
   handing off in sequence. Questions stop the task until you answer in chat.
3. `sf finish` verifies the completed merge, merges into the base branch,
   and removes the task branch and worktree.

`sf ls` shows open tasks and what they need from you; `sf show <id>` shows
history. See the [installed guide](swarmforge/README.md) for commands and the
[handoff protocol](swarmforge/handoff-protocol.md) for approval, recovery,
and sharing rules.

## Harness integration

| Harness | Session entry | Subagent roles | Model tier |
|---|---|---|---|
| Kilo | `/swarm` from `.kilo/command/` | `.kilo/agent/<role>.md` | `model:` `provider/model`, or the session model when `inherit` |
| Claude Code | `/swarm` from `.claude/skills/swarm/` | `.claude/agents/<role>.md` | `model: opus` or `sonnet` |
| Codex CLI | `$swarm` from `.agents/skills/swarm/` | `.codex/agents/<role>.toml` | `model_reasoning_effort` high / medium / low |
| Copilot CLI | `/swarm` from `.agents/skills/swarm/` | `.github/agents/<role>.agent.md` | `model: gpt-6.1-sol` or `gpt-6.1-terra` |

`sf adapters` generates agent files from `swarmforge/roles.conf` and
`swarmforge/tiers.conf`. Each agent reads the shared constitution and its role
prompt under `swarmforge/roles/`.

Codex: custom agent roles need the multi-agent feature. If `codex` does not
offer the roles, enable it in `~/.codex/config.toml` per the Codex subagents
documentation, or fall back to `sf run <id>` below.

If a harness cannot delegate, `sf dispatch <role> <id>` runs the role in a
headless `claude -p`, `codex exec`, or `copilot -p` session started in the
task worktree `.worktrees/<id>` (created when missing, refused when it is on
another branch), and `sf run <id>` loops through the stages until the task
is done or waiting on you. Headless runs need tool permissions you grant
yourself; pass them in `SWARMFORGE_CLAUDE_ARGS`, `SWARMFORGE_CODEX_ARGS`, or
`SWARMFORGE_COPILOT_ARGS`. Claude runs with `--permission-mode acceptEdits`
and Codex with `--full-auto`; sf grants Copilot no tool permissions on its
own, so set them there, for example
`SWARMFORGE_COPILOT_ARGS="--allow-all-tools"`.

## Roles, routes, and model tiers

Each task follows a named route from `swarmforge/routes.conf`, or an explicit
subset of the catalogue in `swarmforge/roles.conf` order:

| Route | Roles |
|---|---|
| `feature` (default) | specifier, coder, cleaner, architect, hardener, security-reviewer, qa |
| `bugfix` | specifier, coder, cleaner, qa |
| `refactor` | cleaner, architect, hardener |
| `harden` | hardener, qa |
| `spike` | specifier |

The [role catalogue](swarmforge/roles.conf) assigns tiers and supports
`subagent`, `session`, `audit`, and `optional` settings. See
[configuration](swarmforge/README.md#configuration) for these settings and
the optional reviewers. Model mappings live in
[`swarmforge/tiers.conf`](swarmforge/tiers.conf); regenerate agents with
`sf adapters` after changing roles, routes, or tiers.

## Reference

- [Installed guide](swarmforge/README.md): commands and configuration.
- [Handoff protocol](swarmforge/handoff-protocol.md): task branches and records.
- Language tools: [Clojure](swarmforge/languages/clojure.md),
  [C#](swarmforge/languages/csharp.md), [F#](swarmforge/languages/fsharp.md),
  [VB.NET](swarmforge/languages/vbnet.md),
  [JavaScript/TypeScript](swarmforge/languages/javascript.md),
  [Ruby](swarmforge/languages/ruby.md), [Java](swarmforge/languages/java.md).

## Tests

Run these from the source checkout; fresh consumer installs omit harness tests and fixtures.

```sh
bash test/sf_test.sh
bash test/sf_tool_test.sh
bb --config swarmforge/tools/sf/bb.edn spec
bb --config swarmforge/tools/sf/bb.edn hardening
bb --config swarmforge/tools/sf/bb.edn property
swarmforge/bin/sf tool ensure gherkin-parser
bash test/acceptance.sh
bb --config swarmforge/tools/csharp/bb.edn test
bb --config swarmforge/tools/csharp/bb.edn property
bb --config swarmforge/tools/javascript/bb.edn test
bb --config swarmforge/tools/javascript/bb.edn property
bb --config swarmforge/tools/javascript/bb.edn acceptance
bb --config swarmforge/tools/ruby/bb.edn test
bb --config swarmforge/tools/java/bb.edn spec
bb --config swarmforge/tools/java/bb.edn hardening
bb --config swarmforge/tools/java/bb.edn acceptance
bb --config swarmforge/tools/java/bb.edn property
```

These suites exercise the CLI with scratch Git repositories and test the
language wrappers against fixture reports and fake external tools.
