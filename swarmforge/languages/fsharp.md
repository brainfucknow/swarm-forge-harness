# F# tooling

Run `swarmforge/bin/sf-fs` from the task worktree for SDK-style `.fsproj`
projects. It reuses the .NET wrapper and writes reports under `./tmp/`.
Prerequisites: Babashka (`bb`), the .NET SDK, and Node with `npx` on PATH.
Test projects must reference `coverlet.collector`.

| Command | Behavior |
|---|---|
| `tools` | Check prerequisites and prepare jscpd; no Stryker installation. |
| `test [dotnet test args]` | `dotnet test --filter Category!=Property`; with Expecto, one run per test project (below). |
| `property [dotnet test args]` | `dotnet test --filter Category=Property`. Mark FsCheck/xUnit properties with `[<Trait("Category", "Property")>]`. With Expecto, one run per test project (below). |
| `coverage [dotnet test args]` | Fresh Coverlet Cobertura reports in `tmp/coverage/`, excluding properties. |
| `crap [--all] [--base REF] [--report DIR]` | Compiled `.fs` methods using Coverlet's complexity, line coverage and coverable source span. CRAP = complexity² × (1 − coverage)³ + complexity; fail above 10. Missing or unusable method data is an input error. |
| `dry [--all] [--base REF] [--min-tokens N] [--report-only]` | `jscpd@4 --format fsharp`; report clones touching changed `.fs` lines in `tmp/jscpd/`. `--report-only` reuses the last report; new runs discard it first. |
| `mutate` | Unsupported: exit 2. Stryker.NET supports C#; there is no F# mutation or scan/count mode. |

## Property tests in Expecto projects

Expecto tests run through `YoloDev.Expecto.TestSdk` have no traits, so the
Category filter cannot select them. The adapter exposes only
`FullyQualifiedName`: the test-list names joined with `.` (a `testProperty "p"`
inside `testList "Laws"` is `Laws.p`).

`test`, `property` and `coverage` search the directory they run in, skipping
`bin/`, `obj/`, `tmp/`, `node_modules/` and directories starting with `.`. A
test project is a `.fsproj`, `.csproj` or `.vbproj` that references
`Microsoft.NET.Test.Sdk` or `YoloDev.Expecto.TestSdk`, or sets
`<IsTestProject>true</IsTestProject>`. An Expecto test project is a `.fsproj`
with a `PackageReference` to `YoloDev.Expecto.TestSdk`.

Each Expecto test project declares the `testList` that holds its property
tests, at any nesting depth:

```xml
<PropertyGroup>
  <SwarmForgePropertyTestList>Laws</SwarmForgePropertyTestList>
</PropertyGroup>
```

```fsharp
[<Tests>]
let laws = testList "Laws" [ testProperty "reverse twice" <| fun (xs: int list) -> List.rev (List.rev xs) = xs ]
```

With no Expecto test project, nothing changes: one `dotnet test` with the
Category filter. With at least one, sf-fs runs `dotnet test <project>
--filter <F> [args]` for every test project, in sorted path order:

| Project | `test`, `coverage` | `property` |
|---|---|---|
| Expecto | `FullyQualifiedName!~NAME.` | `FullyQualifiedName~NAME.` |
| other (xUnit traits) | `Category!=Property` | `Category=Property` |

Every project runs even after one fails; the exit status is the first
non-zero one in run order. Coverage adds its collector to each run and clears
stale reports once, before the first. Pass only `dotnet test` options, not
project or solution paths, when Expecto projects exist: sf-fs supplies the
project. An Expecto test project without a declaration, or with a blank one,
is exit 2 before any `dotnet` runs.

`FullyQualifiedName~` is a case-insensitive substring match: any test whose
name contains `NAME.` counts as a property, wherever that segment sits. Pick a
list name no unit-test list shares. Filter characters in `NAME` are escaped.
A run that prints "No test matches the given testcase filter" and exits 0
found no test in the declared list (or, for an xUnit project, none with the
trait); sf-fs keeps dotnet's exit status.

## Change scope and exit status

Change scope includes committed, working-tree and untracked `.fs` changes
since the merge base with `--base` (default: `SWARMFORGE_BASE`, `main`, then
`master`). `--all` examines every reported method or clone. CRAP uses the
reported compiled-method span without C# signature expansion: compiler-generated
methods need not map one-to-one to source functions. `.fsx` analysis is unsupported.
Use the CRAP `cc` column as a complexity hint when reviewing module boundaries.

Checks exit 0 when clean, 1 on findings, and 2 on usage/input errors; test and
external-tool failures retain their exit status. Acceptance tests use the shared
Acceptance Pipeline Specification tools and the project's runtime/handlers.
