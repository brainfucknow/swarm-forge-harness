---
id: tighten-worker-permissions-9504d91
created: 2026-10-31
---

# Tighten worker permissions, start directory and tool versions

From an external review the operator accepted, three small fixes: (1) the Copilot backend always passes --allow-all-tools although the README says headless permissions come from the user; make it an explicit configuration choice. (2) Workers start in the repository root and rely on the prompt to move into the task worktree; create and validate the worktree and start them there. (3) The engineering rules require the latest upstream tools, but sf tool ensure reuses an existing checkout unchanged; resolve tools to explicit versions or SHAs, record them with verification evidence, and make upgrades deliberate.
