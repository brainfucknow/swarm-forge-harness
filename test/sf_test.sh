#!/usr/bin/env bash
# Behavioral tests for swarmforge/bin/sf in a scratch repo. Tasks live in git
# branches; git-bug is not used.
set -euo pipefail

HERE="$(cd "$(dirname "$0")/.." && pwd)"

failures=0
pass() { echo "ok   $1"; }
fail() { echo "FAIL $1" >&2; failures=$((failures + 1)); }
assert_eq() { if [ "$2" = "$3" ]; then pass "$1"; else fail "$1: expected '$3', got '$2'"; fi; }
assert_match() { if echo "$2" | grep -Eq "$3"; then pass "$1"; else fail "$1: '$2' does not match /$3/"; fi; }
assert_fails() { local name="$1"; shift; if "$@" >/dev/null 2>&1; then fail "$name should fail"; else pass "$name fails"; fi; }

scratch="$(mktemp -d)"
trap 'rc=$?; [ "$rc" -eq 0 ] || diagnose "exit status $rc"; rm -rf "$scratch"' EXIT
export HOME="$scratch/home"; mkdir -p "$HOME"

# On a non-zero exit, dump the task branches so that the CI log shows the cause.
diagnose() {
  echo "=== diagnostics after failure ($1) ===" >&2
  [ -d "$repo/.git" ] || return 0
  cd "$repo"
  for b in $(git for-each-ref --format='%(refname)' refs/heads/swarm/); do
    echo "--- $b" >&2
    git log --first-parent --format='%h %s%n%(trailers)' "$b" >&2 || true
  done
}
git config --global user.name tester
git config --global user.email tester@example.com
git config --global init.defaultBranch main

# A copy of this checkout's swarmforge/ without verify.conf, whose commands
# (this script among them) must not run on every sf finish in the scratch repo.
copy_swarmforge() {
  rm -rf "$1/swarmforge" && cp -R "$HERE/swarmforge" "$1/swarmforge" && rm -f "$1/swarmforge/verify.conf"
}

repo="$scratch/project"
mkdir -p "$repo" && cd "$repo"
git init -q .
copy_swarmforge .
echo "hello" > README.md
git add -A && git commit -q -m "seed"
export PATH="$repo/swarmforge/bin:$PATH"

# init
out="$(sf init)"
assert_match "init prints the identity of record commits" "$out" "identity: tester <tester@example.com>"
assert_match "init reports the catalogue" "$out" "roles: specifier architecture-design-reviewer coder cleaner architect hardener security-reviewer qa"
assert_match "init reports the routes" "$out" "routes: feature bugfix refactor harden spike"
assert_eq "init is idempotent" "$(sf init | head -n 1)" "identity: tester <tester@example.com>"
assert_eq "init writes no git-bug data" "$(git for-each-ref refs/bugs refs/identities)" ""

