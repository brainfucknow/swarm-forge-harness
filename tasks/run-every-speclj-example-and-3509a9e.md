---
id: run-every-speclj-example-and-3509a9e
created: 2026-10-28
---

# Run every speclj example, and check that none is skipped

Some speclj examples never run: an it inside a let that holds several it forms is not registered. Fix the two remaining blocks (swarmforge/tools/sf/spec/sf/flow_spec.clj describe "commits", record_spec.clj describe "resolve"), and add a check that runs in CI and fails when a spec file has a let around several it or context forms, for example a spec next to architecture_spec.
