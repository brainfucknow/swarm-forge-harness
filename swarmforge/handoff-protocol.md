# Handoff Protocol

SwarmForge roles communicate through plain git. A task is its branch,
`swarm/<id>`; there is no daemon, no inbox directory, no dashboard, and no
other store. The branch's first commit adds the task file; every later sf
action is a record commit whose trailers hold the state. `swarmforge/bin/sf`
is the only writer.

## Task

| Part | Where | Meaning |
|---|---|---|
| `<id>` | branch name | A slug of the title, `-`, and 7 random hex digits, for example `add-login-3f9a2c1`. A command also takes the 7 hex digits or a unique prefix. |
| `swarm/<id>` | git branch | All work for the task, every role in sequence, and its records. |
| `tasks/<id>.md` | file on the branch | Front matter (`id`, `created`), the title as a `#` heading, and the operator's request. No command edits it while the task is open. |
| `tasks/done/<id>.md` | file on the branch, then on the base branch | The task file after the handoff to `done`: `stage` and `route` in the front matter, and a `## Records` section with every record fenced. A task is finished once this file is in the main checkout's HEAD. |
| `SwarmForge-Stage` | trailer of the newest record | The role that owns the task now, or `done`. |
| `SwarmForge-Route` | trailer of the newest record | The roles this task visits, in catalogue order. Set by `sf new --route`, changed by `sf route`. |
| `SwarmForge-Needs` | trailer of the newest record | `none`; `operator` after `sf ask` until `sf answer`; `approval` after `sf review` until `sf approve` or `sf reject`. |
| `.worktrees/<id>` | directory | The checkout roles work in. Created by `sf start`, removed by `sf finish`. |
| `swarmforge/verify.conf` | file on the base branch | The project's verification: one shell command per line; blank lines and `#` lines are skipped. `sf finish` runs them on the merge before it merges. Optional. |

## Record commits

Every sf action is one commit on the task branch:

```text
sf <record>: <id>

<free text, when the record has one; any number of lines>

SwarmForge-Task: <id>
SwarmForge-Record: <record>
SwarmForge-From: <role or operator>
SwarmForge-Stage: <stage after the record>
SwarmForge-Route: <route after the record>
SwarmForge-Needs: none | operator | approval
```

The newest record (first-parent history of the branch) is the state. `sf new`
writes `created`, whose commit adds `tasks/<id>.md`. Every other record
changes no file and touches no worktree, except the handoff to `done`, which
moves the task file to `tasks/done/<id>.md`. `sf finish` writes `finished` as
the `--no-ff` merge commit on the base branch, with
`SwarmForge-Merged-Into: <branch>`. The commit-msg hook refuses a role commit
whose trailer block has a `SwarmForge-*` trailer. Author: git's `user.name`
and `user.email`, else `SWARMFORGE_USER` and `SWARMFORGE_EMAIL`.

## Lore lines