# new
id="$(sf new "Add login" -m "Users can log in with email and password.")"
assert_match "new prints a slug and hex id" "$id" '^add-login-[0-9a-f]{7}$'
assert_eq "new puts the task at the first stage" "$(sf stage "$id")" "specifier"
full="specifier,coder,cleaner,architect,hardener,security-reviewer,qa"
fallback="specifier,coder,cleaner,architect,hardener,qa"
assert_eq "new defaults to the feature route" "$(sf route "$id")" "$full"
assert_eq "new creates the task branch" "$(git branch --list "swarm/$id" | tr -d ' *')" "swarm/$id"
assert_match "show prints the body" "$(sf show "$id")" "email and password"
assert_match "new records the branch point" "$(sf show "$id")" "type: created"
assert_match "created names its own commit" "$(sf show "$id")" "commit: $(git rev-parse --short "swarm/$id")"
assert_eq "the created commit sits on main" "$(git rev-parse "swarm/$id^")" "$(git rev-parse main)"
assert_eq "the created commit changes no file" "$(git diff-tree --no-commit-id -r "swarm/$id")" ""
assert_match "the created commit holds the request" "$(git log -1 --format=%B "swarm/$id")" "^Users can log in with email and password\.$"

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
printf '#!/bin/sh\nprintf "$1" | cat - "$2" > "$2.new" && mv "$2.new" "$2"\n' > "$scratch/type-above-template"
chmod +x "$scratch/type-above-template"
GIT_EDITOR="'$scratch/type-above-template' 'Edited\n\nTested: x\n'" git -C ".worktrees/$id" commit -q --allow-empty
assert_eq "editor commit keeps the trailer block last" "$(git -C ".worktrees/$id" log -1 --format='%(trailers:key=Tested,valueonly)' | head -1)" "x"
assert_eq "editor commit gets the byline" "$(git -C ".worktrees/$id" log -1 --format=%B | grep -c '^By specifier\.$')" "1"
assert_fails "an empty editor message still aborts the commit" env GIT_EDITOR="'$scratch/type-above-template' ''" git -C ".worktrees/$id" commit -q --allow-empty
git commit -q --allow-empty -m "On main checkout"
assert_eq "hook leaves commits outside task worktrees alone" "$(git log -1 --format=%B)" "On main checkout"
SWARMFORGE_ROLE=coder git commit -q --allow-empty -m "Forced role outside"
assert_eq "SWARMFORGE_ROLE does not reach outside task worktrees" "$(git log -1 --format=%B)" "Forced role outside"
git -C ".worktrees/$id" commit -q --allow-empty -m "$(printf 'Prose\n\nSwarmForge-Stage: done\n\nmore prose')"
assert_eq "hook allows a SwarmForge line outside the trailer block" "$(git -C ".worktrees/$id" log -1 --format=%s)" "Prose"
assert_fails "hook refuses a SwarmForge trailer" git -C ".worktrees/$id" commit -q --allow-empty -m "$(printf 'Forged\n\nSwarmForge-Stage: done')"
mv "$repo/swarmforge/bin/sf" "$scratch/sf.moved"
assert_fails "task commit is refused when the role cannot be found" git -C ".worktrees/$id" commit -q --allow-empty -m "No role"
mv "$scratch/sf.moved" "$repo/swarmforge/bin/sf"
cp "$hook" "$scratch/our-hook" && printf '#!/bin/sh\nexit 0\n' > "$hook"
assert_match "start leaves a foreign hook alone" "$(sf start "$id")" "^HOOK: existing commit-msg hook left alone"
assert_eq "foreign hook is unchanged" "$(cat "$hook")" "$(printf '#!/bin/sh\nexit 0')"
cp "$scratch/our-hook" "$hook"
git -C ".worktrees/$id" reset -q --hard "$sha"

# review and approval gate
out="$(sf review "$id" -m "spec ready")"
assert_match "review asks for approval" "$out" "waits for operator approval"
assert_match "review lists the files since the branch point" "$(sf show "$id")" "^ *files: login.feature$"
assert_match "review waits for approval" "$(sf ls --needs)" "^$id	specifier	approval	"
assert_fails "handoff while waiting for approval" sf handoff "$id"
assert_eq "next skips tasks waiting on the operator" "$(sf next specifier)" "NO_TASK"
assert_match "reject keeps the stage" "$(sf reject "$id" "split the scenarios")" "stays at stage:specifier"
assert_match "reject records the reason" "$(sf show "$id")" "reason: split the scenarios"
assert_eq "reject clears needs" "$(sf ls --needs)" ""
sf review "$id" >/dev/null
out="$(sf approve "$id")"
assert_match "approve hands off to the next stage" "$out" "^HANDOFF: $id specifier -> coder at $sha$"
assert_eq "approve moves the stage" "$(sf stage "$id")" "coder"
assert_match "handoff record names the role's commit" "$(sf show "$id")" "commit: $sha"
assert_match "handoff with no new commits lists no files" "$(sf show "$id")" "^ *files: -$"
echo "Feature: logout" > ".worktrees/$id/logout.feature"
echo "Feature: login v2" > ".worktrees/$id/login.feature"
git -C ".worktrees/$id" add -A && git -C ".worktrees/$id" commit -q -m "Add logout"
SWARMFORGE_ROLE=coder sf handoff "$id" --to coder >/dev/null
assert_match "handoff lists files since the previous record" "$(sf show "$id")" "^ *files: login.feature,logout.feature$"
sf ask "$id" "$(printf 'which hash?\ncommit: deadbee looked fine')" >/dev/null
assert_match "free text keeps its lines, indented" "$(sf show "$id")" "^  commit: deadbee looked fine$"
sf answer "$id" "argon2id" >/dev/null
echo "Feature: reset" > ".worktrees/$id/reset.feature"
git -C ".worktrees/$id" add -A && git -C ".worktrees/$id" commit -q -m "Add reset"
SWARMFORGE_ROLE=coder sf handoff "$id" --to coder >/dev/null
assert_match "files ignore commit mentions outside records" "$(sf show "$id")" "^ *files: reset.feature$"
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
assert_eq "the handoff to done changes no file" "$(git diff-tree --no-commit-id -r "swarm/$id")" ""

