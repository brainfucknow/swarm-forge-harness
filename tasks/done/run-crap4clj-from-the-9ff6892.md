---
id: run-crap4clj-from-the-9ff6892
created: 2026-10-28
stage: done
route: specifier,coder,qa
---

# Run crap4clj from the repository root

The crap4clj wrapper run from the repository root (crap4clj --source-root swarmforge/tools/sf/src) adds '-s test', picks up the root test/ directory and fails loading test/acceptance; passing the sibling spec dir still runs 0 examples because the speclj runner looks for ./spec in the current directory. The operator chose: the wrapper runs crap4clj in the directory that holds the first source root, with the source roots made relative to it, so the result, target/ and .metrics/ are exactly as when run from that directory. Split from 'Let sf finish resume after a merge conflict, and run crap4clj from the repo root'.

## Records

```text
type: created
from: operator
branch: swarm/run-crap4clj-from-the-9ff6892
commit: 76cbfa5
route: specifier,coder,qa
```

```text
type: review
from: specifier
commit: aa09932
files: test/features/sf_tool/README.md,test/features/sf_tool/crap4clj_coverage.feature,test/features/sf_tool/crap4clj_directory.feature,test/qa/sf_tool.md
summary: Spec: crap4clj wrapper runs in the directory of the first source root
```

```text
type: approval
from: operator
```

```text
type: handoff
from: specifier
to: coder
branch: swarm/run-crap4clj-from-the-9ff6892
commit: aa09932
files: -
summary: specifier work complete
```

```text
type: handoff
from: coder
to: qa
branch: swarm/run-crap4clj-from-the-9ff6892
commit: a81fe03
files: swarmforge/tools.conf,swarmforge/tools/sf/resources/sf/rewrite-coverage.sh,swarmforge/tools/sf/spec/sf/wrapper_spec.clj,swarmforge/tools/sf/src/sf/wrapper.clj,test/acceptance/src/acceptance/steps.clj
summary: crap4clj wrapper runs in the directory of the first source root, roots made relative
```

```text
type: question
from: qa
question: test/qa/sf_tool.md QA-4 and QA-13 Part A require the CRAP report to have a row for the function crap-score in sfcs.cobertura. No such function exists there: commit b4cdf10 (on main) moved crap-score to sfcore.crap in swarmforge/tools/core, which is outside the csharp source root. The report does list sfcs.cobertura functions (resolve-file, signature-start, method-entry, parse-cobertura, ...). All other QA-1..QA-13 checks, unit/property/acceptance/sf tests, CRAP and DRY pass, and QA-13 Part A's crap.edn from the repo root is byte-for-byte that of the run from the directory. Options: (a) QA counts the check as met by any sfcs.cobertura row (e.g. resolve-file), records that in the trailers, and hands off, with the procedure fixed in a later task; (b) QA edits test/qa/sf_tool.md in this task to name resolve-file instead of crap-score, then hands off; (c) send the task back to the specifier to fix the procedure.
```

```text
type: answer
from: operator
answer: QA fixes QA-13 Part A to name parse-cobertura in sfcs.cobertura (the row the clj-mutate note task uses for QA-4), leaves QA-4 to that task (PR #29), records it in the trailers, and hands off.
```

```text
type: handoff
from: qa
to: done
branch: swarm/run-crap4clj-from-the-9ff6892
commit: 76affb8
files: test/qa/sf_tool.md
summary: QA passed; QA-13 Part A names parse-cobertura, QA-4 left to PR #29
```
