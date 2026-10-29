---
id: require-operator-approval-cde2d1f
created: 2026-10-28
stage: done
route: specifier,coder,architect,qa
---

# Require operator approval before specification handoff

Create teams to fix the issues from the report and findings, use the appropriate amount of roles. Confirmed defect: direct specifier handoff advances to implementation without operator approval, including after rejection. Enforce the approval invariant while preserving approved transitions and routes without a specifier.

## Records

```text
type: created
from: operator
branch: swarm/require-operator-approval-cde2d1f
commit: cc7361f
route: specifier,coder,architect,qa
```

```text
type: review
from: specifier
commit: b0a527f
files: test/features/task_store/approval_gate.feature,test/qa/approval_gate.md
summary: Approve specification transitions only through reviewed operator approval
```

```text
type: approval
from: operator
```

```text
type: handoff
from: specifier
to: coder
branch: swarm/require-operator-approval-cde2d1f
commit: b0a527f
files: -
summary: specifier work complete
```

```text
type: handoff
from: coder
to: architect
branch: swarm/require-operator-approval-cde2d1f
commit: debcc68
files: swarmforge/handoff-protocol.md,swarmforge/tools/sf/property/sf/flow_property.clj,swarmforge/tools/sf/spec/sf/cli_spec.clj,swarmforge/tools/sf/spec/sf/flow_spec.clj,swarmforge/tools/sf/spec/sf/task_flow_spec.clj,swarmforge/tools/sf/src/sf/flow.clj,swarmforge/tools/sf/src/sf/task.clj,test/acceptance/src/acceptance/store_steps.clj,test/features/task_store/record_commits.feature,test/features/task_store/task_history.feature
summary: Enforce reviewed operator approval and preserve valid route transitions
```

```text
type: handoff
from: architect
to: qa
branch: swarm/require-operator-approval-cde2d1f
commit: adb35ce
files: -
summary: Reviewed stage-based approval, commit freshness and route compatibility; no further architecture changes needed.
```

```text
type: handoff
from: qa
to: done
branch: swarm/require-operator-approval-cde2d1f
commit: 2877820
files: -
summary: Approved CLI QA passes; official CRAP max7.0 and DRY clean, existing suites verified.
```
