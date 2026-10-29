---
id: refresh-stale-adapter-8ba19b2
created: 2026-10-29
---

# Refresh stale adapter ownership after clean checkouts

Use a subagent team to fix the valid review finding in PR #35: https://github.com/brainfucknow/swarm-forge-harness/pull/35#discussion_r4406270685 . A clean branch switch or pull must not make ignored ownership hashes block legitimate generated adapters. Refresh ownership safely while preserving protection for locally edited, unrelated, and symlinked agents. Reproduce with real clean Git checkouts and retained ownership state; use existing regression tests and keep QA/process detail in commits or ignored scratch, with no new tracked QA recipe or framework. Update PR #35 only after validation.
