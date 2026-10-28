---
id: move-the-crap4clj-argument-e48cf15
created: 2026-10-28
---

# Move the crap4clj argument rewrite into an sf namespace

The operator found that swarmforge/tools/sf/resources/sf/rewrite-coverage.sh holds too much logic in bash: argument parsing, path normalisation, symlink resolution and the coverage command. Move that logic into a pure, tested sf namespace (like sf.gherkin-cache for the gherkin-mutator wrapper) that takes the current folder, the arguments and the folders that exist, and returns the folder to run in and the rewritten arguments; the crap4clj wrapper becomes a thin bb call. Behaviour stays exactly as specified by test/features/sf_tool/crap4clj_directory.feature and crap4clj_coverage.feature, including the symlink fix (733f5e1). This reworks PR #31 ('Run crap4clj from the repository root') before it merges.
