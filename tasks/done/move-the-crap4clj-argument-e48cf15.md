---
id: move-the-crap4clj-argument-e48cf15
created: 2026-10-28
stage: done
route: coder,qa
---

# Move the crap4clj argument rewrite into an sf namespace

The operator found that swarmforge/tools/sf/resources/sf/rewrite-coverage.sh holds too much logic in bash: argument parsing, path normalisation, symlink resolution and the coverage command. Move that logic into a pure, tested sf namespace (like sf.gherkin-cache for the gherkin-mutator wrapper) that takes the current folder, the arguments and the folders that exist, and returns the folder to run in and the rewritten arguments; the crap4clj wrapper becomes a thin bb call. Behaviour stays exactly as specified by test/features/sf_tool/crap4clj_directory.feature and crap4clj_coverage.feature, including the symlink fix (733f5e1). This reworks PR #31 ('Run crap4clj from the repository root') before it merges.

## Records

```text
type: created
from: operator
branch: swarm/move-the-crap4clj-argument-e48cf15
commit: b11fb93
route: coder,qa
```

```text
type: handoff
from: coder
to: qa
branch: swarm/move-the-crap4clj-argument-e48cf15
commit: ce172c5
files: swarmforge/tools/sf/property/sf/coverage_args_property.clj,swarmforge/tools/sf/resources/sf/rewrite-coverage.sh,swarmforge/tools/sf/spec/sf/architecture_spec.clj,swarmforge/tools/sf/spec/sf/coverage_args_spec.clj,swarmforge/tools/sf/spec/sf/coverage_rewrite_spec.clj,swarmforge/tools/sf/spec/sf/tool_spec.clj,swarmforge/tools/sf/spec/sf/wrapper_spec.clj,swarmforge/tools/sf/src/sf/coverage_args.clj,swarmforge/tools/sf/src/sf/coverage_rewrite.clj,swarmforge/tools/sf/src/sf/tool.clj,swarmforge/tools/sf/src/sf/wrapper.clj
summary: crap4clj argument rewrite moved into pure sf.coverage-args with a thin bb wrapper call
```

```text
type: handoff
from: qa
to: done
branch: swarm/move-the-crap4clj-argument-e48cf15
commit: be27bb7
files: swarmforge/tools/sf/resources/sf/rewrite-coverage.sh,swarmforge/tools/sf/spec/sf/wrapper_spec.clj
summary: QA passed; wrapper rewrite no longer reads the caller's bb.edn
```
