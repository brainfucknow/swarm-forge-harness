---
id: encourage-a-code-smell-check-df99fe9
created: 2026-11-04
---

# Encourage a code-smell check for .NET projects

Operator request: add a code-smell check for C#, VB.NET and F# projects, similar to reek for Ruby. It should be encouraged, not merely optional: the language guides recommend it and the cleaner runs it on the task's changed files whenever it is available, but a missing tool does not block. Candidates to verify: for C# and VB the SDK's built-in code-metrics rules (CA1501, CA1502, CA1505, CA1506, enabled through .editorconfig or AnalysisMode), with SonarAnalyzer.CSharp / SonarAnalyzer.VisualBasic as an optional extra once its licence is checked; for F# FSharpLint (MIT). Read only findings in changed files, as the security scanner section of csharp.md does. Respect 'Existing code: ask before refactoring' (findings in pre-existing code are recorded, not fixed without approval). Decide whether it is a documented command or an sf-cs/sf-fs/sf-vb subcommand, and keep the guides short. dotnet is not installed here and learn.microsoft.com is blocked, so record what cannot be run as Not-tested.
