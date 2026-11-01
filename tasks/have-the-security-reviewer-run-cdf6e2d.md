---
id: have-the-security-reviewer-run-cdf6e2d
created: 2026-11-01
---

# Have the security reviewer run the language's security scanners

Operator request: like CRAP, DRY and mutation, each language guide (swarmforge/languages/*.md) should name a security code scanner (vulnerable patterns in source) and a dependency audit (known-vulnerable packages), pinned like other tools where they come from tools.conf. When the security reviewer is on a task's route it runs the language's code scanner on the code the task changed (not a blanket whole-repository scan), and runs the dependency audit only when the task changes dependency files (e.g. bb.edn, deps.edn, package.json, *.csproj). Findings are handled as the role does today: reproduce, fix within scope, or ask. Missing scanner: if the project's language has no scanner named, or it is not installed, the security reviewer blocks (sf ask and stop) unless the project file (swarmforge/constitution/articles/project.md) states a reason why that scanner is absent; with a stated reason it records a Not-tested trailer and continues. This partly reverses the current 'no blanket scanner run' line in swarmforge/roles/security-reviewer.md; keep scans scoped. Candidate tools to verify: Clojure clj-holmes and nvd-clojure; JavaScript semgrep or eslint security rules and npm audit; .NET Roslyn security analyzers and dotnet list package --vulnerable. Keep general role text language-neutral; language specifics go in swarmforge/languages/*.md.
