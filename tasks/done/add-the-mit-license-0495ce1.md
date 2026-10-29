---
id: add-the-mit-license-0495ce1
created: 2026-10-29
stage: done
route: coder,qa
---

# Add the MIT license

Add a MIT license as a PR. Add only a standard root LICENSE using Copyright (c) 2026 swarm-forge-harness contributors. Preserve the standard MIT terms without extra legal prose, source changes, README expansion or new tests. The user has already been informed that upstream SwarmForge has no declared license; adding this file does not resolve that provenance/permission question, which belongs in the PR description. This is an explicitly approved small documentation change: coder,qa; manual license comparison and diff review suffice, no source analyses or broad local suites. Per-command author and committer Codex <codex@openai.com>, no human/global git config edits.

## Records

```text
type: created
from: operator
branch: swarm/add-the-mit-license-0495ce1
commit: f90718b
route: coder,qa
```

```text
type: handoff
from: coder
to: qa
branch: swarm/add-the-mit-license-0495ce1
commit: 79b913b
files: LICENSE
summary: Standard MIT license added with approved contributor copyright; ready for QA
```

```text
type: handoff
from: qa
to: done
branch: swarm/add-the-mit-license-0495ce1
commit: bed0285
files: -
summary: Verified standard MIT terms, approved copyright and license-only scope; diff check passes.
```
