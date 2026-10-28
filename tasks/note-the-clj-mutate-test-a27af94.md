---
id: note-the-clj-mutate-test-a27af94
created: 2026-10-28
---

# Note the clj-mutate test command for the hardener, and fix a stale QA step

Docs only. (1) The hardener prompt's tool notes should say that clj-mutate run through the default bb spec command lets every mutant survive, because bb resolves the worker's symlinked sources back to the real checkout; use a classpath test command instead. (2) test/qa/sf_tool.md QA-4 expects a CRAP row for crap-score in sfcs.cobertura, but that function moved to sfcore.crap; fix the expected row.
