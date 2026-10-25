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
trap 'rc=$?; [ "$rc" -eq 0 ] || diagnose "exit status $rc"; rm -rf "$scratch"' EXIT
export HOME="$scratch/home"; mkdir -p "$HOME"

# When the script exits non-zero, dump the git-bug state so a CI log explains itself.
diagnose() {
  echo "=== diagnostics after failure ($1) ===" >&2
  [ -d "$repo/.git" ] || return 0
  cd "$repo"
  echo "--- refs" >&2; git for-each-ref refs/bugs >&2 || true
  echo "--- git-bug files" >&2; ls -laR .git/git-bug 2>&1 | head -40 >&2 || true
  echo "--- clocks" >&2; for c in .git/git-bug/clocks/*; do printf '%s: ' "$c" >&2; od -An -tu8 "$c" >&2 || true; done
  echo "--- bugs" >&2
  for b in $(git-bug bug --format id 2>/dev/null); do
    git-bug bug show "$b" >&2 || true
    echo "--- ref log for $b" >&2
    git log --format='%h %ci %s' "refs/bugs/$b" >&2 || true
  done
}
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
assert_match "init reports the catalogue" "$out" "roles: specifier coder cleaner architect hardener qa"
assert_match "init reports the routes" "$out" "routes: feature bugfix refactor harden spike"
assert_eq "init is idempotent" "$(sf init | head -n 1)" "identity: operator"

# new
id="$(sf new "Add login" -m "Users can log in with email and password.")"
assert_match "new prints a short id" "$id" '^[0-9a-f]{7}$'
assert_eq "new puts the task at the first stage" "$(sf stage "$id")" "specifier"
full="specifier,coder,cleaner,architect,hardener,qa"
assert_eq "new defaults to the feature route" "$(sf route "$id")" "$full"
assert_eq "new creates the task branch" "$(git branch --list "swarm/$id" | tr -d ' *')" "swarm/$id"
assert_match "show prints the body" "$(sf show "$id")" "email and password"
assert_match "new records the branch point" "$(sf show "$id")" "type: created"
assert_match "created names the base commit" "$(sf show "$id")" "commit: $(git rev-parse --short main)"

# next
assert_eq "next finds the task for its stage" "$(sf next specifier)" "$id"
assert_eq "next prints NO_TASK for other stages" "$(sf next coder)" "NO_TASK"
assert_fails "next with unknown role" sf next janitor
assert_match "ls lists the task with its route" "$(sf ls)" "^$id	specifier		$full	Add login$"

# start
out="$(sf start "$id")"
assert_match "start prints the task id" "$out" "^TASK: $id$"
assert_match "start prints the worktree" "$out" "^DIR: $repo/.worktrees/$id$"
assert_match "start prints the route" "$out" "^ROUTE: $full$"
assert_eq "start checks out the task branch" "$(git -C ".worktrees/$id" rev-parse --abbrev-ref HEAD)" "swarm/$id"
assert_match "start is idempotent" "$(sf start "$id")" "^DIR: "

# commit byline hook
hook="$repo/.git/hooks/commit-msg"
assert_eq "init installs the commit-msg hook" "$(test -x "$hook" && echo installed)" "installed"
echo "Feature: login" > ".worktrees/$id/login.feature"
assert_fails "handoff with uncommitted work" sf handoff "$id"
git -C ".worktrees/$id" add -A && git -C ".worktrees/$id" commit -q -m "Specify login"
sha="$(git -C ".worktrees/$id" rev-parse --short HEAD)"
assert_eq "hook appends the stage byline" "$(git -C ".worktrees/$id" log -1 --format=%B | grep -v "^$" | tail -1)" "By specifier."
git -C ".worktrees/$id" commit -q --allow-empty -m "$(printf 'Already signed\n\nBy specifier.')"
assert_eq "hook does not duplicate a byline" "$(git -C ".worktrees/$id" log -1 --format=%B | grep -c '^By specifier\.$')" "1"
SWARMFORGE_ROLE=coder git -C ".worktrees/$id" commit -q --allow-empty -m "Forced role"
assert_eq "SWARMFORGE_ROLE overrides the stage" "$(git -C ".worktrees/$id" log -1 --format=%B | grep -v "^$" | tail -1)" "By coder."
git commit -q --allow-empty -m "On main checkout"
assert_eq "hook leaves commits outside task worktrees alone" "$(git log -1 --format=%B)" "On main checkout"
SWARMFORGE_ROLE=coder git commit -q --allow-empty -m "Forced role outside"
assert_eq "SWARMFORGE_ROLE does not reach outside task worktrees" "$(git log -1 --format=%B)" "Forced role outside"
assert_fails "task commit is refused when the role cannot be found" env SWARMFORGE_GIT_BUG=/nonexistent/git-bug git -C ".worktrees/$id" commit -q --allow-empty -m "No role"
cp "$hook" "$scratch/our-hook" && printf '#!/bin/sh\nexit 0\n' > "$hook"
assert_match "start leaves a foreign hook alone" "$(sf start "$id")" "^HOOK: existing commit-msg hook left alone"
assert_eq "foreign hook is unchanged" "$(cat "$hook")" "$(printf '#!/bin/sh\nexit 0')"
cp "$scratch/our-hook" "$hook"
git -C ".worktrees/$id" reset -q --hard "$sha"

# review and approval gate
out="$(sf review "$id" -m "spec ready")"
assert_match "review asks for approval" "$out" "waits for operator approval"
assert_match "review lists the files since the branch point" "$(sf show "$id")" "^ *files: login.feature$"
assert_match "review labels needs:approval" "$(sf ls --needs)" "^$id	specifier	approval	"
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
assert_match "handoff with no new commits lists no files" "$(sf show "$id")" "^ *files: -$"
echo "Feature: logout" > ".worktrees/$id/logout.feature"
echo "Feature: login v2" > ".worktrees/$id/login.feature"
git -C ".worktrees/$id" add -A && git -C ".worktrees/$id" commit -q -m "Add logout"
SWARMFORGE_ROLE=coder sf handoff "$id" --to coder >/dev/null
assert_match "handoff lists files since the previous record" "$(sf show "$id")" "^ *files: login.feature,logout.feature$"
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

# a label write that git-bug drops is repeated until it is visible
shim="$scratch/dropping-git-bug"; dropped="$scratch/dropped"
cat > "$shim" <<EOF
#!/usr/bin/env bash
if [ "\$1" = bug ] && [ "\$2" = label ] && [ "\$3" = new ] && [ ! -f "$dropped" ]; then touch "$dropped"; echo "label dropped"; exit 0; fi
exec "$(command -v git-bug)" "\$@"
EOF
chmod +x "$shim"
idx="$(SWARMFORGE_GIT_BUG="$shim" sf new "Dropped write" -m x)"
assert_eq "the shim dropped one write" "$(test -f "$dropped" && echo dropped)" "dropped"
assert_eq "a dropped route write is repeated" "$(sf route "$idx")" "$full"
assert_eq "the stage after it is still set" "$(sf stage "$idx")" "specifier"

# routes
assert_eq "routes lists the presets" "$(sf routes | awk '{print $1}' | paste -sd ' ' -)" "feature bugfix refactor harden spike"
id2="$(sf new "Fix rounding" -m "x" --route bugfix)"
assert_eq "named route sets the label" "$(sf route "$id2")" "specifier,coder,qa"
sf start "$id2" >/dev/null
git -C ".worktrees/$id2" commit -q --allow-empty -m "Spec. By specifier."
sf review "$id2" >/dev/null && sf approve "$id2" >/dev/null
assert_match "handoff follows the route" "$(sf handoff "$id2")" "coder -> qa"
assert_fails "route change must contain the current stage" sf route "$id2" refactor
assert_match "route change at the current stage" "$(sf route "$id2" "coder,cleaner,qa")" "^ROUTE: $id2 coder,cleaner,qa$"
assert_match "route change is recorded" "$(sf show "$id2")" "route: coder,cleaner,qa"
assert_match "handoff follows the changed route" "$(sf handoff "$id2")" "qa -> done"
assert_fails "route in the wrong order" sf new "Bad" -m x --route "qa,coder"
assert_fails "route with an unknown role" sf new "Bad" -m x --route "janitor"
id3="$(sf new "Tidy" -m "x" --route "cleaner,architect")"
assert_eq "explicit route starts at its first role" "$(sf stage "$id3")" "cleaner"
assert_eq "SWARMFORGE_ROUTE sets the default" "$(SWARMFORGE_ROUTE=spike sf new "Idea" -m x | xargs sf route)" "specifier"

# without routes.conf the route is the whole catalogue
rm swarmforge/routes.conf
printf 'coder strong subagent\ncleaner fast subagent\n' > swarmforge/roles.conf
assert_eq "roles reads roles.conf" "$(sf roles | paste -sd ' ' -)" "coder cleaner"
id4="$(sf new "Tidy" -m "x")"
assert_eq "new uses the first catalogue role" "$(sf stage "$id4")" "coder"
assert_eq "default route is the catalogue" "$(sf route "$id4")" "coder,cleaner"

# adapters
printf 'specifier frontier session\ncoder frontier subagent\ncleaner fast subagent\n' > swarmforge/roles.conf
out="$(sf adapters)"
assert_eq "adapters only for subagent roles" "$(ls .claude/agents | paste -sd ' ' -)" "cleaner.md coder.md"
assert_eq "adapters for copilot" "$(ls .github/agents | paste -sd ' ' -)" "cleaner.agent.md coder.agent.md"
assert_eq "adapters for codex" "$(ls .codex/agents | paste -sd ' ' -)" "cleaner.toml coder.toml"
assert_match "claude adapter uses the tier model" "$(cat .claude/agents/cleaner.md)" "^model: sonnet$"
assert_match "frontier adapter inherits the session model" "$(cat .claude/agents/coder.md)" "^model: inherit$"
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