# finish
assert_fails "finish an unknown task" sf finish "zzzzzzz"
idn="$(sf new "Not done" -m x --route coder)"
assert_fails "finish from a non-done task" sf finish "$idn"
out="$(sf finish "$id")"
assert_match "finish merges into the current branch" "$out" "merged into main"
assert_match "finish without verification commands says so" "$out" "^VERIFY: none configured$"
assert_match "main contains the task work" "$(git log --oneline main)" "Specify login"
assert_eq "finish removes the worktree" "$(test -d ".worktrees/$id" && echo present || echo gone)" "gone"
assert_eq "finish deletes the branch" "$(git branch --list "swarm/$id")" ""
assert_eq "finish leaves only unfinished tasks" "$(sf ls | cut -f1)" "$idn"
assert_match "a finished task is still shown" "$(sf show "$id")" "^STATUS: finished$"
assert_match "a finished task shows its request" "$(sf show "$id")" "^Users can log in with email and password\.$"
assert_eq "no path in HEAD names the finished task" "$(git ls-tree -r --name-only HEAD | grep -c "$id" || true)" "0"
assert_fails "a finished task cannot be started" sf start "$id"

# routes
assert_eq "routes lists the presets" "$(sf routes | awk '{print $1}' | paste -sd ' ' -)" "feature bugfix refactor harden spike"
id2="$(sf new "Fix rounding" -m "x" --route bugfix)"
assert_eq "named route is recorded" "$(sf route "$id2")" "specifier,coder,cleaner,qa"
sf start "$id2" >/dev/null
git -C ".worktrees/$id2" commit -q --allow-empty -m "Spec. By specifier."
sf review "$id2" >/dev/null && sf approve "$id2" >/dev/null
assert_fails "route change must contain the current stage" sf route "$id2" refactor
assert_match "handoff follows the route" "$(sf handoff "$id2")" "coder -> cleaner"
assert_match "route change at the current stage" "$(sf route "$id2" "coder,cleaner,qa")" "^ROUTE: $id2 coder,cleaner,qa$"
assert_match "route change is recorded" "$(sf show "$id2")" "route: coder,cleaner,qa"
assert_match "handoff follows the changed route" "$(sf handoff "$id2")" "cleaner -> qa"
assert_fails "route in the wrong order" sf new "Bad" -m x --route "qa,coder"
assert_fails "route with an unknown role" sf new "Bad" -m x --route "janitor"
id3="$(sf new "Tidy" -m "x" --route "cleaner,architect")"
assert_eq "explicit route starts at its first role" "$(sf stage "$id3")" "cleaner"
assert_eq "SWARMFORGE_ROUTE sets the default" "$(SWARMFORGE_ROUTE=spike sf new "Idea" -m x | xargs sf route)" "specifier"

