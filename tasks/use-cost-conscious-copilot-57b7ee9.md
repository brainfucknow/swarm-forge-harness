---
id: use-cost-conscious-copilot-57b7ee9
created: 2026-10-29
---

# Use cost-conscious Copilot model defaults

Create a PR to update the copilot model settings. Following the AI-credit pricing review, set Copilot frontier and strong tiers to GPT-6 Sol and fast to GPT-6.1 Terra; preserve Claude and Codex settings. Regenerate Copilot adapters using the official generator in an isolated scratch Git repository because sf adapters resolves the main checkout from linked worktrees. Verify the exact Copilot model identifiers against official current CLI documentation. Correct existing README statements that imply Copilot frontier inherits the session model, keeping documentation minimal and avoiding pricing tables, reports or new test scaffolding. Use appropriate existing checks and a coder/QA route for this small configuration change. Author and commit as Codex <codex@openai.com> with per-command identity; do not alter human/global Git config. Leave installer PR content alone.
