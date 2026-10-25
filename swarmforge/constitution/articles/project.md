# Project Rules

## Project Shape
- This project runs SwarmForge roles as harness agents. `swarmforge/roles.conf` lists the active roles in pipeline order; `swarmforge/tiers.conf` maps each role's tier to a model per harness.
- Project language: set this when installing the pack (for example Babashka, Go, or Java). Until it is set, infer the language from the repository and say which one you inferred in your report.

## Local Configuration
- Keep SwarmForge configuration under `swarmforge/`, task worktrees under `.worktrees/`, and task state in git-bug (`refs/bugs/*`). Nothing else is swarm state.
- `.worktrees/` and `./tmp/` are ignored by git and never committed.

## Verification
- Every role except the specifier runs unit tests and acceptance tests before handoff and fixes any failures.
- The architect, hardener, and QA run property tests before handoff when the project has them and fix any failures.

## Handoffs
- Prefer terse, explicit handoffs that report state. Do not include verification transcripts or process narrative in the summary.

## Ownership
- Do not change another role's prompt or workflow ownership without explicit operator direction.
