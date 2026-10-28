# Design note: tasks in plain git, without git-bug

Spike 01d767f. This note is the design. The build adopted its Gherkin and
QA procedure: see Build plan below for where they are now and what the
operator changed when the build started.

## Build plan

Settled with the operator in task e6b686e. These override the rest of this
note where they differ.

- **No migration.** The template has no users yet. There is no
  `sf migrate`, and git-bug leaves the tests and CI entirely. Decision 8,
  the Migration section, and steps 3 and 4 of the rollout no longer apply.
- **Three tasks.**
  1. e6b686e, the local store: sf off git-bug, ids, the task file, record
     commits, the state from local `swarm/*` branches, start and show,
     the archive and finish, the hook's refusal, init, and the prompt,
     install and CI changes. Gherkin in `test/features/task_store/`, QA
     procedure in `test/qa/task_store.md`. A task is a local branch only.
  2. The rollout on this repository: re-create by hand in the new store the
     tasks still in git-bug (ec13ecd, and 5bbff27 if it is not finished
     by then), and remove the `git-bug/*` mirror branches on GitHub and
     `refs/bugs`, `refs/identities` locally.
  3. Sharing: the push to `SWARMFORGE_REMOTE`, the check for unseen remote
     records, remote-tracking task branches, the one considered remote, and
     hex uniqueness against remote branches; `sf --help` gains
     `SWARMFORGE_REMOTE` then. Its draft Gherkin is in
     `features/` here.

## Decisions

Each decision was settled with the operator, in the task's `answer` records.

| # | Question | Decision |
|---|---|---|
| 1 | Task id | A slug from the title plus `-` and 7 hex digits, e.g. `add-login-3f9a2c1`. |
| 1b | Slug shape | Lowercase ASCII. Each run of other characters becomes `-`. Cut to at most 30 characters at a `-` boundary. A command takes the full id, a unique prefix of it, or the 7-hex part. A migrated task keeps its git-bug hex. |
| 2 | State format | `tasks/<id>.md` is added by the first commit on `swarm/<id>`. Every other action is a commit that changes no file. Its trailers hold a full snapshot of the state. |
| 3 | The task file at the end | The handoff to `done` moves the file to `tasks/done/<id>.md` and adds the final state and the rendered records. |
| 4 | Backlog | None. `sf new` makes the branch at once. |
| 5 | `sf start` and `sf show` | They keep the current layout. They read records and `LORE:` lines from first-parent history only. `sf show` also works after finish. |
| 6 | Sharing | The pushes are documented, and sf also pushes the task branch after each record when `SWARMFORGE_REMOTE` is set. |
| 7 | Hook | The commit-msg hook refuses a task-worktree commit that has a `SwarmForge-*` trailer. |
| 8 | Migration | `sf migrate` converts the open git-bug tasks. It is run on this repository when the build lands. |
| 9, 9b | Mutation manifests | An orphan cache branch, `swarmforge/metrics`. This note fixes its shape, and ec13ecd's specifier writes its Gherkin. |
| 10 | Order | Finish 5bbff27 under git-bug, build this design, then re-specify ec13ecd. |
| Review | Two clones on one task | No new command. With `SWARMFORGE_REMOTE` set, sf refuses to write a record while the remote branch has records the local one lacks (pull first). A refused push is fixed with `git pull --rebase`; records change no files, so it never conflicts, and the last writer wins. |

## Model

A task is its branch `swarm/<id>`. Nothing else holds task state. There are
no refs outside `refs/heads/`, and there is no tool other than git.

```text
main ──●───────────────────────────────────────●  merge (record: finished)
        \                                     /
swarm/<id>  ●──────●──○──○──●────○──●────────●
           created  work  question work  handoff to done
           (adds tasks/<id>.md)          (moves it to tasks/done/<id>.md)
  ● record commit (sf)   ○ role commit
```

### Task id

`sf new "<title>"` makes `<slug>-<hex>`:

1. Take the title and decompose its letters (Unicode NFD). Drop the
   combining marks, so `Ä` becomes `A`, and lowercase the result.
