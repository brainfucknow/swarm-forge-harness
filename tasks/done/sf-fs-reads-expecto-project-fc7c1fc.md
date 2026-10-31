---
id: sf-fs-reads-expecto-project-fc7c1fc
created: 2026-10-31
stage: done
route: coder,qa
---

# sf-fs reads Expecto project files as XML

Review findings on brainfucknow/swarm-forge-harness#52 (Codex), both reproduced with a fake dotnet against swarmforge/bin/sf-fs on the PR head:
P1 (https://github.com/brainfucknow/swarm-forge-harness/pull/52#discussion_r4433311198): declared-property-list in swarmforge/tools/csharp/src/sfcs/cli.clj returns the raw source text of <SwarmForgePropertyTestList>. A project declaring <SwarmForgePropertyTestList>Laws&amp;Properties</SwarmForgePropertyTestList> (the only valid way to write Laws&Properties) gets --filter FullyQualifiedName~Laws\&amp;Properties. instead of Laws\&Properties., so the property suite matches nothing and exits 0. Use the decoded MSBuild/XML value before escaping it for the vstest filter.
P2 (https://github.com/brainfucknow/swarm-forge-harness/pull/52#discussion_r4433311204): references-package? matches raw project text, so a commented-out <!-- <PackageReference Include="YoloDev.Expecto.TestSdk" ... /> --> in an xUnit F# project classifies it as Expecto; sf-fs property then exits 2 asking for SwarmForgePropertyTestList. Only active elements count, for the Expecto reference, Microsoft.NET.Test.Sdk and IsTestProject alike.
Read project files as XML (comments ignored, entities decoded, legacy MSBuild xmlns tolerated) instead of regex over text. Keep every other behaviour of the PR, add tests for both cases to swarmforge/tools/csharp/test/sfcs/core_test.clj, and keep bb test and test/sf_test.sh green.

## Records

```text
type: created
from: operator
branch: swarm/sf-fs-reads-expecto-project-fc7c1fc
commit: bef3125
route: coder,qa
```

```text
type: handoff
from: coder
to: qa
branch: swarm/sf-fs-reads-expecto-project-fc7c1fc
commit: 847ef82
files: swarmforge/languages/fsharp.md,swarmforge/tools/csharp/src/sfcs/cli.clj,swarmforge/tools/csharp/src/sfcs/project.clj,swarmforge/tools/csharp/test/sfcs/core_test.clj
summary: sf-fs reads project files as XML: decoded property list, comments ignored
```

```text
type: handoff
from: qa
to: coder
branch: swarm/sf-fs-reads-expecto-project-fc7c1fc
commit: 64d271a
files: -
summary: Code passes QA; cut the Expecto section of swarmforge/languages/fsharp.md to the keep list in 64d271a (operator: too much chatter)
```

```text
type: handoff
from: coder
to: qa
branch: swarm/sf-fs-reads-expecto-project-fc7c1fc
commit: bc92566
files: swarmforge/languages/fsharp.md
summary: F# guide Expecto section cut to QA's keep list
```

```text
type: handoff
from: qa
to: done
branch: swarm/sf-fs-reads-expecto-project-fc7c1fc
commit: 0056707
files: -
summary: QA passed: sf-fs reads project files as XML; F# guide cut to 53 lines
```
