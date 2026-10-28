---
id: note-the-clj-mutate-test-a27af94
created: 2026-10-28
stage: done
route: specifier,coder,qa
---

# Note the clj-mutate test command for the hardener, and fix a stale QA step

Docs only. (1) The hardener prompt's tool notes should say that clj-mutate run through the default bb spec command lets every mutant survive, because bb resolves the worker's symlinked sources back to the real checkout; use a classpath test command instead. (2) test/qa/sf_tool.md QA-4 expects a CRAP row for crap-score in sfcs.cobertura, but that function moved to sfcore.crap; fix the expected row.

## Records

```text
type: created
from: operator
branch: swarm/note-the-clj-mutate-test-a27af94
commit: 6cb964e
route: specifier,coder,qa
```

```text
type: question
from: specifier
question: Two questions before I write the spec. Item (2) is settled: I ran crap4clj on swarmforge/tools/csharp; crap-score no longer appears (sfcore.crap is not instrumented there), so QA-4 will expect the row parse-cobertura in sfcs.cobertura. The note for item (1) goes in the Mutation Work section of swarmforge/roles/hardener.md (the adapters only point at that file).
  1. clj-mutate keeps its manifest under .metrics/mutate/ of the directory it runs from, and sf start/handoff load and save only the worktree root's. Should the hardener note also say to run clj-mutate from the worktree root? (a) yes, one more sentence in the same note; (b) no, the note covers only the test command.
  2. How concrete should the note be? (a) words only: the default bb --config swarmforge/tools/sf/bb.edn spec test command lets every mutant survive because bb resolves the worker's symlinked bb.edn back to the real checkout, so pass a --test-command that puts the worker's own source and spec directories on the classpath (with --no-coverage, which clj-mutate requires for a custom test command); QA checks the text. (b) the same plus a working example command for the sf tree, which QA runs with clj-mutate on one small sf source file to show that mutants get killed. (a) is simpler; (b) proves the advice but takes the coder and QA longer.
```

```text
type: answer
from: operator
answer: 1a yes, add a sentence: run clj-mutate from the worktree root. 2a words only, no tested example command.
```

```text
type: review
from: specifier
commit: 1d922b5
files: test/qa/clj_mutate_note.md
summary: QA procedure for the hardener's clj-mutate note and the QA-4 parse-cobertura row
```

```text
type: approval
from: operator
```

```text
type: handoff
from: specifier
to: coder
branch: swarm/note-the-clj-mutate-test-a27af94
commit: 1d922b5
files: -
summary: specifier work complete
```

```text
type: handoff
from: coder
to: qa
branch: swarm/note-the-clj-mutate-test-a27af94
commit: 754391f
files: swarmforge/roles/hardener.md,test/qa/sf_tool.md
summary: hardener clj-mutate note added; QA-4 expects parse-cobertura
```

```text
type: handoff
from: qa
to: done
branch: swarm/note-the-clj-mutate-test-a27af94
commit: 9851d55
files: -
summary: QA passed: clj-mutate note and QA-4 parse-cobertura row verified
```
