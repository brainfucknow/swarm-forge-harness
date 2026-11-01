# Project Rules

## Project Shape
- This project runs SwarmForge roles as harness agents. `swarmforge/roles.conf` is the role catalogue in canonical order; `swarmforge/routes.conf` names the routes a task can take through it; `swarmforge/tiers.conf` maps each role's tier to a model per harness.
- Project language: set this after installing (for example Babashka, Go, Java, or C#). Until it is set, infer the language from the repository and say which one you inferred in your report.
- Existing code: refactor freely. The opt-in value is `ask before refactoring`, which turns on the engineering article's Existing Code section; a project opts in by changing this line. A missing line means `refactor freely`.

## Local Configuration
- Keep SwarmForge configuration under `swarmforge/`, task worktrees under `.worktrees/`, and task state in the `swarm/*` branches and `tasks/` (`tasks/done/` once a task is done). The orphan branch `swarmforge/metrics` caches mutation manifests. Nothing else is swarm state.
- `.worktrees/` and `./tmp/` are ignored by git and never committed.

## Verification
- After installation, record the application's public interfaces and verification commands here, or link to maintained project documentation that defines them.
- Apply the engineering article's target-based verification and evidence-reuse rules. Fix applicable failures before handoff.
- The architect, hardener, and QA own property-test verification for affected behavior; keep it separate from normal unit, coverage, and mutation runs.

## Handoffs
- Prefer terse, explicit handoffs that report state. Do not include verification transcripts or process narrative in the summary.

## Ownership
- Do not change another role's prompt or workflow ownership without explicit operator direction.
