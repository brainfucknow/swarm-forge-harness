---
id: add-ruby-support-cac94d7
created: 2026-11-04
---

# Add Ruby support

Operator request: add Ruby support, like the existing language packs (Clojure, JavaScript/TypeScript, C#, F#, VB.NET). Roles working on a Ruby project need: tests and property tests, coverage, CRAP (complexity with coverage), DRY, mutation testing, and the security code scanner and dependency audit that the security reviewer runs (see swarmforge/languages/*.md and the constitution's engineering article). Add a swarmforge/languages/ruby.md guide and whatever wrapper or tools.conf rows the chosen tools need, pinned like the others. Prefer existing maintained Ruby tools and the simplest wrapper; keep the guide short; no language-specific text in general role prompts. The specifier should verify each candidate tool and ask the operator to choose where there is a real choice (test runner, mutation tool, how CRAP is computed, DRY tool, scanners, whether a wrapper like sf-js is needed).
