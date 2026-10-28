# Design note: tasks in plain git, without git-bug

From the task "Spike: replace git-bug with Markdown task files and commit
trailers". This note keeps only the part of its design that is still
pending. The local store is built: `swarmforge/handoff-protocol.md` and
`test/features/task_store/` describe it. Sharing is not built; it is below,
with its draft Gherkin in `features/` here. The section on mutation
manifests belongs to the task "Keep mutation manifests on an orphan metrics
branch". git history keeps the rest of the spike's design.

## Sharing

Sharing is not built. It covers the push to `SWARMFORGE_REMOTE`, the check
for unseen remote records, remote-tracking task branches, the one
considered remote, and hex uniqueness of new ids against the remote's
`swarm/*` branches. `sf --help` gains `SWARMFORGE_REMOTE` with it.

- **The pushes.** Everything lives under `refs/heads/`, so a branch-only
  host (this environment's proxy) takes it all:
  `git push origin swarm/<id>` moves a task, with its records;
  `git push origin swarmforge/metrics` moves the cache; and a fetch brings
  them back.
- **`SWARMFORGE_REMOTE=<remote>`.** After each record, sf runs `git push
  <remote> swarm/<id>`. This covers `new`, `ask`, `answer`, `review`,
  `approve`, `reject`, `route` and `handoff`. The push also carries the
  role commits under the record. When the push fails, the record stays
  local. sf warns `push of swarm/<id> to <remote> failed; the record is
  only local` on stderr, and the command keeps its output and exit status 0.
- **One remote.** sf considers a single remote: `SWARMFORGE_REMOTE` when
  it is set, else `origin`. The remote-tracking branches of other remotes
  are ignored, also when they have `swarm/<id>`.
- **Local or remote.** A local `swarm/<id>` wins over the considered
  remote's `<remote>/swarm/<id>`. A remote-tracking `swarm/<id>` without a
  local branch is a task too, unless it is finished. `sf start` makes the
  local branch from it.
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

## Open questions

These are not settled. They are listed for the operator.

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
