---
id: make-sf-finish-safe-and-verify-5223659
created: 2026-10-31
stage: done
route: specifier,coder,cleaner,architect,hardener,qa
---

# Make sf finish safe and verify the merged result

From an external review the operator accepted: sf finish merges, then runs git worktree remove --force and git branch -D with no check for uncommitted changes, so edits made after the final handoff are lost; and nothing verifies the merged result, although conflict resolution can add unchecked code. sf finish must refuse a dirty task worktree, keep the worktree and branch until cleanup succeeds so a failure leaves a recoverable task, and run the project's verification on the merge candidate before cleaning up. Acceptance: a task that passed QA is still blocked from finishing when the merge breaks a test, and uncommitted work survives the failed finish.

## Records

```text
type: created
from: operator
branch: swarm/make-sf-finish-safe-and-verify-5223659
commit: 33c4b48
route: specifier,coder,cleaner,architect,hardener,qa
```

```text
type: question
from: specifier
question: Settled without asking (object if wrong): sf finish refuses before any merge when the task worktree has uncommitted changes, tracked or untracked (the same check as sf handoff), and changes nothing; it removes the worktree without --force and deletes the branch only after the merge and verification succeeded; a rerun of sf finish completes an interrupted cleanup, as today.
  1. Where is the verification command configured? (a) new file swarmforge/verify.conf: each non-comment line is a shell command, run in order with bash -c from the checkout root of the candidate, all must exit 0 [recommended]; (b) env var SWARMFORGE_VERIFY; (c) git config key swarmforge.verify.
  2. What does this repository put there? (a) bash test/sf_test.sh only [recommended, fast]; (b) the fast CI set: bash test/sf_test.sh, bash test/sf_tool_test.sh, bb --config swarmforge/tools/sf/bb.edn spec; (c) everything in .github/workflows/test.yml.
  3. With nothing configured: (a) finish as today and print 'VERIFY: none configured' [recommended, existing installs keep working]; (b) refuse to finish until a command is configured.
  4. Where does the merge candidate get verified? (a) in the task worktree: sf runs git merge --no-commit <target> there, verifies, aborts that merge, and only on a pass merges into the target as today, so the target never holds an unverified merge [recommended]; (b) in the main checkout: sf refuses a dirty main checkout, merges with --no-commit, verifies, then commits the merge on a pass or aborts it on a failure.
  5. Conflicts. Today the operator resolves them in the main checkout, commits the merge on the target, and reruns sf finish. Keep that path, and on the rerun verify the committed merge (HEAD) before cleanup; a failure keeps branch and worktree but the merge stays on the target, because the operator committed it [recommended]? Or (b) refuse conflicting merges outright and tell the operator to merge the target into the task branch in the task worktree, commit, and rerun sf finish (drops the existing finish_resume path).
  6. A failed verification: (a) sf finish exits 1 with the failing command and its exit status, records nothing, and leaves target, branch and worktree as they were; the operator decides what happens next [recommended, small]; (b) sf finish also records changes-requested and moves the task back from done to a role (which one: coder, or the first role after the specifier on the route?), which reopens the archived task file and overlaps the task Require the whole route before a task is done.
```

```text
type: answer
from: operator
answer: All recommended: 1a swarmforge/verify.conf, one shell command per line, all must exit 0; 2a this repository lists bash test/sf_test.sh only; 3a with nothing configured, finish as today and print VERIFY: none configured; 4a verify the merge candidate in the task worktree before touching the target; 5a keep the conflict path and verify the committed merge before cleanup; 6a a failed verification exits 1 with the failing command and status, records nothing and leaves target, branch and worktree as they were. The settled points (refuse a dirty worktree, no --force, delete the branch only after merge and verification succeed) stand.
```

```text
type: review
from: specifier
commit: 535cbd4
files: test/features/task_store/README.md,test/features/task_store/finish_resume.feature,test/features/task_store/finish_verify.feature,test/features/task_store/task_archive.feature
summary: sf finish refuses a dirty worktree and verifies the merge candidate via swarmforge/verify.conf
```

```text
type: approval
from: operator
```

```text
type: handoff
from: specifier
to: coder
branch: swarm/make-sf-finish-safe-and-verify-5223659
commit: 535cbd4
files: -
summary: specifier work complete
```

```text
type: handoff
from: coder
to: cleaner
branch: swarm/make-sf-finish-safe-and-verify-5223659
commit: f379113
files: install.sh,swarmforge/README.md,swarmforge/handoff-protocol.md,swarmforge/tools/sf/spec/sf/architecture_spec.clj,swarmforge/tools/sf/spec/sf/task_flow_spec.clj,swarmforge/tools/sf/spec/sf/verify_spec.clj,swarmforge/tools/sf/src/sf/cli.clj,swarmforge/tools/sf/src/sf/shell.clj,swarmforge/tools/sf/src/sf/task.clj,swarmforge/tools/sf/src/sf/verify.clj,swarmforge/verify.conf,test/acceptance/src/acceptance/store_steps.clj,test/acceptance/src/acceptance/support.clj,test/sf_test.sh,test/sf_tool_test.sh
summary: sf finish refuses a dirty worktree and verifies the merge via swarmforge/verify.conf
```

```text
type: handoff
from: cleaner
to: architect
branch: swarm/make-sf-finish-safe-and-verify-5223659
commit: bc10212
files: swarmforge/tools/sf/spec/sf/task_flow_spec.clj
summary: cleaner: covered the unbuildable merge candidate; CRAP and DRY clean, no other cleanup
```

```text
type: handoff
from: architect
to: hardener
branch: swarm/make-sf-finish-safe-and-verify-5223659
commit: 217edc2
files: swarmforge/tools/sf/property/sf/verify_property.clj,swarmforge/tools/sf/src/sf/shell.clj,swarmforge/tools/sf/src/sf/verify.clj
summary: architect: verify.conf reads through shared shell/conf-lines; pins unchanged; verify property tests added
```

```text
type: handoff
from: hardener
to: qa
branch: swarm/make-sf-finish-safe-and-verify-5223659
commit: 31a5eff
files: -
summary: hardener: all mutants killed except equivalent renames in finish_resume; CRAP 7.0, DRY clean
```

```text
type: handoff
from: qa
to: done
branch: swarm/make-sf-finish-safe-and-verify-5223659
commit: a4c8bf6
files: README.md,swarmforge/README.md,swarmforge/bin/sf-vb,swarmforge/constitution/articles/engineering.md,swarmforge/handoff-protocol.md,swarmforge/languages/vbnet.md,swarmforge/tools.conf,swarmforge/tools/csharp/src/sfcs/cli.clj,swarmforge/tools/csharp/src/sfcs/cobertura.clj,swarmforge/tools/csharp/src/sfvb/cli.clj,swarmforge/tools/csharp/test/sfcs/core_test.clj,swarmforge/tools/javascript/test/sfjs/architecture_test.clj,swarmforge/tools/sf/spec/sf/architecture_spec.clj,swarmforge/tools/sf/spec/sf/dispatch_spec.clj,swarmforge/tools/sf/src/sf/dispatch.clj,swarmforge/tools/sf/src/sf/task.clj,test/acceptance/src/acceptance/metrics_steps.clj,test/features/metrics_cache/README.md,test/features/metrics_cache/install_ignore.feature,test/features/metrics_cache/metrics_save.feature,test/features/task_store/finish_resume.feature,test/sf_tool_test.sh
summary: qa: finish verified on the merge with main; dirty-worktree, verification and resume paths pass
```
