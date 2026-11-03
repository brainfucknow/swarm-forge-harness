---
id: have-the-cleaner-trim-task-5b6012c
created: 2026-11-02
stage: done
route: specifier,coder,qa
---

# Have the cleaner trim task-added Markdown

Operator request: PRs #52 and #53 added wordy Markdown (languages/fsharp.md, engineering.md, clojure.md, the tools.conf header, feature READMEs and Feature descriptions) and no role trimmed it. The cleaner prompt (swarmforge/roles/cleaner.md) only covers task-added comments and API docs in code and tests. Extend the cleaner to task-added prose in Markdown and other documentation (language guides, constitution articles, protocol docs, READMEs, Feature descriptions, conf-file header comments, usage text) with the same rules it applies to comments: cut restatements, repetition and narration; keep non-obvious rules, contracts and constraints concise; leave pre-existing text unchanged; report task-added doc lines before -> after. It must not change scenario steps, step meanings that step definitions rely on, or behaviour. Keep the change small and language-neutral; do not test prompt text. Also consider that tasks on routes without a cleaner (e.g. coder,qa) then get no trim; the specifier should ask whether anything should change there.

## Records

```text
type: created
from: operator
branch: swarm/have-the-cleaner-trim-task-5b6012c
commit: 1b0c4f5
route: specifier,coder,qa
```

```text
type: question
from: specifier
question: 1. Which text counts as documentation for the cleaner's trim? Options: (a) exactly the request's list: tracked Markdown (language guides, constitution articles, handoff-protocol and other protocol docs, READMEs including test/features step-meaning tables), Gherkin Feature descriptions, conf-file header comments, and usage/help text printed by scripts; (b) (a) plus role prompts (swarmforge/roles/*.md) and adapter bodies; (c) any task-added prose file. In every case excluded: tasks/ files and SwarmForge-* records (sf-owned), commit messages and Lore trailers, Scenario titles and steps, Background and Examples, and step meanings that step definitions rely on. Recommended: (b), since a task that edits a role prompt adds the same kind of prose and the rule already says keep every rule and constraint.
  2. Routes without a cleaner (bugfix specifier,coder,qa; harden hardener,qa; this task's own route) get no trim. Options: (a) no change; the operator picks a route with a cleaner when docs matter; (b) one language-neutral bullet in coder.md: write task-added documentation to the same rules (no restatement, repetition or narration; keep non-obvious rules and constraints concise), so every route with a coder gets them at writing time; (c) QA, when the route has no cleaner, trims task-added documentation by the cleaner's rule; (d) add cleaner to the bugfix route in routes.conf. Recommended: (b), smallest change that covers bugfix; QA stays an independent verifier (in PR #52 QA flagged the long guide and sent it back rather than rewriting it).
  3. Length budget or QA check? Options: (a) neither; the cleaner's before -> after report in its commit is the record; (b) a numeric budget (for example task-added lines per file) for the cleaner or QA; (c) a QA bullet: QA treats task-added documentation that breaks the trim rule as a defect and fixes or returns it. Recommended: (a); a budget is arbitrary across file kinds and a QA check adds a second rule to keep in sync.
```

```text
type: answer
from: operator
answer: 1b: task-added prose in Markdown (language guides, constitution articles, protocol docs, READMEs including step-meaning tables), Gherkin Feature descriptions, conf header comments, usage/help text, plus role prompts and adapter bodies; excluded as listed (tasks/ files and records, commit messages and Lore, Scenario titles, steps, Background, Examples, and step meanings step definitions rely on). Evidence: the largest recent prose additions were role prompts (#49 526 words, #50 618 words). 2: both (b) one language-neutral bullet in coder.md to write task-added documentation to the same rules, and add cleaner to the bugfix route in swarmforge/routes.conf (specifier,coder,cleaner,qa); harden and spike stay as they are, explicit routes remain the operator's choice. Evidence: 6 of the last 10 PRs ran without a cleaner, and #52's coder self-trim stayed wordy. 3a: no budget or QA check; the cleaner's before -> after report is the record (the #53 trim cut articles 60-70% with no budget while correctly keeping step definitions).
```

```text
type: review
from: specifier
commit: 52c041c
files: -
summary: Cleaner trims task-added docs, coder writes them to the same rules, bugfix gains the cleaner; criteria and QA in 52c041c
```

