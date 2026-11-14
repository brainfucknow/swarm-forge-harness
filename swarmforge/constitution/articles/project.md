# Project Rules

## Project Shape
- This project runs SwarmForge roles as harness agents. `swarmforge/roles.conf` is the role catalogue in canonical order; `swarmforge/routes.conf` names the routes a task can take through it; `swarmforge/tiers.conf` maps each role's tier to a model per harness.
- Project language: Babashka (Clojure) for `sf` and the tools; Python for `swarmforge/workflows/` (the Mistral Workflows project).
- Existing code: refactor freely. The opt-in value is `ask before refactoring`, which requires the [existing-code refactoring policy](../existing-code.md); a project opts in by changing this line. A missing line means `refactor freely`.

## Local Configuration
- Keep SwarmForge configuration under `swarmforge/`, task worktrees under `.worktrees/`, and task state in the `swarm/*` branches and the `sf finish` merges. The orphan branch `swarmforge/metrics` caches mutation manifests. Nothing else is swarm state.
- `.worktrees/` and `./tmp/` are ignored by git and never committed.

## Verification
- After installation, record the application's public interfaces and verification commands here, or link to maintained project documentation that defines them.
- Apply the engineering article's target-based verification and evidence-reuse rules. Fix applicable failures before handoff.
- Each language guide in `swarmforge/languages/` names a security code scanner and a dependency audit. A project without one adds a line here per absent tool, with its reason:
  - `Security code scanner absent: <reason>`
  - `Dependency audit absent: <reason>`
- The architect, hardener, and QA own property-test verification for affected behavior; keep it separate from normal unit, coverage, and mutation runs.
- Security code scanner absent for `swarmforge/workflows/`: no Python scanner is installed in this project; the sf tools cover the Clojure code.
- Dependency audit absent for `swarmforge/workflows/`: its Python dependencies are pinned in `swarmforge/workflows/uv.lock`; no audit tool is configured.

## Handoffs
- Prefer terse, explicit handoffs that report state. Do not include verification transcripts or process narrative in the summary.

## Ownership
- Do not change another role's prompt or workflow ownership without explicit operator direction.
