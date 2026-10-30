---
id: trim-code-and-documentation-850b5c5
created: 2026-10-30
---

# Trim code and documentation, second pass

Second pass of 'Trim code and documentation to essentials'. Remove what roles do not need, keeping behaviour unchanged. Candidates the orchestrator measured: (1) swarmforge/languages/javascript.md sections 'Testing sf-js itself', 'Tool compatibility checks' and 'Recommendations' (maintainer notes, ~70 of 211 lines); (2) swarmforge/handoff-protocol.md 'Records' section (the SwarmForge-* trailer format only sf writes, ~77 lines) and the 'When changing cache transport' real-tool checklist in Sharing; (3) the 'Behavior' and 'Not covered' sections of the feature READMEs (task_store, metrics_cache, sf_tool, manifest_guard, lore_trailers), which restate the features; keep the placeholder and step-meaning tables. Keep tasks/done/ (sf uses the archives). Also sweep the code under swarmforge/tools/*/src for unused functions and dead code. Ask the operator before removing anything a role or sf still reads.
