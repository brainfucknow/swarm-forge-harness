---
id: run-every-speclj-example-and-3509a9e
created: 2026-10-28
stage: done
route: specifier,coder,qa
---

# Run every speclj example, and check that none is skipped

Some speclj examples never run: an it inside a let that holds several it forms is not registered. Fix the two remaining blocks (swarmforge/tools/sf/spec/sf/flow_spec.clj describe "commits", record_spec.clj describe "resolve"), and add a check that runs in CI and fails when a spec file has a let around several it or context forms, for example a spec next to architecture_spec.

## Records

```text
type: created
from: operator
branch: swarm/run-every-speclj-example-and-3509a9e
commit: 4fd127b
route: specifier,coder,qa
```

```text
type: question
from: specifier
question: 1) Scope of the scan. A third block has the same fault: swarmforge/tools/sf/property/sf/record_property.clj describe "role-commits" (a let around 2 its; the first, 'points every record at the created commit...', never runs). The other spec directories (tools/core, csharp, javascript, test/) use clojure.test, where a let is harmless. Options: (a) fix all three blocks and have the check scan both swarmforge/tools/sf/spec and swarmforge/tools/sf/property; (b) fix all three, check scans spec/ only; (c) leave property/ out entirely, as the request says.
  2) What the check flags. Options: (a) only a let, at any depth inside describe/context, whose body holds two or more it/context/describe forms (as requested); (b) any non-speclj form (let, do, when, binding, with-redefs, doseq, ...) whose body holds two or more of them, since describe drops all but the last value of each of those too.
  3) Acceptance. The check is a test of the tests, not sf behaviour. Options: (a) no Gherkin; the specification is the speclj check plus a QA procedure (plant a let around two its in a copy, run 'bb --config swarmforge/tools/sf/bb.edn spec', see it fail naming the file and the hidden examples; remove it, see it pass); (b) a small Gherkin feature in test/features run by test/acceptance.sh, whose steps add a spec file to a scratch copy of swarmforge/ and run the sf unit specs there, checking exit status and output.
  4) Failure output. My default: the failing example names the file and the description of every it/context the let hides (all but the last). OK, or do you want line numbers too?
```

```text
type: answer
from: operator
answer: 1a fix all three blocks and scan both spec and property; 2a flag only a let holding two or more it/context/describe forms; 3a no Gherkin, the speclj check plus a QA procedure; 4 yes, give line numbers.
```

```text
type: review
from: specifier
commit: a174e97
files: test/qa/speclj_examples.md
summary: Spec and QA procedure for the speclj let check
```

```text
type: approval
from: operator
```

```text
type: handoff
from: specifier
to: coder
branch: swarm/run-every-speclj-example-and-3509a9e
commit: a174e97
files: -
summary: specifier work complete
```

```text
type: handoff
from: coder
to: qa
branch: swarm/run-every-speclj-example-and-3509a9e
commit: b0a2c48
files: swarmforge/tools/sf/property/sf/record_property.clj,swarmforge/tools/sf/spec/sf/examples_spec.clj,swarmforge/tools/sf/spec/sf/flow_spec.clj,swarmforge/tools/sf/spec/sf/record_spec.clj
summary: Let blocks hoisted to defs; examples_spec fails on a let hiding examples
```

```text
type: handoff
from: qa
to: done
branch: swarm/run-every-speclj-example-and-3509a9e
commit: 543ddf4
files: -
summary: QA passed: all examples run, let check verified
```
