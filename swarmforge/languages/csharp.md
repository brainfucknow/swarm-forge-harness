# C# tooling

Run `swarmforge/bin/sf-cs` from the task worktree. It needs Babashka, the
.NET SDK, and Node with `npx`; reports go under `./tmp/`.

The minimum supported .NET SDK is 10, the currently supported one. .NET 8 is
out of support; do not work around it. Ask `dotnet` and MSBuild for toolchain
facts (SDK version, project and MSBuild properties, packages) instead of
re-implementing their rules in `sf-cs`.

| Constitution tool | Command | Behind it |
|---|---|---|
| Startup tools | `sf-cs tools` | A check that `dotnet`, `node`, and `npx` are on PATH, `dotnet tool restore` (the pinned dotnet-coverage and dotnet-stryker), then `npx --yes jscpd@4 --version`. It installs nothing and writes no tool manifest. |
| Unit and acceptance tests | `sf-cs test [dotnet test args]` | `dotnet test --filter Category!=Property`. |
| Property tests | `sf-cs property` | `dotnet test --filter Category=Property`. |
| Coverage | `sf-cs coverage [dotnet test args]` | The pin check for dotnet-coverage (as for Stryker under Language mutation; exit 2 when it refuses), the removal of earlier reports under `tmp/coverage/`, then a walk of the current directory for project files: exit 2, asking you to run from the repository or solution root, when it finds none or every project it restored is a test project. For each project, `dotnet restore <project>`, then `dotnet msbuild <project> -getProperty:IsTestProject -getProperty:IsTestingPlatformApplication -getProperty:AssemblyName`; a project whose restore fails gets a warning naming it, is not evaluated, and does not change the exit status. Then `dotnet dotnet-coverage collect -s tmp/dotnet-coverage.xml -f cobertura -o tmp/coverage/run-<i>/coverage.cobertura.xml -- dotnet test --filter Category!=Property [args]`, the args passed through untouched. The settings keep sources under the current directory outside `obj/`, and leave out each test project's assembly (`<AssemblyName>.dll` or `.exe`), so production sources beside or below a test project stay in the report. The same for VSTest and Microsoft.Testing.Platform test projects. A run status other than 0 or 8 (MTP: zero tests ran) is exit 1. |
| CRAP | `sf-cs crap [--all] [--base REF]` | CRAP per method from the Cobertura files: `comp² × (1 − line coverage)³ + comp`, using dotnet-coverage's own per-method complexity. Lists changed methods, or all with `--all`. Exit 1 when any is over 10. |
| DRY | `sf-cs dry [--all] [--base REF] [--min-tokens N]` | `npx jscpd@4 --format csharp` with a JSON report under `tmp/jscpd/`, then the clones that touch a changed line. Exit 1 when any. |
| Language mutation | `sf-cs mutate [--base REF] [--all] [-- stryker args]` | First `dotnet tool list --local dotnet-stryker --format json`: exit 2 unless it names a pin in a committed `dotnet-tools.json` or `.config/dotnet-tools.json` at the repository root. Then `dotnet stryker --help`: exit 2 when it fails (run `sf-cs tools`). Then `dotnet stryker --since:<base>` (differential: only files changed since the base branch) with the JSON report under `tmp/stryker/`, then every `Survived` or `NoCoverage` mutant. Exit 1 when any; exit 2 when Stryker fails without a report. `--all` drops `--since`. |

Stryker.NET's and dotnet-coverage's versions are pinned by the project's
committed dotnet tool manifest at the repository root, not by
`swarmforge/tools.conf`; global and preinstalled copies are not used. Roles
never pin, install or commit them: when `sf-cs mutate` or `sf-cs coverage`
refuses the pin, the role reports and stops, and the operator
pins it from the repository root (`dotnet new tool-manifest` when the root has
no manifest, then `dotnet tool install <dotnet-stryker or dotnet-coverage> --version <version>`)
and commits the manifest. An untracked `dotnet-tools.json` left by an older
`sf-cs tools` must be committed or deleted by the operator.

Every command exits 0 when clean, 1 on a finding, and 2 on a usage or input
error such as a missing report. `--report-only` on `dry` and `mutate` re-reads
the last report without rerunning the tool.

"Changed" means the task: everything since the merge base with `--base`,
committed or not, plus untracked `.cs` files. `--base` defaults to
`SWARMFORGE_BASE`, then `main`, then `master`. A method counts as changed when
any line from its signature (attributes and wrapped parameters included) to
its last coverable line changed.

