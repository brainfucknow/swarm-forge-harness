---
id: keep-mutation-manifests-on-an-2a5978d
created: 2026-10-28
---

# Keep mutation manifests on an orphan metrics branch

Keep mutation manifests (the .metrics/ files and the manifest header blocks the hardener writes into feature files) out of the tree and out of PRs. The operator decided they are a cache: store them on an orphan branch, swarmforge/metrics, detached from main. This replaces paused task ec13ecd, which kept them in git notes; its work is on branch old/ec13ecd-git-notes (also swarm/ec13ecd on GitHub) for reference only. Re-specify against the new git task store.
