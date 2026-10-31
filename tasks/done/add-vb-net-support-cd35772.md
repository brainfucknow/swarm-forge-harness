---
id: add-vb-net-support-cd35772
created: 2026-10-31
stage: done
route: specifier,coder,cleaner,architect,hardener,qa
---

# Add VB.NET support

Add VB.NET (.vbproj, .vb) support to SwarmForge, like the F# support added in PR #40 ('Add F# language support'): a language wrapper through the shared .NET wrapper (sf-cs / sf-fs), a languages/vbnet.md guide, and the constitution's language tool table, covering tests, property tests, coverage, CRAP, DRY and mutation where the tools support VB.NET.

## Records

```text
type: created
from: operator
branch: swarm/add-vb-net-support-cd35772
commit: 29e09e2
route: specifier,coder,cleaner,architect,hardener,qa
```

```text
type: question
from: specifier
question: Plan: sf-vb for SDK-style .vbproj through the shared .NET wrapper, modelled on sf-fs: tools (dotnet/node/npx + jscpd, no Stryker), test/property (property tests marked <Trait("Category", "Property")>), coverage and CRAP from Coverlet's per-method complexity, line rate and reported .vb line span (no C# signature widening), plus languages/vbnet.md and the constitution tool table. Coverlet works on IL, so VB is covered the way F# is. Three choices remain:
  1. DRY tokenizer. jscpd 4.3.0 has two grammars for .vb. (a) --format visual-basic --formats-exts visual-basic:vb: the VB.NET-aware grammar, one extra flag because both grammars claim .vb (recommended). (b) --format vbnet: works with no mapping, but it is the legacy BASIC grammar and treats everything after '!' as a comment. I confirmed it drops the tokens in dict!Key lookups and the 1.5! type suffix, which can hide differences and report false clones.
  2. Mutation. Stryker.NET (checked at upstream HEAD 1b749ba) loads only .csproj/.fsproj and throws 'Language not supported' for VB, and I found no other maintained VB.NET mutator. (a) sf-vb mutate exits 2 with an explanation, as sf-fs does (recommended). (b) Leave the mutate command out of sf-vb.
  3. Generated VB files in DRY. sf-cs ignores *.Designer.cs. (a) Also ignore **/*.Designer.vb (My Project resources/settings and WinForms designers) (recommended). (b) No extra ignores, as with F#.
```

```text
type: answer
from: operator
answer: 1 (a) jscpd visual-basic grammar with --formats-exts visual-basic:vb. 2 (a) sf-vb mutate exits 2 with an explanation, as sf-fs does. 3 (a) ignore **/*.Designer.vb in DRY.
```

```text
type: review
from: specifier
commit: 5478324
files: -
summary: VB.NET spec: sf-vb via shared .NET wrapper; visual-basic jscpd grammar, mutate exits 2, Designer.vb ignored
```

```text
type: approval
from: operator
```

```text
type: handoff
from: specifier
to: coder
branch: swarm/add-vb-net-support-cd35772
commit: 5478324
files: -
summary: specifier work complete
```

```text
type: handoff
from: coder
to: cleaner
branch: swarm/add-vb-net-support-cd35772
commit: efb70fd
files: README.md,swarmforge/README.md,swarmforge/bin/sf-vb,swarmforge/constitution/articles/engineering.md,swarmforge/languages/vbnet.md,swarmforge/tools.conf,swarmforge/tools/csharp/src/sfcs/cli.clj,swarmforge/tools/csharp/src/sfvb/cli.clj,swarmforge/tools/csharp/test/sfcs/core_test.clj,swarmforge/tools/javascript/test/sfjs/architecture_test.clj,test/sf_tool_test.sh
summary: sf-vb via shared .NET wrapper: profile-driven F#/VB spots, visual-basic jscpd, Designer.vb ignored, mutate exits 2, vbnet guide
```

```text
type: handoff
from: cleaner
to: architect
branch: swarm/add-vb-net-support-cd35772
commit: c5c163a
files: swarmforge/tools/csharp/src/sfcs/cli.clj,swarmforge/tools/csharp/test/sfcs/core_test.clj
summary: cleaner: trimmed two docstrings; CRAP max 8, DRY clean, tests green
```

```text
type: handoff
from: architect
to: hardener
branch: swarm/add-vb-net-support-cd35772
commit: afd7d0d
files: swarmforge/tools/javascript/test/sfjs/architecture_test.clj
summary: architect: sf-vb boundaries hold; arch test now keeps sffs/sfvb apart from sf-js
```

```text
type: handoff
from: hardener
to: qa
branch: swarm/add-vb-net-support-cd35772
commit: c9ae83d
files: -
summary: hardener: sf-vb mutation 39/39 killed, CRAP max 8, DRY clean
```

```text
type: handoff
from: qa
to: done
branch: swarm/add-vb-net-support-cd35772
commit: f1cb07c
files: swarmforge/languages/vbnet.md,swarmforge/tools/csharp/src/sfcs/cobertura.clj,swarmforge/tools/csharp/test/sfcs/core_test.clj
summary: qa: real VB SDK A-D pass; fixed BOM Cobertura parse, corrected vbnet guide
```