Mutation must be differential: use `sf-cs mutate` without `--all`, so Stryker
mutates only files changed since the base branch.

## Reading the output

```text
OVER crap   10.8  cc   4  cov  25%  Bank/Ledger.cs:10 Ledger.Withdraw
ok   crap    6.0  cc   2  cov   0%  Bank/Audit.cs:4 Audit<T>.Record
2 changed method(s), 1 over CRAP 10
```

Reduce CRAP by covering the method or splitting it. A single `switch` that
answers one question may stay above 10 (see your role prompt).

```text
64 tokens  Bank/Ledger.cs:13-15 (changed)  ==  Bank/Legacy.cs:30-32
2 of 3 clone(s) touch changed lines
```

A clone with only unchanged sides is pre-existing and not listed unless you
pass `--all`.

```text
Survived   Bank/Ledger.cs:14  Equality mutation  > replaced with >=
NoCoverage Bank/Ledger.cs:14  Boolean mutation  true
mutation score 50.0% (Killed 1, Survived 1, NoCoverage 1, Timeout 1, Ignored 1)
```

Kill a survivor with a test that fails under the replacement. `NoCoverage`
means no test reaches the line.

## Security scanners

- Code scanner: build the enclosing project or solution with the SDK's
  security analyzers and read only the warnings in changed `.cs` files:
  `mkdir -p tmp && dotnet build <project> --no-incremental -p:EnableNETAnalyzers=true -p:AnalysisModeSecurity=All -p:ReportAnalyzer=true -v:d > tmp/security-build.log`
  The log's analyzer summary must list a `NetAnalyzers` assembly; otherwise
  the analyzers did not run and the scan is Not-tested, not clean.
- Extra, encouraged but not blocking (not the code scanner): Semgrep on the
  changed `.cs` files. Read and follow the [shared Semgrep setup](semgrep.md)
  with `--config tmp/semgrep-rules/csharp`.

- Dependency audit, when the task changes a `*.csproj`,
  `Directory.Packages.props`, `Directory.Build.props` or `packages.lock.json`:
  `dotnet package list <project> --vulnerable --include-transitive`. It needs
  the NuGet feeds. Read its output, not only its exit status.

## Code-smell check

The SDK's CA1501 (inheritance depth over 5), CA1505 (maintainability index
under 10) and CA1506 (class coupling over 95 per type, 40 per member) are off
by default; `swarmforge/tools/csharp/resources/smell.targets` turns them on
without changing the project. Build the enclosing project or solution:
`mkdir -p tmp && dotnet build <project> --no-incremental -p:EnableNETAnalyzers=true -p:ReportAnalyzer=true -v:d -p:CustomAfterMicrosoftCommonTargets="$PWD/swarmforge/tools/csharp/resources/smell.targets" > tmp/smell-build.log`
Without a `NetAnalyzers` assembly in the analyzer summary and
`smell.globalconfig` in the compiler command, the check is Not-tested, not
clean. Read only CA1501, CA1505 and CA1506 lines in changed `.cs` files; a
project that treats warnings as errors reports them as errors.

## Conventions the tools rely on

- Test projects need no coverage package, whether they run on VSTest or on Microsoft.Testing.Platform (for example xUnit v3 with `"test": { "runner": "Microsoft.Testing.Platform" }` in `global.json`): dotnet-coverage, pinned in the committed root tool manifest, instruments `dotnet test` from outside. A test project is one MSBuild evaluates with `IsTestProject` or `IsTestingPlatformApplication` set to `true`; its assembly stays out of the coverage report, run or never run. A test-support library MSBuild does not evaluate as a test project is scored like production code, and so is a test project whose restore failed (the warning names it). A source compiled only into a test assembly is not measured.
- Property tests (FsCheck, CsCheck, or similar) carry `[Trait("Category", "Property")]` so `sf-cs test` excludes them and `sf-cs property` runs them.
- Stryker needs a test project it can find; pass `-- --project <csproj>` or `-- --solution <sln>` when the repository holds more than one, or add a `stryker-config.json`.
- Gherkin acceptance tests use the Acceptance Pipeline Specification tools; the acceptance runtime and step handlers are C# and run through `sf-cs test`.
- There is no mutation scan/count mode for C#. Use the `cc` column of `sf-cs crap --all` as the hint that a file may mix jobs.
