# Every speclj example runs: specification and QA procedure (task run-every-speclj-example-and-3509a9e)

speclj's `describe` and `context` register the values of their body forms. A
`let` around several `it` forms has one value, the last one, so only the last
example is registered and the others never run.

## Settled with the operator

- Fix all three blocks that have the fault. Each fix must keep every example
  in the block: none is deleted, merged or renamed.
  - `swarmforge/tools/sf/spec/sf/flow_spec.clj`, describe `commits` (4 its)
  - `swarmforge/tools/sf/spec/sf/record_spec.clj`, describe `resolve` (6 its)
  - `swarmforge/tools/sf/property/sf/record_property.clj`, describe
    `role-commits` (2 its)
- The check scans `swarmforge/tools/sf/spec` and `swarmforge/tools/sf/property`.
  The other test directories use clojure.test and are out of scope.
- The check flags only `let`. Other forms that wrap examples are out of scope.
- No Gherkin. This file is the specification. The check is a speclj spec in
  `swarmforge/tools/sf/spec/sf/`, next to `architecture_spec.clj`, so the CI
  step `bb --config swarmforge/tools/sf/bb.edn spec` runs it.

## The check

- It reads every `.clj` file under `swarmforge/tools/sf/spec` and
  `swarmforge/tools/sf/property`, whatever the current directory.
- A `let` form, at any depth in a file, is flagged when its body (the forms
  after the binding vector) has two or more forms whose head is the symbol
  `it`, `context` or `describe`. A `let` with one such form is not flagged.
- For a flagged `let`, every one of those forms except the last is hidden.
- The check fails when any `let` is flagged. Its failure output has one entry
  per hidden form, in this form:

  `<path>:<line>: <head> <description>, hidden by the let at line <let line>`

  `<path>` is relative to `swarmforge/tools/sf` (for example
  `spec/sf/flow_spec.clj`). `<line>` is the line where the hidden form starts,
  and `<let line>` is the line where the `let` starts. `<head>` is `it`,
  `context` or `describe`. `<description>` is the form's first argument
  without quotes.
- It passes when no `let` is flagged.

## QA procedure

This procedure runs speclj from the command line on a copy of the task
branch. Check only exit statuses and output. Turn the checks into a throwaway
script under `./tmp/` and do not commit it. Record each check's result in the
QA handoff commit's Lore trailers: `Tested:` for a check that passed,
`Not-tested:` with the reason for a check that could not run. A failing check
is a defect to fix, not a trailer.

### Prerequisites

- `git`, `bb` and `java` on PATH, and network access to repo.clojars.org and
  Maven Central for speclj.
- `WT` is the absolute path of the task worktree. Work under `$WT/tmp/qa/`,
  starting empty.

### Setup

1. `git clone -q --branch "$(git -C "$WT" branch --show-current)" "$WT" "$WT/tmp/qa/project"`.
   `P` is `$WT/tmp/qa/project`. Run every command below from `$P`.
2. `SPEC` is `bb --config swarmforge/tools/sf/bb.edn spec`.
3. `DOC <dir>` is
   `bb --config swarmforge/tools/sf/bb.edn -m speclj.main -f documentation -c swarmforge/tools/sf/<dir>`.
   Strip ANSI colour codes from its output before you match it.

### QA-1: every example in the three blocks runs

- `SPEC` exits 0, and so does `bb --config swarmforge/tools/sf/bb.edn property`.
- In the output of `DOC spec`, the group `commits` lists these four examples:
  - `is the tip, or the role's commit under the newest record at the tip`
  - `finds the commit the branch started from`
  - `finds the commit of the newest created, review or handoff record`
  - `finds the finished record`
- In the output of `DOC spec`, the group `resolve` lists these six:
  - `takes the full id`
  - `takes the hex part`
  - `takes a unique prefix`
  - `names every match of an ambiguous prefix, sorted`
  - `finds nothing`
  - `prefers the full id over a longer id it prefixes`
- In the output of `DOC property`, the group `role-commits` lists these two:
  - `points every record at the created commit or a role's commit, never at a later record`
  - `is idempotent`
- Both `DOC` runs report `0 failures`.

### QA-2: a let around several examples in spec/ fails the check

Write `swarmforge/tools/sf/spec/sf/planted_spec.clj` with exactly these 11
lines:

```clojure
(ns sf.planted-spec
  (:require [speclj.core :refer [describe context it should]]))

(describe "planted"
  (let [x 1]
    (it "first hidden"
      (should (= 1 x)))
    (context "second hidden"
      (it "inner" (should true)))
    (it "last registered"
      (should true))))
```

- `SPEC` exits non-zero.
- Its output contains
  `spec/sf/planted_spec.clj:6: it first hidden, hidden by the let at line 5`.
- Its output contains
  `spec/sf/planted_spec.clj:8: context second hidden, hidden by the let at line 5`.
- Its output does not contain `last registered, hidden`.

Delete the file afterwards.

### QA-3: the check scans property/, at any depth, and passes a single example

Write `swarmforge/tools/sf/property/sf/planted_property.clj` with exactly
these 10 lines:

```clojure
(ns sf.planted-property
  (:require [speclj.core :refer [describe context it should]]))

(describe "planted property"
  (let [x 1]
    (it "alone" (should (= 1 x))))
  (context "nested"
    (let [y 2]
      (describe "deep hidden" (it "a" (should (= 2 y))))
      (it "deep last" (should true)))))
```

- `SPEC` exits non-zero.
- Its output contains
  `property/sf/planted_property.clj:9: describe deep hidden, hidden by the let at line 8`.
- Its output does not contain `planted_property.clj:6` and does not contain
  `deep last, hidden`.

Delete the file afterwards.

### QA-4: the clean tree passes

With both planted files deleted, `SPEC` exits 0 and reports `0 failures`.
