---
id: keep-sf-tool-ensure-from-ff06da1
created: 2026-10-28
stage: done
route: specifier,coder,qa
---

# Keep sf tool ensure from writing a broken wrapper

A task worktree whose branch predates main's tools.conf ran sf tool ensure and replaced the shared wrappers in .swarmforge/bin with broken ones (unknown kind 'bbmain', unknown rewrite 'coverage'). It happened twice. ensure must check the kind and rewrite before it writes anything, and never replace a working wrapper with one it cannot build.

## Records

```text
type: created
from: operator
branch: swarm/keep-sf-tool-ensure-from-ff06da1
commit: 65c7c5a
route: specifier,coder,qa
```

```text
type: question
from: specifier
question: 1. When ensure fails on a missing entry point (no bb.edn, no task, no namespace, unreadable bb.edn), today it deletes the existing wrapper so require reports MISSING (settled in task aca0457: entry_point_guard 1 and 4, QA-8). That is how an older branch's catalogue (e.g. dry4clj kind bb, task dry4clj) removes main's working wrapper. Options: (a) any failing ensure leaves an existing wrapper byte-for-byte unchanged and exits 1 with the same error; require keeps reporting OK [recommended]; (b) keep deleting on an entry-point failure and only guard unknown kind/rewrite. 2. Scope of the up-front check: (a) ensure checks the kind and rewrite of the tool and of every tool in its needs chain before it writes anything: no wrapper, no clone, no coverage config when any is unknown [recommended]; (b) check only the named tool, so its needs may still be installed first. 3. Should ensure in a task worktree also refuse when its catalogue line for the tool differs from the main checkout's, so an older branch cannot replace a wrapper with an older but buildable one (e.g. gherkin-mutator without the gherkin rewrite)? (a) no, out of scope [recommended, smaller]; (b) yes, exit 1 naming both lines and write nothing.
```

```text
type: answer
from: operator
answer: 1a any failing ensure leaves an existing wrapper unchanged and exits 1; require keeps reporting OK. 2a check the kind and rewrite of the tool and its whole needs chain before writing anything. 3a no catalogue-line comparison, out of scope.
```

```text
type: review
from: specifier
commit: cd9a002
files: test/features/sf_tool/README.md,test/features/sf_tool/entry_point_guard.feature,test/features/sf_tool/wrappers.feature,test/features/sf_tool/write_guard.feature,test/qa/sf_tool.md
summary: Spec: failing sf tool ensure checks kind/rewrite of the needs chain first and never changes .swarmforge
```

```text
type: approval
from: operator
```

```text
type: handoff
from: specifier
to: coder
branch: swarm/keep-sf-tool-ensure-from-ff06da1
commit: cd9a002
files: -
summary: specifier work complete
```

```text
type: question
from: coder
question: write_guard 1 example 3 (tool outer, which needs weird) expects 'sf tool require outer' to print 'OK: outer MAIN/.swarmforge/bin/outer', but the scenario leaves no wrapper for weird, and require checks the needs chain (settled behavior, unit spec 'names a missing need'), so it prints 'MISSING: weird'. Everything else passes, including the write-nothing snapshot for outer. Options: (a) specifier changes the scenario, e.g. add 'And an earlier install left a wrapper for "weird"' or check require only for weird/odd [recommended, no behavior change]; (b) require stops checking needs and reports only the named tool's wrapper; (c) the outer example drops its require check.
```

```text
type: answer
from: operator
answer: (a) fix the scenario, no behavior change: add an earlier wrapper for weird in the outer example, or check require only for weird and odd.
```

```text
type: handoff
from: coder
to: qa
branch: swarm/keep-sf-tool-ensure-from-ff06da1
commit: 9dc0ced
files: swarmforge/tools/sf/spec/sf/tool_spec.clj,swarmforge/tools/sf/src/sf/config.clj,swarmforge/tools/sf/src/sf/tool.clj,test/acceptance/src/acceptance/steps.clj,test/features/sf_tool/write_guard.feature
summary: ensure checks kind/rewrite of the needs chain first and keeps earlier wrappers on failure
```

```text
type: handoff
from: qa
to: done
branch: swarm/keep-sf-tool-ensure-from-ff06da1
commit: 596e1e3
files: -
summary: QA-1..QA-12 and all suites pass; ensure leaves shared wrappers unchanged on failure
```
