---
id: run-crap4clj-from-the-9ff6892
created: 2026-10-28
---

# Run crap4clj from the repository root

The crap4clj wrapper run from the repository root (crap4clj --source-root swarmforge/tools/sf/src) adds '-s test', picks up the root test/ directory and fails loading test/acceptance; passing the sibling spec dir still runs 0 examples because the speclj runner looks for ./spec in the current directory. The operator chose: the wrapper runs crap4clj in the directory that holds the first source root, with the source roots made relative to it, so the result, target/ and .metrics/ are exactly as when run from that directory. Split from 'Let sf finish resume after a merge conflict, and run crap4clj from the repo root'.
