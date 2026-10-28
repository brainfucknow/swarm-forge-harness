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

## Records

`sf start` and `sf show` print each record as a block of `key: value` lines,
oldest first, after the request. A value of several lines continues on lines
indented by two spaces. Later roles read them from `sf start`; the operator
reads them from `sf show`.

```text
type: created
from: operator
branch: swarm/add-login-3f9a2c1
commit: 1c0ffee
route: specifier,coder,cleaner,architect,hardener,qa
```

```text
type: handoff
from: coder
to: cleaner
branch: swarm/add-login-3f9a2c1
commit: 9f1c2ab
files: src/Login.cs,test/LoginTest.cs
summary: login scenarios pass
```

The `commit` of a `created` record is its own commit; that of a `review` or
`handoff` record is the role's commit it was made on. `files` lists what
changed between the previous `created`, `review` or `handoff` record's
`commit` and this one's, capped at forty names, or `-` when nothing changed.
`review` records carry the same keys.

```text
type: question
from: coder
question: Which password hash: bcrypt or argon2id?
```

```text
type: answer
from: operator
answer: argon2id
```

```text
type: review
from: specifier
commit: 4e21d0c
files: features/login.feature,qa/login.md
summary: login spec and QA procedure
```

```text
type: approval
from: operator
```

```text
type: changes-requested
from: operator
reason: split the lockout scenarios into their own feature
```

```text
type: route
from: operator
route: specifier,coder,cleaner,qa
```

```text
type: finished
from: operator
merged-into: main
```

`from` is the acting role: `SWARMFORGE_ROLE` when set, otherwise the task's
current stage. Operator records always say `operator`.

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
- `sf finish` merges `swarm/<id>` into the base branch with `--no-ff` (the
  merge commit is the `finished` record), then removes the worktree and the
  branch. A finished task refuses `sf start` and every record.

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
