---
id: let-sf-finish-resume-after-a-94351fb
created: 2026-10-28
stage: done
route: specifier,coder,qa
---

# Let sf finish resume after a merge conflict, and run crap4clj from the repo root

Two sf bugs seen in the last run. (1) When sf finish hits a merge conflict it stops; after the operator resolves and commits, sf sees tasks/done/<id>.md in HEAD, calls the task finished and refuses to go on, so the finished record is never written and the worktree and task branch stay. Running sf finish <id> again after a resolved conflict must complete it, with no hand-written SwarmForge-* trailers. (2) The crap4clj wrapper run from the repository root builds its coverage command with -s test, picks up the root test/ directory and fails; it only works from swarmforge/tools/sf. It must work from the root too.

## Records

```text
type: created
from: operator
branch: swarm/let-sf-finish-resume-after-a-94351fb
commit: 437a6c3
route: specifier,coder,qa
```

```text
type: question
from: specifier
question: Both bugs reproduced. (a) sf finish on a conflict exits 1 and prints nothing (git's CONFLICT lines are swallowed); the next sf finish says 'task is finished' and leaves worktree and branch. Committing with git's prepared message (git commit --no-edit) does keep the finished trailers; a commit with your own -m message loses them. (b) From the root, 'crap4clj --source-root swarmforge/tools/sf/src' adds '-s test' and fails loading test/acceptance; passing the sibling spec dir instead still runs 0 examples, because the speclj runner looks for ./spec in the current directory. Questions:
  1. Resume flow for sf finish after a conflict. A) You resolve and commit (any message); sf finish <id> then removes the worktree and branch, and if the merge commit at HEAD lacks the finished record, sf rewrites that commit's message to it (tree unchanged). A merge still in progress is refused with 'commit the merge first'. B) You resolve and git add only; sf finish <id> commits the merge with the finished record itself, then cleans up; a merge you already committed is handled as in A. Recommend A (smallest, matches the request).
  2. The conflicting sf finish will exit 1 with 'sf: merge of swarm/<id> into <target> stopped on conflicts: <files>; resolve them, commit, then run: sf finish <id>' and leave the merge in progress. OK, or other wording?
  3. crap4clj from the root. A) The wrapper runs crap4clj in the directory that holds the first source root (swarmforge/tools/sf for --source-root swarmforge/tools/sf/src), with the source roots made relative to it, so the result, target/ and .metrics/ are exactly as when run from swarmforge/tools/sf. B) Stay in the current directory; take spec/test from beside each source root and point the speclj runner at them (more moving parts: runner args, that project's bb.edn paths). Recommend A.
  4. Split into two tasks for small PRs: this one becomes 'Let sf finish resume after a merge conflict', and a new bugfix task 'Run crap4clj from the repository root'? Recommend yes.
```

```text
type: answer
from: operator
answer: 1A you commit, sf finish then cleans up and rewrites the merge message to the finished record when it lacks it; a merge in progress is refused. 2 the proposed message is OK. 3A run crap4clj in the directory that holds the first source root. 4 yes, split: this task covers only sf finish; crap4clj goes to a new task 'Run crap4clj from the repository root'.
```

```text
type: review
from: specifier
commit: b776d3e
files: test/features/task_store/README.md,test/features/task_store/finish_resume.feature,test/qa/finish_resume.md
summary: sf finish resume: conflict message, merge-in-progress refusal, finish after the operator commits
```

```text
type: approval
from: operator
```

```text
type: handoff
from: specifier
to: coder
branch: swarm/let-sf-finish-resume-after-a-94351fb
commit: b776d3e
files: -
summary: specifier work complete
```

```text
type: handoff
from: coder
to: qa
branch: swarm/let-sf-finish-resume-after-a-94351fb
commit: 1138c7e
files: swarmforge/tools/sf/spec/sf/flow_spec.clj,swarmforge/tools/sf/spec/sf/task_flow_spec.clj,swarmforge/tools/sf/src/sf/flow.clj,swarmforge/tools/sf/src/sf/store.clj,swarmforge/tools/sf/src/sf/task.clj,test/acceptance/src/acceptance/store_steps.clj
summary: sf finish resumes after a committed conflicting merge; conflict and merge-in-progress messages
```

```text
type: handoff
from: qa
to: done
branch: swarm/let-sf-finish-resume-after-a-94351fb
commit: 5bcf99e
files: -
summary: QA passed: finish resume procedure, specs, property, acceptance, CRAP and DRY
```