# optional roles are selected through existing routes and lifecycle
review_route="specifier,architecture-design-reviewer,coder,architect,qa"
idr="$(sf new "Migrate interface" -m x --route "$review_route")"
assert_eq "explicit opt-in route is recorded" "$(sf route "$idr")" "$review_route"
sf start "$idr" >/dev/null
git -C ".worktrees/$idr" commit -q --allow-empty -m "Approve migration criteria"
sf review "$idr" >/dev/null
assert_match "approval sends an opted-in task to design review" "$(sf approve "$idr")" "specifier -> architecture-design-reviewer"
assert_fails "design review cannot be placed after coder" sf new Bad -m x --route "coder,architecture-design-reviewer,qa"
sf ask "$idr" "Which compatibility window?" >/dev/null
assert_fails "reviewer cannot hand off a blocker" sf handoff "$idr"
sf answer "$idr" "Keep both interfaces for one release" >/dev/null
assert_match "reviewer uses ordinary handoff without approval" "$(sf handoff "$idr")" "architecture-design-reviewer -> coder"
assert_match "coder still hands off to existing architect" "$(sf handoff "$idr")" "coder -> architect"
assert_match "existing architect still hands off to QA" "$(sf handoff "$idr")" "architect -> qa"
assert_eq "environment route can opt in" "$(SWARMFORGE_ROUTE=architecture-design-reviewer,coder sf new "Review migration" -m x | xargs sf route)" "architecture-design-reviewer,coder"
cp swarmforge/routes.conf "$scratch/routes.conf"
rm swarmforge/routes.conf
assert_eq "implicit catalogue fallback skips optional reviewer" "$(sf new "Legacy fallback" -m x | xargs sf route)" "$fallback"
printf 'quick coder\n' > swarmforge/routes.conf
assert_eq "fallback without feature also skips optional reviewer" "$(sf new "Missing feature fallback" -m x | xargs sf route)" "$fallback"
mv "$scratch/routes.conf" swarmforge/routes.conf

# Optional review uses the existing route and handoff machinery.
review_id="$(sf new "Review execution boundary" -m x --route coder,hardener,security-reviewer,qa)"
assert_eq "optional security review is explicit" "$(sf route "$review_id")" "coder,hardener,security-reviewer,qa"
sf start "$review_id" >/dev/null
sf handoff "$review_id" >/dev/null
assert_eq "hardener still precedes optional security review" "$(sf stage "$review_id")" "hardener"
sf handoff "$review_id" >/dev/null
assert_eq "security review follows hardener" "$(sf stage "$review_id")" "security-reviewer"
sf ask "$review_id" "Confirmed defect needs a scoped correction" >/dev/null
assert_fails "blocking security findings prevent handoff" sf handoff "$review_id"
sf answer "$review_id" "Correction authorized" >/dev/null
sf handoff "$review_id" >/dev/null
assert_eq "QA follows optional security review" "$(sf stage "$review_id")" "qa"
assert_fails "security review cannot precede hardener" sf new "Wrong review order" --route security-reviewer,hardener,qa

