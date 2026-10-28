# sf tool end-to-end QA procedure

For the task "Port the tool-wrapper fix to the Babashka sf tool".

This procedure runs `sf tool` and the wrappers it writes from the command
line. It uses real Babashka, real GitHub clones, and this repository's own
Babashka code as the code under measurement. Check only what a user sees:
exit statuses, stdout, stderr, and files on disk. Do not call `sf.tool`
functions or read its source.

Turn the checks into a throwaway script under `./tmp/` and do not commit it.
Record the result of each check in the QA handoff commit's Lore trailers:
`Tested:` for a check that passed, `Not-tested:` with the reason for a check
that could not run. A failing check is a defect to fix, not a trailer.

## Prerequisites

- `git`, `bb` and `java` on PATH. Babashka needs `java` to resolve the git
  and Maven dependencies of a `bb.edn`.
- Network access to github.com, repo.clojars.org and Maven Central.
- No `clj` on PATH, so that the Babashka coverage path is used. If `clj` is
  installed, run the checks with a PATH that leaves it out.
- `WT` is the absolute path of the task worktree. Work under `$WT/tmp/qa/`,
  which is never committed. Start with an empty `$WT/tmp/qa/`.

## Setup

1. `git clone -q --branch "$(git -C "$WT" branch --show-current)" "$WT" "$WT/tmp/qa/project"`.
   `P` is `$WT/tmp/qa/project`. It has no `.swarmforge/` yet, so every tool
   is cloned fresh from GitHub.
2. Run every command below with `PATH="$P/swarmforge/bin:$PATH"`, and with
   `SWARMFORGE_TOOL_SRC` unset unless a step sets it.

## Checks

### QA-1: the Clojure tools install

From `$P`, run `sf tool ensure T` for T in `dry4clj`, `clj-mutate`,
`crap4clj` and `gherkin-parser`.

- Each exits 0 and prints `INSTALLED: T $P/.swarmforge/bin/T`. `clj-mutate`
  and `crap4clj` also print `INSTALLED: cloverage …` first.
- `sf tool require T` prints `OK: T $P/.swarmforge/bin/T` for each.
- `$P/.swarmforge/tools/crap4clj-coverage/bb.edn` exists and contains
  `61e3cac426e9907a9dd01c37597f85c71a57ff90` and `3.12.2`.
- `sf tool list` shows `dry4clj` with kind `bbmain`.

### QA-2: dry4clj runs

From `$P`, run `.swarmforge/bin/dry4clj swarmforge/tools/csharp/src swarmforge/tools/sf/src`.

- Exit 0.
- Stdout is a dry4clj report: either `No duplicate candidates found.` or
  candidate lines with file names and line ranges.
- Neither stream contains `File does not exist`.
- `.swarmforge/bin/dry4clj --format edn swarmforge/tools/csharp/src`
  prints EDN (it starts with `[` or `{`).

### QA-3: clj-mutate runs

From `$P/swarmforge/tools/csharp`, run
`$P/.swarmforge/bin/clj-mutate src/sfcs/cobertura.clj --scan`.

- Exit 0.
- Stdout reports mutation counts for `src/sfcs/cobertura.clj`.
- Neither stream contains `File does not exist`.
- `$P/.swarmforge/bin/clj-mutate --help` prints the clj-mutate usage
  (it mentions `--scan`).

### QA-4: crap4clj with clojure.test and no Clojure CLI

From `$P/swarmforge/tools/csharp` (it has `src` and `test`, no `spec` and
no `deps.edn`), run `$P/.swarmforge/bin/crap4clj`. The first run downloads
dependencies and can take several minutes.

- Exit 0.
- The output shows `Ran N tests` with `0 failures, 0 errors`, a Cloverage
  table with `sfcs.cobertura`, and a CRAP report whose rows include the
  function `crap-score` in `sfcs.cobertura`.
- `target/coverage/lcov.info` and `.metrics/crap.edn` exist in that
  directory. Delete `target/` and `.metrics/` afterwards.

### QA-5: crap4clj with speclj

`cp -R "$P/.swarmforge/tools/dry4clj" "$WT/tmp/qa/dry"` (it has `src` and
`spec`). From `$WT/tmp/qa/dry`, run `rm -f deps.edn`, then
`$P/.swarmforge/bin/crap4clj`.

