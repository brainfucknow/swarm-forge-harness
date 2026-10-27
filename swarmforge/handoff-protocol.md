# Handoff Protocol

SwarmForge roles communicate through [git-bug](https://github.com/git-bug/git-bug)
issues stored in the project repository under `refs/bugs/*`. There is no
daemon, no inbox directory, and no dashboard. A task is an issue; its state is
labels; its history is comments; its work is a branch. `swarmforge/bin/sf`
is the only writer.

## Task

| Part | Where | Meaning |
|---|---|---|
| Title | issue title | Short stable task name. |
| Body | first comment | The operator's request, in their words. |
| `route:<role>,<role>,…` | label, exactly one | The roles this task visits, in catalogue order. Set by `sf new --route`, changed by `sf route`. |
| `stage:<role>` | label, exactly one | The role that owns the task now. `stage:done` after the last role on the route. |
| `needs:operator` | label | A role asked a question. Nothing moves until `sf answer`. |
| `needs:approval` | label | The specifier asked for approval. Nothing moves until `sf approve` or `sf reject`. |
| `swarm/<id>` | git branch | All work for the task, every role in sequence. |
| `.worktrees/<id>` | directory | The checkout roles work in. Created by `sf start`, removed by `sf finish`. |
| Status | open / closed | Closed by `sf finish` after the merge. |

`<id>` is git-bug's seven-character human id.

## Records

Every record is a comment with `key: value` headers. Later roles read them
from `sf start`; the operator reads them from `sf show`.

```text
type: created
from: operator
branch: swarm/3d9502c
commit: 1c0ffee
route: specifier,coder,cleaner,architect,hardener,qa
```

```text
type: handoff
from: coder
to: cleaner
branch: swarm/3d9502c
commit: 9f1c2ab
files: src/Login.cs,test/LoginTest.cs
summary: login scenarios pass
```

`files` lists what changed on the branch since the previous record's
`commit` (the branch point for the first one), capped at forty names, or `-`
when nothing changed. `review` records carry the same header.

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

After the comments, `sf start` and `sf show` print the
[Lore](https://github.com/Ian-stetsenko/lore-protocol) trailers of the task
branch, one line per trailer:

```text
LORE: 3e1f0a2 coder Constraint: keep the byline format
LORE: 3e1f0a2 coder Rejected: byline as a trailer | breaks old commits
LORE: 9b07c44 qa Lore-id: 5d2c81fe
LORE: 9b07c44 qa Tested: byline placement
```

The commits are those on `swarm/<id>` since the commit of the `created`
record, oldest first, trailers in message order. The sha is abbreviated as
`git rev-parse --short` prints it; the role is the commit's last
`By <role>.` line, or `-`. Only trailers that git reads from the trailer block
count, with folded values on one line, and only Lore keys (`Constraint`,
`Rejected`, `Confidence`, `Scope-risk`, `Reversibility`, `Directive`,
`Tested`, `Not-tested`, `Supersedes`, `Depends-on`, `Related`, `Lore-id`).
After `sf finish` the branch is gone and no `LORE:` lines are printed;
`git log --format='%(trailers)'` on the base branch still shows them.

## Lifecycle

```text
sf new ──► stage:<first role>
             │ sf review        (specifier only)
             ▼
         needs:approval ──sf reject──► stage:<first role>
             │ sf approve
             ▼
         stage:coder ──sf handoff──► stage:cleaner ──► … ──► stage:done
             │ sf ask                                          │ sf finish
             ▼                                                 ▼
         needs:operator ──sf answer──► same stage           closed, merged
```

- `sf new` records the route (`--route`, else `SWARMFORGE_ROUTE`, else
  `feature` from `swarmforge/routes.conf`, else every role) and sets the stage
  to its first role. A route is any subset of `swarmforge/roles.conf` in that
  file's order.
- `sf handoff` requires a clean worktree on the task branch and no `needs:*`
  label. It records HEAD and moves the stage to the next role on the task's
  route, or to `done` after the last one. `--to <role>` jumps elsewhere; role
  prompts say when that is allowed.
- `sf route <id> <route>` changes an open task's route; the new route must
  contain the current stage. The change is recorded as `type: route`.
- For a role flagged `audit` in `roles.conf`, the first `sf handoff` or
  `sf review` at a given HEAD exits 3 with `AUDIT_REQUIRED` and a self-review
  checklist, recording nothing; the next attempt at the same HEAD goes
  through. A new commit starts a new audit. State lives in
  `.swarmforge/audit/<id>` and is removed when the record is written.
- `sf next <role>` lists open tasks at that stage with no `needs:*` label, so a
  blocked task is invisible to the role until the operator acts.
- `sf finish` merges `swarm/<id>` into the base branch with `--no-ff`, removes
  the worktree and branch, records `type: finished`, and closes the issue.

## Sharing

git-bug data travels with the repository: `git bug push` and `git bug pull`
move `refs/bugs/*` and `refs/identities/*` to and from a remote, so two
machines or two harnesses can take turns on one task. Pushing the task branch
(`git push origin swarm/<id>`) moves the work itself.

A host that accepts only branch pushes refuses `git bug push`. There, mirror
the data to branches:

```sh
git push origin 'refs/bugs/*:refs/heads/git-bug/bugs/*' 'refs/identities/*:refs/heads/git-bug/identities/*'
```

Another clone restores it with the reverse fetch, then `git bug ls`:

```sh
git fetch origin 'refs/heads/git-bug/bugs/*:refs/bugs/*' 'refs/heads/git-bug/identities/*:refs/identities/*'
```

Mutation manifests live in the git notes ref `refs/notes/mutate`, one note per
saved commit. Notes travel only when pushed explicitly:
`git push origin refs/notes/mutate`. To fetch them, fetch into a separate ref
and merge: `git fetch origin refs/notes/mutate:refs/notes/mutate-origin`, then
`git notes --ref=mutate merge refs/notes/mutate-origin`. A squash or rebase
merge leaves the notes on commits the base branch does not reach, so the next
mutation run is a full run.

## Audit

`sf show <id>` prints the complete history: request, every question and
answer, every handoff with its commit, the approval, and the merge. `git log
swarm/<id>` before finishing, or the merge commit after, shows the code.
