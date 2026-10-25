#!/usr/bin/env bash
# Behavioral tests for swarmforge/bin/sf against a real git-bug in a scratch repo.
set -euo pipefail

HERE="$(cd "$(dirname "$0")/.." && pwd)"
command -v git-bug >/dev/null || { echo "git-bug not found on PATH" >&2; exit 1; }

failures=0
pass() { echo "ok   $1"; }
fail() { echo "FAIL $1" >&2; failures=$((failures + 1)); }
assert_eq() { if [ "$2" = "$3" ]; then pass "$1"; else fail "$1: expected '$3', got '$2'"; fi; }
assert_match() { if echo "$2" | grep -Eq "$3"; then pass "$1"; else fail "$1: '$2' does not match /$3/"; fi; }
assert_fails() { if "$@" >/dev/null 2>&1; then fail "$1 should fail"; else pass "$1 fails"; fi; }

scratch="$(mktemp -d)"
trap 'rm -rf "$scratch"' EXIT
export HOME="$scratch/home"; mkdir -p "$HOME"
git config --global user.name tester
git config --global user.email tester@example.com
git config --global init.defaultBranch main

repo="$scratch/project"
mkdir -p "$repo" && cd "$repo"
git init -q .
cp -R "$HERE/swarmforge" swarmforge
echo "hello" > README.md
git add -A && git commit -q -m "seed"
export PATH="$repo/swarmforge/bin:$PATH"

# init
out="$(sf init)"
assert_match "init creates identity" "$out" "identity: operator \(created\)"
assert_match "init reports pipeline" "$out" "pipeline: specifier coder cleaner architect hardener qa"
assert_eq "init is idempotent" "$(sf init | head -n 1)" "identity: operator"

# new
id="$(sf new "Add login" -m "Users can log in with email and password.")"
assert_match "new prints a short id" "$id" '^[0-9a-f]{7}$'
assert_eq "new puts the task at the first stage" "$(sf stage "$id")" "specifier"
assert_eq "new creates the task branch" "$(git branch --list "swarm/$id" | tr -d ' *')" "swarm/$id"
assert_match "show prints the body" "$(sf show "$id")" "email and password"

# next
assert_eq "next finds the task for its stage" "$(sf next specifier)" "$id"
assert_eq "next prints NO_TASK for other stages" "$(sf next coder)" "NO_TASK"
assert_fails "next with unknown role" sf next janitor
assert_match "ls lists the task" "$(sf ls)" "^$id	specifier		Add login$"

# start
out="$(sf start "$id")"
assert_match "start prints the task id" "$out" "^TASK: $id$"
assert_match "start prints the worktree" "$out" "^DIR: $repo/.worktrees/$id$"
assert_eq "start checks out the task branch" "$(git -C ".worktrees/$id" rev-parse --abbrev-ref HEAD)" "swarm/$id"
assert_match "start is idempotent" "$(sf start "$id")" "^DIR: "

# handoff
echo "Feature: login" > ".worktrees/$id/login.feature"
assert_fails "handoff with uncommitted work" sf handoff "$id"
git -C ".worktrees/$id" add -A && git -C ".worktrees/$id" commit -q -m "Specify login"
sha="$(git -C ".worktrees/$id" rev-parse --short HEAD)"

# review and approval gate
out="$(sf review "$id" -m "spec ready")"
assert_match "review asks for approval" "$out" "waits for operator approval"
assert_match "review labels needs:approval" "$(sf ls --needs)" "^$id	specifier	approval"
assert_fails "handoff while waiting for approval" sf handoff "$id"
assert_eq "next skips tasks waiting on the operator" "$(sf next specifier)" "NO_TASK"
assert_match "reject keeps the stage" "$(sf reject "$id" "split the scenarios")" "stays at stage:specifier"
assert_match "reject records the reason" "$(sf show "$id")" "reason: split the scenarios"
assert_eq "reject clears needs" "$(sf ls --needs)" ""
sf review "$id" >/dev/null
out="$(sf approve "$id")"
assert_match "approve hands off to the next stage" "$out" "^HANDOFF: $id specifier -> coder at $sha$"
assert_eq "approve moves the stage" "$(sf stage "$id")" "coder"
assert_match "handoff comment records the commit" "$(sf show "$id")" "commit: $sha"
assert_fails "approve when not waiting" sf approve "$id"

