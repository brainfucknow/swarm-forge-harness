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

Expecto tests have no traits, so each Expecto test project declares the
`testList` that holds its property tests, at any depth, in a `<PropertyGroup>`:
`<SwarmForgePropertyTestList>Laws</SwarmForgePropertyTestList>`.

```fsharp
[<Tests>]
let laws = testList "Laws" [ testProperty "reverse twice" <| fun (xs: int list) -> List.rev (List.rev xs) = xs ]
```

Once any Expecto test project exists, sf-fs runs one `dotnet test` per test project:

| Project | `test`, `coverage` | `property` |
|---|---|---|
| Expecto | `FullyQualifiedName!~NAME.` | `FullyQualifiedName~NAME.` |
| other (xUnit traits) | `Category!=Property` | `Category=Property` |

`FullyQualifiedName~` matches `NAME.` anywhere in a test name; pick a name no
unit-test list shares. Write `Laws&amp;Properties` for `Laws&Properties`. A
missing or blank declaration is exit 2 before `dotnet` runs. A run that prints
"No test matches the given testcase filter" found no test in the declared list;
sf-fs keeps dotnet's exit status.

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

## Security scanners

- Code scanner: none. The SDK's security analyzers skip F# and no maintained
  F# scanner exists, so an F# project adds
  `Security code scanner absent: <reason>` to `project.md`.
- Dependency audit: as in [csharp.md](csharp.md#security-scanners), restore
  included, with `*.fsproj` in place of `*.csproj` among the dependency files.

## Code-smell check

FSharpLint 0.27.0 (MIT) with only the size and nesting rules in
`swarmforge/tools/csharp/resources/fsharplint.json`, installed under `./tmp`
(needs nuget.org) and run on the enclosing project or solution:
`mkdir -p tmp && { [ -x tmp/fsharplint/dotnet-fsharplint ] || dotnet tool install dotnet-fsharplint --version 0.27.0 --tool-path tmp/fsharplint; }`
`tmp/fsharplint/dotnet-fsharplint lint --format msbuild --lint-config swarmforge/tools/csharp/resources/fsharplint.json <project> > tmp/fsharplint.log`
It exits non-zero on any warning, so read the log, keeping only warnings in
changed `.fs` files. A "Lint failed" line leaves that file Not-tested.
