---
id: preserve-unrelated-custom-25ee2da
created: 2026-10-28
stage: done
route: specifier,coder,qa
---

# Preserve unrelated custom agents during adapter generation

Create teams to fix the issues from the report and findings, use the appropriate amount of roles. Confirmed defect: sf adapters and installation delete unrelated custom agents in shared harness directories. Preserve project-owned agents and define collision handling without losing managed role regeneration.

## Records

```text
type: created
from: operator
branch: swarm/preserve-unrelated-custom-25ee2da
commit: bc0c600
route: specifier,coder,qa
```

```text
type: review
from: specifier
commit: abbe27b
files: test/features/adapters/adapters.feature,test/qa/adapters.md
summary: Specify safe regeneration, collision refusal, and legacy adapter updates
```

```text
type: approval
from: operator
```

```text
type: handoff
from: specifier
to: coder
branch: swarm/preserve-unrelated-custom-25ee2da
commit: abbe27b
files: -
summary: specifier work complete
```

```text
type: handoff
from: coder
to: qa
branch: swarm/preserve-unrelated-custom-25ee2da
commit: 7134667
files: swarmforge/README.md,swarmforge/tools/sf/spec/sf/adapters_spec.clj,swarmforge/tools/sf/src/sf/adapters.clj,test/acceptance/src/acceptance/adapter_steps.clj,test/acceptance/src/acceptance/generate.clj
summary: Preserve unrelated agents and preflight managed adapter ownership
```

```text
type: handoff
from: qa
to: done
branch: swarm/preserve-unrelated-custom-25ee2da
commit: 247e1ce
files: swarmforge/tools/sf/spec/sf/adapters_spec.clj
summary: Verify adapter preservation, collision boundaries and required quality gates
```
