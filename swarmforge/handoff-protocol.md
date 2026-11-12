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
| `sf approve` / `sf reject` | Approve advances the specifier; reject requests changes. Approval must match the reviewed role commit; a new specification needs review again. An answer or rejection is not approval. Each `--rejected "<finding> \| <reason>"` on approve records a gate finding the operator approved over as a `Rejected:` trailer of the approval record. |
| `sf verdict <id> <verdict> <text>` | Records the judge's verdict on the newest round (see below). `continue` is an automatic send-back; the others leave the task waiting for approval. |
| `sf ask` / `sf answer` | Block for an operator decision, then resume the same role. |
| `sf handoff` | Requires a clean task worktree and needs `none`; records HEAD and advances to the next route role, then `done`. Use `--to` only as the role prompt directs; it names the current stage or a later route role, and `done` only after the route's last role. |
| `sf route <id> <route>` | Changes an open task's route, retaining the current stage. Roles must follow catalogue order. |
| `sf next <role>` | Lists open tasks at that stage needing nothing; blocked tasks stay hidden. |

`sf new`, `sf approve`, `sf reject`, `sf verdict`, `sf answer`, `sf finish`
and a route change are the operator's. `sf dispatch` and `sf run` start each role and gate with
`SWARMFORGE_DISPATCHED` set to its name, and those commands refuse to run
under it. `sf run` also stops when a stage it dispatched added an operator
record. Subagents that the session starts share the session's environment,
so for them this rule rests on the role prompts.

For an `audit` role, the first handoff or review at a HEAD exits 3 with
`AUDIT_REQUIRED`, recording nothing. Complete the self-review before retrying
at that HEAD; a new commit requires a new audit. The role is the acting role:
`SWARMFORGE_ROLE`, else the task's stage. Pending audit state is kept per
role, in `.swarmforge/audit/<id>/<role>`: the HEAD the gate last refused that
role at and the number of that role's refusals since its last audited record
on the task. Only a refusal of the same role at the same HEAD lets an attempt
through, and another role's refusals never add to its count. Writing a role's
record clears that role's file alone, and only once the record is written, so
refusals before a write that failed still count; the directory goes when no
role is pending. `sf approve` clears nothing: a specifier refused while
waiting for approval keeps that refusal for its own next audited record. A
file at `.swarmforge/audit/<id>` itself is from an earlier sf that kept one
state per task. It names no role, so it counts for none: the next audited
attempt deletes it and is refused as that role's first.

The audited role's review or handoff record carries that number in an
sf-only `SwarmForge-Audit-Refusals` trailer, which `sf show` and `sf start`
print as `audit-refusals: <n>`. Because the first attempt at each HEAD is
always refused, 1 means a clean audited record and n - 1 counts the extra
audit rounds. Records of unaudited roles, the handoff that `sf approve`
performs, and records written before the count existed carry none.

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

## Specification rounds

With a `judge` row in `roles.conf`, the specification gate loops: after the
gates of each round the judge gives a verdict, which the orchestrator
records with `sf verdict`. Removing the judge line turns the loop off;
`sf verdict` then refuses.

- A round is one `review` record made at stage specifier, numbered from 1,
  with the `verdict` and `changes-requested` records after it and before the
  next one. A review of another stage is not a round.
- `sf verdict <id> converged|needs-operator|stuck <text>` writes a `verdict`
  record from the judge; stage, route and needs stay, so the task still
  waits for approval. `sf verdict <id> continue <text>` writes a
  `changes-requested` record from the judge, an automatic send-back: it
  keeps the stage and clears needs like `sf reject`. Records with a verdict
  print a `verdict:` line after `from:`. sf refuses a verdict, recording
  nothing, under `SWARMFORGE_DISPATCHED`, without a judge, outside stage
  specifier, when the task is not waiting for approval, and when the round
  already has one.
- sf refuses `continue` when a limit holds, cap first: the task already has
  2 automatic send-backs (`sf reject` never counts), or the round is over the
  growth limit.
- Counted commits of a round: the first-parent commits after the created
  record up to the round's reviewed commit that are neither records nor
  merges. Size is the sum of the nonblank message lines, without the trailer
  block, of each counted commit no counted commit supersedes (by
  `Supersedes: <Lore-id>`), and of the lines added minus removed over every
  counted commit (`git show --numstat`, at least 0 in total). A merge of the
  base branch adds nothing.
- The baseline of a round is the first round after the newest `sf reject`
  before it, else round 1; B is its size. Growth is shown as
  `(size - B) * 100 / B` rounded down, `-` when B is 0. The round is over the
  growth limit when `(size - B) * 100 > 50 * B` and B is not 0.
- `sf rounds <id>` reads only and runs for any role on any task `sf show`
  reads. It prints `NO_ROUNDS`, or per round
  `ROUND <n> <sha> size:<size> growth:<growth>% verdict:<verdict or ->`
  with each of its verdict and changes-requested texts, then one line per
  limit that holds for the newest round:
  `LIMIT: cap 2 of 2 automatic send-backs used` and
  `LIMIT: growth size <size> is more than half over <B>`.

## Sharing

Push `swarm/<id>` to share its work and records; fetch it in another clone.
Record commits change no files, so a rebase replays them without conflicts,
but onto other role commits: a record then sits on a commit its role never
saw. Merge a fetched task branch rather than rebasing it. A `review` record
names the commit it reviewed, so `sf approve` refuses a review that a rebase
moved; run `sf review` again.

Mutation manifests live on the orphan branch `swarmforge/metrics`, mirroring
`.metrics/mutate/` and `.metrics/gherkin/`. `sf start` loads missing files;
`sf handoff` saves new and changed ones without checking out that branch.
Push `swarmforge/metrics` explicitly to share it; `sf` does not push it.

## Harvest

`sf harvest` takes no arguments and prints one JSON line per task for
mining role failure patterns: finished tasks first, oldest finish merge
first, then open tasks by id. It reads only: no ref, worktree, or file
changes, and any role may run it. A `swarm/*` branch with no record is
skipped with an `sf: ` line on stderr.

Each line has `id`, `title`, `request`, `status` (`open` or `finished`),
`truncated`, `stage`, `needs` and `route` (an array) from the newest record,
plus `records` and `lore`:

- `records`, oldest first, as `sf show` prints them. Every element has
  `type`, `from`, `stage`, `needs`, `route`, `commit` (created, review and
  handoff only), `text` (the free text of any record but created),
  `merged_into` (finished), `rejected` (approval), `audit_refusals`
  (review and handoff, a number) and `verdict` (a verdict record or an
  automatic send-back), each null where it does not apply.
  `rejected` holds one `{finding, reason, kind, severity}` per `Rejected:`
  trailer, split at the last ` | `; `kind` and `severity` come from a
  `DEFECT|PREMISE, CRITICAL|HIGH|MEDIUM|LOW: ` prefix, else null. `files:`
  are not exported.
- `lore`: every Lore trailer `sf show` prints, evidence and superseded
  commits included, except `Lore-id`, as `{commit, role, key, value,
  lore_id}`, `role` null for `-`. `lore[].commit` is the commit that carries
  the trailer; it equals a `records[].commit` only when a review or handoff
  recorded that commit, so it is not a join key.

A task whose readable history lacks its `created` record (a shallow clone)
is `truncated`: `title` and `request` are empty, its readable records are
listed, a commit outside the history is null, and its Lore starts after its
oldest readable record.

## Audit

`sf show <id>` gives the complete request and record history. Inspect role
work with `git log --first-parent swarm/<id>` before finishing, or the merge's
second parent afterwards.