# questions
out="$(sf ask "$id" "Which hash algorithm?")"
assert_match "ask stops for the operator" "$out" "waits for the operator"
assert_match "ask records the asking role" "$(sf show "$id")" "from: coder"
assert_fails "handoff while a question is open" sf handoff "$id"
assert_eq "next skips a blocked task" "$(sf next coder)" "NO_TASK"
assert_match "answer resumes the stage" "$(sf answer "$id" "bcrypt")" "resumes at stage:coder"
assert_eq "next finds the resumed task" "$(sf next coder)" "$id"

# explicit role and explicit target
out="$(SWARMFORGE_ROLE=coder sf handoff "$id" --to qa -m "skip ahead")"
assert_match "handoff honors --to" "$out" "coder -> qa"
assert_match "handoff honors -m" "$(sf show "$id")" "summary: skip ahead"
out="$(sf handoff "$id")"
assert_match "last stage hands off to done" "$out" "qa -> done"
assert_eq "done is the terminal stage" "$(sf stage "$id")" "done"

# finish
assert_fails "finish from a non-done task" sf finish "zzzzzzz"
out="$(sf finish "$id")"
assert_match "finish merges into the current branch" "$out" "merged into main"
assert_match "main contains the task work" "$(git log --oneline main)" "Specify login"
assert_eq "finish removes the worktree" "$(test -d ".worktrees/$id" && echo present || echo gone)" "gone"
assert_eq "finish deletes the branch" "$(git branch --list "swarm/$id")" ""
assert_eq "finish closes the task" "$(sf ls)" ""
assert_eq "closed task is not next" "$(sf next done 2>/dev/null || echo NO_TASK)" "NO_TASK"

# pipeline follows roles.conf
cp swarmforge/packs/two-pack.conf swarmforge/roles.conf
assert_eq "pipeline reads roles.conf" "$(sf pipeline | tr '\n' ' ')" "coder cleaner "
id2="$(sf new "Tidy" -m "x")"
assert_eq "new uses the first configured role" "$(sf stage "$id2")" "coder"

# adapters
out="$(sf adapters)"
assert_eq "adapters only for subagent roles" "$(ls .claude/agents)" "cleaner.md"
assert_eq "adapters for copilot" "$(ls .github/agents)" "cleaner.agent.md"
assert_eq "adapters for codex" "$(ls .codex/agents)" "cleaner.toml"
assert_match "claude adapter uses the tier model" "$(cat .claude/agents/cleaner.md)" "^model: sonnet$"
assert_match "copilot adapter uses the tier model" "$(cat .github/agents/cleaner.agent.md)" "^model: claude-sonnet-5.5$"
assert_match "codex adapter uses the tier effort" "$(cat .codex/agents/cleaner.toml)" '^model_reasoning_effort = "low"$'

# committed adapters are current
work="$(mktemp -d "$scratch/adapters.XXXX")"
cp -R "$HERE/swarmforge" "$HERE/.claude" "$HERE/.github" "$HERE/.codex" "$work/"
(cd "$work" && git init -q . && git add -A && git commit -q -m seed && ./swarmforge/bin/sf adapters >/dev/null && git diff --quiet) \
  && pass "committed adapters match sf adapters" \
  || fail "committed adapters are stale; run swarmforge/bin/sf adapters"

for pair in ".claude/skills/swarm/SKILL.md .agents/skills/swarm/SKILL.md"; do
  set -- $pair
  cmp -s "$HERE/$1" "$HERE/$2" && pass "$1 matches $2" || fail "$1 differs from $2"
done

if [ "$failures" -gt 0 ]; then echo "$failures failure(s)" >&2; exit 1; fi
echo "all tests passed"
