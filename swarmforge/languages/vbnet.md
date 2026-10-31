# VB.NET tooling

Run `swarmforge/bin/sf-vb` from the task worktree for SDK-style `.vbproj`
projects. It reuses the .NET wrapper and writes reports under `./tmp/`.
Prerequisites: Babashka (`bb`), the .NET SDK, and Node with `npx` on PATH.
Test projects must reference `coverlet.collector`.

| Command | Behavior |
|---|---|
| `tools` | Check prerequisites and prepare jscpd; no Stryker installation. |
| `test [dotnet test args]` | `dotnet test --filter Category!=Property`. |
| `property [dotnet test args]` | `dotnet test --filter Category=Property`. Mark property tests with `<Trait("Category", "Property")>`. |
| `coverage [dotnet test args]` | Fresh Coverlet Cobertura reports in `tmp/coverage/`, excluding properties. |
| `crap [--all] [--base REF] [--report DIR]` | Compiled `.vb` methods using Coverlet's complexity, line coverage and coverable source span. CRAP = complexity² × (1 − coverage)³ + complexity; fail above 10. Missing or unusable method data is an input error. |
| `dry [--all] [--base REF] [--min-tokens N] [--report-only]` | `jscpd@4 --format visual-basic --formats-exts visual-basic:vb`; report clones touching changed `.vb` lines in `tmp/jscpd/`. Generated `*.Designer.vb` files are ignored. `--report-only` reuses the last report; new runs discard it first. |
| `mutate` | Unsupported: exit 2. Stryker.NET loads only C# and F# projects; there is no VB.NET mutation or scan/count mode. |

Change scope includes committed, working-tree and untracked `.vb` changes
since the merge base with `--base` (default: `SWARMFORGE_BASE`, `main`, then
`master`). `--all` examines every reported method or clone.

CRAP scores what the compiler emitted, not source procedures. It uses the
reported compiled-method span without C# signature expansion; VB marks the
`Function` or `Sub` line as coverable, so that span starts at the declaration.
`Async` and `Iterator` methods compile into state machines, scored as
`VB$StateMachine_*.MoveNext` rows. Coverlet reports no methods for VB lambda
closures, so a lambda's branches count toward no row. Use the CRAP `cc` column
as a complexity hint when reviewing module boundaries.

jscpd's `visual-basic` grammar reads VB.NET syntax; its older `vbnet` grammar
treats text after `!` as a comment and is not used.

Checks exit 0 when clean, 1 on findings, and 2 on usage/input errors; test and
external-tool failures retain their exit status. Acceptance tests use the shared
Acceptance Pipeline Specification tools and the project's runtime/handlers.
