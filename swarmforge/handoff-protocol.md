# Handoff Protocol

A task is a Git branch, `swarm/<id>`, containing the request, role work, and
record commits. `swarmforge/bin/sf` is the only writer of task state; never
edit task files or write `SwarmForge-*` trailers by hand.

## Task

| Part | Purpose |
|---|---|
| `<id>` | Title slug plus seven random hex digits, e.g. `add-login-3f9a2c1`. Commands also accept the suffix or a unique prefix. |
| `tasks/<id>.md` | Operator request, title, ID, and creation date; unchanged while open. |
| `tasks/done/<id>.md` | Archive created at `done`, with stage, route, and all records; presence in main checkout HEAD marks the task finished. |
| `swarmforge/project.conf` | `tasks-dir <dir>` puts the task files under `<dir>/` instead of `tasks/`; sf reads the main checkout's. |
| `.worktrees/<id>` | Role checkout, created by `sf start` and removed by `sf finish`. |
| `swarmforge/verify.conf` | Optional project verification commands, one per line; blank lines and `#` comments are ignored. |

## Record commits

The newest record in first-parent history supplies stage, route, and needs.
Needs is `none`, `operator` while a question awaits an answer, or `approval`
while a reviewed specification awaits a decision. `sf show <id>` prints the
records; use it rather than reconstructing them from trailers.

Paths here use the default `tasks/`; read them as the configured `tasks-dir`.

Records change no files except `created` (adds the request), handoff to
`done` (archives it), and `moved` (see [Moving the task files](#moving-the-task-files)). `finished` is the base branch's `--no-ff` merge commit.
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
After a host squash merge, archived records remain readable, but Lore lines
require the task branch to still exist.

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
| `sf handoff` | Requires a clean task worktree and needs `none`; records HEAD and advances to the next route role, then `done`. Use `--to` only as the role prompt directs. |
| `sf route <id> <route>` | Changes an open task's route, retaining the current stage. Roles must follow catalogue order. |
| `sf next <role>` | Lists open tasks at that stage needing nothing; blocked tasks stay hidden. |

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
rerun `sf finish`. It verifies that committed merge before recording and
cleaning up. If verification fails, fix it with `git commit --amend` and retry;
a new commit on top of the merge is refused as finished. Nobody writes the
finished-record trailers manually.

## Moving the task files

To change `tasks-dir`, edit it in the main checkout's `swarmforge/project.conf`,
then run `sf move-tasks <old-dir>`. On each open task branch it records a
`moved` record that moves the task file and keeps the stage, route, and needs,
so a task waiting on the operator keeps waiting. It refuses before moving
anything when a task worktree has uncommitted changes, and brings each clean
task worktree to the new tip. In the main checkout it stages a `git mv` of the
archives under `<old-dir>/done/`; commit them with `project.conf`. Only files
named `<id>.md` and `done/<id>.md` move; other files under `<old-dir>/` stay.
The next review or handoff lists the moved paths among its files.

## Sharing

Push `swarm/<id>` to share its work and records; fetch it in another clone.
Record commits change no files, so rebasing them introduces no record-file
conflicts; the newest record supplies the state.

Mutation manifests live on the orphan branch `swarmforge/metrics`, mirroring
`.metrics/mutate/` and `.metrics/gherkin/`. `sf start` loads missing files;
`sf handoff` saves new and changed ones without checking out that branch.
Push `swarmforge/metrics` explicitly to share it; `sf` does not push it.

## Audit

`sf show <id>` gives the complete request and record history. Inspect role
work with `git log --first-parent swarm/<id>` before finishing, or the merge's
second parent afterwards.