```text
type: changes-requested
from: operator
reason: Rescope after PR #55 (merged: cleaner.md now has a Documentation Cleanup section covering the core rule). Merge current main first. New scope: (1) name what counts as documentation in that section: Markdown guides and articles, READMEs, Feature descriptions, conf header comments, usage/help text, role prompts and adapter bodies; (2) one line on what the trim never changes: scenarios and their steps, what a step-table row means (its wording may be tightened), tasks/ files and sf records, commit messages and Lore, and text a test checks; (3) one coder.md bullet to write task-added documentation to the same rules; (4) cleaner on the bugfix route (specifier,coder,cleaner,qa) with the README.md route table and sentence, the swarmforge/README.md diagram line and the test/sf_test.sh routes block updated; (5) tighten #55's section while editing it (it repeats itself, e.g. 'inspect new files and additions' vs 'limit cleanup to the task's documentation changes'). Clarity fixes for the spec itself: no bare answer codes; no contradiction between step tables or usage text being in and out of scope; no 'or' choices in criteria; the fsharp.md dry run should expect the code example and the table restating the text to go, keeping the declaration, filter syntax and exit-2 rule (64d271a only flagged length; the cut was bc92566); keep the spec itself short.
```

```text
type: review
from: specifier
commit: 82cac9c
files: README.md,install.sh,swarmforge/README.md,swarmforge/constitution/articles/engineering.md,swarmforge/constitution/articles/handoffs.md,swarmforge/constitution/articles/workflow.md,swarmforge/handoff-protocol.md,swarmforge/languages/clojure.md,swarmforge/languages/csharp.md,swarmforge/languages/javascript.md,swarmforge/roles/architect.md,swarmforge/roles/cleaner.md,swarmforge/roles/coder.md,swarmforge/roles/hardener.md,swarmforge/roles/qa.md,swarmforge/roles/specifier.md,swarmforge/tools.conf,swarmforge/tools/javascript/features/README.md,swarmforge/tools/sf/property/sf/catalogue_property.clj,swarmforge/tools/sf/spec/sf/architecture_spec.clj,swarmforge/tools/sf/spec/sf/catalogue_spec.clj,swarmforge/tools/sf/spec/sf/clone_spec.clj,swarmforge/tools/sf/spec/sf/config_repo_spec.clj,swarmforge/tools/sf/spec/sf/config_spec.clj,swarmforge/tools/sf/spec/sf/tool_spec.clj,swarmforge/tools/sf/spec/sf/wrapper_spec.clj,swarmforge/tools/sf/src/sf/catalogue.clj,swarmforge/tools/sf/src/sf/clone.clj,swarmforge/tools/sf/src/sf/config.clj,swarmforge/tools/sf/src/sf/shell.clj,swarmforge/tools/sf/src/sf/tool.clj,swarmforge/tools/sf/src/sf/wrapper.clj,test/acceptance/src/acceptance/generate.clj,test/acceptance/src/acceptance/install_steps.clj,test/acceptance/src/acceptance/steps.clj,test/features/install/README.md,test/features/install/bin_tracked.feature,test/features/lore_trailers/README.md,test/features/manifest_guard/README.md,test/features/metrics_cache/README.md,+11 more
summary: Rescoped after #55: name documentation, add a never-trim line, tighten the section, add a coder bullet, put the cleaner on bugfix; criteria and QA in the spec commit
```

```text
type: approval
from: operator
```

```text
type: handoff
from: specifier
to: coder
branch: swarm/have-the-cleaner-trim-task-5b6012c
commit: 82cac9c
files: -
summary: specifier work complete
```

```text
type: handoff
from: coder
to: qa
branch: swarm/have-the-cleaner-trim-task-5b6012c
commit: d8becea
files: README.md,swarmforge/constitution/articles/project.md,swarmforge/languages/clojure.md,swarmforge/languages/csharp.md,swarmforge/languages/fsharp.md,swarmforge/languages/javascript.md,swarmforge/languages/vbnet.md,swarmforge/roles/cleaner.md,swarmforge/roles/coder.md,swarmforge/roles/security-reviewer.md,swarmforge/routes.conf,swarmforge/tools.conf,test/sf_test.sh
summary: Cleaner section names documentation and never-change line in 160 words; coder bullet; bugfix gains cleaner
```

```text
type: handoff
from: qa
to: done
branch: swarm/have-the-cleaner-trim-task-5b6012c
commit: 8b88dc9
files: -
summary: QA passed: criteria 1-7 met, dry runs match, sf check/adapters clean on the branch, sf_test.sh passes
```