# audit gate
sed -i 's/^coder     strong   subagent$/coder strong subagent audit/' swarmforge/roles.conf
assert_match "check accepts the audit flag" "$(sf check)" "^CHECK: ok"
id5="$(sf new "Audited" -m "x" --route "coder,qa")"
sf start "$id5" >/dev/null
git -C ".worktrees/$id5" commit -q --allow-empty -m "Coder work"
head5="$(git -C ".worktrees/$id5" rev-parse --short HEAD)"
set +e; out="$(sf handoff "$id5")"; rc=$?; set -e
assert_eq "first handoff of an audited role exits 3" "$rc" "3"
assert_match "first handoff prints the checklist" "$out" "^AUDIT_REQUIRED: $id5 at $head5; nothing was recorded"
assert_eq "refused handoff leaves the stage" "$(sf stage "$id5")" "coder"
assert_match "second handoff at the same commit is recorded" "$(sf handoff "$id5")" "^HANDOFF: $id5 coder -> qa at $head5$"
assert_eq "audit state is cleared after the record" "$(test -e ".swarmforge/audit/$id5" && echo kept || echo cleared)" "cleared"
assert_match "unaudited role hands off at once" "$(sf handoff "$id5")" "^HANDOFF: $id5 qa -> done"
id6="$(sf new "Audited twice" -m "x" --route "coder")"
sf start "$id6" >/dev/null
git -C ".worktrees/$id6" commit -q --allow-empty -m "First"
sf handoff "$id6" >/dev/null || true
git -C ".worktrees/$id6" commit -q --allow-empty -m "Second"
set +e; sf handoff "$id6" >/dev/null; rc=$?; set -e
assert_eq "a new commit restarts the audit" "$rc" "3"
sed -i 's/^specifier frontier subagent$/specifier frontier subagent audit/' swarmforge/roles.conf
id7="$(sf new "Audited spec" -m "x" --route "specifier,coder")"
sf start "$id7" >/dev/null
git -C ".worktrees/$id7" commit -q --allow-empty -m "Spec. By specifier."
set +e; sf review "$id7" >/dev/null; rc=$?; set -e
assert_eq "first review of an audited specifier exits 3" "$rc" "3"
assert_match "second review is recorded" "$(sf review "$id7")" "waits for operator approval"
assert_match "approval does not re-gate the audited review" "$(sf approve "$id7")" "^HANDOFF: $id7 specifier -> coder"
sed -i 's/^specifier frontier subagent audit$/specifier frontier subagent/' swarmforge/roles.conf
sed -i 's/^coder strong subagent audit$/coder strong subagent bogus/' swarmforge/roles.conf
assert_fails "check rejects an unknown role flag" sf check
cp "$HERE/swarmforge/roles.conf" swarmforge/roles.conf

# without routes.conf the route is the whole catalogue
rm swarmforge/routes.conf
printf 'coder strong subagent\ncleaner fast subagent\n' > swarmforge/roles.conf
assert_eq "roles reads roles.conf" "$(sf roles | paste -sd ' ' -)" "coder cleaner"
id4="$(sf new "Tidy" -m "x")"
assert_eq "new uses the first catalogue role" "$(sf stage "$id4")" "coder"
assert_eq "default route is the catalogue" "$(sf route "$id4")" "coder,cleaner"

# A computed fallback is a literal role list, never a named-route lookup.
printf 'coder strong subagent\nsecurity-reviewer strong subagent optional\n' > swarmforge/roles.conf
printf 'coder coder,security-reviewer\n' > swarmforge/routes.conf
assert_match "check permits a named route sharing the fallback name" "$(sf check)" "^CHECK: ok"
fallback_id="$(sf new "Literal fallback" -m x)"
assert_eq "fallback collision cannot opt in security review" "$(sf route "$fallback_id")" "coder"
explicit_id="$(sf new "Explicit named review" -m x --route coder)"
assert_eq "explicit colliding named route remains available" "$(sf route "$explicit_id")" "coder,security-reviewer"

rm swarmforge/routes.conf

# adapters
printf 'specifier frontier session\ncoder frontier subagent\ncleaner fast subagent\n' > swarmforge/roles.conf
out="$(sf adapters)"
assert_eq "adapters only for subagent roles" "$(ls .claude/agents | paste -sd ' ' -)" "cleaner.md coder.md"
assert_eq "adapters for copilot" "$(ls .github/agents | paste -sd ' ' -)" "cleaner.agent.md coder.agent.md"
assert_eq "adapters for codex" "$(ls .codex/agents | paste -sd ' ' -)" "cleaner.toml coder.toml"
assert_eq "adapters for kilo" "$(ls .kilo/agent | paste -sd ' ' -)" "cleaner.md coder.md"
assert_match "claude adapter uses the tier model" "$(cat .claude/agents/cleaner.md)" "^model: sonnet$"
assert_match "frontier adapter inherits the session model" "$(cat .claude/agents/coder.md)" "^model: inherit$"
assert_eq "copilot adapter uses the tier model" \
  "$(sed -n 's/^model: //p' .github/agents/cleaner.agent.md)" "$(awk '$1 == "fast" {print $4}' swarmforge/tiers.conf)"
