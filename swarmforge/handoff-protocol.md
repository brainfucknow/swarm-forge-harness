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

The newest record in first-parent history supplies stage, route, gates, and needs.
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

### Backend

sf stores on each record the backend detected in the command environment, in two
sf-only trailers: `SwarmForge-Backend` (`claude`, `codex`, `copilot` or
`mistral`) and `SwarmForge-Dispatched` (`dispatched` or `session`). A record
has both or neither. The operator's records carry the detected backend too.

| Environment of the `sf` command | Record carries |
|---|---|
| `SWARMFORGE_DISPATCHED` not blank, `SWARMFORGE_BACKEND` a backend name | that backend, `dispatched` |
| `SWARMFORGE_DISPATCHED` not blank, `SWARMFORGE_BACKEND` anything else | nothing |
| `SWARMFORGE_DISPATCHED` blank or unset, `CLAUDECODE` is `1`, `CODEX_THREAD_ID` blank or unset | `claude`, `session` |
| `SWARMFORGE_DISPATCHED` blank or unset, `CODEX_THREAD_ID` not blank, `CLAUDECODE` is not `1` | `codex`, `session` |
| `SWARMFORGE_DISPATCHED` blank or unset, both session markers active or neither active | nothing |

- `sf dispatch` and `sf run` start each role and gate with
  `SWARMFORGE_BACKEND` set to the backend they run. Outside dispatch sf does
  not read `SWARMFORGE_BACKEND`. Under dispatch it ignores session markers.
- A Copilot or Vibe session and a plain terminal without session markers
  carry nothing.
- Child processes can inherit `CLAUDECODE` and `CODEX_THREAD_ID`. These
  markers identify the command environment, not the immediate parent
  harness. Both active markers omit session metadata.
- The handoff record that `sf approve` writes for the role carries what the
  review record it approves carries, not the backend of the `sf approve`
  command. The approval record carries the backend of the command.
- A `sf finish` merge that stops on conflicts carries the backend of the
  `sf finish` run that wrote its message, whoever commits it.
- sf reads a record with one trailer missing, or with a value not listed
  above, as carrying nothing. Records written before the trailers existed
  carry nothing.

`sf show` and `sf start` print `backend: <name> dispatched` or
`backend: <name> session` as the last line of a record that carries a
backend, and no line for one that carries nothing.

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
| `sf new` | Creates a route and starts its first role. Route precedence: `--route`, `SWARMFORGE_ROUTE`, named `feature`, then all non-optional catalogue roles. `--fixes <target>` names what the task repairs: a task id, stored as the full id, or a pull request, quoted: `--fixes '#115'`. `--found-by` needs `--fixes` and says how the defect was found: `pr-review` (a pull request review after the task left the swarm), `ci` (a CI run) or `operator` (the operator reports it). The created record keeps them; `sf show` prints them as `fixes:` and `found-by:`, and `sf start` as `FIXES:` and `FOUND-BY:` after `ROUTE:`. |
| `sf review` | Specifier requests operator approval of its committed specification. |
| `sf approve` / `sf reject` | Approve advances the specifier; reject requests changes. Approval must match the reviewed role commit; a new specification needs review again. An answer or rejection is not approval. Each `--rejected "<finding> \| <reason>"` on approve records a gate finding the operator approved over as a `Rejected:` trailer of the approval record. |
| `sf spec-verdict <id> <spec-verdict> <text>` | Records the spec-judge's spec verdict on the newest spec revision ([rules](#spec-revisions)). |
| `sf ask` / `sf answer` | Block for an operator decision, then resume the same role. |
| `sf handoff` | Requires a clean task worktree and needs `none`; records HEAD and advances to the next route role, then `done`. Use `--to` only as the role prompt directs; it names the current stage or a later route role, and `done` only after the route's last role. When the stage's role has the `send-back` flag, `--to` may also name an earlier route role other than the specifier; the task then moves forward along the route again. A send-back is a handoff record whose target comes before its sender on the route. `sf` refuses the third send-back of a task. |
| `sf route <id> <route>` | Changes an open task's route, retaining the current stage. Roles must follow catalogue order. |
| `sf new --gates <gates>` / `sf gates <id> [<gates>]` | `<gates>` is a comma list of gate roles (`sf gates` lists them), or `none`. sf stores each role once, in `roles.conf` order, and refuses an empty value or a role that is not a gate. A task without the option has gates `none`, also a task created before gates existed. `sf gates <id>` prints the task's gates, one per line, and nothing for `none`. `sf gates <id> <gates>` changes an open task's gates with a `gates` record that keeps stage, route and needs; it is not a spec revision. `sf start` and `sf show` print `GATES:` after `ROUTE:`; `sf ls` prints the gates as the fifth column. |
| `sf next <role>` | Lists open tasks at that stage needing nothing; blocked tasks stay hidden. |

