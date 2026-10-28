---
id: add-focused-comment-review-to-6b3ec39
created: 2026-10-28
stage: done
route: coder,qa
---

# Add focused comment review to the cleaner role

Nice, do create a pull request for that. Approved wording: Review task-added comments and API docs in production and tests. Remove restatements and arrange/act/assert narration; prefer clearer names or extracted methods. Keep non-obvious rationale, contracts, and ordering constraints concise—ideally one line. Leave pre-existing comments unchanged. Report task-added comment lines: before → after. Add this to the shared cleaner role, not generated Codex adapters. Keep PR focused on this rule.

## Records

```text
type: created
from: operator
branch: swarm/add-focused-comment-review-to-6b3ec39
commit: 702d7bf
route: coder,qa
```

```text
type: handoff
from: coder
to: qa
branch: swarm/add-focused-comment-review-to-6b3ec39
commit: 13b8dc8
files: swarmforge/roles/cleaner.md
summary: Added approved task-added comment review rule to shared cleaner scope
```

```text
type: handoff
from: qa
to: done
branch: swarm/add-focused-comment-review-to-6b3ec39
commit: 719d5b7
files: -
summary: Verified exact approved cleaner rule, single-bullet scope and unchanged adapters; focused checks pass.
```
