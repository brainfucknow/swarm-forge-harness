---
id: fail-ci-when-mutation-7b7d47b
created: 2026-10-28
---

# Fail CI when mutation manifests are committed

The hardener committed gherkin-mutator manifest blocks into 15 feature files against the operator's rule that manifests stay out of the tree. Add a CI check that fails when a tracked .feature file contains a gherkin-mutator manifest block or anything under .metrics/ is tracked.
