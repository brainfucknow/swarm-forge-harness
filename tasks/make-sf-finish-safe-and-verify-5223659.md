---
id: make-sf-finish-safe-and-verify-5223659
created: 2026-10-31
---

# Make sf finish safe and verify the merged result

From an external review the operator accepted: sf finish merges, then runs git worktree remove --force and git branch -D with no check for uncommitted changes, so edits made after the final handoff are lost; and nothing verifies the merged result, although conflict resolution can add unchecked code. sf finish must refuse a dirty task worktree, keep the worktree and branch until cleanup succeeds so a failure leaves a recoverable task, and run the project's verification on the merge candidate before cleaning up. Acceptance: a task that passed QA is still blocked from finishing when the merge breaks a test, and uncommitted work survives the failed finish.
