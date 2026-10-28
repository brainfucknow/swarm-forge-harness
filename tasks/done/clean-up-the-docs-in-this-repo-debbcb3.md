---
id: clean-up-the-docs-in-this-repo-debbcb3
created: 2026-10-28
stage: done
route: specifier,coder,qa
---

# Clean up the docs in this repo

Clean up the docs in this repo. The operator asked for this after the task store moved off git-bug (PR #21): find docs that are stale, duplicated or out of date (for example docs/spikes/git-task-store now that part 1 is built, mentions of hex-only task ids, READMEs that drifted from the code), and make the docs match the code. Ask the operator about scope before deleting or moving anything substantial.

## Records

```text
type: created
from: operator
branch: swarm/clean-up-the-docs-in-this-repo-debbcb3
commit: 5d2615c
route: specifier,coder,qa
```

```text
type: question
from: specifier
question: Scope check before I specify. Candidate docs, with what I propose for each; please answer per item (yes / no / other). Not touched, because "Keep mutation manifests on an orphan metrics branch" will change them: the manifest rules in constitution/articles/engineering.md, roles/hardener.md and roles/cleaner.md, install.sh's ignore list, and the "Mutation manifests: the cache branch" section of the design note. Also not touched: roles/orchestrator.md (the unmerged branch claude/name-tasks-by-title changes it). The prompts, handoff-protocol.md, both READMEs' command lists and the language docs match the code; I found no drift there beyond item 6.
  
  1. docs/spikes/git-task-store/design.md (473 lines). Part 1, the local store, is built, and handoff-protocol.md plus test/features/task_store/ now describe it. Part 2, the rollout, is done except deleting the git-bug/* mirror branches on origin (a git action for you, not a doc change). Part 3, sharing, is not built.
     (a) Recommended: trim it to what is still pending. Keep the Sharing section, the open questions and the metrics section unchanged. Delete what is built or dropped: the decisions table, Model, the hook, Migration and the rollout, "What else changes in the build task", and the Backlog.md/Beads comparison. Git history keeps the rest.
     (b) Smaller diff: keep the text and add a short status paragraph at the top saying which parts are built, done, pending, and owned by the metrics task.
     (c) Leave it as it is.
  2. docs/spikes/git-task-store/features/ (README and the two draft sharing features). Keep them where they are, since CI runs every feature under test/features/. Only reword the README's "Task e6b686e built ..." per item 3.
  3. Bare git-bug task ids in feature READMEs and QA procedures, which no longer resolve with sf: test/features/{task_store,sf_tool,lore_trailers}/README.md, swarmforge/tools/javascript/features/README.md, test/qa/{task_store,lore_trailers,sf_tool}.md and swarmforge/tools/javascript/qa/sf-js.md. Proposal: name each task by its title instead:
     01d767f "Spike: replace git-bug with Markdown task files and commit trailers"
     e6b686e "Build the local git task store" (git kept no title for it; this is my wording)
     df7e1a7 "QA records verification as Lore commit trailers instead of committed scripts"
     aca0457 "Port the tool-wrapper fix to the Babashka sf tool"
     8414dd5 "Fix the dry4clj and clj-mutate tool wrappers"
     0306e88 "Add JavaScript and TypeScript support (sf-js)"
     5680706 "sf-js crap: oxlint as complexity source, ESLint fallback"
     5f9a812 "sf-js crap: exit 2 when oxlint cannot parse a file"
     5bbff27 "Tighten sf-js Gherkin examples to kill mutation survivors"
  4. test/qa/sf_tool.md setup step 1 runs git clone --branch swarm/aca0457, a branch that no longer exists. Proposal: clone the task worktree's current branch instead.
  5. test/features/task_store/README.md is a build log in places. "Settled with the operator" lists the rollout and sharing as future tasks, and "Changes to existing specifications" lists edits the build already made. Its "Not covered" section points at the design note's "What else changes" section, which item 1(a) deletes. Proposal: drop the "Changes to existing specifications" section and the bullets about removing git-bug, keep the decisions that still explain behavior, and point sharing at the spike features directory. test/qa/task_store.md's intro, which points at the design note's "Build plan", then points at handoff-protocol.md. Its section 9 reading check stays.
  6. README.md. The Tests block lists "sf tool ensure gherkin-parser" after "bash test/acceptance.sh", which needs it first (CI runs it first). Proposal: move it up. The Layout block lacks tasks/; proposal: add a line for it.
  7. Leftover "git-bug is not needed" and "Remove git-bug from PATH" lines in test/features/lore_trailers/README.md and test/qa/lore_trailers.md. Proposal: delete them. The Gherkin step "no git-bug on PATH" in test/features/task_store/init.feature stays, since it is spec text with a step handler.
  
  There is no Gherkin for this task because it changes no behavior. I will write only a QA reading procedure (greps and link checks). Should any item be its own task to keep the PR small?
```

```text
type: answer
from: operator
answer: 1a trim the design note to what is pending; 2 and 3 yes, name old tasks by their titles (e6b686e's title is 'Build the git-native task store'); 4 yes; 5 yes; 6 yes; 7 yes; one PR.
```

```text
type: review
from: specifier
commit: 643507e
files: test/qa/docs_cleanup.md
summary: docs cleanup end state and QA reading procedure (items 1a, 2-7)
```

```text
type: approval
from: operator
```

```text
type: handoff
from: specifier
to: coder
branch: swarm/clean-up-the-docs-in-this-repo-debbcb3
commit: 643507e
files: -
summary: specifier work complete
```

```text
type: handoff
from: coder
to: qa
branch: swarm/clean-up-the-docs-in-this-repo-debbcb3
commit: b5cc023
files: README.md,docs/spikes/git-task-store/design.md,docs/spikes/git-task-store/features/README.md,swarmforge/tools/javascript/features/README.md,swarmforge/tools/javascript/qa/sf-js.md,test/features/lore_trailers/README.md,test/features/sf_tool/README.md,test/features/task_store/README.md,test/qa/lore_trailers.md,test/qa/sf_tool.md,test/qa/task_store.md
summary: docs trimmed and old task ids replaced by titles per test/qa/docs_cleanup.md
```

```text
type: handoff
from: qa
to: done
branch: swarm/clean-up-the-docs-in-this-repo-debbcb3
commit: b791506
files: docs/spikes/git-task-store/design.md
summary: docs cleanup verified; one sentence restored in the design note's Sharing section
```
