# C# tooling

Run `swarmforge/bin/sf-cs` from the task worktree. It needs Babashka, the
.NET SDK, and Node with `npx`; reports go under `./tmp/`.

| Constitution tool | Command | Behind it |
|---|---|---|
| Startup tools | `sf-cs tools` | A check that `dotnet`, `node`, and `npx` are on PATH, `dotnet tool restore`, then `npx --yes jscpd@4 --version`. It installs nothing and writes no tool manifest. |
| Unit and acceptance tests | `sf-cs test [dotnet test args]` | `dotnet test --filter Category!=Property`. |
| Property tests | `sf-cs property` | `dotnet test --filter Category=Property`. |
| Coverage | `sf-cs coverage [dotnet test args]` | `dotnet test --collect "XPlat Code Coverage"` (coverlet) writing Cobertura files under `tmp/coverage/`. |
| CRAP | `sf-cs crap [--all] [--base REF]` | CRAP per method from the Cobertura files: `comp² × (1 − line coverage)³ + comp`, using coverlet's own per-method complexity. Lists changed methods, or all with `--all`. Exit 1 when any is over 10. |
| DRY | `sf-cs dry [--all] [--base REF] [--min-tokens N]` | `npx jscpd@4 --format csharp` with a JSON report under `tmp/jscpd/`, then the clones that touch a changed line. Exit 1 when any. |
| Language mutation | `sf-cs mutate [--base REF] [--all] [-- stryker args]` | First `dotnet tool list --local dotnet-stryker --format json`: exit 2 unless it names a pin in a committed `dotnet-tools.json` or `.config/dotnet-tools.json` at the repository root. Then `dotnet stryker --help`: exit 2 when it fails (run `sf-cs tools`). Then `dotnet stryker --since:<base>` (differential: only files changed since the base branch) with the JSON report under `tmp/stryker/`, then every `Survived` or `NoCoverage` mutant. Exit 1 when any; exit 2 when Stryker fails without a report. `--all` drops `--since`. |

Stryker.NET's version is pinned by the project's committed dotnet tool
manifest at the repository root, not by `swarmforge/tools.conf`; global and
preinstalled copies are not used. Roles never pin, install or commit it: when
`sf-cs mutate` refuses the pin, the role reports and stops, and the operator
pins it from the repository root (`dotnet new tool-manifest` when the root has
no manifest, then `dotnet tool install dotnet-stryker --version <version>`)
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
  changed `.cs` files, with rules from a pinned semgrep-rules commit in `tmp/`
  (never commit them: Semgrep Rules License). The fetch and the first uvx run
  reach github.com and pypi.org. When it cannot run, record
  `Not-tested: semgrep: <reason>` and continue:

  ```sh
  git init -q tmp/semgrep-rules
  git -C tmp/semgrep-rules fetch -q --depth 1 https://github.com/semgrep/semgrep-rules.git 16b9e6d45f10649f6a531ec4838ed9924372c3bc
  git -C tmp/semgrep-rules checkout -q FETCH_HEAD
  uvx semgrep@1.183.0 scan --config tmp/semgrep-rules/csharp --metrics=off --disable-version-check --error <changed files>
  ```

- Dependency audit, when the task changes a `*.csproj`,
  `Directory.Packages.props`, `Directory.Build.props` or `packages.lock.json`:
  `dotnet restore <project>` on SDKs before .NET 10, then
  `dotnet list <project> package --vulnerable --include-transitive`
  (`dotnet package list ...` on .NET 10+). It needs the NuGet feeds. Read its
  output, not only its exit status.

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

- Test projects reference `coverlet.collector` so `--collect "XPlat Code Coverage"` works.
- Property tests (FsCheck, CsCheck, or similar) carry `[Trait("Category", "Property")]` so `sf-cs test` excludes them and `sf-cs property` runs them.
- Stryker needs a test project it can find; pass `-- --project <csproj>` or `-- --solution <sln>` when the repository holds more than one, or add a `stryker-config.json`.
- Gherkin acceptance tests use the Acceptance Pipeline Specification tools; the acceptance runtime and step handlers are C# and run through `sf-cs test`.
- There is no mutation scan/count mode for C#. Use the `cc` column of `sf-cs crap --all` as the hint that a file may mix jobs.