- Exit 0.
- The output shows speclj's `examples, 0 failures` line, a Cloverage table
  with `dry4clj.core`, and a CRAP report with functions of `dry4clj.core`.

### QA-6: crap4clj leaves a caller's coverage choice alone

From `$P/swarmforge/tools/csharp`, run
`$P/.swarmforge/bin/crap4clj --use-existing-coverage --lcov does-not-exist.info`.

- Stdout and stderr show no Cloverage run (no `Ran N tests`, no
  `Writing LCOV report`). crap4clj may fail or report no coverage. That is
  expected, because the file does not exist.

### QA-7: a tool without a Babashka entry point fails clearly

From `$P`, run `sf tool ensure crap4go`.

- Exit 1.
- Stderr contains `sf tool: crap4go: no bb.edn in $P/.swarmforge/tools/crap4go`.
- `$P/.swarmforge/bin/crap4go` does not exist. `sf tool require crap4go`
  prints `MISSING: crap4go` and exits 1.
- Run `sf tool ensure crap4go` again. It prints the same error. Stderr does
  not mention `clone`.

### QA-8: a missing task fails clearly

Make `$WT/tmp/qa/fake-src/bb.edn` with the content `{:tasks {spec (println "spec")}}`.
From `$P`, with `SWARMFORGE_TOOL_SRC=$WT/tmp/qa/fake-src`, write an
executable `$P/.swarmforge/bin/clj-mutate` that prints `stale`, then run
`sf tool ensure clj-mutate`.

- Exit 1.
- Stderr contains `sf tool: clj-mutate: $WT/tmp/qa/fake-src/bb.edn has no task 'mutate'`.
- `$P/.swarmforge/bin/clj-mutate` no longer exists.
- Afterwards, unset `SWARMFORGE_TOOL_SRC` and run `sf tool ensure clj-mutate`
  again so the later checks have it. It exits 0.

### QA-9: the catalogue comes from the checkout

From `$P`, run `git worktree add -q .worktrees/q1 -b swarm/q1`. In
`$P/.worktrees/q1`:

- Append the line `probe local swarmforge/bin/probe - - -` to
  `swarmforge/tools.conf`. Write an executable `swarmforge/bin/probe` that
  prints `probe ran`.
- `sf tool list` there has a line starting with `probe`. `sf tool list` in
  `$P` has none.
- `sf tool ensure probe` there exits 0 and prints
  `INSTALLED: probe $P/.swarmforge/bin/probe`. `sf tool require probe` there
  prints `OK: probe $P/.swarmforge/bin/probe`.
- `$P/.swarmforge/bin/probe`, run from `$P/.worktrees/q1`, prints `probe ran`.
- `sf tool ensure probe` in `$P` exits 1. Stderr contains `unknown tool 'probe'`.

### QA-10: a local wrapper runs the caller's copy of the script

From `$P`, run `sf tool ensure sf-cs`. In `$P/.worktrees/q1`, replace
`swarmforge/bin/sf-cs` with an executable script that prints `task copy`.
Make `$WT/tmp/qa/outside`.

- `$P/.swarmforge/bin/sf-cs`, run from `$P/.worktrees/q1`, prints `task copy`.
- `$P/.swarmforge/bin/sf-cs --help`, run from `$P`, prints the real sf-cs
  usage, not `task copy`.
- `$P/.swarmforge/bin/sf-cs --help`, run from `$WT/tmp/qa/outside` with
  `GIT_CEILING_DIRECTORIES=$WT/tmp/qa`, so that directory is outside any git
  repository, prints the real sf-cs usage.
- `git -C "$P" worktree add -q .worktrees/q2 -b swarm/q2`, then delete
  `swarmforge/bin/sf-cs` in `$P/.worktrees/q2`. Run
  `$P/.swarmforge/bin/sf-cs --help` from there. It prints the real sf-cs usage.

### QA-11: the gherkin-parser wrapper and sf check

- From `$P`, run `mkdir -p tmp`, then run
  `.swarmforge/bin/gherkin-parser test/features/sf_tool/wrappers.feature ./tmp/wrappers.json`.
  It exits 0 and `./tmp/wrappers.json` names the feature `sf tool wrappers`.
- `sf check` in `$P` exits 0 and prints no line that mentions `tools.conf`.

## Cleanup

Remove `$WT/tmp/qa/`. Nothing under it is committed.
