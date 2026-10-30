# F# tooling

Run `swarmforge/bin/sf-fs` from the task worktree for SDK-style `.fsproj`
projects. It reuses the .NET wrapper and writes reports under `./tmp/`.
Prerequisites: Babashka (`bb`), the .NET SDK, and Node with `npx` on PATH.
Test projects must reference `coverlet.collector`.

| Command | Behavior |
|---|---|
| `tools` | Check prerequisites and prepare jscpd; no Stryker installation. |
| `test [dotnet test args]` | `dotnet test --filter Category!=Property`. |
| `property [dotnet test args]` | `dotnet test --filter Category=Property`. Mark FsCheck/xUnit properties with `[<Trait("Category", "Property")>]`. |
| `coverage [dotnet test args]` | Fresh Coverlet Cobertura reports in `tmp/coverage/`, excluding properties. |
| `crap [--all] [--base REF] [--report DIR]` | Compiled `.fs` methods using Coverlet's complexity, line coverage and coverable source span. CRAP = complexity² × (1 − coverage)³ + complexity; fail above 10. Missing or unusable method data is an input error. |
| `dry [--all] [--base REF] [--min-tokens N] [--report-only]` | `jscpd@4 --format fsharp`; report clones touching changed `.fs` lines in `tmp/jscpd/`. `--report-only` reuses the last report; new runs discard it first. |
| `mutate` | Unsupported: exit 2. Stryker.NET supports C#; there is no F# mutation or scan/count mode. |

Change scope includes committed, working-tree and untracked `.fs` changes
since the merge base with `--base` (default: `SWARMFORGE_BASE`, `main`, then
`master`). `--all` examines every reported method or clone. CRAP uses the
reported compiled-method span without C# signature expansion: compiler-generated
methods need not map one-to-one to source functions. `.fsx` analysis is unsupported.
Use the CRAP `cc` column as a complexity hint when reviewing module boundaries.

Checks exit 0 when clean, 1 on findings, and 2 on usage/input errors; test and
external-tool failures retain their exit status. Acceptance tests use the shared
Acceptance Pipeline Specification tools and the project's runtime/handlers.
