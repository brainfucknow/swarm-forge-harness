---
id: fail-ci-when-mutation-7b7d47b
created: 2026-10-28
stage: done
route: specifier,coder,qa
---

# Fail CI when mutation manifests are committed

The hardener committed gherkin-mutator manifest blocks into 15 feature files against the operator's rule that manifests stay out of the tree. Add a CI check that fails when a tracked .feature file contains a gherkin-mutator manifest block or anything under .metrics/ is tracked.

## Records

```text
type: created
from: operator
branch: swarm/fail-ci-when-mutation-7b7d47b
commit: 1b87104
route: specifier,coder,qa
```

```text
type: review
from: specifier
commit: d53c732
files: test/features/manifest_guard/README.md,test/features/manifest_guard/manifest_guard.feature,test/qa/manifest_guard.md
summary: CI manifest guard: test/no_manifests.sh spec and QA procedure
```

```text
type: approval
from: operator
```

```text
type: handoff
from: specifier
to: coder
branch: swarm/fail-ci-when-mutation-7b7d47b
commit: d53c732
files: -
summary: specifier work complete
```

```text
type: handoff
from: coder
to: qa
branch: swarm/fail-ci-when-mutation-7b7d47b
commit: 5285adf
files: .github/workflows/test.yml,test/acceptance/src/acceptance/generate.clj,test/acceptance/src/acceptance/guard_steps.clj,test/acceptance/src/acceptance/support.clj,test/no_manifests.sh,test/no_manifests_test.sh
summary: CI manifest guard: test/no_manifests.sh, its unit tests, acceptance steps, workflow steps
```

```text
type: handoff
from: qa
to: done
branch: swarm/fail-ci-when-mutation-7b7d47b
commit: 05d3fd3
files: -
summary: QA verified the CI manifest guard: QA procedure, tests, CRAP and DRY pass
```