2. Each run of characters other than `a`–`z` and `0`–`9` becomes one `-`.
   Remove the `-` at both ends. An empty result is `task`.
3. When the slug is longer than 30 characters, keep its longest prefix that
   ends just before a `-` and has at most 30 characters. When the first word
   alone is longer than 30 characters, cut it at 30.
4. Add `-` and 7 random lowercase hex digits. The hex part must differ from
   the hex part of every task that sf can see: local `swarm/*` branches,
   the considered remote's `swarm/*` branches (see Reading the state), and
   `tasks/done/*.md` in the main checkout's HEAD.

A command resolves an id argument in this order:

1. It is a full id.
2. It is the 7-hex part of exactly one task.
3. It is a prefix of exactly one full id.

When more than one id matches, the command fails and names them all. When
none matches, it fails with `no task '<arg>'`.

### Task file

`sf new` writes `tasks/<id>.md`. It is the only file that the first commit on
the branch adds:

```markdown
---
id: add-login-3f9a2c1
created: 2026-10-27
---

# Add login

Users log in with email and password.
```

The title is the `#` heading. This keeps YAML quoting out of titles such as
`Spike: …`. The request follows, as the operator gave it (`-m` or `-F`). No
command edits the file while the task is open. Thus a record never changes a
file that a role has checked out.

### Record commits

Every sf action is one commit on the task branch, with this message:

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

The last four trailers are a full snapshot. Thus the newest record alone
gives the state of the task.

| Command | Record | From | Stage | Needs | Free text | Files |
|---|---|---|---|---|---|---|
| `sf new` | `created` | operator | first role of the route | none | title | adds `tasks/<id>.md` |
| `sf ask` | `question` | acting role | unchanged | operator | question | none |
| `sf answer` | `answer` | operator | unchanged | none | answer | none |
| `sf review` | `review` | acting role | unchanged | approval | summary | none |
| `sf approve` | `approval`, then `handoff` | operator, then acting role | next role | none | none, then summary | as handoff |
| `sf reject` | `changes-requested` | operator | unchanged | none | reason | none |
| `sf route` | `route` | operator | unchanged | unchanged | none | none |
| `sf handoff` | `handoff` | acting role | next role or `done` | none | summary | none; to `done`, it moves the task file |
| `sf finish` | `finished` (the merge commit on the target) | operator | done | none | none | the merge |
| `sf migrate` | `created`, plus `SwarmForge-Base: <sha>` | operator | from the git-bug labels | from the labels | title | adds `tasks/<id>.md` |

The `finished` record also has `SwarmForge-Merged-Into: <target branch>`.

- **Free text has no one-line rule.** Several arguments are joined with
  spaces, one argument keeps its newlines, and white space is trimmed at both
  ends. The text is never in the trailer block, so it cannot fake a state.