`sf new`, `sf approve`, `sf reject`, `sf spec-verdict`, `sf answer`, `sf finish`,
a route change and a gates change are the operator's. `sf dispatch` and `sf run` start each role and gate with
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

Before the audit gate, `sf handoff` and `sf review` read every `Not-tested`
trailer (key in any case) of the role's commits since the newest created,
review or handoff record. A trailer names a tool when the name of a non-local
`tools.conf` row appears in it, ignoring case, with no letter, digit, `-` or
`_` next to it. When `sf tool require` would exit 1 for a named tool, the
command exits 4 and records nothing. It prints a `MISSING_TOOL` line and a
`Run: sf tool ensure <tool>` line per missing tool, then what to do. The check
catches tool names only; the
[engineering rule](constitution/articles/engineering.md#startup-tools) is the
main control.

`--allow-missing-tool`, anywhere in the arguments and left out of the summary,
lets the command record over a missing tool only when a question record since
that milestone names the tool and an operator answer follows the question.
Otherwise the command exits 4 with an `ALLOW_REFUSED` line for each tool not
accepted. A record made over missing tools carries the sf-only
`SwarmForge-Missing-Tools` trailer, which `sf show` and `sf start` print as
`missing-tools: <tools>`. `sf approve` does not run the check; its review did.
`sf answer` refuses a role only under `sf dispatch`, so sf enforces the
operator's part only there.

### Finishing and recovery

`sf finish` refuses staged, unstaged, or untracked worktree changes without
changing anything. It verifies a merge candidate in the task worktree, using
the base branch as the current side of the merge. Commands run sequentially
with `bash -c` from that checkout's root. `VERIFY:` reports each pass or
`none configured`. A failed command exits 1, preserving both branches and the
original worktree.

Without `--into`, `sf finish <id>` merges into the main checkout's current
branch, unless that is the repository's default branch (the remote's HEAD,
else `init.defaultBranch`, else `master`): it refuses with exit 1, prints the
pull-request alternative, and changes nothing. An explicit `--into BRANCH`
always chooses the target, also when BRANCH is the default branch.

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

With a `spec-judge` row in `roles.conf`, the specification gate of a task with gates loops. After the task's gates run on each spec revision, the spec-judge gives a spec verdict and the orchestrator records it with `sf spec-verdict`. Remove the spec-judge line to turn the loop off. `sf spec-verdict` then refuses.

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
  - `WARNING: total growth <total> over spec revision 1 (<S1>); more than half over across operator send-backs`, when the newest spec revision's baseline is not spec revision 1, S1 is not 0 and `(size - S1) * 100 > 50 * S1`;
  - `NOTE: total growth <total> over spec revision 1 (<S1>); not a limit`, under the same condition as the WARNING. With baseline spec revision 1, `LIMIT: growth` says it.
- The WARNING and the NOTE are not limits: `sf spec-verdict continue` is not refused for them; only the LIMIT lines refuse it.
- `sf reject <id> <text>` prints the WARNING line after `REJECTED: <id> stays at stage:<stage>` when the condition holds for the newest spec revision, the one it sends back. The reject still exits 0 and writes the changes-requested record; it is never refused.
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
first, then open tasks by id, then unclosed histories by id. It reads only:
no ref, worktree, or file changes, and any role may run it. A `swarm/*` branch with no record is
skipped with an `sf: ` line on stderr.

Each line has `id`, `title`, `request`, `status` (`open`, `finished`, or `unclosed`),
`truncated`, `stage`, `needs`, `route` and `gates` (arrays; `gates` is empty for `none`) from the newest record,
`fixes` and `found_by` from the created record (null when it has none, or
the task is truncated), plus `records` and `lore`. A `fixes` value that
starts with `#` is a pull request; any other is a task id.

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
  Every element ends with `backend` and `dispatched`, from the record's
  [backend](#backend): `backend` is the name and `dispatched` is `true` for
  a dispatched record and `false` for a session record. Both are null for
  a record that carries nothing.
- `lore`: every Lore trailer `sf show` prints, evidence and superseded
  commits included, except `Lore-id`, as `{commit, role, key, value,
  lore_id}`, `role` null for `-`. `lore[].commit` is the commit that carries
  the trailer; it equals a `records[].commit` only when a review or handoff
  recorded that commit, so it is not a join key.

An `unclosed` history has recognized records reachable from HEAD but no local
task branch or `sf finish` record. Recognition requires a nonblank task id and
a known record type; incidental or unknown-type trailers do not establish a
record. Its newest recognized record anchors the history; later unrecorded
commits cannot be attributed to it. `sf show` and `sf harvest` expose that
history. `sf stage` and `sf route <id>` return its last recorded values, or fail
explicitly if the requested value is absent. These values do not prove that
verification or lifecycle closure ran. Execution and lifecycle commands,
including `run`, `dispatch` and `finish`, refuse this read-only history without
recreating its branch. It does not appear in `sf ls` and is not a finished task.
Discovery cannot recover records removed by a squash or rebase.

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
