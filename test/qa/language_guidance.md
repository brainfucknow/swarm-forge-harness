# QA: language guidance outside engineering rules

Acceptance contract: [language guidance](../../docs/features/language_guidance.feature).
Review the rendered documents and their links manually. Do not implement
string-matching tests or acceptance step handlers for constitution wording.

## Scope

Relocate guidance from `swarmforge/constitution/articles/engineering.md`
without changing requirements. Keep the full six-role route requested.

- Add short `swarmforge/languages/go.md` and `java.md` guides for the existing
  tool mappings and Java's dedicated-test-runner requirement.
- Put Clojure/Babashka tool installation and defaults in `clojure.md`.
- Consolidate C# and JavaScript/TypeScript wrapper commands, property-test
  conventions, scan/count alternatives and differential-mutation meanings
  in their existing guides. Reuse existing explanations instead of copying
  them again.
- In `engineering.md`, replace those language details with a mandatory
  instruction to read the relevant guide and links for all five languages;
  generalize language-specific guardrail examples. Update the constitution's
  article summary if needed. Do not change roles, commands or tool versions.

## Review procedure

1. Follow all five language links from `engineering.md`. Each must resolve
   under the installed `swarmforge/` tree without the root README.
2. Compare the task diff with its base. Account for each relocated clause
   in its guide: tool sources, installation, defaults, property conventions,
   mutation scope and scan alternatives. Preserve meaning without duplication.
3. Read the shared rules. Startup freshness, project-local
   wrappers, testability, property-test separation, APS, serial verification,
   worker limits and manifest protections must retain their meaning.
   Keep APS runtime selection shared even though it names implementation
   languages. Read each guide on its own for conflicting or lost advice.
4. Confirm no production behavior or automated tests of constitution, prompt
   or adapter text were added. Run existing role-required verification;
   report passing checks and unavailable tools separately.

The feature file is outside executable acceptance discovery intentionally.
Parsing and IR dry-checking validate its Gherkin structure, not the wording
or implementation of the constitution.