assert_match "codex adapter uses the tier effort" "$(cat .codex/agents/cleaner.toml)" '^model_reasoning_effort = "low"$'
assert_match "kilo adapter is a subagent" "$(cat .kilo/agent/cleaner.md)" "^mode: subagent$"
assert_eq "kilo adapter inherits the session model" \
  "$(sed -n 's/^model: //p' .kilo/agent/cleaner.md)" ""

# committed adapters are current
work="$(mktemp -d "$scratch/adapters.XXXX")"
cp -R "$HERE/.claude" "$HERE/.github" "$HERE/.codex" "$work/" \
  && mkdir -p "$work/.kilo" && cp -R "$HERE/.kilo/agent" "$HERE/.kilo/command" "$work/.kilo/" \
  && copy_swarmforge "$work"
(cd "$work" && git init -q . && git add -A && git commit -q -m seed && ./swarmforge/bin/sf adapters >/dev/null && git diff --quiet) \
  && pass "committed adapters match sf adapters" \
  || fail "committed adapters are stale; run swarmforge/bin/sf adapters"

# configuration check
copy_swarmforge .
assert_match "check passes on the shipped configuration" "$(sf check)" "^CHECK: ok \(8 roles, 5 routes\)$"
check_fails() {
  local name="$1" expected="$2" out
  out="$(sf check 2>&1 || true)"
  assert_match "$name" "$out" "$expected"
  copy_swarmforge .
}
echo "qa strong subagent" >> swarmforge/roles.conf
check_fails "check rejects a duplicate role" "CHECK: roles.conf: role 'qa' listed twice"
echo "janitor strong" >> swarmforge/roles.conf
check_fails "check rejects a short role line" "CHECK: roles.conf: 'janitor' needs <role>"
echo "Big_Role strong subagent" >> swarmforge/roles.conf
check_fails "check rejects a bad role name" "CHECK: roles.conf: role name 'Big_Role'"
echo "done strong subagent" >> swarmforge/roles.conf
check_fails "check rejects a reserved role name" "CHECK: roles.conf: 'done' is reserved"
echo "scribe mythic subagent" >> swarmforge/roles.conf
check_fails "check rejects an unknown tier" "CHECK: roles.conf: 'scribe' uses unknown tier 'mythic'"
echo "scribe fast daemon" >> swarmforge/roles.conf
check_fails "check rejects an unknown runs-as" "CHECK: roles.conf: 'scribe' runs-as must be"
echo "scribe fast subagent" >> swarmforge/roles.conf
check_fails "check requires a role prompt" "CHECK: roles/scribe.md: missing role prompt"
echo "slow opus" >> swarmforge/tiers.conf
check_fails "check rejects a short tier line" "CHECK: tiers.conf: 'slow' needs exactly"
echo "fast haiku low claude-haiku-5.5" >> swarmforge/tiers.conf
check_fails "check rejects a duplicate tier" "CHECK: tiers.conf: tier 'fast' listed twice"
echo "sf-cs local swarmforge/bin/sf-cs - - - -" >> swarmforge/tools.conf
check_fails "check rejects a duplicate tool" "CHECK: tools.conf: tool 'sf-cs' listed twice"
echo "backwards qa,coder" >> swarmforge/routes.conf
check_fails "check rejects an out-of-order route" "CHECK: routes.conf: backwards: route 'qa,coder' is out of order"
echo "feature specifier" >> swarmforge/routes.conf
check_fails "check rejects a duplicate route" "CHECK: routes.conf: route 'feature' listed twice"
echo "weird magic github.com/x/y - task - -" >> swarmforge/tools.conf
check_fails "check rejects an unknown tool kind" "CHECK: tools.conf: 'weird' has unknown kind 'magic'"
echo "qa strong subagent" >> swarmforge/roles.conf
assert_fails "init refuses a broken configuration" sf init
assert_fails "adapters refuse a broken configuration" sf adapters
copy_swarmforge .
sf adapters >/dev/null

