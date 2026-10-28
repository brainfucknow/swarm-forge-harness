# Historical QA: clj-mutate guidance

This records the completed task "Note the clj-mutate test command for the
hardener, and fix a stale QA step". The original editing checklist remains
in Git history.

The [Clojure guide](../../swarmforge/languages/clojure.md) explains why
clj-mutate must run from the worktree root with the worker's own classpath
and a custom test command, rather than a symlinked `bb.edn`. It is the current
reference for this setup.

[Tool-wrapper QA](sf_tool.md#qa-4-crap4clj-with-clojuretest-and-no-clojure-cli) checks the
CRAP report; it expects `parse-cobertura` in `sfcs.cobertura`. The separate
task "Run crap4clj from the repository root" owns that working-directory
behavior. This note does not change role policies.
