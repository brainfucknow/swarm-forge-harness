---
id: sf-fs-reads-expecto-project-fc7c1fc
created: 2026-10-31
---

# sf-fs reads Expecto project files as XML

Review findings on brainfucknow/swarm-forge-harness#52 (Codex), both reproduced with a fake dotnet against swarmforge/bin/sf-fs on the PR head:
P1 (https://github.com/brainfucknow/swarm-forge-harness/pull/52#discussion_r4433311198): declared-property-list in swarmforge/tools/csharp/src/sfcs/cli.clj returns the raw source text of <SwarmForgePropertyTestList>. A project declaring <SwarmForgePropertyTestList>Laws&amp;Properties</SwarmForgePropertyTestList> (the only valid way to write Laws&Properties) gets --filter FullyQualifiedName~Laws\&amp;Properties. instead of Laws\&Properties., so the property suite matches nothing and exits 0. Use the decoded MSBuild/XML value before escaping it for the vstest filter.
P2 (https://github.com/brainfucknow/swarm-forge-harness/pull/52#discussion_r4433311204): references-package? matches raw project text, so a commented-out <!-- <PackageReference Include="YoloDev.Expecto.TestSdk" ... /> --> in an xUnit F# project classifies it as Expecto; sf-fs property then exits 2 asking for SwarmForgePropertyTestList. Only active elements count, for the Expecto reference, Microsoft.NET.Test.Sdk and IsTestProject alike.
Read project files as XML (comments ignored, entities decoded, legacy MSBuild xmlns tolerated) instead of regex over text. Keep every other behaviour of the PR, add tests for both cases to swarmforge/tools/csharp/test/sfcs/core_test.clj, and keep bb test and test/sf_test.sh green.
