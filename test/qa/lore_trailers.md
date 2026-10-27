# QA procedure: Lore trailers (task df7e1a7)

End to end through the command line only: real `git`, real `git-bug`, and
`swarmforge/bin/sf` as an operator and roles run them. Do not call `sf`
internals. Turn sections 1-5 into a throwaway script under `./tmp/` and do
not commit it. Section 6 is a reading check and is not scripted.

## Setup

1. `export PATH=$HOME/.local/bin:$PATH` (git-bug).
2. Make a scratch directory `S` under `./tmp/`. In it: `git init -b main`,
   copy the task worktree's `swarmforge/` in, commit it, run
   `swarmforge/bin/sf init`. Expect exit 0 and a `roles:` line.
3. Commit `Base\n\nTested: base` in `S` with `git commit --allow-empty`.
   Expect the message unchanged: the main checkout gets no byline and no
   Lore-id.
4. `sf new "Lore QA" -m "Lore QA" --route specifier,coder,qa`; note the id
   `ID`. `sf start ID`; note `DIR:`.

## 1. Byline placement

In `DIR`, without `SWARMFORGE_ROLE`, commit each message with
`git commit --allow-empty -m "$(printf '<message>')"` and read it back with
`git log -1 --format=%B`:

| Message | Expected message |
|---|---|
| `Fix` | `Fix`, blank, `By specifier.` |
| `Fix\n\nBody` | `Fix`, blank, `Body`, blank, `By specifier.` |
| `Fix\n\nCo-Authored-By: A <a@b>\nSession: s1` | `Fix`, blank, `By specifier.`, blank, `Co-Authored-By: A <a@b>`, `Session: s1` |
| `Fix\n\nBy specifier.\n\nSession: s1` | unchanged |

For the third row also check `git log -1 --format='%(trailers:key=Session,valueonly)'`
prints `s1`: git still reads the trailers.

## 2. Lore-id

1. Commit `Fix\n\nTested: x\nNot-tested: y` in `DIR`. Expect `Fix`, blank,
   `By specifier.`, blank, `Lore-id: <8 lowercase hex>`, `Tested: x`,
   `Not-tested: y`. Check `git log -1 --format='%(trailers:key=Lore-id,valueonly)'`
   matches `^[0-9a-f]{8}$`.
2. Commit the same message again. Expect a different Lore-id.
3. Commit `Fix\n\nTested: x\nLore-id: 0badc0de`. Expect the trailer block
   unchanged (one Lore-id, `0badc0de`, still last).
4. Commit `Fix\n\nTested: x\n\nClosing`. Expect no Lore-id; the byline is
   the last paragraph.
5. Commit `Fix\n\nTested-by: A <a@b>`. Expect no Lore-id.
6. With `SWARMFORGE_ROLE=done`, commit `Fix\n\nTested: x`. Expect it
   unchanged.

## 3. Roles take turns

1. `SWARMFORGE_ROLE=coder git commit --allow-empty -m "$(printf 'Code\n\nConstraint: keep the\n byline\nRejected: byline trailer | breaks old commits\nDirective: keep By lines')"`.
2. `SWARMFORGE_ROLE=qa git commit --allow-empty -m "$(printf 'Verify\n\nTested: placement\nNot-tested: Windows\nConfidence: high')"`.
3. `git commit --allow-empty --no-verify -m "$(printf 'Raw\n\nTested: raw')"`.

## 4. sf start and sf show

1. Run `sf start ID` from `S`. Expect exit 0 and, after the comment history,
   one `LORE: <short sha> <role> <Key>: <value>` line per Lore trailer of
   every commit made in sections 1-3, oldest commit first, trailers in message
   order, `Lore-id` included. For example the coder commit gives
   `LORE: <sha> coder Constraint: keep the byline` (folded value on one
   line), `LORE: <sha> coder Rejected: byline trailer | breaks old commits`,
   and the raw commit gives `LORE: <sha> - Tested: raw`.
2. Expect no `LORE:` line for `Co-Authored-By`, `Session`, `Tested-by`, the
   `Tested: x` of the `Closing` commit, or the main checkout's
   `Tested: base`.
3. Check each `<short sha>` against `git rev-parse --short` of that commit.
4. Run `sf show ID`. Expect the same `LORE:` lines at the end.
5. Make a second task with `sf new` and `sf start`, no commits. Expect no
   `LORE:` line from `sf start` or `sf show` for it.

## 5. After finish

Move the first task to done (`sf route ID specifier`, `sf review ID`,
`sf approve ID`), run `sf finish ID`, then `sf show ID`. Expect exit 0 and no
`LORE:` line. On `main`, `git log --format='%(trailers:key=Tested)'` still
shows the trailers.

## 6. Reading check (manual, not scripted)

Read the changed role prompts and constitution articles once and confirm by
judgement, not by string search:

- QA writes its checks as throwaway scripts under `./tmp/`, commits only real
  fixes, and ends with a commit carrying `Tested:` and `Not-tested:` trailers
  (empty when there were no fixes).
- Coder, cleaner, architect and hardener record departures from the spec and
  trade-offs as `Constraint:`, `Rejected:` and `Directive:` trailers in the
  last paragraph, not after a byline.
- The handoffs article tells roles to read the `LORE:` lines from `sf start`.
- The workflow article describes where the hook puts the byline and the
  Lore-id.

Record the outcome as trailers on the QA handoff commit.
