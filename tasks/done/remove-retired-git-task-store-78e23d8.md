---
id: remove-retired-git-task-store-78e23d8
created: 2026-10-29
stage: done
route: coder,qa
---

# Remove retired git task-store spike documents

Yes do that then. Remove all four files under docs/spikes/git-task-store: they are unimplemented remote-sharing drafts unused by runtime, installation or CI. Repair the active README reference, retain a short note that tasks are local branches and automatic remote synchronization is unsupported, and preserve existing manual Git sharing guidance. Remove obsolete future-sharing wording in the metrics README. Git history preserves the proposal; add no replacement backlog, QA recipe or process document.

## Records

```text
type: created
from: operator
branch: swarm/remove-retired-git-task-store-78e23d8
commit: 4b00555
route: coder,qa
```

```text
type: handoff
from: coder
to: qa
branch: swarm/remove-retired-git-task-store-78e23d8
commit: e59b5be
files: docs/spikes/git-task-store/design.md,docs/spikes/git-task-store/features/README.md,docs/spikes/git-task-store/features/remote_push.feature,docs/spikes/git-task-store/features/remote_tasks.feature,test/features/metrics_cache/README.md,test/features/task_store/README.md
summary: Removed unused sharing proposal and repaired active documentation references
```

```text
type: handoff
from: qa
to: done
branch: swarm/remove-retired-git-task-store-78e23d8
commit: 3e943ed
files: -
summary: Verified four retired drafts removed, active references repaired and current sharing/discovery boundaries preserved.
```
