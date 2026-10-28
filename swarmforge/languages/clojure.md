# Clojure and Babashka tooling

The Clojure tools come from `swarmforge/tools.conf` and run through the
wrappers `sf tool ensure` writes to `.swarmforge/bin/`: clj-mutate for
mutation, crap4clj for CRAP, dry4clj for DRY. This file holds what a role
needs to know to run them on Babashka code.

## Mutation with clj-mutate

- Run clj-mutate from the worktree root. It keeps its manifest under
  `.metrics/mutate/` of the directory it runs from, and `sf start` and
  `sf handoff` load and save only the worktree root's.
- Do not give clj-mutate the test command `bb --config <path>/bb.edn spec`
  (or any `bb --config` test command). bb resolves the worker's symlinked
  `bb.edn` back to the real checkout, so it tests the unmutated sources and
  every mutant survives.
- Pass a `--test-command` that puts the worker's own copy of each `:paths`
  entry of the module's `bb.edn` on the classpath instead (for example `src`
  and `spec`, or `src`, `test`, `../core/src` and `../core/test`), with
  `--no-coverage`, which clj-mutate requires for a custom test command.
