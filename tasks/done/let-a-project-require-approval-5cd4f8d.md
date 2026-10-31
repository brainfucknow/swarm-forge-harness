---
id: let-a-project-require-approval-5cd4f8d
created: 2026-10-31
stage: done
route: specifier,coder,qa
---

# Let a project require approval before refactoring existing code

Operator request: some projects do not want spurious refactorings. There the preferred change is the existing code plus a small diff with additional tests, rather than many refactorings. For new code (code the task adds) refactoring still makes sense. For old code (code that existed before the task) a role must get approval from a human before refactoring it. Add a mechanism a project can switch on to get this behaviour; projects that do not switch it on keep today's behaviour. Roles that refactor today include cleaner, architect and hardener; mutation and quality gates may also push roles to restructure old code. Prefer the simplest mechanism; ask the operator where the setting lives, how 'old code' is decided, how approval is asked and recorded, and whether sf enforces it or only the role prompts.

## Records

```text
type: created
from: operator
branch: swarm/let-a-project-require-approval-5cd4f8d
commit: 14a635f
route: specifier,coder,cleaner,architect,hardener,qa
```

```text
type: question
from: specifier
question: Before I specify, please settle these. Each has options and my recommendation (R).
  
  1. Where does the project setting live?
   a) One line in swarmforge/constitution/articles/project.md, next to 'Project language', e.g. 'Existing code: refactor freely' (default) or 'Existing code: ask before refactoring'. The rule itself lives once in engineering.md as a new 'Existing Code' section that applies only when project.md says 'ask before refactoring'; coder, cleaner, architect and hardener each get one pointer line to it.
   b) A new conf file (e.g. swarmforge/project.conf) that sf reads.
   c) An environment variable.
   R: a. No sf change, projects that do not edit project.md keep today's behaviour, and the text stays language-neutral.
  
  2. How is 'existing code' decided?
   a) Function level against the merge base of the task branch with the base branch (the same base sf-cs/sf-js use for changed lines): a function, method, test or file present at that merge base is existing; one the task added is new. Editing an existing function to deliver the requested behaviour is allowed; restructuring it (rename, extract, inline, move, split a file, dedupe, reformat) is a refactoring that needs approval. Adding new tests, even for existing code, never needs approval.
   b) Line level against the merge base (any pre-existing line touched beyond the behaviour change).
   c) Against the task's created commit (parent of the created record). Downside: code merged in from main mid-task would count as the task's own.
   R: a.
  
  3. How does a role ask for and record approval?
   a) Existing sf ask/sf answer, no new command or record type. A role first finishes everything it can without touching existing structure, then makes ONE sf ask listing each proposed refactoring of existing code as a numbered item (file, function, what, why, which gate finding if any), and stops. The operator answers per item (e.g. 'approve 1,3; decline 2'); the answer record is the approval. The role then records each approved refactoring as Lore 'Related: approved in answer <item>' and each declined one as 'Rejected: <refactoring> | operator declined'. If it has nothing to propose, it does not ask.
   b) A new sf command/record type (e.g. sf approve-refactor).
   c) The specifier collects pre-approvals at the spec gate only.
   R: a.
  
  4. What happens to gate findings (CRAP, DRY, mutation survivors, 'split a file with more than one job') on existing code?
   a) Gates still run. Findings in new code are fixed as today. For existing code, a role may fix a finding by adding tests (kills mutants, raises coverage/CRAP) without approval; fixing it by restructuring needs approval via 3. A finding left unfixed (not proposed or declined) does not block handoff and is recorded as a Lore 'Constraint: existing code, <tool> finding left: <where>' trailer. The cleaner's CRAP run on all files stays, but findings in existing functions the task did not change are only recorded, not proposed.
   b) Gates ignore existing code entirely (scope tools to new code only).
   c) Every finding on existing code must be asked about.
   R: a.
  
  5. Does sf enforce anything, or do only the prompts/constitution change?
   a) Prompts/constitution only; sf unchanged and no automated test (per your preference that prompt text is not tested). Acceptance is a manual review checklist in the spec commit.
   b) sf check validates the project.md line's value.
   c) sf refuses handoff when existing functions changed without an answer record.
   R: a. Detecting 'refactoring' mechanically is language-specific and unreliable.
  
  6. Which roles does the rule cover?
   a) Every role that edits code: coder, cleaner, architect, hardener (qa writes no production code).
   b) Only cleaner, architect, hardener.
   R: a. The coder's 'leave broad cleanup to the cleaner' is close already, but extracting/moving existing code to fit the feature is still a refactoring.
  
  7. Route: since the result is prompt/constitution text only, there is nothing executable for the CRAP/DRY/mutation roles. Keep 'feature' or narrow to specifier,coder,qa?
   R: specifier,coder,qa.
```

```text
type: answer
from: operator
answer: 1a: one line in project.md (Existing code: refactor freely | ask before refactoring), rule once in engineering.md, one pointer line in coder, cleaner, architect and hardener. 2a: function level against the merge base; behaviour edits allowed, restructuring needs approval, new tests never need approval. 3a: existing sf ask/sf answer, one numbered list per role, recorded as Related/Rejected Lore. 4a: gates still run; old-code findings fixed by tests freely, restructuring needs approval, unfixed findings recorded as Constraint and do not block. 5a: prompts and constitution only, no sf change, manual checklist. 6a: coder, cleaner, architect, hardener. 7: not answered; keep the current route.
```

```text
type: review
from: specifier
commit: 11197c8
files: -
summary: Opt-in existing-code approval rule: 10 criteria and manual QA in commit 11197c8
```

```text
type: route
from: operator
route: specifier,coder,qa
```

```text
type: approval
from: operator
```

```text
type: handoff
from: specifier
to: coder
branch: swarm/let-a-project-require-approval-5cd4f8d
commit: 11197c8
files: -
summary: specifier work complete
```

```text
type: handoff
from: coder
to: qa
branch: swarm/let-a-project-require-approval-5cd4f8d
commit: c16fe73
files: swarmforge/constitution/articles/engineering.md,swarmforge/constitution/articles/project.md,swarmforge/roles/architect.md,swarmforge/roles/cleaner.md,swarmforge/roles/coder.md,swarmforge/roles/hardener.md
summary: Existing Code rule in engineering.md, opt-in line in project.md, pointers in coder/cleaner/architect/hardener
```

```text
type: handoff
from: qa
to: done
branch: swarm/let-a-project-require-approval-5cd4f8d
commit: 004250e
files: -
summary: QA passed: manual QA 1-8, sf check and sf_test.sh green
```
