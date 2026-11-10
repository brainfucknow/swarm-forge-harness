# Handoff Protocol

A task is a Git branch, `swarm/<id>`, containing the request, role work, and
record commits. `swarmforge/bin/sf` is the only writer of task state; never
write `SwarmForge-*` trailers by hand. There are no task files.

## Task

| Part | Purpose |
|---|---|
| `<id>` | Title slug plus seven random hex digits, e.g. `add-login-3f9a2c1`. Commands also accept the suffix or a unique prefix. |
| `created` record | The branch's first commit: the title and the operator's request in its message. |
| `finished` record | The `sf finish` merge. A task is finished when the main checkout's HEAD reaches it through any parent. |
| `.worktrees/<id>` | Role checkout, created by `sf start` and removed by `sf finish`. |
| `swarmforge/verify.conf` | Optional project verification commands, one per line; blank lines and `#` comments are ignored. |

## Record commits

The newest record in first-parent history supplies stage, route, and needs.
Needs is `none`, `operator` while a question awaits an answer, or `approval`
while a reviewed specification awaits a decision. `sf show <id>` prints the
records; use it rather than reconstructing them from trailers.

Records change no files. `finished` is the base branch's `--no-ff` merge
commit. sf reads only local `swarm/*` branches; a remote one never lists or
reopens a task. A task made before task files were dropped keeps its request
in the file its `created` commit adds, and its handoff to `done` deletes it.
The hook rejects reserved `SwarmForge-*` trailers in role commits. Git's
`user.name` and `user.email` supply the author, with `SWARMFORGE_USER` and
`SWARMFORGE_EMAIL` as fallbacks.

## Lore lines

`sf show` prints every [Lore trailer](constitution/articles/workflow.md#lore-trailers),
including evidence, from role commits on the task's first-parent history:

```text
LORE: 3e1f0a2 coder Constraint: keep the byline format
LORE: 3e1f0a2 coder Rejected: byline as a trailer | breaks old commits
```

Lines appear oldest commit first, then trailer order. Base-branch merges add
no Lore lines. The SHA is abbreviated; the role is the commit's last
`By <role>.` line, or `-`. Only Git-parsed trailer blocks count; folded values
print on one line. After finishing, `sf show` reads the merge's second parent.
A task squash-merged before `sf finish` stays open at `done` until `sf finish`
merges its branch. One squash-merged after `sf finish` leaves no record in the
base: `sf` no longer knows it; its history stays on the pushed branch.

## What sf start leaves out

`sf start` prints the request and a brief for the receiving role:

- Every question, answer, and changes-requested record, plus the last ten
  other records, oldest first. `RECORDS:` reports how many were omitted.
- Decision Lore, excluding `Tested`, `Not-tested`, `Confidence`, and `Lore-id`.
- No lines from a commit named by a `Supersedes: <Lore-id>` trailer. The
  superseding trailer stays unless its own commit is superseded; unmatched
  IDs remove nothing. `LORE-OMITTED:` counts omitted evidence and superseded lines.

Use `sf show <id>` when the brief omits needed history or evidence.

## Lifecycle

| Command | Transition or requirement |
|---|---|
| `sf new` | Creates a route and starts its first role. Route precedence: `--route`, `SWARMFORGE_ROUTE`, named `feature`, then all non-optional catalogue roles. |
| `sf review` | Specifier requests operator approval of its committed specification. |
| `sf approve` / `sf reject` | Approve advances the specifier; reject requests changes. Approval must match the reviewed role commit; a new specification needs review again. An answer or rejection is not approval. |
| `sf ask` / `sf answer` | Block for an operator decision, then resume the same role. |
| `sf handoff` | Requires a clean task worktree and needs `none`; records HEAD and advances to the next route role, then `done`. Use `--to` only as the role prompt directs; it names the current stage or a later route role, and `done` only after the route's last role. |
| `sf route <id> <route>` | Changes an open task's route, retaining the current stage. Roles must follow catalogue order. |
| `sf next <role>` | Lists open tasks at that stage needing nothing; blocked tasks stay hidden. |

`sf approve`, `sf reject`, `sf answer`, `sf finish` and a route change are the
operator's. `sf dispatch` and `sf run` start each role and gate with
`SWARMFORGE_DISPATCHED` set to its name, and those commands refuse to run
under it. `sf run` also stops when a stage it dispatched added an operator
record. Subagents that the session starts share the session's environment,
so for them this rule rests on the role prompts.

For an `audit` role, the first handoff or review at a HEAD exits 3 with
`AUDIT_REQUIRED`, recording nothing. Complete the self-review before retrying
at that HEAD; a new commit requires a new audit. Pending audit state lives
under `.swarmforge/audit/<id>` and is cleared when the record is written.

### Finishing and recovery

`sf finish` refuses staged, unstaged, or untracked worktree changes without
changing anything. It verifies a merge candidate in the task worktree, using
the base branch as the current side of the merge. Commands run sequentially
with `bash -c` from that checkout's root. `VERIFY:` reports each pass or
`none configured`. A failed command exits 1, preserving both branches and the
original worktree.

On success, `sf finish` merges with `--no-ff`, removes the worktree without
force, then removes the branch. Rerun it to complete interrupted cleanup.
Finished tasks refuse `sf start` and further records.

If the merge conflicts, resolve and commit it in the main checkout, then
rerun `sf finish`. It verifies that committed merge in the task worktree,
detached at HEAD, before recording and cleaning up, so an uncommitted fix in
the main checkout does not count. If verification fails, commit the fix and
retry. When HEAD contains the branch tip without the finished record (a
merge committed with another message, or a host's merge, with or without
commits after it), `sf finish` commits the finished record on top of HEAD as
a merge of the tip. It never rewrites a commit HEAD already has. Nobody
writes the finished-record trailers manually.

## Sharing

Push `swarm/<id>` to share its work and records; fetch it in another clone.
Record commits change no files, so rebasing them introduces no conflicts; the
newest record supplies the state.

Mutation manifests live on the orphan branch `swarmforge/metrics`, mirroring
`.metrics/mutate/` and `.metrics/gherkin/`. `sf start` loads missing files;
`sf handoff` saves new and changed ones without checking out that branch.
Push `swarmforge/metrics` explicitly to share it; `sf` does not push it.

## Audit

`sf show <id>` gives the complete request and record history. Inspect role
work with `git log --first-parent swarm/<id>` before finishing, or the merge's
second parent afterwards.
