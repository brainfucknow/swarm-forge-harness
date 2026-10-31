---
id: give-roles-a-compact-brief-2d0c615
created: 2026-10-31
stage: done
route: specifier,coder,cleaner,architect,hardener,qa
---

# Give roles a compact brief instead of the whole history

From the external review the operator accepted: sf start prints the request, the complete record history and every Lore line of the task branch, so each fresh role session receives a progressively larger history on a long route or after rejections. Lore lines that a later Supersedes trailer replaces are still printed, so later roles must work out themselves which directives no longer apply. Cap what roles are given: at least leave out superseded Lore and bound the history sf start prints, keeping the full history available through sf show. The review also proposed an 'sf brief' view with the approved criteria, unresolved findings, the decisions that still apply and the exact diff boundaries; the specifier should ask the operator how far to go and prefer the simplest form.

## Records

```text
type: created
from: operator
branch: swarm/give-roles-a-compact-brief-2d0c615
commit: 69db113
route: specifier,coder,cleaner,architect,hardener,qa
```

```text
type: question
from: specifier
question: 1. Scope of this task? (a) only leave out superseded Lore in sf start; (b) (a) plus a bound on the records sf start prints; (c) (b) plus a new sf brief view (approved criteria, unresolved findings, live decisions, diff boundaries). Recommended: (b), with sf brief left for a later task if still wanted; (a) alone if you want the smallest PR.
  2. What counts as superseded? A trailer 'Supersedes: <Lore-id>' on any commit of the task branch removes every LORE line of the commit whose Lore-id equals the trimmed value (its Lore-id line included); the Supersedes line itself stays; chains fall out naturally; a value matching no commit on the branch is ignored. Alternative: drop only that commit's Directive/Constraint lines. Recommended: the whole commit's lines, as described.
  3. If records are bounded (1b), how? (a) sf start prints only the last N records, preceded by one line 'RECORDS: <k> earlier records omitted; sf show <id> prints them'; (b) the same, but every ask/answer/changes-requested record is always kept; (c) drop only the record kinds roles do not read (start, route). Recommended: (a) with N = 10 (a full six-role route with one review fits); say if you want another N.
  4. Cap the LORE lines by count too? Recommended: no; dropping old directives by age is unsafe, and superseding is the way to retire them.
  5. sf show: keep printing every record and every LORE line, superseded ones included, unchanged? Recommended: yes.
```

```text
type: answer
from: operator
answer: 1: sf start leaves out superseded Lore and the evidence keys (Tested, Not-tested, Confidence, Lore-id), keeping Constraint, Rejected, Directive, Scope-risk, Reversibility, Supersedes, Depends-on and Related; no record cap and no sf brief view. Measured on four finished tasks: sf show is 2-4k tokens; a 10-record cap and superseding alone save 0-7%, while the evidence keys are about a third of the output. 2: as recommended, a Supersedes: <Lore-id> on the branch removes every LORE line of the commit with that Lore-id; the Supersedes line stays; chains fall out; unmatched ids are ignored. 3: no record bound. 4: no LORE count cap. 5: sf show unchanged, printing every record and every LORE line.
```

```text
type: answer
from: operator
answer: Amendment to 3: do bound the records after all; the measured tasks are young and not representative of mature use with long routes and rejection rounds. Use 3(a): sf start prints only the last 10 records, preceded by 'RECORDS: <k> earlier records omitted; sf show <id> prints them'. sf show stays unbounded.
```

```text
type: review
from: specifier
commit: 25ca149
files: swarmforge/constitution/articles/handoffs.md,swarmforge/handoff-protocol.md,test/features/lore_trailers/lore_lines.feature,test/features/task_store/README.md,test/features/task_store/start_brief.feature,test/features/task_store/task_history.feature
summary: sf start brief: last 10 records, no evidence or superseded Lore, omission lines; spec in start_brief.feature
```

```text
type: changes-requested
from: operator
reason: Operator approves the spec with two decisions: (1) the Lore omission line LORE-OMITTED: <k> superseded or evidence lines; sf show <id> prints them is confirmed as specified; (2) 2a: sf start always keeps every question, answer and changes-requested record; the 10-record bound applies only to the other records, and the RECORDS line counts only what was left out. Amend the criteria for (2) only, then sf review again.
```

```text
type: review
from: specifier
commit: 59f7154
files: swarmforge/constitution/articles/handoffs.md,swarmforge/handoff-protocol.md,test/features/task_store/README.md,test/features/task_store/start_brief.feature
summary: sf start brief amended: questions, answers and changes-requested always kept; bound applies to other records
```

```text
type: approval
from: operator
```

```text
type: handoff
from: specifier
to: coder
branch: swarm/give-roles-a-compact-brief-2d0c615
commit: 59f7154
files: -
summary: specifier work complete
```

```text
type: handoff
from: coder
to: cleaner
branch: swarm/give-roles-a-compact-brief-2d0c615
commit: 643e662
files: swarmforge/tools/sf/spec/sf/history_spec.clj,swarmforge/tools/sf/spec/sf/lore_spec.clj,swarmforge/tools/sf/spec/sf/record_spec.clj,swarmforge/tools/sf/src/sf/history.clj,swarmforge/tools/sf/src/sf/lore.clj,swarmforge/tools/sf/src/sf/record.clj,swarmforge/tools/sf/src/sf/task.clj,test/acceptance/src/acceptance/store_steps.clj
summary: sf start brief: bounded records, no evidence or superseded Lore, RECORDS/LORE-OMITTED lines; step handlers added
```

```text
type: handoff
from: cleaner
to: architect
branch: swarm/give-roles-a-compact-brief-2d0c615
commit: c4520aa
files: swarmforge/tools/sf/src/sf/record.clj
summary: cleaner: record/brief simplified; CRAP and DRY clean for task code
```

```text
type: handoff
from: architect
to: hardener
branch: swarm/give-roles-a-compact-brief-2d0c615
commit: c905fce
files: swarmforge/tools/sf/property/sf/lore_property.clj,swarmforge/tools/sf/property/sf/record_property.clj,swarmforge/tools/sf/spec/sf/lore_spec.clj,swarmforge/tools/sf/src/sf/history.clj,swarmforge/tools/sf/src/sf/lore.clj
summary: architect: lore/brief returns :omitted like record/brief; property tests for both brief filters
```

```text
type: handoff
from: hardener
to: qa
branch: swarm/give-roles-a-compact-brief-2d0c615
commit: b1344fa
files: swarmforge/tools/sf/spec/sf/history_spec.clj
summary: hardener: finished-history spec kills last task survivor; brief code and 3 features fully mutation-killed, CRAP and DRY clean
```

```text
type: handoff
from: qa
to: done
branch: swarm/give-roles-a-compact-brief-2d0c615
commit: c15bfc0
files: swarmforge/constitution/articles/engineering.md,swarmforge/constitution/articles/project.md,swarmforge/roles/architect.md,swarmforge/roles/cleaner.md,swarmforge/roles/coder.md,swarmforge/roles/hardener.md
summary: qa: merged main, suites and manual QA pass; sf start brief verified
```