After the records, `sf start` and `sf show` print the
[Lore](https://github.com/Ian-stetsenko/lore-protocol) trailers of the task
branch, one line per trailer:

```text
LORE: 3e1f0a2 coder Constraint: keep the byline format
LORE: 3e1f0a2 coder Rejected: byline as a trailer | breaks old commits
LORE: 9b07c44 qa Lore-id: 5d2c81fe
LORE: 9b07c44 qa Tested: byline placement
```

The commits are those on the first-parent history of `swarm/<id>` after the
`created` record, oldest first, trailers in message order; a merge of the base
branch brings in no lines. The sha is abbreviated as
`git rev-parse --short` prints it; the role is the commit's last
`By <role>.` line, or `-`. Only trailers that git reads from the trailer block
count, with folded values on one line, and only Lore keys (`Constraint`,
`Rejected`, `Confidence`, `Scope-risk`, `Reversibility`, `Directive`,
`Tested`, `Not-tested`, `Supersedes`, `Depends-on`, `Related`, `Lore-id`).
After `sf finish`, `sf show` reads them from the merge's second parent. After
a host squash merge, it prints the records from `tasks/done/<id>.md`, and
`LORE:` lines only while the task branch still exists.

## What sf start leaves out

`sf show` prints every record and every `LORE:` line. `sf start` gives a role
a shorter brief:

- Only the last 10 records. When there are more, the line
  `RECORDS: <k> earlier records omitted; sf show <id> prints them` comes after
  the request, before the first printed record.
- No evidence lines: `Tested`, `Not-tested`, `Confidence` and `Lore-id`.
- No lines of a superseded commit. A `Supersedes: <Lore-id>` trailer on any
  commit of the branch removes every line of the commit with that `Lore-id`;
  the `Supersedes` line stays unless its own commit is superseded, and a value
  that matches no commit removes nothing.
- When it leaves out any `LORE:` line, the line
  `LORE-OMITTED: <k> superseded or evidence lines; sf show <id> prints them`
  comes before the printed `LORE:` lines.

`test/features/task_store/start_brief.feature` specifies this.

## Lifecycle

```text
sf new ──► stage <first role>
             │ sf review        (specifier only)
             ▼
         needs approval ──sf reject──► stage <first role>
             │ sf approve
             ▼
         stage coder ──sf handoff──► stage cleaner ──► … ──► stage done
             │ sf ask                                    (tasks/done/<id>.md)
             ▼                                                 │ sf finish
         needs operator ──sf answer──► same stage              ▼
                                                        merged, branch removed
```

- `sf new` records the route (`--route`, else `SWARMFORGE_ROUTE`, else
  `feature` from `swarmforge/routes.conf`, else every role) and sets the stage
  to its first role. A route is any subset of `swarmforge/roles.conf` in that
  file's order.
- `sf handoff` requires a clean worktree on the task branch and needs
  `none`. It records HEAD and moves the stage to the next role on the task's
  route, or to `done` after the last one. `--to <role>` jumps elsewhere; role
  prompts say when that is allowed.
- The specifier leaves its stage only through `sf approve` after `sf review`.
  Approval must match the reviewed role commit; a new specification commit
  requires another review. Rejection or an answered question is not approval.
- `sf route <id> <route>` changes an open task's route; the new route must
  contain the current stage. The change is recorded as `type: route`.
- For a role flagged `audit` in `roles.conf`, the first `sf handoff` or
  `sf review` at a given HEAD exits 3 with `AUDIT_REQUIRED` and a self-review
  checklist, recording nothing; the next attempt at the same HEAD goes
  through. A new commit starts a new audit. State lives in
  `.swarmforge/audit/<id>` and is removed when the record is written.
- `sf next <role>` lists open tasks at that stage that need nothing, so a
  blocked task is invisible to the role until the operator acts.
- `sf finish` refuses a task worktree with uncommitted changes (staged,
  unstaged or untracked) and changes nothing. It then detaches the worktree at
  the base branch and merges `swarm/<id>` there without committing, as the
  real merge will, runs the commands
  of that checkout's `swarmforge/verify.conf` with `bash -c` from its root,
  printing `VERIFY: passed: <command>` for each (or `VERIFY: none
  configured`), and returns the worktree to the branch tip. A failing command
  stops `sf finish` with exit 1 and leaves the base branch, the task branch
  and the worktree as they were. On a pass it merges `swarm/<id>` into the
  base branch with `--no-ff` (the merge commit is the `finished` record),
  then removes the worktree without `--force`, then the branch; a rerun
  completes a cleanup that stopped. A finished task refuses `sf start` and
  every record.
- When that merge stops on conflicts, the operator resolves and commits them
  in the main checkout and runs `sf finish` again, which verifies the
  committed merge there before it records it and cleans up. If that
  verification fails, fix the merge with `git commit --amend` and run
  `sf finish` again; a new commit on top of the merge is refused as finished.

## Sharing

Everything is under `refs/heads/`, so any host that takes branch pushes takes
a task: `git push origin swarm/<id>` moves the task with its records and its
work, and a fetch brings it back. Records change no file, so
`git pull --rebase origin swarm/<id>` never conflicts on them; the newest
record wins.

Mutation manifests are a cache on the orphan branch `swarmforge/metrics`,
whose tree mirrors `.metrics/mutate/` and `.metrics/gherkin/`. `sf start`
copies the cached files a worktree lacks, and `sf handoff` commits the new and
changed ones without a checkout. sf does not push it: `git push origin
swarmforge/metrics` shares the cache, and a fetch brings it back.

## Audit

`sf show <id>` prints the complete history: request, every question and
answer, every handoff with its commit, the approval, and the merge.
`git log --first-parent swarm/<id>` before finishing, or the merge commit
after, shows the code and the records.
