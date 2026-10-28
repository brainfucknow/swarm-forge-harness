---
id: keep-mutation-manifests-on-an-2a5978d
created: 2026-10-28
stage: done
route: specifier,coder,cleaner,architect,hardener,qa
---

# Keep mutation manifests on an orphan metrics branch

Keep mutation manifests (the .metrics/ files and the manifest header blocks the hardener writes into feature files) out of the tree and out of PRs. The operator decided they are a cache: store them on an orphan branch, swarmforge/metrics, detached from main. This replaces paused task ec13ecd, which kept them in git notes; its work is on branch old/ec13ecd-git-notes (also swarm/ec13ecd on GitHub) for reference only. Re-specify against the new git task store.

## Records

```text
type: created
from: operator
branch: swarm/keep-mutation-manifests-on-an-2a5978d
commit: e7bd58c
route: specifier,coder,cleaner,architect,hardener,qa
```

```text
type: question
from: specifier
question: Specifier questions for the metrics cache branch. I take as settled, from the spike note (docs/spikes/git-task-store/design.md, 'Mutation manifests: the cache branch'): swarmforge/metrics is an orphan branch whose tree mirrors .metrics/ by path; sf start copies each cached file the worktree's .metrics/ lacks and leaves existing files alone; sf handoff (and so sf approve, which calls it) commits each changed file to the branch with commit-tree plus update-ref, touching no worktree, newest copy wins, deleted files stay cached, and nothing is saved when the handoff is refused; the gherkin-mutator wrapper moves the stamp and manifest block between the feature file and .metrics/gherkin/<feature path from the worktree root>; only clj-mutate and gherkin-mutator are covered (mutate4go/mutate4java footers and Stryker's tmp/ files stay as they are); sf does not push (SWARMFORGE_REMOTE belongs to the sharing task) and handoff-protocol.md documents 'git push origin swarmforge/metrics'; no squash command. Say if any of that is wrong. Questions:
  1. Which files go to the cache? (a) only .metrics/mutate/ and .metrics/gherkin/ (recommended: .metrics/crap.edn and other reports are per-run output, not manifests); (b) everything under .metrics/.
  2. Commands? (a) automatic only: start loads, handoff saves, the wrapper moves the block; no new sf subcommand (recommended, smallest PR); (b) also 'sf metrics load|save <id>' for manual use; (c) (b) plus ec13ecd's 'sf manifest tested' that prints Tested: trailer lines for the hardener.
  3. Output? (a) unchanged: sf start and sf handoff print what they print today (recommended); (b) sf start adds 'METRICS: <n> loaded from swarmforge/metrics' before the --- line.
  4. Unignored .metrics/? (a) install.sh adds '.metrics/' to .gitignore once, and sf does not check (recommended); (b) also sf start skips the load with a warning when git does not ignore .metrics/.
  5. PR size? (a) one task: cache branch, install ignore line, and gherkin block move (recommended: the request names both, and the cache holds Gherkin manifests only once the block moves); (b) two tasks: this one does the cache branch and install line, a follow-up does the gherkin-mutator block move.
```

```text
type: answer
from: operator
answer: 1a only .metrics/mutate/ and .metrics/gherkin/; 2a automatic only, no new sf subcommand; 3a output unchanged; 4a install.sh adds .metrics/ to .gitignore, sf does not check; 5a one task: cache branch, install line and the gherkin block move. The settled points in your summary stand.
```

```text
type: review
from: specifier
commit: db312fb
files: test/features/metrics_cache/README.md,test/features/metrics_cache/gherkin_block.feature,test/features/metrics_cache/install_ignore.feature,test/features/metrics_cache/metrics_load.feature,test/features/metrics_cache/metrics_save.feature,test/qa/metrics_cache.md
summary: Gherkin and QA procedure for the swarmforge/metrics cache branch and gherkin block move
```

```text
type: approval
from: operator
```

```text
type: handoff
from: specifier
to: coder
branch: swarm/keep-mutation-manifests-on-an-2a5978d
commit: db312fb
files: -
summary: specifier work complete
```

