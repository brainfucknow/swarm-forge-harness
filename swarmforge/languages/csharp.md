# C# tooling

`swarmforge/bin/sf-cs` wraps the C# tools so every role runs the same command
and reads the same output. Run it from the task worktree (`DIR:` from
`sf start`), by path or with `swarmforge/bin` on PATH. It writes only under
`./tmp/`.

| Constitution tool | Command | Behind it |
|---|---|---|
| Startup tools | `sf-cs tools` | `dotnet tool install dotnet-stryker` in a local manifest, `dotnet tool restore`, and a check that `node`, `npx`, and `dotnet` are on PATH. |
| Unit and acceptance tests | `sf-cs test [dotnet test args]` | `dotnet test --filter Category!=Property`. |
| Property tests | `sf-cs property` | `dotnet test --filter Category=Property`. |
| Coverage | `sf-cs coverage [dotnet test args]` | `dotnet test --collect "XPlat Code Coverage"` (coverlet) writing Cobertura files under `tmp/coverage/`. |
| CRAP | `sf-cs crap [--all] [--base REF]` | CRAP per method from the Cobertura files: `comp² × (1 − line coverage)³ + comp`, using coverlet's own per-method complexity. Lists changed methods, or all with `--all`. Exit 1 when any is over 10. |
| DRY | `sf-cs dry [--all] [--base REF] [--min-tokens N]` | `npx jscpd@4 --format csharp` with a JSON report under `tmp/jscpd/`, then the clones that touch a changed line. Exit 1 when any. |
| Language mutation | `sf-cs mutate [--base REF] [--all] [-- stryker args]` | `dotnet stryker --since:<base>` (differential: only files changed since the base branch) with the JSON report under `tmp/stryker/`, then every `Survived` or `NoCoverage` mutant. Exit 1 when any. `--all` drops `--since`. |

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

## Conventions the tools rely on

- Test projects reference `coverlet.collector` so `--collect "XPlat Code Coverage"` works.
- Property tests (FsCheck, CsCheck, or similar) carry `[Trait("Category", "Property")]` so `sf-cs test` excludes them and `sf-cs property` runs them.
- Stryker needs a test project it can find; pass `-- --project <csproj>` or `-- --solution <sln>` when the repository holds more than one, or add a `stryker-config.json`.
- Gherkin acceptance tests use the Acceptance Pipeline Specification tools; the acceptance runtime and step handlers are C# and run through `sf-cs test`.
- There is no mutation scan/count mode for C#. Use the `cc` column of `sf-cs crap --all` as the hint that a file may mix jobs.
