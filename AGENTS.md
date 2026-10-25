# Agent instructions

This repository is a SwarmForge pack: a pipeline of AI roles that pass work
through git-bug tasks. Read `swarmforge/README.md` for the model.

- The operator's session is the orchestrator and plays the `session` roles.
  Follow `swarmforge/roles/orchestrator.md` when asked to swarm, specify, or
  forge a feature, or when a task id is mentioned.
- Subagent roles are defined in `.codex/agents/*.toml`, `.claude/agents/*.md`,
  and `.github/agents/*.agent.md`. Delegate to them by role name; do not do
  their work in the session.
- All task bookkeeping goes through `swarmforge/bin/sf`. Never run `git bug`
  on a task directly and never edit labels or comments by hand.
- Roles that need a decision stop and ask in chat. Do not guess.

Do not test the text of prompts with an automated unit or acceptance test.
Constitution articles, role prompts, and adapter bodies are not production
behavior to pin with string checks. Test `sf` behavior instead
(`test/sf_test.sh`).
