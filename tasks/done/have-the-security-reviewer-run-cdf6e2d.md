---
id: have-the-security-reviewer-run-cdf6e2d
created: 2026-11-01
stage: done
route: specifier,coder,qa
---

# Have the security reviewer run the language's security scanners

Operator request: like CRAP, DRY and mutation, each language guide (swarmforge/languages/*.md) should name a security code scanner (vulnerable patterns in source) and a dependency audit (known-vulnerable packages), pinned like other tools where they come from tools.conf. When the security reviewer is on a task's route it runs the language's code scanner on the code the task changed (not a blanket whole-repository scan), and runs the dependency audit only when the task changes dependency files (e.g. bb.edn, deps.edn, package.json, *.csproj). Findings are handled as the role does today: reproduce, fix within scope, or ask. Missing scanner: if the project's language has no scanner named, or it is not installed, the security reviewer blocks (sf ask and stop) unless the project file (swarmforge/constitution/articles/project.md) states a reason why that scanner is absent; with a stated reason it records a Not-tested trailer and continues. This partly reverses the current 'no blanket scanner run' line in swarmforge/roles/security-reviewer.md; keep scans scoped. Candidate tools to verify: Clojure clj-holmes and nvd-clojure; JavaScript semgrep or eslint security rules and npm audit; .NET Roslyn security analyzers and dotnet list package --vulnerable. Keep general role text language-neutral; language specifics go in swarmforge/languages/*.md.

## Records

```text
type: created
from: operator
branch: swarm/have-the-security-reviewer-run-cdf6e2d
commit: 09b141b
route: specifier,coder,cleaner,architect,hardener,qa
```

```text
type: question
from: specifier
question: Tool check (Nov 2026) and open decisions. Recommended answer marked (R).
  
  1. Tools per language. Findings: clj-holmes exists but is unmaintained since 2022 (rules repo too): 9 security rules, scans only .clj files (not .cljc/.cljs/.bb), ships as a native binary and on Clojars as org.clojars.clj-holmes/clj-holmes 1.4.0. nvd-clojure is maintained (README shows 5.3.0, on Clojars), reads a classpath (Babashka: bb print-deps --format classpath), and needs an NVD API key (NVD_API_TOKEN) plus a DependencyCheck database download. clj-watson is a maintained alternative, but it reads deps.edn only and needs an NVD key or GITHUB_TOKEN. eslint-plugin-security 4.2.1 is maintained but noisy (detect-object-injection), and it needs the typescript-eslint parser for .ts files. semgrep 1.183.0 (PyPI) parses JS and TS natively, but it fetches rulesets from semgrep.dev at run time. Security Code Scan (.NET) has been unmaintained since 2022, so I would drop it. The .NET SDK's built-in analyzers (AnalysisModeSecurity=All) cover C# and VB but not F#. No maintained F# security scanner exists.
     (R) Clojure: code clj-holmes (limits stated in the guide), audit nvd-clojure. JS/TS: code semgrep with p/javascript and p/typescript on the changed files, audit by the package manager (npm audit, pnpm audit, yarn npm audit or yarn audit, bun audit). C# and VB: code dotnet build --no-incremental -p:AnalysisModeSecurity=All, reading only findings in changed files; audit dotnet list package --vulnerable --include-transitive (dotnet package list on .NET 10+). F#: the guide says no code scanner exists (an F# project states its reason in project.md); same dotnet audit. Alternatives: (b) eslint-plugin-security instead of semgrep; (c) clj-watson instead of nvd-clojure; (d) name no Clojure code scanner.
     Dependency files per guide (R): Clojure deps.edn, bb.edn, project.clj; JS package.json plus lockfiles; .NET *.csproj/*.fsproj/*.vbproj, Directory.Packages.props, Directory.Build.props, packages.lock.json.
  
  2. Installation and pinning. (R) Add rows to tools.conf with the existing clj kind (pinned Maven version, no sf code change): clj-holmes org.clojars.clj-holmes/clj-holmes 1.4.0 main clj-holmes.main, with clojure.md pinning its rules to clj-holmes-rules commit fbf5e2eeebe9a85e2dc568c04b482a50fc482a02 via fetch-rules -r; and nvd-clojure nvd-clojure/nvd-clojure <current Clojars version> main nvd.task.check. Pin semgrep in javascript.md's command (uvx semgrep@1.183.0, metrics off). Use the SDK and package-manager audits as installed. No new sf-js/sf-cs subcommands. Alternatives: (b) new sf-js/sf-cs 'security' and 'audit' subcommands that scope findings to changed lines like crap/dry (larger PR); (c) a new tools.conf kind for PyPI or binary tools (larger PR).
  
  3. 'Missing' means: the language has no guide or the guide names no tool, or the named tool cannot be installed and run here (including a missing key such as NVD_API_TOKEN). (R) Yes. A language with no guide (Go, Java) is missing; adding Go/Java guides is out of scope.
  
  4. Exact project.md text. (R) One bullet under '## Verification' explaining the contract, plus the lines a project adds, one per absent tool:
     'Security code scanner absent: <reason>'
     'Dependency audit absent: <reason>'
     The shipped project.md carries the explanatory bullet only, with no absent line. The reviewer then records 'Not-tested: <code scanner|dependency audit>: absent, <reason from project.md>' and continues.
  
  5. Route. The change is prompt and guide text plus two tools.conf rows; no wrapper code. (R) specifier,coder,qa, with QA running sf check, test/sf_test.sh and test/sf_tool_test.sh, sf tool list, and each named command where this environment allows. Note: clojars.org, semgrep.dev, the NVD API and learn.microsoft.com are unreachable from this session, so QA records Not-tested for those. Alternative: keep the current feature route.
```

```text
type: answer
from: operator
answer: All recommended: 1 Clojure clj-holmes (limits stated: unmaintained since 2022, 9 rules, .clj only) and nvd-clojure (needs an NVD API key); JS/TS semgrep on changed files plus the package manager's audit; C#/VB the .NET SDK security analyzers plus dotnet list package --vulnerable; F# has no code scanner, so an F# project states its reason in project.md. 2 two pinned tools.conf clj rows for clj-holmes and nvd-clojure, no sf change; semgrep pinned in the JS guide's command; no new sf-js or sf-cs subcommands. 3 missing means no guide, no tool named, or the tool cannot be installed or run here (including a missing key); Go and Java out of scope. 4 project.md lines 'Security code scanner absent: <reason>' and 'Dependency audit absent: <reason>', explained by one bullet under Verification. 5 narrow the route to specifier,coder,qa.
```

```text
type: route
from: operator
route: specifier,coder,qa
```

```text
type: review
from: specifier
commit: 6dde401
files: swarmforge/constitution/articles/engineering.md,swarmforge/languages/clojure.md,swarmforge/tools.conf,swarmforge/tools/sf/property/sf/catalogue_property.clj,swarmforge/tools/sf/spec/sf/architecture_spec.clj,swarmforge/tools/sf/spec/sf/catalogue_spec.clj,swarmforge/tools/sf/spec/sf/clone_spec.clj,swarmforge/tools/sf/spec/sf/config_repo_spec.clj,swarmforge/tools/sf/spec/sf/config_spec.clj,swarmforge/tools/sf/spec/sf/tool_spec.clj,swarmforge/tools/sf/spec/sf/wrapper_spec.clj,swarmforge/tools/sf/src/sf/catalogue.clj,swarmforge/tools/sf/src/sf/clone.clj,swarmforge/tools/sf/src/sf/config.clj,swarmforge/tools/sf/src/sf/shell.clj,swarmforge/tools/sf/src/sf/tool.clj,swarmforge/tools/sf/src/sf/wrapper.clj,test/acceptance/src/acceptance/steps.clj,test/features/sf_tool/README.md,test/features/sf_tool/catalogue_location.feature,test/features/sf_tool/catalogue_refs.feature,test/features/sf_tool/clj_versions.feature,test/features/sf_tool/entry_point_guard.feature,test/features/sf_tool/pinned_refs.feature,test/features/sf_tool/wrappers.feature,test/features/sf_tool/write_guard.feature,test/sf_test.sh,test/sf_tool_test.sh
summary: Spec: security reviewer runs the guide's code scanner on changed files and the dependency audit on dependency-file changes; criteria and QA in 6dde401
```

```text
type: changes-requested
from: operator
reason: Codex review of spec 6dde401, verified against its text; fix all four: (1) Yarn 2+ audit must cover transitive packages and all workspaces: yarn npm audit --all --recursive; add a transitive vulnerable dependency to manual QA. (2) AnalysisModeSecurity=All selects rules but does not enable analysis; .NET Standard/Framework targets need it enabled and projects can disable analyzers. The guide passes -p:EnableNETAnalyzers=true with AnalysisModeSecurity=All and tells the reviewer to confirm analyzers ran; QA uses a file with a known security violation (e.g. a CA5350/CA5351 weak-crypto call or CA2100 SQL text) so a clean build alone never counts as a scan. (3) Criteria 1a/1f forbid scanning more than changed files, but the .NET scanner must build the project: allow analysis of the enclosing project or solution when the scanner needs compilation context, and limit actionable findings to changed files. (4) Before .NET 10, dotnet list package needs restored assets: run dotnet restore for the affected project or solution before the audit. Keep the spec short.
```

```text
type: review
from: specifier
commit: d79308a
files: README.md,install.sh,swarmforge/README.md,swarmforge/constitution/articles/engineering.md,swarmforge/constitution/articles/handoffs.md,swarmforge/constitution/articles/workflow.md,swarmforge/handoff-protocol.md,swarmforge/languages/csharp.md,swarmforge/languages/javascript.md,swarmforge/roles/architect.md,swarmforge/roles/cleaner.md,swarmforge/roles/coder.md,swarmforge/roles/hardener.md,swarmforge/roles/qa.md,swarmforge/roles/specifier.md,swarmforge/tools/javascript/features/README.md,test/acceptance/src/acceptance/generate.clj,test/acceptance/src/acceptance/install_steps.clj,test/features/install/README.md,test/features/install/bin_tracked.feature,test/features/lore_trailers/README.md,test/features/manifest_guard/README.md,test/features/metrics_cache/README.md,test/features/sf_tool/README.md,test/features/task_store/README.md,test/sf_tool_test.sh
summary: Spec revised for the four review points (Yarn --all --recursive, .NET analyzers enabled and confirmed, project context for compiled scanners, restore before audit); criteria and QA in d79308a
```

```text
type: approval
from: operator
```

```text
type: handoff
from: specifier
to: coder
branch: swarm/have-the-security-reviewer-run-cdf6e2d
commit: d79308a
files: -
summary: specifier work complete
```

```text
type: handoff
from: coder
to: qa
branch: swarm/have-the-security-reviewer-run-cdf6e2d
commit: b41b2ef
files: swarmforge/constitution/articles/project.md,swarmforge/languages/clojure.md,swarmforge/languages/csharp.md,swarmforge/languages/fsharp.md,swarmforge/languages/javascript.md,swarmforge/languages/vbnet.md,swarmforge/roles/security-reviewer.md,swarmforge/tools.conf
summary: Scanner sections in the reviewer role, project.md and each guide; clj-holmes and nvd-clojure pinned
```

```text
type: handoff
from: qa
to: done
branch: swarm/have-the-security-reviewer-run-cdf6e2d
commit: 4c86380
files: swarmforge/languages/clojure.md
summary: QA passed: reviewer and guide scanner text verified; JS audits run; clj-holmes scan run off-wrapper; Clojure CLI, NVD, semgrep registry and dotnet Not-tested
```
