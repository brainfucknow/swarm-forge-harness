---
id: run-semgrep-offline-with-53c43fa
created: 2026-11-05
---

# Run Semgrep offline with pinned rules

The Ruby and JS/TS guides run Semgrep with registry packs (--config p/ruby, p/javascript, p/typescript). Those need semgrep.dev at run time (blocked in sandboxed environments, where the scan hangs) and are not pinned. A research run showed semgrep 1.183.0 (LGPL) works fully offline against a pinned commit of github.com/semgrep/semgrep-rules fetched into ./tmp (example SHA 16b9e6d45f10649f6a531ec4838ed9924372c3bc, 2026-10-21), with --metrics=off --disable-version-check --error; exit 0 clean, 1 findings. Coverage: ruby/, javascript/ + typescript/, csharp/ (52 files), clojure/ (5 rules, scans .clj/.cljc/.cljs/.edn, not .bb); F# and VB.NET unsupported. Rules are under the Semgrep Rules License v1.0: fine for a project scanning its own code, so fetch at run time and never vendor them into the repo. Operator decision: switch ruby.md and javascript.md to the pinned local rules and offline command, keeping Semgrep as their primary code scanner (a missing primary scanner still blocks unless project.md records why). Add Semgrep as an encouraged, non-blocking extra for C# (next to the NetAnalyzers build) and Clojure (next to clj-holmes): run when available, record Not-tested when not. No sf code change and no tools.conf kind for pip/uv; pin the engine version and rules SHA in the guides. Keep the guides short.