```text
type: handoff
from: coder
to: cleaner
branch: swarm/keep-mutation-manifests-on-an-2a5978d
commit: ae2ca6a
files: install.sh,swarmforge/README.md,swarmforge/constitution/articles/engineering.md,swarmforge/constitution/articles/project.md,swarmforge/handoff-protocol.md,swarmforge/roles/cleaner.md,swarmforge/roles/hardener.md,swarmforge/tools.conf,swarmforge/tools/sf/resources/sf/keep-feature-block.sh,swarmforge/tools/sf/spec/sf/architecture_spec.clj,swarmforge/tools/sf/spec/sf/feature_block_spec.clj,swarmforge/tools/sf/spec/sf/gherkin_cache_spec.clj,swarmforge/tools/sf/spec/sf/metrics_spec.clj,swarmforge/tools/sf/spec/sf/task_flow_spec.clj,swarmforge/tools/sf/spec/sf/tool_spec.clj,swarmforge/tools/sf/spec/sf/wrapper_spec.clj,swarmforge/tools/sf/src/sf/feature_block.clj,swarmforge/tools/sf/src/sf/gherkin_cache.clj,swarmforge/tools/sf/src/sf/metrics.clj,swarmforge/tools/sf/src/sf/store.clj,swarmforge/tools/sf/src/sf/task.clj,swarmforge/tools/sf/src/sf/tool.clj,swarmforge/tools/sf/src/sf/wrapper.clj,test/acceptance/src/acceptance/generate.clj,test/acceptance/src/acceptance/metrics_steps.clj,test/acceptance/src/acceptance/store_steps.clj,test/acceptance/src/acceptance/support.clj
summary: Metrics cache branch: start loads, handoff saves, gherkin-mutator wrapper moves the block, install ignores .metrics/
```

```text
type: handoff
from: cleaner
to: architect
branch: swarm/keep-mutation-manifests-on-an-2a5978d
commit: ff9d3c4
files: swarmforge/tools/sf/spec/sf/gherkin_cache_spec.clj,swarmforge/tools/sf/src/sf/gherkin_cache.clj
summary: Cleaned: gherkin-cache main testable, CRAP and DRY clean
```

```text
type: handoff
from: architect
to: hardener
branch: swarm/keep-mutation-manifests-on-an-2a5978d
commit: f1593c4
files: swarmforge/tools/sf/property/sf/feature_block_property.clj,swarmforge/tools/sf/spec/sf/architecture_spec.clj,swarmforge/tools/sf/spec/sf/git_spec.clj,swarmforge/tools/sf/src/sf/git.clj,swarmforge/tools/sf/src/sf/metrics.clj,swarmforge/tools/sf/src/sf/store.clj,swarmforge/tools/sf/src/sf/task.clj
summary: Git plumbing split into sf.git with a shared CAS ref move; metrics no longer depends on the task store
```

```text
type: handoff
from: hardener
to: qa
branch: swarm/keep-mutation-manifests-on-an-2a5978d
commit: cdc36b8
files: swarmforge/tools/sf/spec/sf/git_spec.clj,swarmforge/tools/sf/spec/sf/store_spec.clj,swarmforge/tools/sf/spec/sf/task_flow_spec.clj,test/features/metrics_cache/metrics_load.feature,test/features/metrics_cache/metrics_save.feature
summary: Hardened: mutation 0 survivors, Gherkin soft 0 survivors, CRAP at most 9.6, DRY clean
```

```text
type: question
from: qa
question: QA procedure section 4 contradicts the Gherkin rule for restoring the block. Section 1.2 caches the text 'g1' at .metrics/gherkin/features/x.feature, sf start loads it into DD, and section 4.1 adds features/x.feature there. The wrapper, as gherkin_block.feature says ('when .metrics/gherkin/<path> exists and the feature file has no block, the wrapper puts its lines and a blank line at the top'), prepends 'g1' before the run; after the run it stashes only the real block, so 'g1' and a blank line stay in the committed feature file. Steps 4.2-4.4 then fail (git status shows ' M features/x.feature', and sf handoff D refuses the dirty worktree). With that test cache removed from DD, all 72 scripted checks in sections 1-5 pass, as do bb spec, bb property, test/acceptance.sh, sf_test.sh, sf_tool_test.sh; CRAP max 9.6, DRY clean. Options: (a) recommended: QA changes sf.gherkin-cache/restore! to put the cached text on only when it is a block (sf.feature-block/split finds one and nothing follows it), else leave the feature alone, with a unit spec; a cache file that is not a block can then never leak into a tracked feature. (b) no behavior change: the procedure's section 1 test data is at fault; QA records section 4 as run with the colliding cache file removed, and hands off. Which?
```

```text
type: answer
from: operator
answer: (a) restore! puts the cached text on only when it is a real block (sf.feature-block/split finds one and nothing follows it), else leaves the feature alone; add a unit spec.
```

```text
type: handoff
from: qa
to: done
branch: swarm/keep-mutation-manifests-on-an-2a5978d
commit: f626206
files: swarmforge/tools/sf/spec/sf/feature_block_spec.clj,swarmforge/tools/sf/spec/sf/gherkin_cache_spec.clj,swarmforge/tools/sf/src/sf/feature_block.clj,swarmforge/tools/sf/src/sf/gherkin_cache.clj
summary: QA passed: restore ignores a cache that is not a block; procedure, suites, CRAP, DRY clean
```