- **The commit of a record** is the first parent of a `review` or `handoff`
  record commit (the role's HEAD). For `created`, it is `SwarmForge-Base`
  when present, else the created commit itself. `files` is computed when the
  record is shown: the paths changed from the commit of the previous
  `created`, `review` or `handoff` record to this record's commit. At most 40
  are listed, or `-` when there are none, as now.
- **Author.** A record commit has the identity from git's `user.name` and
  `user.email`. When git has no identity, sf uses `SWARMFORGE_USER` (default
  `operator`) and `SWARMFORGE_EMAIL` (default `<user>@swarmforge.local`).
  `sf init` prints the identity that it will use. sf no longer needs git-bug.

### How records are written

A record that changes no file is made with `git commit-tree <tip>^{tree} -p
<tip>` and `git update-ref refs/heads/swarm/<id> <new> <tip>`:

- **A worktree is never touched.** A worktree that has the branch checked
  out moves to the new commit, and its tree is the same as before. The spike
  checked that a role's staged and unstaged changes survive.
- **A concurrent write is detected.** `update-ref` compares the old value
  before it writes. When another writer moved the tip, the write fails and
  nothing is recorded. sf reads the state again and retries once. Then it
  fails with the git error.
- **No hook runs.** `commit-tree` does not run hooks, so the hook in
  decision 7 never sees sf's own trailers.

The handoff to `done` changes files. The worktree is clean at that point
(handoff requires it), so sf builds the commit from a temporary index and
then updates the worktree to it. The worktree is clean afterwards. The
`finished` record is the `--no-ff` merge commit that `sf finish` makes in the
main checkout.

### Reading the state

- **Records of a task.** These are the commits in `git log --first-parent
  swarm/<id>` that have `SwarmForge-Task: <id>`, starting at the created
  record. A merge of main into the task branch brings in other tasks'
  records, and first-parent order leaves them out. Role commits cannot carry
  the trailer (decision 7).
- **State.** The snapshot trailers of the newest record. `sf stage`,
  `sf next`, `sf ls`, the commit-msg hook (through `sf stage`), `sf run`
  and `sf dispatch` all read it.
- **`sf ls`.** One `git for-each-ref` over `refs/heads/swarm/` and
  `refs/remotes/<remote>/swarm/`. Then, for each task, one `git log -1
  --first-parent --grep='^SwarmForge-Task: <id>$'`, and the title from the
  task file in one `git cat-file --batch`. The cost grows with the number of
  open tasks, not with history. The output is unchanged: `id`, `stage`,
  `needs`, `route` and `title`, tab-separated, sorted by id.
- **One remote.** sf considers a single remote: `SWARMFORGE_REMOTE` when
  it is set, else `origin`. The remote-tracking branches of other remotes
  are ignored, also when they have `swarm/<id>`.
- **Local or remote.** A local `swarm/<id>` wins over the considered
  remote's `<remote>/swarm/<id>`. A remote-tracking `swarm/<id>` without a
  local branch is a task too, unless it is finished. `sf start` makes the
  local branch from it.
- **Finished tasks.** A task is finished when `tasks/done/<id>.md` is in
  the main checkout's HEAD, whatever local or remote-tracking branches still
  have `swarm/<id>`. The archived task file is the signal because it reaches
  the base branch by every kind of merge: `sf finish`, a host merge commit,
  or a host squash merge, where the task's tip never becomes an ancestor of
  HEAD. `sf ls` and `sf next` skip a finished task. `sf start`, and every
  command that writes a record, refuse it with `task <id> is finished`.
- **Showing a finished task.** `sf show` looks for the task's `finished`
  record in `git log --first-parent HEAD`.
  - When the record is there (`sf finish` made the merge), sf reads the
    records and `LORE:` lines from the merge's second parent back to the
    created record, then the `finished` record.
  - When it is not there (a host merge or a squash merge), sf prints the
    records from the `## Records` section of `tasks/done/<id>.md`, which the
    archive already carries, without their fences. There is no `finished`
    block. `LORE:` lines are printed only while the task branch (local, or
    the considered remote's) still exists, read from its first-parent
    history as for an open task. Once the branch is gone, the squashed
    commit on the base branch has lost the Lore trailers.

### sf start and sf show

`sf start` prints the same header as now: `TASK`, `TITLE`, `STAGE`, `ROUTE`,
`BRANCH`, `DIR`, then `---`. `sf show` prints `TASK`, `TITLE`, `STATUS`
(`open` or `finished`), `STAGE` and `ROUTE`, then `---`. After `---`, both
print these sections, with one blank line between them:

1. The request. This is the task file below the title heading.
2. The records, oldest first, each as a block of `key: value` lines with the
   keys of `handoff-protocol.md` (`type`, `from`, `to`, `branch`, `commit`,
   `files`, `question`, `answer`, `summary`, `reason`, `route`,
   `merged-into`). In a value of several lines, each later line is indented
   by two spaces.
3. The `LORE:` lines, as now. They come from the commits after the created
   record's commit on the first-parent line, up to the tip (for a finished
   task, see Showing a finished task).

There are two changes from today:

- `LORE:` lines from main that a merge of main brings in are no longer
  printed. `sf show ec13ecd` prints them today.
- `sf show` prints the `LORE:` lines of a finished task.
  `test/features/lore_trailers/lore_lines.feature` scenario 4 changes to
  match.

### The task file at done

The handoff to `done` (`sf handoff`, `sf handoff --to done`, or `sf approve`
on a route whose last role is the specifier) commits a single change. It
moves `tasks/<id>.md` to `tasks/done/<id>.md` and adds these:

- `stage: done` and `route: <route>` in the front matter.
- A `## Records` section with each record rendered as in `sf show`, the
  handoff itself included. Each record is in its own ```` ```text ```` fence.

The file is on the branch before any merge. Thus the archive reaches the base
branch both through `sf finish` and through a host pull request, and also
through a squash merge, which loses the record commits. `tasks/` on the base
branch therefore holds no files, and `tasks/done/` holds one file per
finished task. Each task has its own file name, so two tasks never conflict
there.

### sf finish

`sf finish` is unchanged in what it does. It merges `swarm/<id>` into the
target with `--no-ff`, removes the worktree, and deletes the branch. The
merge commit is the `finished` record, so `git log --first-parent main` shows
one commit per task. The record commits are not squashed.

## The commit-msg hook

- **Role lookup.** `sf stage <worktree name>` reads the branch, so the hook
  no longer needs git-bug. Its error text no longer mentions git-bug.
- **The new refusal.** `sf commit-msg` refuses a message whose trailer block
  has a key that starts with `SwarmForge-` (git trailer keys ignore case).
  It applies to every task-worktree commit, at every stage, `done` included.
  It fails with `<key> is a SwarmForge record trailer; only sf writes those`
  and leaves the message file unchanged. A `SwarmForge-…` line outside the
  trailer block is allowed. Commits in the main checkout are not checked,
  as now.
- **What is left.** `git commit --no-verify` can still forge a record. The
  constitution forbids skipping hooks. sf does not check it further.

## Sharing

The documented pushes are part of the local store. `SWARMFORGE_REMOTE`, the
remote-tracking branches and the two-clone check are the sharing task.

- **The pushes.** Everything lives under `refs/heads/`, so a branch-only
  host (this environment's proxy) takes it all:
  `git push origin swarm/<id>` moves a task, with its records;
  `git push origin swarmforge/metrics` moves the cache; and a fetch brings
  them back. `handoff-protocol.md` drops the git-bug section and its mirror
  recipe.
- **`SWARMFORGE_REMOTE=<remote>`.** After each record, sf runs `git push
  <remote> swarm/<id>`. This covers `new`, `ask`, `answer`, `review`,
  `approve`, `reject`, `route`, `handoff` and `migrate`. The push also
  carries the role commits under the record. When the push fails, the record
  stays local. sf warns `push of swarm/<id> to <remote> failed; the record is
  only local` on stderr, and the command keeps its output and exit status 0.
- **Not settled.** What `sf finish` pushes. See Open questions.
- **Two clones on one task.** sf adds no command for this. Someone merges
  with plain git, as follows.
  - **Take turns.** Fetch, run `sf start` (a fast-forward of the local
    branch), then work.
  - **sf refuses to write over unseen records.** With `SWARMFORGE_REMOTE`
    set, sf first fetches `swarm/<id>` from the remote, before it writes a
    record. When the fetched tip has a record commit
    (`SwarmForge-Task: <id>`) that the local branch does not contain, sf
    refuses: `sf: <remote>/swarm/<id> has records that swarm/<id> lacks;
    pull first: git pull --rebase <remote> swarm/<id>`. The exit status is
    1, and sf writes no commit. When the fetch fails, there is no check; the
    record is written, and the push warns as above.
  - **When the push is still refused.** The other clone pushed between the
    check and the push. In the task worktree (or with the branch checked
    out), run `git pull --rebase <remote> swarm/<id>`, then
    `git push <remote> swarm/<id>`.
  - **Why the rebase is safe.** A record changes no file, so rebasing a
    record never conflicts. git keeps commits that start empty, which the
    spike checked with git 2.43. History stays linear, and the newest
    record's snapshot is the state: the last writer wins. Role commits can
    still conflict like any other code, and the role resolves them.

## Migration

Dropped; see Build plan. Kept as the spike's record.

`sf migrate` is the only command that needs git-bug. It is one-shot and safe
to run again. For each open git-bug issue that has a `stage:` label, it does
these steps:

1. The new id is the slug of the title, `-`, and the 7-hex git-bug human id.
2. The branch `swarm/<hex>` becomes `swarm/<new id>`, and `.worktrees/<hex>`
   moves to `.worktrees/<new id>` (`git worktree move`). When the branch is
   gone, it is made at the commit of the issue's `type: created` record.
3. One commit is appended. It adds `tasks/<new id>.md`, with the request (the
   issue's first comment) and an `## Imported records` section that holds
   each later comment verbatim, each in its own `text` fence. Its trailers
   are a `created` record: the snapshot from the labels (`stage:`, `route:`,
   `needs:operator` or `needs:approval`) and
   `SwarmForge-Base: <full sha of the created record's commit>`. The `LORE:`
   lines and the first `files` then count from that commit, as before.
4. It prints `MIGRATED: <hex> -> <new id>`.

A task whose `swarm/<new id>` already has a created record is skipped with
`SKIPPED: <hex> already migrated as <new id>`. Closed issues are not touched.
git-bug data (`refs/bugs/*`, `refs/identities/*`) is left in place.
Removing it is a manual step after the operator checks the result:
`git for-each-ref --format='%(refname)' refs/bugs refs/identities | xargs -n1 git update-ref -d`,
plus the `git-bug/*` mirror branches on the remote.

### Rollout on this repository

The operator asked for this.

1. Finish 5bbff27 under the git-bug sf. Leave ec13ecd open; it goes back to
   the specifier.
2. Merge the build task.
3. Run `sf migrate` in the main checkout, with git-bug on PATH. Expect one
   `MIGRATED:` line per open task at that time: ec13ecd, aca0457, and any
   others that are open.
4. Run `sf ls` and `sf show <id>` for each one. Check stage, route, needs,
   the imported records and the `LORE:` lines.
5. Push the renamed branches. Delete the old `swarm/<hex>` and
   `git-bug/*` branches on origin once checked.
6. Send ec13ecd back to the specifier: `sf route` it, or start a new task
   that names it.

## Mutation manifests: the cache branch (for ec13ecd)

The operator chose a branch detached from main, because the manifests are a
cache. This fixes its shape. ec13ecd's specifier writes the Gherkin.

- **Branch.** `swarmforge/metrics` is an orphan, with no common history with
  main. It is not under `swarm/`, so sf never takes it for a task.
- **Tree.** The tree is the contents of `.metrics/` by path:
  `mutate/<path>.edn` from clj-mutate, and `gherkin/<feature path>` for the
  gherkin-mutator block that ec13ecd's wrapper moves out of the feature.
  `.metrics/` stays git-ignored in every worktree.
- **Load.** In `sf start`, sf copies each file of the tip that the worktree's
  `.metrics/` does not have. Files already there stay as they are.
- **Save.** In `sf handoff`, before the record, sf commits each `.metrics/`
  file that differs from the tip's copy. It uses `commit-tree` and
  `update-ref` (compare-and-swap), so no worktree is touched. The message is
  `sf metrics: <id>`, with `SwarmForge-Task: <id>`. The newest copy of a path
  wins. A file deleted from `.metrics/` stays in the cache.
- **Correctness.** A manifest carries the hashes of its source. Thus a copy
  from unmerged work, or a lost or reset branch, costs a re-run, never a wrong
  result. The branch can be squashed to one commit or deleted at any time.
- **Sharing.** `git push origin swarmforge/metrics`. When
  `SWARMFORGE_REMOTE` is set, sf pushes it after a save. When that push is
  refused because another clone moved the branch, sf warns and keeps its
  local copy. Merging the two copies is ec13ecd's question.
- **What ec13ecd keeps.** The gherkin-mutator wrapper that moves the
  feature's block, `sf manifest tested` and its `Tested:` trailers, and
  `.metrics/` in `install.sh`'s ignore list.
- **What ec13ecd drops.** `refs/notes/mutate`, notes on commits, and the rule
  that a task loads only the notes on commits that HEAD reaches.

## What else changes in the build task

These are prompt and document changes. Per `AGENTS.md`, they are not pinned
by tests, and QA reads them by hand.

- **Prompts and articles.** In `swarmforge/handoff-protocol.md`, the task
  table becomes trailers and the task file, the records become commits, and
  the Sharing and Lore lines sections change. `swarmforge/README.md` drops
  the git-bug prerequisite. In `constitution/articles/project.md`, "task
  state in git-bug (`refs/bugs/*`)" becomes the `swarm/*` branches and
  `tasks/`. `constitution/articles/handoffs.md` and `workflow.md` lose their
  git-bug wording, and `roles/orchestrator.md` loses "it needs git-bug".
  `AGENTS.md` and the adapter bodies say "git-bug tasks"; they change too.
- **sf itself.** `sf --help` drops `SWARMFORGE_GIT_BUG` and adds
  `SWARMFORGE_REMOTE` and `migrate`. `sf.bug` is replaced by a store
  namespace over git. `sf.task` and `sf.dispatch` read through it.
- **Install and CI.** `install.sh` no longer checks for git-bug.
  `.github/workflows/test.yml` drops the git-bug install and
  `test/probe_gitbug.clj`, which is deleted.
- **Tests.** `test/sf_test.sh` is rewritten for the new store. The Lore
  features' "git-bug is on PATH" step meaning goes away, and `lore_lines`
  scenario 4 changes as above.

## Backlog.md and Beads

The operator asked whether a dedicated tool is needed. It is not. This is
what each would add if adopted instead, from their public descriptions. The
spike did not install or try them.

- **Backlog.md.** It keeps one Markdown file per task with YAML front
  matter (id, title, status, labels, dependencies) in a `backlog/` folder.
  It has a CLI, a terminal kanban board and a browser UI, and it runs on
  Node or Bun.
  - It would add a board and browser view, acceptance-criteria checklists
    in the task file, and task dependencies.
  - It costs this: a change of status is an edit of the task file. That
    edit conflicts with the role's checkout of the task branch, or lands on
    the base branch. Its sequential ids collide when two clones create
    tasks. sf would still own route, stage and needs as custom fields. And
    it is a second runtime.
- **Beads (`bd`).** It keeps issues as JSON lines in a `.beads/` file
  committed to git, with a local SQLite cache and a sync daemon. Ids are
  hash-based, issues have dependency links, and `bd ready` lists unblocked
  work for agents.
  - It would add dependencies and blockers between tasks, a ready queue
    across tasks, and memory meant for agents.
  - It costs this: one shared file for all tasks, which needs its own merge
    handling across branches; a binary plus a daemon; and state that is not
    on the task branch. Route, stage and needs would again be duplicated as
    fields.
- **What to borrow later.** Task dependencies. A `Depends-on:` Lore trailer
  already exists. A `SwarmForge-Depends-On` record and an `sf next` that
  skips blocked tasks would give Beads' ready queue without the tool.

## Open questions

None of these blocks the build. They are listed for the operator.

1. **`sf finish` and the remote.** With `SWARMFORGE_REMOTE` set, should
   `sf finish` push the target branch and delete the remote task branch?
   Today it pushes neither. A remote task branch whose tip the main checkout
   contains is hidden from `sf ls`. Pushing main from sf would skip a pull
   request review.
2. **Host pull request merges.** When a host merges `swarm/<id>`, the task
   counts as finished (its file is in `tasks/done/` on the base branch), but
   the local branch and worktree remain. Should `sf finish` accept such a
   task and only delete the branch and worktree?
3. **Abandoning a task.** No command closes a task without a merge (git-bug
   had `status close`). Should `sf drop <id>` keep the branch under another
   name, such as `swarm-dropped/<id>`?
4. **Size of `swarmforge/metrics`.** Should sf squash it, for example in
   `sf finish`, or leave that to the operator?
