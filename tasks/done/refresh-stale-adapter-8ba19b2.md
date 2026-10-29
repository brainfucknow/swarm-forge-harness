---
id: refresh-stale-adapter-8ba19b2
created: 2026-10-29
stage: done
route: coder,architect,qa
---

# Refresh stale adapter ownership after clean checkouts

Use a subagent team to fix the valid review finding in PR #35: https://github.com/brainfucknow/swarm-forge-harness/pull/35#discussion_r4406270685 . A clean branch switch or pull must not make ignored ownership hashes block legitimate generated adapters. Refresh ownership safely while preserving protection for locally edited, unrelated, and symlinked agents. Reproduce with real clean Git checkouts and retained ownership state; use existing regression tests and keep QA/process detail in commits or ignored scratch, with no new tracked QA recipe or framework. Update PR #35 only after validation.

## Records

```text
type: created
from: operator
branch: swarm/refresh-stale-adapter-8ba19b2
commit: 0127a4a
route: coder,architect,qa
```

```text
type: handoff
from: coder
to: architect
branch: swarm/refresh-stale-adapter-8ba19b2
commit: 214515c
files: swarmforge/handoff-protocol.md,swarmforge/roles/cleaner.md,swarmforge/tools/sf/property/sf/flow_property.clj,swarmforge/tools/sf/spec/sf/adapters_spec.clj,swarmforge/tools/sf/spec/sf/cli_spec.clj,swarmforge/tools/sf/spec/sf/flow_spec.clj,swarmforge/tools/sf/spec/sf/task_flow_spec.clj,swarmforge/tools/sf/src/sf/adapters.clj,swarmforge/tools/sf/src/sf/flow.clj,swarmforge/tools/sf/src/sf/task.clj,tasks/done/add-focused-comment-review-to-6b3ec39.md,tasks/done/require-operator-approval-cde2d1f.md,test/acceptance/src/acceptance/store_steps.clj,test/features/task_store/approval_gate.feature,test/features/task_store/record_commits.feature,test/features/task_store/task_history.feature
summary: Refresh stale ownership only for clean tracked canonical adapters
```

```text
type: handoff
from: architect
to: qa
branch: swarm/refresh-stale-adapter-8ba19b2
commit: 828ab0c
files: -
summary: Reviewed narrow tracked-canonical refresh, ownership precedence and regression coverage; no architecture changes needed.
```

```text
type: handoff
from: qa
to: done
branch: swarm/refresh-stale-adapter-8ba19b2
commit: 17f768d
files: -
summary: Verified clean checkout and pull refresh, preservation controls and required source quality gates
```
