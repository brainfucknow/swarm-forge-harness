---
id: encourage-a-code-smell-check-df99fe9
created: 2026-11-04
stage: done
route: specifier,coder,cleaner,qa
---

# Encourage a code-smell check for .NET projects

Operator request: add a code-smell check for C#, VB.NET and F# projects, similar to reek for Ruby. It should be encouraged, not merely optional: the language guides recommend it and the cleaner runs it on the task's changed files whenever it is available, but a missing tool does not block. Candidates to verify: for C# and VB the SDK's built-in code-metrics rules (CA1501, CA1502, CA1505, CA1506, enabled through .editorconfig or AnalysisMode), with SonarAnalyzer.CSharp / SonarAnalyzer.VisualBasic as an optional extra once its licence is checked; for F# FSharpLint (MIT). Read only findings in changed files, as the security scanner section of csharp.md does. Respect 'Existing code: ask before refactoring' (findings in pre-existing code are recorded, not fixed without approval). Decide whether it is a documented command or an sf-cs/sf-fs/sf-vb subcommand, and keep the guides short. dotnet is not installed here and learn.microsoft.com is blocked, so record what cannot be run as Not-tested.

## Records

```text
type: created
from: operator
branch: swarm/encourage-a-code-smell-check-df99fe9
commit: 6ed426d
route: specifier,coder,cleaner,architect,hardener,qa
```

```text
type: question
from: specifier
question: Verified here (no dotnet): NetAnalyzers 10.0.403 package and CodeMetricsAnalyzer source; SonarAnalyzer nuspecs and licence; dotnet-fsharplint 0.27.0 package, config and source.
  Findings: CA1501/1502/1505/1506 are disabled by default and AnalysisModeMaintainability=All does NOT turn them on (absent from its globalconfig); each needs dotnet_diagnostic.CA15xx.severity = warning in an .editorconfig/.globalconfig. They cover C# and VB. Defaults: CA1501 depth >5, CA1502 cc >25, CA1505 MI <10, CA1506 coupling >95 (type) / >40 (member); CodeMetricsConfig.txt (AdditionalFiles) overrides. A build can inject a config without touching the project: -p:CustomAfterMicrosoftCommonTargets=<targets that add EditorConfigFiles> (verified in MSBuild source). SonarAnalyzer.CSharp/VisualBasic 10.35: SONAR Source-Available License v1.0 since 10.3 (LGPL-3.0 up to 9.32); not open source, forbids 'Competing' use. FSharpLint 0.27.0 (2026-06-05): MIT, dotnet tool dotnet-fsharplint, runs on .NET 10 (rollForward LatestMajor); 'dotnet fsharplint lint --format msbuild --lint-config <json> <fsproj|sln>' exits non-zero on any warning; a config file enables only the rules it lists. Its size/complexity rules are all off by default; its on-by-default rules are naming, style and hints.
  
  1. Rules/tools. (a) C#/VB: CA1501, CA1505, CA1506; drop CA1502 because CRAP<=10 already caps cc at 10, below 25. F#: FSharpLint with only its smell rules: nestedStatements FL0015, maxLinesInFunction FL0025, maxLinesInMember FL0026, maxNumberOfItemsInTuple FL0051, maxNumberOfFunctionParameters FL0052, maxNumberOfMembers FL0053, maxNumberOfBooleanOperatorsInCondition FL0054 (cyclomaticComplexity FL0071 dropped like CA1502). No Sonar in the guides. (b) as (a) plus CA1502 and FL0071. (c) FSharpLint's default config (naming/style/hints) instead of or besides the smell rules. (d) also name SonarAnalyzer as an optional extra with its licence. Recommended: (a).
  2. Form. (a) Documented commands in a short 'Code-smell check' section of each guide, like 'Security scanners', using SwarmForge-owned config shipped under swarmforge/tools/csharp/ (a .targets plus .globalconfig injected by -p:CustomAfterMicrosoftCommonTargets for C#/VB; an fsharplint.json passed by --lint-config and dotnet-fsharplint installed with --tool-path under ./tmp for F#); the reader keeps findings in changed files. No code, no project changes. (b) sf-cs/sf-vb/sf-fs 'smell [--all] [--base REF]' subcommands that run the same, keep findings in changed files and exit 0/1/2, with bb tests. Recommended: (a).
  3. Cleaner prompt. (a) One language-neutral bullet after DRY: run the code-smell check the project language's guide names on the task's changed files when the guide names one and it runs here; fix findings in new code where reasonable and record any left as 'Constraint: code-smell finding left: <where> | <reason>'; a missing or failing check does not block and is recorded as 'Not-tested: code-smell check: <reason>'. The engineering article's Existing Code section adds 'code-smell' to its gate list so findings in existing code are recorded, not restructured without approval. (b) as (a) but findings in new code must be fixed before handoff once the check runs. Recommended: (a).
  4. Thresholds. (a) The tools' defaults above (CA1501 5, CA1505 10, CA1506 95/40; FSharpLint nesting 8, 100 lines per function/member, 4 tuple items, 5 parameters, 32 members, 4 boolean operators); no CodeMetricsConfig.txt. (b) Tighter SwarmForge values (name them). Recommended: (a).
  5. Route. (a) bugfix route (specifier, coder, cleaner, qa) if 2a: Markdown and config only, no architecture or hardening surface. (b) keep the feature route (fits 2b). Recommended: (a).
```

