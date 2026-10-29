---
id: use-cost-conscious-copilot-57b7ee9
created: 2026-10-29
stage: done
route: coder,qa
---

# Use cost-conscious Copilot model defaults

Create a PR to update the copilot model settings. Following the AI-credit pricing review, set Copilot frontier and strong tiers to GPT-6 Sol and fast to GPT-6.1 Terra; preserve Claude and Codex settings. Regenerate Copilot adapters using the official generator in an isolated scratch Git repository because sf adapters resolves the main checkout from linked worktrees. Verify the exact Copilot model identifiers against official current CLI documentation. Correct existing README statements that imply Copilot frontier inherits the session model, keeping documentation minimal and avoiding pricing tables, reports or new test scaffolding. Use appropriate existing checks and a coder/QA route for this small configuration change. Author and commit as Codex <codex@openai.com> with per-command identity; do not alter human/global Git config. Leave installer PR content alone.

## Records

```text
type: created
from: operator
branch: swarm/use-cost-conscious-copilot-57b7ee9
commit: 9e54e1a
route: coder,qa
```

```text
type: handoff
from: coder
to: qa
branch: swarm/use-cost-conscious-copilot-57b7ee9
commit: d95cd3e
files: .github/agents/architect.agent.md,.github/agents/cleaner.agent.md,.github/agents/coder.agent.md,.github/agents/hardener.agent.md,.github/agents/qa.agent.md,.github/agents/specifier.agent.md,README.md,swarmforge/tiers.conf
summary: Use verified Sol and Terra Copilot IDs; preserve Claude and Codex configuration
```

```text
type: handoff
from: qa
to: done
branch: swarm/use-cost-conscious-copilot-57b7ee9
commit: 543315d
files: -
summary: Verified Copilot IDs, generated adapters and README consistency; Claude/Codex unchanged, focused checks pass.
```
