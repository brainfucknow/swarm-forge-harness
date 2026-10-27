# Agent instructions

This repository runs SwarmForge: AI roles that pass work through task
branches, each task on its own route through the role catalogue. Read
`swarmforge/README.md` for the model.

- The operator's session is the orchestrator. Follow
  `swarmforge/roles/orchestrator.md` when asked to swarm, specify, or forge a
  feature, or when a task id is mentioned.
- Every role, the specifier included, is defined in `.codex/agents/*.toml`,
  `.claude/agents/*.md`, and `.github/agents/*.agent.md`. Delegate to them by
  role name; do not do their work in the session.
- All task bookkeeping goes through `swarmforge/bin/sf`. Never edit a task
  file or write a `SwarmForge-*` trailer by hand.
- Roles that need a decision stop and ask in chat. Do not guess.

Do not test the text of prompts with an automated unit or acceptance test.
Constitution articles, role prompts, and adapter bodies are not production
behavior to pin with string checks. Test `sf` behavior instead
(`test/sf_test.sh`).