# Config commands read the checkout they run in; bookkeeping reads main's.
idc="$(sf new "Config change" -m x --route coder)"
sf start "$idc" >/dev/null
wt="$repo/.worktrees/$idc"
echo "quick coder" >> "$wt/swarmforge/routes.conf"
sed -i '/^security-reviewer /d' "$wt/swarmforge/roles.conf"
assert_match "routes reads the worktree's routes.conf" "$(cd "$wt" && sf routes)" "^quick +coder$"
assert_eq "routes in main reads main's routes.conf" "$(sf routes | grep -c '^quick' || true)" "0"
assert_eq "roles reads the worktree's roles.conf" "$(cd "$wt" && sf roles | grep -c '^security-reviewer$' || true)" "0"
assert_match "check names the worktree's swarmforge" "$(cd "$wt" && sf check 2>&1 || true)" "problem\(s\) in .*/\.worktrees/$idc/swarmforge$"
assert_match "check in main reads main's config" "$(sf check)" "^CHECK: ok"
git -C "$wt" checkout -q -- swarmforge/roles.conf
assert_fails "new from a worktree resolves routes from main" sh -c "cd '$wt' && sf new Y -m x --route quick"
assert_eq "route from a worktree is unchanged" "$(cd "$wt" && sf route "$idc")" "coder"
cp -R .claude .codex .github "$wt/"
sed -i 's/^cleaner   fast /cleaner   strong /' "$wt/swarmforge/roles.conf"
sed -i '3s/.*/Cleans up after the coder./' "$wt/swarmforge/roles/cleaner.md"
(cd "$wt" && sf adapters >/dev/null) && pass "adapters in a worktree take over main's adapters" || fail "adapters in a worktree"
assert_match "adapters writes the worktree's agents" "$(cat "$wt/.claude/agents/cleaner.md")" "^model: opus$"
assert_match "adapters leaves main's agents" "$(cat .claude/agents/cleaner.md)" "^model: sonnet$"
sf adapters >/dev/null && pass "adapters in main still succeeds" || fail "adapters in main after a worktree run"
assert_match "main's agents follow main's config" "$(cat .claude/agents/cleaner.md)" "^model: sonnet$"

# Tasks live in their record commits. Finished state is the finished merge in
# HEAD's history, any parent included; only local swarm/* branches are read.
store="$scratch/store"
mkdir -p "$store/remote.git" "$store/project" && cd "$store/project"
git init -q --bare ../remote.git
git init -q . && copy_swarmforge . && git add -A && git commit -q -m seed
git remote add origin ../remote.git && git push -q origin main
sf init >/dev/null
done_task() { # <title>: a started task handed off to done with one commit
  local tid; tid="$(sf new "$1" -m "Request of $1." --route coder)"
  sf start "$tid" >/dev/null
  git -C ".worktrees/$tid" commit -q --allow-empty -m "Work on $1"
  sf handoff "$tid" >/dev/null
  echo "$tid"
}

idp="$(done_task "Via PR")"
git push -q origin "swarm/$idp"
git checkout -q -b pr
assert_match "finish into a PR branch" "$(sf finish "$idp")" "^FINISHED: $idp merged into pr$"
git checkout -q main && git merge -q --no-ff --no-edit pr && git fetch -q origin
assert_match "a finish merged by the host shows as finished" "$(sf show "$idp")" "^STATUS: finished$"
assert_eq "ls skips a task finished through a PR" "$(sf ls)" ""
assert_eq "next skips a task finished through a PR" "$(sf next coder)" "NO_TASK"
assert_match "start refuses a task finished through a PR" "$(sf start "$idp" 2>&1 || true)" "is finished"
assert_eq "the remote branch is left in place" "$(git rev-parse --verify --quiet "origin/swarm/$idp" >/dev/null && echo kept)" "kept"
assert_eq "no task path is in HEAD" "$(git ls-tree -r --name-only HEAD | grep -c "$idp" || true)" "0"

