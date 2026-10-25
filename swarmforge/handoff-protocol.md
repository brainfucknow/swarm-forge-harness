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
- `sf next <role>` lists open tasks at that stage with no `needs:*` label, so a
  blocked task is invisible to the role until the operator acts.
- `sf finish` merges `swarm/<id>` into the base branch with `--no-ff`, removes
  the worktree and branch, records `type: finished`, and closes the issue.

## Sharing

git-bug data travels with the repository: `git bug push` and `git bug pull`
move `refs/bugs/*` and `refs/identities/*` to and from a remote, so two
machines or two harnesses can take turns on one task. Pushing the task branch
(`git push origin swarm/<id>`) moves the work itself.

## Audit

`sf show <id>` prints the complete history: request, every question and
answer, every handoff with its commit, the approval, and the merge. `git log
swarm/<id>` before finishing, or the merge commit after, shows the code.
