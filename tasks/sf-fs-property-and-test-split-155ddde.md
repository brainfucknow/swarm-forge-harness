---
id: sf-fs-property-and-test-split-155ddde
created: 2026-10-31
---

# sf-fs property and test split work with Expecto

sf-fs property runs dotnet test --filter Category=Property and sf-fs test runs Category!=Property, as documented in swarmforge/languages/fsharp.md for xUnit traits. Expecto tests (via YoloDev.Expecto.TestSdk) have no categories or traits; the adapter only exposes FullyQualifiedName. Verified on auctions-api-fsharp-mainstream with an Expecto testProperty inside testList "Property": Category=Property matched no test, Category!=Property ran the property with the unit tests, while FullyQualifiedName~Property. selected exactly the property and FullyQualifiedName!~Property. excluded it. So for an Expecto project sf-fs property is a silent no-op and sf-fs test and coverage include properties. Make the F# property/test split work for Expecto projects while keeping xUnit trait projects working, and update the F# guide.
