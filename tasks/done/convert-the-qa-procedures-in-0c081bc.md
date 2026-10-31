---
id: convert-the-qa-procedures-in-0c081bc
created: 2026-10-28
stage: done
route: specifier,coder,qa
---

# Convert the QA procedures in test/qa into tests, then delete them

The operator asked: test/qa holds many Markdown QA procedures (one per task, written by the specifier); convert them to tests, then delete them. Go through every check in test/qa/*.md, including the ones open PRs add or change (#29 clj_mutate_note.md, #30 finish_resume.md, #31 sf_tool.md): drop checks the Gherkin features and specs already cover, turn the automatable rest into acceptance features or specs, and list what cannot run in CI (real tools, fresh clones) for the operator. Then delete test/qa/. Also settle with the operator where future QA procedures go so the folder does not grow back (the specifier and qa role prompts say the procedure is a committed file).

## Records

```text
type: created
from: operator
branch: swarm/convert-the-qa-procedures-in-0c081bc
commit: d938c79
route: specifier,coder,qa
```

```text
type: question
from: specifier
question: Inventory of test/qa/*.md (9 files on main, plus QA-13 from open PR #31):
  
  Already covered, so drop:
  - task_store.md 1-8: task_ids, task_file, record_commits, task_archive, task_history, task_state, hook_record_trailers and init features. The identity overrides are in git_spec, and handoff with uncommitted work is in sf_test.sh. Section 9 (reading check after the git-bug removal) is a one-time check.
  - lore_trailers.md 1-5: byline_placement, lore_id, lore_lines features and lore_spec. Section 6 is a reading check of prompts.
  - finish_resume.md, every section: finish_resume 1-5 and task_archive 5.
  - manifest_guard.md 1: manifest_guard 1-6 and no_manifests_test.sh. 2.1: the CI step runs the guard on every push. 2.2-2.3 replay old commits 14c03ea and b1555ae. They could run in CI with full history, but the scenarios already cover what they replay. 3 is a read of test.yml.
  - metrics_cache.md setup and 1-3: install_ignore, metrics_load 1-5, metrics_save 1-8. Section 6 is a reading check.
  - speclj_examples.md QA-1 to QA-4: examples_spec (unit examples, plus the whole-tree check that the CI spec step runs).
  - sf_tool.md QA-6 to QA-12: crap4clj_coverage 3, entry_point_guard 1-4, catalogue_location 1-4, write_guard 1, wrappers 3 and sf_tool_test.sh. For QA-11, every CI acceptance run already runs the real gherkin-parser.
  - clj_mutate_note.md and docs_cleanup.md: one-time checks of document edits that are already merged (changed-file lists, old-id greps, reading checks), plus "suites pass", which CI runs.
  
  Not covered yet but automatable. These become steps in the existing features:
  - G1 finish_resume 1: stdout is empty.
  - G2 finish_resume 2: HEAD did not move.
  - G3 metrics_save 1: the save leaves the main checkout on main and adds no worktree.
  - G4 install_ignore 1: install.sh makes no swarmforge/metrics branch.
  
  Need the real tools (GitHub clones, Clojars/Maven downloads, minutes per run). In every case, sf's own side is already covered with a fake bb:
  - R1 sf_tool QA-1: a real ensure of dry4clj, clj-mutate, crap4clj and gherkin-parser. Also the real tail steps of QA-7 and QA-8.
  - R2 QA-2 to QA-5: real dry4clj, clj-mutate --scan, and crap4clj (with clojure.test and with speclj) on real code.
  - R3 QA-13 (PR #31): crap4clj run from the repo root gives a .metrics/crap.edn byte-identical to a run from the module directory. crap4clj_directory.feature and wrapper_spec in #31 already cover the directory logic.
  - R4 metrics_cache 4: a real gherkin-mutator run, and its manifest reused on a second run.
  - R5 metrics_cache 5 and clj_mutate_note check 3: a real clj-mutate writes .metrics/mutate/ and runs a differential run in a later task.
  
  Questions:
  1. What happens to R1-R5? (a) Drop them. sf's side is covered, and roles run the real tools at every startup. (b) Make one opt-in script, test/real_tools.sh, run by hand or by a manual or scheduled workflow, not on every push. (c) The same script as a step of the CI job on every push. That adds minutes, and upstream changes can break it. (d) A short manual checklist in test/features/sf_tool/README.md. I recommend (a).
  2. Where do future QA procedures go, so test/qa/ does not grow back? (a) The specifier still commits one for approval, and QA deletes it in its handoff commit after running it. The Tested/Not-tested trailers are the record, and main never keeps the file. This needs one line each in specifier.md and qa.md. (b) No procedure file. The specifier writes the end-to-end checks as Gherkin and lists the checks it cannot automate in the sf review message. This is a larger change to specifier.md, qa.md and orchestrator.md. (c) The procedure goes into the task file through a new sf option and is archived in tasks/done/. This needs an sf change. I recommend (a). This task itself would get no procedure file: QA runs the suites and checks that test/qa/ and every link to it are gone. Is that OK?
  3. Should the prompt change from Q2 be in this task, or in a follow-up task? project.md requires your direction for a role prompt change. I recommend a follow-up.
  4. PR #31 changes test/qa/sf_tool.md. (a) Merge #31 first, then this task deletes the file and drops the qa link that #31 adds to test/features/sf_tool/README.md. (b) This task goes first, and #31 drops its sf_tool.md hunk. I recommend (a).
  5. swarmforge/tools/javascript/qa/sf-js.md (220 lines, real npm/pnpm/yarn/bun, ships with install.sh) is another QA procedure outside test/qa/. (a) Out of scope here. (b) Include it. I recommend (a).
  6. Split: this task does G1-G4, deletes test/qa/, and removes the links to it from the 5 feature READMEs and README.md. Q3 becomes a follow-up task. Only if Q1 is (b) or (c), the real-tool script is a third task. Is that OK?
```

```text
type: answer
from: operator
answer: 1 drop the real-tool checks R1-R5. 2 (b) future end-to-end checks are written as Gherkin, no procedure file. 3 the prompt change is a follow-up task. 4 merge #31 first (it is being reworked: the crap4clj argument logic moves to an sf namespace). 5 include swarmforge/tools/javascript/qa/sf-js.md: convert and delete it the same way. 6 split as proposed: this task G1-G4 plus deleting test/qa/ and sf-js.md and their links.
```

```text
type: review
from: specifier
commit: 6c9877e
files: README.md,swarmforge/languages/javascript.md,swarmforge/tools/javascript/features/README.md,swarmforge/tools/javascript/qa/sf-js.md,test/features/lore_trailers/README.md,test/features/manifest_guard/README.md,test/features/metrics_cache/README.md,test/features/metrics_cache/install_ignore.feature,test/features/metrics_cache/metrics_save.feature,test/features/sf_tool/README.md,test/features/task_store/README.md,test/features/task_store/finish_resume.feature,test/qa/clj_mutate_note.md,test/qa/docs_cleanup.md,test/qa/finish_resume.md,test/qa/lore_trailers.md,test/qa/manifest_guard.md,test/qa/metrics_cache.md,test/qa/sf_tool.md,test/qa/speclj_examples.md,test/qa/task_store.md
summary: Spec: G1-G4 steps in finish_resume, metrics_save, install_ignore; delete test/qa/ and sf-js.md and their links
```

```text
type: changes-requested
from: operator
reason: Shrink the task: main (#36) already deleted test/qa/ and sf-js.md and their links. Keep only the four Gherkin additions G1-G4 (finish_resume 1 stdout empty, finish_resume 2 HEAD unchanged, metrics_save 1 stays on main with no new worktree, install_ignore 1 makes no swarmforge/metrics branch). Merge main into the task branch first.
```

```text
type: review
from: specifier
commit: b8fa810
files: .claude/agents/cleaner.md,.claude/agents/coder.md,.claude/agents/hardener.md,.claude/agents/qa.md,.claude/agents/specifier.md,.codex/agents/cleaner.toml,.codex/agents/coder.toml,.codex/agents/hardener.toml,.codex/agents/qa.toml,.codex/agents/specifier.toml,.github/agents/architect.agent.md,.github/agents/cleaner.agent.md,.github/agents/coder.agent.md,.github/agents/hardener.agent.md,.github/agents/qa.agent.md,.github/agents/specifier.agent.md,LICENSE,README.md,docs/spikes/git-task-store/design.md,docs/spikes/git-task-store/features/README.md,docs/spikes/git-task-store/features/remote_push.feature,docs/spikes/git-task-store/features/remote_tasks.feature,install.sh,swarmforge/README.md,swarmforge/bin/sf-fs,swarmforge/constitution/articles/engineering.md,swarmforge/constitution/articles/handoffs.md,swarmforge/constitution/articles/project.md,swarmforge/constitution/articles/workflow.md,swarmforge/handoff-protocol.md,swarmforge/languages/clojure.md,swarmforge/languages/fsharp.md,swarmforge/languages/javascript.md,swarmforge/roles/architect.md,swarmforge/roles/cleaner.md,swarmforge/roles/coder.md,swarmforge/roles/hardener.md,swarmforge/roles/orchestrator.md,swarmforge/roles/qa.md,swarmforge/roles/specifier.md,+55 more
summary: Spec: G1-G4 Gherkin steps only, after merging main
```

```text
type: approval
from: operator
```

```text
type: handoff
from: specifier
to: coder
branch: swarm/convert-the-qa-procedures-in-0c081bc
commit: b8fa810
files: -
summary: specifier work complete
```

```text
type: handoff
from: coder
to: qa
branch: swarm/convert-the-qa-procedures-in-0c081bc
commit: 4cfbde8
files: README.md,swarmforge/README.md,swarmforge/bin/sf-vb,swarmforge/constitution/articles/engineering.md,swarmforge/constitution/articles/handoffs.md,swarmforge/handoff-protocol.md,swarmforge/languages/javascript.md,swarmforge/languages/vbnet.md,swarmforge/tools.conf,swarmforge/tools/csharp/src/sfcs/cli.clj,swarmforge/tools/csharp/src/sfcs/cobertura.clj,swarmforge/tools/csharp/src/sfvb/cli.clj,swarmforge/tools/csharp/test/sfcs/core_test.clj,swarmforge/tools/javascript/features/README.md,swarmforge/tools/javascript/src/sfjs/oxlint.clj,swarmforge/tools/javascript/test/sfjs/architecture_test.clj,swarmforge/tools/javascript/test/sfjs/oxlint_test.clj,swarmforge/tools/sf/spec/sf/architecture_spec.clj,swarmforge/tools/sf/spec/sf/dispatch_spec.clj,swarmforge/tools/sf/src/sf/dispatch.clj,swarmforge/tools/sf/src/sf/task.clj,test/acceptance/src/acceptance/metrics_steps.clj,test/features/lore_trailers/README.md,test/features/manifest_guard/README.md,test/features/metrics_cache/README.md,test/features/sf_tool/README.md,test/features/task_store/README.md,test/features/task_store/task_history.feature,test/sf_tool_test.sh
summary: G3 step handlers added; G1-G4 scenarios and the full acceptance suite pass on main merged in
```

```text
type: handoff
from: qa
to: done
branch: swarm/convert-the-qa-procedures-in-0c081bc
commit: 0b9ad73
files: -
summary: QA passed: G1-G4 scenarios and all suites green; each new step fails on its defect
```
