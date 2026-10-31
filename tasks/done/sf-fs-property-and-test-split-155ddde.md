---
id: sf-fs-property-and-test-split-155ddde
created: 2026-10-31
stage: done
route: specifier,coder,qa
---

# sf-fs property and test split work with Expecto

sf-fs property runs dotnet test --filter Category=Property and sf-fs test runs Category!=Property, as documented in swarmforge/languages/fsharp.md for xUnit traits. Expecto tests (via YoloDev.Expecto.TestSdk) have no categories or traits; the adapter only exposes FullyQualifiedName. Verified on auctions-api-fsharp-mainstream with an Expecto testProperty inside testList "Property": Category=Property matched no test, Category!=Property ran the property with the unit tests, while FullyQualifiedName~Property. selected exactly the property and FullyQualifiedName!~Property. excluded it. So for an Expecto project sf-fs property is a silent no-op and sf-fs test and coverage include properties. Make the F# property/test split work for Expecto projects while keeping xUnit trait projects working, and update the F# guide.

## Records

```text
type: created
from: operator
branch: swarm/sf-fs-property-and-test-split-155ddde
commit: 1a0f5df
route: specifier,coder,qa
```

```text
type: question
from: specifier
question: Observed on a scratch copy of auctions-api-fsharp-mainstream with an Expecto [<Tests>] testList "Property" (1 property, 64 unit tests): the combined filters Category=Property|FullyQualifiedName~Property. (1 test) and Category!=Property&FullyQualifiedName!~Property. (64 tests) both work with YoloDev.Expecto.TestSdk; a filter matching nothing prints 'No test matches the given testcase filter' and exits 0.
  1. How should sf-fs choose the split? (a) Recommended: always use the combined filters above for property, test and coverage, no project detection; works for xUnit, Expecto and mixed solutions, but any xUnit test whose fully qualified name contains 'Property.' (case-insensitive, e.g. module UserProperty or namespace Domain.Property) is then treated as a property. (b) Detect Expecto (a project referencing YoloDev.Expecto.TestSdk under the working directory) and use the FullyQualifiedName filter only there, trait filter otherwise; mixed solutions get one or the other. (c) Keep the trait filter by default; Expecto projects opt in with an environment variable or option.
  2. Expecto marking convention: (a) Recommended: property tests live in a testList named exactly "Property" at any nesting depth, so their names contain the segment 'Property.'; (b) another list name you prefer (say which).
  3. Zero matches: (a) Recommended: keep dotnet's exit status and document the 'No test matches' line as the sign of a missing marker; (b) make sf-fs property exit non-zero when no test matched, which also fails projects that have no property tests yet.
  4. Scope: (a) Recommended: change sf-fs only; sf-cs and sf-vb keep Category!=Property / Category=Property; (b) apply the same split to sf-vb too.
```

```text
type: answer
from: operator
answer: Operator, verbatim: "the only reasonable option is to detect expecto style since you can have xunit projecs with f#, the list is project specific, keep dotnet's exit status and document the 'No test matches' line. 4 a". Mapped to the questions: 1b, detect Expecto per test project (a project referencing YoloDev.Expecto.TestSdk) and use the name filter only there, since F# projects can also use xUnit. 2: the property test list name is project specific, not a fixed "Property". 3a: keep dotnet's exit status and document the "No test matches" line. 4a: sf-fs only.
```

```text
type: review
from: specifier
commit: c230a3c
files: -
summary: sf-fs detects Expecto per test project; SwarmForgePropertyTestList names the property list; criteria AC-1..AC-7 and QA in c230a3c
```

```text
type: approval
from: operator
```

```text
type: handoff
from: specifier
to: coder
branch: swarm/sf-fs-property-and-test-split-155ddde
commit: c230a3c
files: -
summary: specifier work complete
```

```text
type: handoff
from: coder
to: qa
branch: swarm/sf-fs-property-and-test-split-155ddde
commit: 57642fc
files: swarmforge/languages/fsharp.md,swarmforge/tools/csharp/src/sfcs/cli.clj,swarmforge/tools/csharp/test/sfcs/core_test.clj
summary: sf-fs runs one dotnet test per test project when Expecto is present, filtering by SwarmForgePropertyTestList; F# guide updated
```

```text
type: handoff
from: qa
to: done
branch: swarm/sf-fs-property-and-test-split-155ddde
commit: 65503a9
files: -
summary: QA passed: sf-fs Expecto/xUnit/mixed split verified on real dotnet; no fixes
```
