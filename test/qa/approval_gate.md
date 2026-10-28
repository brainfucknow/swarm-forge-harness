# QA: specification approval gate

Contract: [sf specification approval](../features/task_store/approval_gate.feature).
Use the real `git` and `swarmforge/bin/sf` command-line interfaces only.
Create fresh scratch projects under the task's ignored `tmp/`, copy its
`swarmforge/`, initialize Git and `sf`, and commit with an explicit test
identity. Keep scripts and outputs under `tmp/`; do not commit them.

## Refused transitions

1. Create/start a `specifier,coder,qa` task and commit its specification.
   Record its branch tip. Direct `sf handoff`, `sf handoff --to coder` and
   `sf handoff --to done` must each exit 1 and leave that tip unchanged.
   Repeat with `SWARMFORGE_ROLE=coder`: the override must not bypass the
   gate. Diagnostics should explain the missing approval, not an unrelated
   configuration or worktree failure.
2. Run `sf review` and confirm a direct handoff still fails. Run `sf reject`
   and confirm handoff fails again. `sf stage` must remain `specifier`.
3. On a reviewed specification, run `sf ask` and `sf answer`. Confirm that
   clearing the clarification does not allow direct handoff. An answer is
   not an approval. Starting a task and approving without any review must
   also fail without changing its branch.
4. In another task, review commit C1, then commit C2. `sf approve` must exit
   1 without recording approval or handoff for the unreviewed C2. A fresh
   `sf review` followed by `sf approve` must succeed for C2. A dirty worktree
   must continue to be rejected rather than approved implicitly.

## Successful transitions and compatibility

5. Review then approve clean, unchanged specification commits on routes
   `specifier,coder,qa`, `specifier,qa` and `specifier`. Expect respectively
   `coder`, `qa` and `done`. Check `sf show` records operator approval before
   handoff and names the reviewed commit; the specifier-only task archives
   normally. Repeat after rejection and resubmission of a new commit.
6. Create tasks on `coder,qa` and `qa`. Their normal handoffs must still
   succeed without review or approval. Existing non-specifier destinations
   and waiting-on-operator checks must retain their behavior.
7. In a separate scratch project, enable the existing `audit` flag for the
   specifier only. The initial `sf review` must retain its exit-3 audit
   challenge and leave the branch unchanged; complete that self-review and
   retry at the same commit. Approval must then advance without a second
   audit challenge. Repeating direct handoff attempts must never substitute
   for operator approval. Do not change any default audit policy.

Run the relevant existing unit, behavioral, property and generated acceptance
suites. Test transition behavior, not constitution, role or adapter wording.
Record actual results and unavailable checks separately in QA Lore trailers.
