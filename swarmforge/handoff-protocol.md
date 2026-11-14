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
| `sf spec-verdict <id> <spec-verdict> <text>` | Records the spec-judge's spec verdict on the newest spec revision ([rules](#spec-revisions)). |
| `sf ask` / `sf answer` | Block for an operator decision, then resume the same role. |
| `sf handoff` | Requires a clean task worktree and needs `none`; records HEAD and advances to the next route role, then `done`. Use `--to` only as the role prompt directs; it names the current stage or a later route role, and `done` only after the route's last role. |
| `sf route <id> <route>` | Changes an open task's route, retaining the current stage. Roles must follow catalogue order. |
| `sf next <role>` | Lists open tasks at that stage needing nothing; blocked tasks stay hidden. |

`sf new`, `sf approve`, `sf reject`, `sf spec-verdict`, `sf answer`, `sf finish`
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

## Spec revisions

With a `spec-judge` row in `roles.conf`, the specification gate loops. After the gates of each spec revision, the spec-judge gives a spec verdict and the orchestrator records it with `sf spec-verdict`. Remove the spec-judge line to turn the loop off. `sf spec-verdict` then refuses.

- A spec revision is one `review` record made at stage specifier, numbered from 1. It includes the `spec-verdict` and `changes-requested` records before the next review. A review of another stage is not a spec revision.
- `sf spec-verdict <id> converged|needs-operator|stuck <text>` writes a `spec-verdict` record from the spec-judge. The task keeps stage, route and needs, and waits for approval.
- `sf spec-verdict <id> continue <text>` writes a `changes-requested` record from the spec-judge, an automatic send-back. Like `sf reject`, it keeps the stage and clears needs.
- Both carry the `SwarmForge-Spec-Verdict` trailer. A record with a spec verdict prints a `spec-verdict:` line after `from:`.
- sf reads a record of an older sf the same way: type `verdict` as `spec-verdict`, and the trailer `SwarmForge-Verdict` as `SwarmForge-Spec-Verdict`. Its `from:` stays as stored.
- sf records nothing and refuses a spec verdict when:
  - `SWARMFORGE_DISPATCHED` is set;
  - there is no spec-judge;
  - the stage is not specifier;
  - the task is not waiting for approval;
  - the spec revision already has a spec verdict.
- sf refuses `continue` when a limit holds. It checks the cap first.
  - Cap: the task has 2 automatic send-backs. `sf reject` never counts.
  - Growth: the spec revision is over the growth limit.
- Counted commits of a spec revision: the first-parent commits after the created record, up to the spec revision's reviewed commit, that are not records or merges.
- Size is the sum of:
  - the nonblank message lines, without the trailer block, of each counted commit that no counted commit supersedes (`Supersedes: <Lore-id>`);
  - the lines added minus removed over all counted commits (`git show --numstat`), at least 0 in total.
- A merge of the base branch adds nothing.
- The baseline of a spec revision is the first spec revision after the newest `sf reject` before it, else spec revision 1. B is its size.
- Growth is `(size - B) * 100 / B`, rounded down, or `-` when B is 0.
- A spec revision is over the growth limit when B is not 0 and `(size - B) * 100 > 50 * B`.
- Total is the growth of a spec revision's size over the size of spec revision 1, S1, by the same formula: `-` when S1 is 0, `0%` in spec revision 1.
- `sf spec-revisions <id> [--full]` only reads. It runs for any role on any task `sf show` reads, and refuses any other argument. It prints `NO_SPEC_REVISIONS`, or:
  - per spec revision, `SPEC-REVISION <n> <sha> size:<size> growth:<growth> total:<total> spec-verdict:<spec-verdict or ->`; with `--full`, the text of its spec-verdict and changes-requested records under it;
  - one line per limit that holds for the newest spec revision: `LIMIT: cap 2 of 2 automatic send-backs used` and `LIMIT: growth size <size> is more than half over <B>`;
  - `NOTE: total growth <total> over spec revision 1 (<S1>); not a limit`, when the newest spec revision's baseline is not spec revision 1, S1 is not 0 and `(size - S1) * 100 > 50 * S1`. With baseline spec revision 1, `LIMIT: growth` says it.
- The NOTE is not a limit: `sf spec-verdict continue` is not refused for it.
- `sf show <id>` prints the record texts with or without `--full`.

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
  (review and handoff, a number) and `spec_verdict` (a spec-verdict record
  or an automatic send-back), each null where it does not apply.
  `rejected` holds one `{finding, reason, kind, severity}` per `Rejected:`
  trailer, split at the last ` | `; `kind` and `severity` come from a
  `DEFECT|PREMISE, CRITICAL|HIGH|MEDIUM|LOW: ` prefix, else null. `files:`
  are not exported.
  Every element also has `time` and `recorded_by`, never null: the author
  date of the record commit in UTC, as `YYYY-MM-DDTHH:MM:SSZ`, and the git
  author name of that commit. The name is the backend (`Claude`, `Codex`)
  only where each backend has its own git identity. For a finished record
  the record commit is the `sf finish` merge.
- `lore`: every Lore trailer `sf show` prints, evidence and superseded
  commits included, except `Lore-id`, as `{commit, role, key, value,
  lore_id}`, `role` null for `-`. `lore[].commit` is the commit that carries
  the trailer; it equals a `records[].commit` only when a review or handoff
  recorded that commit, so it is not a join key.

A task whose readable history lacks its `created` record (a shallow clone)
is `truncated`: `title` and `request` are empty, its readable records are
listed, a commit outside the history is null, and its Lore starts after its
oldest readable record.

In a shallow repository `sf harvest` first prints this line on stderr and
changes nothing else:
`sf: shallow repository: older tasks are truncated or missing; run git fetch --unshallow`.

## Audit

`sf show <id>` gives the complete request and record history. Inspect role
work with `git log --first-parent swarm/<id>` before finishing, or the merge's
second parent afterwards.
