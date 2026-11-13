# Agent instructions

This repository runs SwarmForge: AI roles that pass work through task
branches, each task on its own route through the role catalogue. Read
`swarmforge/README.md` for the model.

- The operator's session is the orchestrator. Follow
  `swarmforge/roles/orchestrator.md` when asked to swarm, specify, or forge a
  feature, or when a task id is mentioned.
- Every role, the specifier included, is defined in `.codex/agents/*.toml`,
  `.claude/agents/*.md`, `.github/agents/*.agent.md`, and `.vibe/agents/*.toml`.
  Delegate to the mapped agent names in `swarmforge/roles/orchestrator.md`;
  do not do their work in the session.
- All task bookkeeping goes through `swarmforge/bin/sf`. Never write a
  `SwarmForge-*` trailer by hand.
- Roles that need a decision stop and ask in chat. Do not guess.

## Prose and tests

- Follow the ban on tests of SwarmForge prompt text in the Verification
  section of
  [the engineering article](swarmforge/constitution/articles/engineering.md#verification).
  Test `sf` behavior (`test/sf_test.sh`).
- Write tracked documentation and SwarmForge prompt text in simplified
  technical English: short sentences, active voice, common words, no
  filler. Use the imperative for instructions. Change a generated
  adapter through its template. This rule and the next do not apply to
  code comments, commit messages, Lore trailers, or `sf` records.
- Keep that text dry and to the point. Say each rule once, where it
  belongs. Give no rationale or history that an agent cannot act on.
