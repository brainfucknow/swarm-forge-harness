---
id: reduce-permanent-process-254484f
created: 2026-10-28
stage: done
route: specifier,coder,architect,qa
---

# Reduce permanent process artifacts produced by roles

If we look at the roles from the point of view that we do not want so much fluff added to any repository that uses these roles, what can be done? It is OK to add fluff to commits, but having lots of markdown nonsense or huge amount of nonsense code is kind of bad. Accepted direction: minimize permanent repository footprint; keep task plans, QA procedures, reports and verification detail in commits or ignored scratch files; retain tracked documentation only for lasting user or maintainer needs; use existing code and test structures where clear; a review may conclude with no changes. Make a consistent separate role-policy change, not part of either approved defect fix.

## Records

```text
type: created
from: operator
branch: swarm/reduce-permanent-process-254484f
commit: a513fbe
route: specifier,coder,architect,qa
```

```text
type: review
from: specifier
commit: e2b16e8
files: -
summary: Role-policy scope and manual QA: specification commit e2b16e8f98117481f86cff1dd7e6fbd3b3e2cf29
```

```text
type: approval
from: operator
```

```text
type: handoff
from: specifier
to: coder
branch: swarm/reduce-permanent-process-254484f
commit: e2b16e8
files: -
summary: specifier work complete
```

```text
type: handoff
from: coder
to: architect
branch: swarm/reduce-permanent-process-254484f
commit: bdf0a97
files: .claude/agents/cleaner.md,.claude/agents/coder.md,.claude/agents/hardener.md,.claude/agents/qa.md,.claude/agents/specifier.md,.codex/agents/cleaner.toml,.codex/agents/coder.toml,.codex/agents/hardener.toml,.codex/agents/qa.toml,.codex/agents/specifier.toml,.github/agents/cleaner.agent.md,.github/agents/coder.agent.md,.github/agents/hardener.agent.md,.github/agents/qa.agent.md,.github/agents/specifier.agent.md,README.md,swarmforge/constitution/articles/engineering.md,swarmforge/constitution/articles/project.md,swarmforge/constitution/articles/workflow.md,swarmforge/handoff-protocol.md,swarmforge/roles/architect.md,swarmforge/roles/cleaner.md,swarmforge/roles/coder.md,swarmforge/roles/hardener.md,swarmforge/roles/orchestrator.md,swarmforge/roles/qa.md,swarmforge/roles/specifier.md,swarmforge/tools/sf/property/sf/flow_property.clj,swarmforge/tools/sf/spec/sf/cli_spec.clj,swarmforge/tools/sf/spec/sf/flow_spec.clj,swarmforge/tools/sf/spec/sf/task_flow_spec.clj,swarmforge/tools/sf/src/sf/flow.clj,swarmforge/tools/sf/src/sf/task.clj,tasks/done/require-operator-approval-cde2d1f.md,tasks/done/review-harness-against-dea7cbc.md,test/acceptance/src/acceptance/store_steps.clj,test/features/lore_trailers/README.md,test/features/task_store/approval_gate.feature,test/features/task_store/record_commits.feature,test/features/task_store/task_history.feature
summary: Make artifacts and verification proportional; QA owns audited recipe cleanup
```

```text
type: handoff
from: architect
to: qa
branch: swarm/reduce-permanent-process-254484f
commit: 03495a0
files: -
summary: Reviewed consistent proportional policy and recoverable criteria; QA can consolidate unique checks and remove redundant recipes.
```

```text
type: handoff
from: qa
to: done
branch: swarm/reduce-permanent-process-254484f
commit: db07234
files: swarmforge/constitution/articles/handoffs.md,swarmforge/handoff-protocol.md,swarmforge/languages/clojure.md,swarmforge/languages/javascript.md,swarmforge/tools/javascript/features/README.md,swarmforge/tools/javascript/qa/sf-js.md,test/features/lore_trailers/README.md,test/features/manifest_guard/README.md,test/features/metrics_cache/README.md,test/features/sf_tool/README.md,test/features/task_store/README.md,test/qa/clj_mutate_note.md,test/qa/docs_cleanup.md,test/qa/finish_resume.md,test/qa/lore_trailers.md,test/qa/manifest_guard.md,test/qa/metrics_cache.md,test/qa/sf_tool.md,test/qa/speclj_examples.md,test/qa/task_store.md
summary: Removed ten redundant QA recipes, retained concise compatibility checks and repaired references; manual policy and focused checks pass.
```
