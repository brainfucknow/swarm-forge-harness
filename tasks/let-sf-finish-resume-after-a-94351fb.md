---
id: let-sf-finish-resume-after-a-94351fb
created: 2026-10-28
---

# Let sf finish resume after a merge conflict, and run crap4clj from the repo root

Two sf bugs seen in the last run. (1) When sf finish hits a merge conflict it stops; after the operator resolves and commits, sf sees tasks/done/<id>.md in HEAD, calls the task finished and refuses to go on, so the finished record is never written and the worktree and task branch stay. Running sf finish <id> again after a resolved conflict must complete it, with no hand-written SwarmForge-* trailers. (2) The crap4clj wrapper run from the repository root builds its coverage command with -s test, picks up the root test/ directory and fails; it only works from swarmforge/tools/sf. It must work from the root too.