ids="$(done_task "Squash after")"
git checkout -q -b pr2 && sf finish "$ids" >/dev/null
git checkout -q main && git merge -q --squash pr2 >/dev/null && git commit -q --allow-empty -m "Squash pr2" && git branch -q -D pr2
assert_eq "ls skips a task squash-merged after finish" "$(sf ls)" ""
assert_match "show finds no task squash-merged after finish" "$(sf show "$ids" 2>&1 || true)" "no task '$ids'"
assert_match "start finds no task squash-merged after finish" "$(sf start "$ids" 2>&1 || true)" "no task '$ids'"

idb="$(done_task "Squash before")"
git merge -q --squash "swarm/$idb" >/dev/null && git commit -q --allow-empty -m "Squash $idb"
assert_match "a task squash-merged before finish is listed at done" "$(sf ls)" "^$idb	done		coder	Squash before$"
assert_match "finish merges a squash-merged task" "$(sf finish "$idb")" "^FINISHED: $idb merged into main$"
assert_match "the squash-merged task is finished" "$(sf show "$idb")" "^STATUS: finished$"

idh="$(done_task "Host merge")"
git merge -q --no-ff --no-edit "swarm/$idh" && git commit -q --allow-empty -m "After the host merge"
assert_match "finish completes a host merge" "$(sf finish "$idh")" "^FINISHED: $idh merged into main$"
assert_eq "finish removes the host-merged branch" "$(git branch --list "swarm/$idh")" ""

idl="legacy-task-1234567"
git checkout -q -b "swarm/$idl"
mkdir -p tasks && printf -- '---\nid: %s\ncreated: 2026-10-27\n---\n\n# Legacy task\n\nOld request.\n' "$idl" > "tasks/$idl.md"
git add "tasks/$idl.md"
git commit -q --no-verify -m "$(printf 'sf created: %s\n\nLegacy task\n\nSwarmForge-Task: %s\nSwarmForge-Record: created\nSwarmForge-From: operator\nSwarmForge-Stage: coder\nSwarmForge-Route: coder\nSwarmForge-Needs: none' "$idl" "$idl")"
git checkout -q main
assert_match "ls reads the title of a legacy task" "$(sf ls)" "^$idl	coder		coder	Legacy task$"
assert_match "show reads the request of a legacy task" "$(sf show "$idl")" "^Old request\.$"
sf start "$idl" >/dev/null && sf handoff "$idl" >/dev/null
assert_eq "the handoff to done deletes the legacy task file" "$(git diff-tree --no-commit-id --name-status -r "swarm/$idl")" "D	tasks/$idl.md"
sf finish "$idl" >/dev/null
assert_eq "a finished legacy task leaves no file" "$(git ls-tree -r --name-only HEAD | grep -c "$idl" || true)" "0"

idt="$(sf new "$(printf 'Two\nlines')" -m x --route coder)"
assert_match "a title with a line break is listed as one line" "$(sf ls)" "^$idt	coder		coder	Two lines$"

assert_fails "move-tasks is gone" sf move-tasks tasks
printf 'tasks-dir somewhere\n' > swarmforge/project.conf
assert_match "check ignores a leftover project.conf" "$(sf check)" "^CHECK: ok"
assert_match "ls ignores a leftover project.conf" "$(sf ls)" "^$idt	"
rm swarmforge/project.conf
mkdir -p tasks/done && echo archive > tasks/done/old-archive-7654321.md
git add tasks && git commit -q -m "Old archive"
assert_match "an old archive makes no task" "$(sf show old-archive-7654321 2>&1 || true)" "no task 'old-archive-7654321'"
cd "$repo"

for pair in ".claude/skills/swarm/SKILL.md .agents/skills/swarm/SKILL.md"; do
  set -- $pair
  cmp -s "$HERE/$1" "$HERE/$2" && pass "$1 matches $2" || fail "$1 differs from $2"
done

if [ "$failures" -gt 0 ]; then echo "$failures failure(s)" >&2; exit 1; fi
echo "all tests passed"