```text
type: answer
from: operator
answer: All recommended: 1 C#/VB CA1501, CA1505, CA1506 (not CA1502); F# FSharpLint with only its size and complexity rules except FL0071; no Sonar. 2 documented commands in a short Code-smell check section per guide, configs under swarmforge/tools/csharp/. 3 one language-neutral cleaner bullet: run the guide's check on changed files, fix findings in new code where reasonable, record leftovers as Constraint, missing tool is Not-tested and does not block; Existing Code section lists code-smell among its gates. 4 tool defaults, no CodeMetricsConfig.txt. 5 route specifier,coder,cleaner,qa.
```

```text
type: route
from: operator
route: specifier,coder,cleaner,qa
```

```text
type: review
from: specifier
commit: 2117f85
files: -
summary: Spec: encouraged .NET code-smell check (CA1501/1505/1506, FSharpLint size rules) as documented commands; cleaner runs it, non-blocking
```

```text
type: approval
from: operator
```

```text
type: handoff
from: specifier
to: coder
branch: swarm/encourage-a-code-smell-check-df99fe9
commit: 2117f85
files: -
summary: specifier work complete
```

```text
type: handoff
from: coder
to: cleaner
branch: swarm/encourage-a-code-smell-check-df99fe9
commit: 2fab919
files: swarmforge/constitution/articles/engineering.md,swarmforge/languages/csharp.md,swarmforge/languages/fsharp.md,swarmforge/languages/vbnet.md,swarmforge/roles/cleaner.md,swarmforge/tools/csharp/resources/fsharplint.json,swarmforge/tools/csharp/resources/smell.globalconfig,swarmforge/tools/csharp/resources/smell.targets,test/sf_tool_test.sh
summary: Code-smell configs, guide sections, cleaner bullet, Existing Code gate, install assertion
```

```text
type: handoff
from: cleaner
to: qa
branch: swarm/encourage-a-code-smell-check-df99fe9
commit: aa0378a
files: swarmforge/languages/csharp.md
summary: Cleanup found nothing to cut; smell build now passes EnableNETAnalyzers=true
```

```text
type: handoff
from: qa
to: done
branch: swarm/encourage-a-code-smell-check-df99fe9
commit: 2213300
files: swarmforge/languages/csharp.md
summary: QA passed; smell build gains analyzer ran-check; dotnet runs Not-tested
```
