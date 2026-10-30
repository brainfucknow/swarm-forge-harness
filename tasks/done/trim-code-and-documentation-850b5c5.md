---
id: trim-code-and-documentation-850b5c5
created: 2026-10-30
stage: done
route: cleaner,architect,qa
---

# Trim code and documentation, second pass

Second pass of 'Trim code and documentation to essentials'. Remove what roles do not need, keeping behaviour unchanged. Candidates the orchestrator measured: (1) swarmforge/languages/javascript.md sections 'Testing sf-js itself', 'Tool compatibility checks' and 'Recommendations' (maintainer notes, ~70 of 211 lines); (2) swarmforge/handoff-protocol.md 'Records' section (the SwarmForge-* trailer format only sf writes, ~77 lines) and the 'When changing cache transport' real-tool checklist in Sharing; (3) the 'Behavior' and 'Not covered' sections of the feature READMEs (task_store, metrics_cache, sf_tool, manifest_guard, lore_trailers), which restate the features; keep the placeholder and step-meaning tables. Keep tasks/done/ (sf uses the archives). Also sweep the code under swarmforge/tools/*/src for unused functions and dead code. Ask the operator before removing anything a role or sf still reads.

## Records

```text
type: created
from: operator
branch: swarm/trim-code-and-documentation-850b5c5
commit: 3d6c350
route: cleaner,architect,qa
```

```text
type: question
from: cleaner
question: Candidate (2) would remove the handoff-protocol.md 'Records' section (the key: value record formats). The constitution's handoffs article says 'See swarmforge/handoff-protocol.md for the record formats', and test/features/task_store/task_history.feature mentions 'as in handoff-protocol.md'; the same blocks are printed by sf start/sf show. Options: (a) remove the section and change that pointer in handoffs.md (and the feature comment) to say sf start/sf show print the records; (b) keep the section. Everything else is done and committed: JS guide sections, cache-transport checklist, feature README Behavior/Not covered, unused oxlint/read-report; all suites pass.
```

```text
type: answer
from: operator
answer: (a) remove the Records section of handoff-protocol.md; change the pointer in swarmforge/constitution/articles/handoffs.md and the comment in test/features/task_store/task_history.feature to say sf start and sf show print the records.
```

```text
type: handoff
from: cleaner
to: architect
branch: swarm/trim-code-and-documentation-850b5c5
commit: baf2561
files: swarmforge/constitution/articles/handoffs.md,swarmforge/handoff-protocol.md,swarmforge/languages/javascript.md,swarmforge/tools/javascript/features/README.md,swarmforge/tools/javascript/src/sfjs/oxlint.clj,swarmforge/tools/javascript/test/sfjs/oxlint_test.clj,test/features/lore_trailers/README.md,test/features/manifest_guard/README.md,test/features/metrics_cache/README.md,test/features/sf_tool/README.md,test/features/task_store/README.md,test/features/task_store/task_history.feature
summary: Trimmed docs and unused read-report; Records section removed per operator; all suites pass
```

```text
type: handoff
from: architect
to: qa
branch: swarm/trim-code-and-documentation-850b5c5
commit: 0eb4197
files: -
summary: Trim reviewed: no dangling references, oxlint boundary sound; no changes
```

```text
type: handoff
from: qa
to: done
branch: swarm/trim-code-and-documentation-850b5c5
commit: ea863c8
files: -
summary: QA passed: docs coherent, no dangling links, all suites green
```
