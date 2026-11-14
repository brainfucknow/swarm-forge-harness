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
# These decide the backend on a record; this script may run inside a harness.
unset CLAUDECODE CODEX_THREAD_ID SWARMFORGE_BACKEND SWARMFORGE_DISPATCHED

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
assert_match "a dispatched role cannot approve" "$(SWARMFORGE_DISPATCHED=specifier sf approve "$id" 2>&1 || true)" "^sf: approve is the operator's; the dispatched specifier cannot run it$"
assert_fails "a dispatched role cannot reject" env SWARMFORGE_DISPATCHED=specifier sf reject "$id" "no"
assert_fails "a dispatched role cannot change the route" env SWARMFORGE_DISPATCHED=specifier sf route "$id" specifier,coder
assert_fails "a dispatched role cannot answer" env SWARMFORGE_DISPATCHED=specifier sf answer "$id" "yes"
assert_fails "a dispatched role cannot finish" env SWARMFORGE_DISPATCHED=specifier sf finish "$id"
branches="$(git for-each-ref --format='%(refname)' refs/heads/swarm/)"
assert_match "a dispatched gate cannot create a task" "$(SWARMFORGE_DISPATCHED=falsifier sf new "Gate task" -m x 2>&1 || true)" "^sf: new is the operator's; the dispatched falsifier cannot run it$"
assert_eq "the refused new makes no task branch" "$(git for-each-ref --format='%(refname)' refs/heads/swarm/)" "$branches"
assert_match "a dispatched role can still read the route" "$(SWARMFORGE_DISPATCHED=specifier sf route "$id")" "^specifier,"
assert_match "the refused commands leave the task waiting for approval" "$(sf ls --needs)" "^$id	specifier	approval	"
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

# approval over gate findings
reviewed_task() {
  local t
  t="$(sf new "$1" -m x --route specifier,coder)"
  sf start "$t" >/dev/null
  git -C ".worktrees/$t" commit -q --allow-empty -m "Spec"
  sf review "$t" >/dev/null
  echo "$t"
}
assert_match "help lists approve --rejected" "$(sf help)" '^  approve <id> \[--rejected "<finding> \| <reason>"\]\.\.\.$'
ida="$(reviewed_task "Approve over findings")"
spec_sha="$(git rev-parse --short "swarm/$ida^")"
out="$(sf approve "$ida" --rejected "DEFECT, LOW: a | b | accepted by the operator at the gate" --rejected "  C | d  ")"
assert_match "approve --rejected hands off as a plain approve" "$out" "^HANDOFF: $ida specifier -> coder at $spec_sha$"
approval="$(git rev-parse --short "swarm/$ida^")"
assert_match "the approval record is the commit under the handoff" "$(git log -1 --format=%s "$approval")" "^sf approval: $ida$"
assert_eq "the approval record holds one Rejected trailer per option, trimmed, in order" \
  "$(git log -1 --format='%(trailers:key=Rejected,valueonly)' "$approval")" \
  "$(printf 'DEFECT, LOW: a | b | accepted by the operator at the gate\nC | d')"
assert_match "the approval record holds one Lore-id" "$(git log -1 --format='%(trailers:key=Lore-id,valueonly)' "$approval")" '^[0-9a-f]{8}$'
assert_match "show prints each Rejected trailer as a LORE line with role -" "$(sf show "$ida")" \
  "^LORE: $approval - Rejected: DEFECT, LOW: a \| b \| accepted by the operator at the gate$"
assert_match "start prints them in the next stage" "$(sf start "$ida")" "^LORE: $approval - Rejected: C \| d$"
idp="$(reviewed_task "Plain approve")"
sf approve "$idp" >/dev/null
assert_eq "a plain approve writes no Lore trailer" "$(git log -1 --format='%(trailers:key=Rejected,key=Lore-id)' "swarm/$idp^")" ""
idb="$(reviewed_task "Bad rejected")"
tip="$(git rev-parse "swarm/$idb")"
refused_approve() {
  local name="$1"; shift
  assert_match "approve refuses $name" "$(sf approve "$idb" "$@" 2>&1 || true)" "^sf: "
}
refused_approve "--rejected with no value" --rejected
refused_approve "a value without ' | '" --rejected "A|b"
refused_approve "a value blank after its last ' | '" --rejected "A | "
refused_approve "a value blank before its last ' | '" --rejected " | b"
refused_approve "a value with a newline" --rejected "$(printf 'A | b\nc')"
refused_approve "a mistyped option" --rejectd "A | b"
refused_approve "an extra argument" --rejected "A | b" extra
assert_eq "the refused approvals write no record" "$(git rev-parse "swarm/$idb")" "$tip"
assert_match "the refused approvals leave the task waiting for approval" "$(sf ls --needs)" "^$idb	specifier	approval	"
assert_match "a dispatched role cannot approve with --rejected" "$(SWARMFORGE_DISPATCHED=specifier sf approve "$idb" --rejected "A | b" 2>&1 || true)" "^sf: approve is the operator's"
git -C ".worktrees/$idb" commit -q --allow-empty -m "After review"
tip="$(git rev-parse "swarm/$idb")"
assert_match "approve --rejected refuses a moved review commit" "$(sf approve "$idb" --rejected "A | b" 2>&1 || true)" "changed since review"
assert_eq "the moved-commit refusal writes no record" "$(git rev-parse "swarm/$idb")" "$tip"
assert_match "approve --rejected refuses a task not waiting for approval" "$(sf approve "$ida" --rejected "A | b" 2>&1 || true)" "not waiting for approval"

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
assert_eq "adapters for vibe" "$(ls .vibe/agents | paste -sd ' ' -)" "cleaner.toml coder.toml"
assert_match "claude adapter uses the tier model" "$(cat .claude/agents/cleaner.md)" "^model: sonnet$"
assert_match "frontier adapter inherits the session model" "$(cat .claude/agents/coder.md)" "^model: inherit$"
assert_eq "copilot adapter uses the tier model" \
  "$(sed -n 's/^model: //p' .github/agents/cleaner.agent.md)" "$(awk '$1 == "fast" {print $4}' swarmforge/tiers.conf)"
assert_match "codex adapter uses the tier effort" "$(cat .codex/agents/cleaner.toml)" '^model_reasoning_effort = "low"$'
assert_match "kilo adapter is a subagent" "$(cat .kilo/agent/cleaner.md)" "^mode: subagent$"
assert_eq "kilo adapter inherits the session model" \
  "$(sed -n 's/^model: //p' .kilo/agent/cleaner.md)" ""
assert_match "vibe adapter is a subagent" "$(cat .vibe/agents/cleaner.toml)" '^agent_type = "subagent"$'
assert_eq "vibe adapter uses the tier model" \
  "$(sed -n 's/^active_model = \"//p' .vibe/agents/cleaner.toml | sed 's/\"$//')" "$(awk '$1 == "fast" {print $6}' swarmforge/tiers.conf)"

# committed adapters are current
work="$(mktemp -d "$scratch/adapters.XXXX")"
cp -R "$HERE/.claude" "$HERE/.github" "$HERE/.codex" "$HERE/.vibe" "$work/" \
  && mkdir -p "$work/.kilo" && cp -R "$HERE/.kilo/agent" "$HERE/.kilo/command" "$work/.kilo/" \
  && copy_swarmforge "$work"
(cd "$work" && git init -q . && git add -A && git commit -q -m seed && ./swarmforge/bin/sf adapters >/dev/null && git diff --quiet) \
  && pass "committed adapters match sf adapters" \
  || fail "committed adapters are stale; run swarmforge/bin/sf adapters"

# configuration check
copy_swarmforge .
assert_match "check passes on the shipped configuration" "$(sf check)" "^CHECK: ok \(8 roles, 5 routes\)$"
assert_eq "the shipped configuration has the falsifier gate" "$(sf gates)" "falsifier"
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
after="$(git rev-parse HEAD)"
assert_match "finish completes a host merge" "$(sf finish "$idh")" "^FINISHED: $idh merged into main$"
assert_eq "finish removes the host-merged branch" "$(git branch --list "swarm/$idh")" ""
assert_eq "finish records on top of the commits after a host merge" "$(git rev-parse HEAD^1)" "$after"
assert_match "a task finished after a host merge shows as finished" "$(sf show "$idh")" "^STATUS: finished$"

idm="$(done_task "Host merge at HEAD")"
git merge -q --no-ff --no-edit "swarm/$idm"
merge="$(git rev-parse HEAD)"
sf finish "$idm" >/dev/null
assert_eq "finish keeps a host merge at HEAD" "$(git rev-parse HEAD^1)" "$merge"
assert_eq "the finished record merges the task tip" "$(git log -1 --format=%B HEAD | head -n 1)" "sf finished: $idm"
assert_match "a task host-merged at HEAD shows as finished" "$(sf show "$idm")" "^STATUS: finished$"

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
assert_match "new writes å as aa, æ as ae and ø as oe" "$(sf new "Blåbærsyltetøy" -m x --route coder)" '^blaabaersyltetoey-[0-9a-f]{7}$'
assert_match "new drops the accents of other letters" "$(sf new "Crème brûlée" -m x --route coder)" '^creme-brulee-[0-9a-f]{7}$'

assert_fails "move-tasks is gone" sf move-tasks tasks
printf 'tasks-dir somewhere\n' > swarmforge/project.conf
assert_match "check ignores a leftover project.conf" "$(sf check)" "^CHECK: ok"
assert_match "ls ignores a leftover project.conf" "$(sf ls)" "^$idt	"
rm swarmforge/project.conf
mkdir -p tasks/done && echo archive > tasks/done/old-archive-7654321.md
git add tasks && git commit -q -m "Old archive"
assert_match "an old archive makes no task" "$(sf show old-archive-7654321 2>&1 || true)" "no task 'old-archive-7654321'"
cd "$repo"

# sf harvest: one JSON line per task, read-only
hv="$scratch/harvest"
mkdir -p "$hv" && cd "$hv"
git init -q . && copy_swarmforge . && git add -A && git commit -q -m seed
sf init >/dev/null
assert_eq "harvest of a repository without tasks prints nothing" "$(sf harvest)" ""
hf="$(sf new "Harvest finished" -m "$(printf 'First line of the request.\n\nSecond paragraph, é.')" --route specifier,coder,cleaner)"
sf start "$hf" >/dev/null
git -C ".worktrees/$hf" commit -q --allow-empty -m "$(printf 'Specify harvest\n\nConstraint: keep it read-only\nTested: a folded\n value\nSupersedes: 0badc0de')"
spec_lore_id="$(git -C ".worktrees/$hf" log -1 --format='%(trailers:key=Lore-id,valueonly)')"
sf ask "$hf" "Which format?" >/dev/null
sf answer "$hf" "JSON lines" >/dev/null
sf review "$hf" -m "spec ready" >/dev/null
sf reject "$hf" "split the criteria" >/dev/null
sf review "$hf" -m "spec revised" >/dev/null
sf approve "$hf" --rejected "DEFECT, LOW: a | b | reason" --rejected "plain finding | accepted" >/dev/null
git -C ".worktrees/$hf" commit -q --allow-empty -m "Code harvest"
git config user.name "Codex Ü"
GIT_AUTHOR_DATE="2026-03-01T10:00:00+02:00" sf handoff "$hf" -m "coded" >/dev/null
git config --unset user.name
sf route "$hf" specifier,coder,cleaner,qa >/dev/null
sf handoff "$hf" >/dev/null
sf handoff "$hf" >/dev/null
sf finish "$hf" >/dev/null
git clone -q --depth 3 "file://$hv" "$scratch/harvest-shallow" 2>/dev/null
ho="$(sf new "Harvest open" -m "Open request." --route coder)"
sf ask "$ho" "Open question?" >/dev/null
out="$(sf harvest 2>"$scratch/harvest.err")"
line_f="$(echo "$out" | sed -n 1p)"
line_o="$(echo "$out" | sed -n 2p)"
assert_eq "harvest gives no shallow warning in the full repository" "$(grep -c '^sf: shallow repository: ' "$scratch/harvest.err" || true)" "0"
record_times() { TZ=UTC git log --first-parent --reverse --all-match --grep='^SwarmForge-Record: ' --grep="^SwarmForge-Task: $1\$" \
  --date=format-local:%Y-%m-%dT%H:%M:%SZ --format='%ad %an' "$2"; }
hf_merge="$(git log --merges --format=%H -1 --grep="^SwarmForge-Task: $hf\$" HEAD)"
assert_eq "harvest gives a finished task's records their commit times and authors" \
  "$(echo "$line_f" | jq -r '.records[] | "\(.time) \(.recorded_by)"')" "$(record_times "$hf" "$hf_merge^2"; record_times "$hf" "$hf_merge" | tail -1)"
assert_eq "harvest gives an open task's records their commit times and authors" \
  "$(echo "$line_o" | jq -r '.records[] | "\(.time) \(.recorded_by)"')" "$(record_times "$ho" "swarm/$ho")"
assert_eq "harvest gives the record time in UTC and the author name unchanged" \
  "$(echo "$line_f" | jq -r '.records[] | select(.text == "coded") | "\(.time) \(.recorded_by)"')" "2026-03-01T08:00:00Z Codex Ü"
assert_eq "harvest ends every record with time and recorded_by" "$(echo "$out" | jq -c '.records[] | keys_unsorted' | sort -u)" \
  '["type","from","stage","needs","route","commit","text","merged_into","rejected","audit_refusals","spec_verdict","time","recorded_by","backend","dispatched"]'
assert_eq "harvest prints the same lines under another time zone" "$(TZ=Asia/Tokyo sf harvest)" "$out"
assert_eq "harvest prints one line per task" "$(echo "$out" | wc -l | tr -d ' ')" "2"
assert_eq "every harvest line is JSON" "$(echo "$out" | jq -e -c 'type' | sort -u)" '"object"'
assert_eq "harvest prints the finished task first" "$(echo "$out" | jq -r '.id' | paste -sd ' ' -)" "$hf $ho"
assert_eq "harvest keys every line" "$(echo "$out" | jq -c 'keys' | sort -u)" '["fixes","found_by","id","lore","needs","records","request","route","stage","status","title","truncated"]'
for t in "$hf" "$ho"; do
  line="$(echo "$out" | jq -c --arg id "$t" 'select(.id == $id)')"
  assert_eq "harvest title equals sf show ($t)" "$(echo "$line" | jq -r '.title')" "$(sf show "$t" | sed -n 's/^TITLE: //p')"
  assert_eq "harvest status equals sf show ($t)" "$(echo "$line" | jq -r '.status')" "$(sf show "$t" | sed -n 's/^STATUS: //p')"
  assert_eq "harvest stage equals sf stage ($t)" "$(echo "$line" | jq -r '.stage')" "$(sf stage "$t")"
  assert_eq "harvest route equals sf route ($t)" "$(echo "$line" | jq -r '.route | join(",")')" "$(sf route "$t")"
  assert_eq "harvest records follow sf show's blocks ($t)" "$(echo "$line" | jq -r '.records[].type' | paste -sd ' ' -)" \
    "$(sf show "$t" | sed -n 's/^type: //p' | paste -sd ' ' -)"
  assert_eq "harvest lore follows sf show's LORE lines but Lore-id ($t)" \
    "$(echo "$line" | jq -r '.lore[] | "LORE: \(.commit) \(.role // "-") \(.key): \(.value)"')" \
    "$(sf show "$t" | grep '^LORE: ' | grep -v '^LORE: [^ ]* [^ ]* Lore-id: ' || true)"
done
assert_eq "harvest keeps the request" "$(echo "$line_f" | jq -r '.request')" "$(printf 'First line of the request.\n\nSecond paragraph, é.')"
assert_eq "harvest statuses" "$(echo "$out" | jq -r '.status' | paste -sd ' ' -)" "finished open"
assert_eq "harvest needs of the waiting open task equals sf ls" "$(echo "$line_o" | jq -r '.needs')" "$(sf ls | awk -F'\t' -v id="$ho" '$1 == id {print $3}')"
assert_eq "harvest needs of the finished task" "$(echo "$line_f" | jq -r '.needs')" "none"
assert_eq "harvest texts" "$(echo "$line_f" | jq -c '[.records[] | select(.text != null) | .text]')" \
  '["Which format?","JSON lines","spec ready","split the criteria","spec revised","specifier work complete","coded","cleaner work complete","qa work complete"]'
assert_eq "harvest gives the route change its new route" "$(echo "$line_f" | jq -c '[.records[] | select(.type == "route") | .route]')" '[["specifier","coder","cleaner","qa"]]'
assert_eq "harvest review records wait for approval" "$(echo "$line_f" | jq -r '[.records[] | select(.type == "review") | .needs] | unique | .[]')" "approval"
assert_eq "harvest gives the finished record its branch" "$(echo "$line_f" | jq -r '.records[-1] | "\(.type) \(.merged_into)"')" "finished main"
assert_eq "harvest commits equal sf show's" "$(echo "$line_f" | jq -r '.records[] | select(.commit != null) | .commit')" "$(sf show "$hf" | sed -n 's/^commit: //p')"
assert_eq "harvest parses the approved-over findings" "$(echo "$line_f" | jq -c '.records[] | select(.type == "approval") | .rejected')" \
  '[{"finding":"DEFECT, LOW: a | b","reason":"reason","kind":"DEFECT","severity":"LOW"},{"finding":"plain finding","reason":"accepted","kind":null,"severity":null}]'
assert_eq "harvest gives unaudited records no refusal count" "$(echo "$out" | jq -c '[.records[].audit_refusals] | unique')" "$(printf '[null]\n[null]')"
assert_eq "harvest gives lore the Lore-id of its commit" "$(echo "$line_f" | jq -r --arg id "$spec_lore_id" '[.lore[] | select(.lore_id == $id) | .key] | join(" ")')" "Constraint Tested Supersedes"
assert_eq "harvest unfolds a folded value" "$(echo "$line_f" | jq -r '.lore[] | select(.key == "Tested") | .value')" "a folded value"
assert_eq "harvest gives the approval's Rejected lore role null" "$(echo "$line_f" | jq -c '[.lore[] | select(.key == "Rejected") | .role] | unique')" "[null]"
assert_eq "harvest lines are not truncated in the full repository" "$(echo "$out" | jq -c '.truncated' | sort -u)" "false"
snapshot() { git for-each-ref; git status --porcelain --ignored; git worktree list; find .git | sort; { find .swarmforge 2>/dev/null || true; } | sort; }
before="$(snapshot)"
sf harvest >/dev/null
assert_eq "harvest changes no ref, worktree or file" "$(snapshot)" "$before"
assert_eq "a dispatched role can harvest" "$(SWARMFORGE_DISPATCHED=coder sf harvest)" "$out"
set +e; extra="$(sf harvest extra 2>"$scratch/harvest.err")"; rc=$?; set -e
assert_eq "harvest refuses an argument" "$rc-$extra" "1-"
assert_match "harvest names the refusal" "$(cat "$scratch/harvest.err")" "^sf: harvest takes no arguments$"
git branch swarm/experiment
set +e; stray="$(sf harvest 2>"$scratch/harvest.err")"; rc=$?; set -e
assert_eq "harvest skips a branch without records" "$rc" "0"
assert_eq "harvest still prints the tasks beside a stray branch" "$stray" "$out"
assert_match "harvest names the stray branch" "$(cat "$scratch/harvest.err")" "swarm/experiment"
git branch -D -q swarm/experiment

# audit refusal count
sed -i 's/^coder     strong   subagent$/coder strong subagent audit/; s/^specifier frontier subagent$/specifier frontier subagent audit/' swarmforge/roles.conf
ia="$(sf new "Audit count" -m x --route coder,cleaner)"
sf start "$ia" >/dev/null
git -C ".worktrees/$ia" commit -q --allow-empty -m "X"
set +e; sf handoff "$ia" --to coder >/dev/null; rc1=$?
git -C ".worktrees/$ia" commit -q --allow-empty -m "Y"
sf handoff "$ia" --to coder >/dev/null; rc2=$?; set -e
assert_eq "each refusal exits 3" "$rc1 $rc2" "3 3"
sf handoff "$ia" --to coder >/dev/null
count() { sf harvest | jq -c --arg id "$1" 'select(.id == $id) | [.records[] | select(.type == "handoff" or .type == "review") | .audit_refusals]'; }
assert_eq "the record counts the refusals since the last record" "$(sf harvest | jq --arg id "$ia" 'select(.id == $id) | .records[-1].audit_refusals == 2')" "true"
assert_match "show prints the count" "$(sf show "$ia")" "^audit-refusals: 2$"
assert_match "start prints the count" "$(sf start "$ia")" "^audit-refusals: 2$"
git -C ".worktrees/$ia" commit -q --allow-empty -m "Z"
set +e; sf handoff "$ia" >/dev/null; set -e
sf handoff "$ia" >/dev/null
sf handoff "$ia" >/dev/null
assert_eq "the count resets at each record; an unaudited role has none" "$(count "$ia")" "[2,1,null]"
is="$(sf new "Audit spec" -m x --route specifier,coder)"
sf start "$is" >/dev/null
git -C ".worktrees/$is" commit -q --allow-empty -m "Spec"
set +e; sf review "$is" >/dev/null; set -e
sf review "$is" >/dev/null
sf approve "$is" >/dev/null
assert_eq "the handoff of an approval has no count; the audited review has one" "$(count "$is")" "[1,null]"
roles() { sf harvest | jq -c --arg id "$1" 'select(.id == $id) | [.records[] | select(.type == "handoff" or .type == "review") | [.from, .audit_refusals]]'; }
# a specifier refused while waiting for approval: one task through sf approve
spec_waits() {
  local t; t="$(sf new "$1" -m x --route specifier,coder)"
  sf start "$t" >/dev/null
  git -C ".worktrees/$t" commit -q --allow-empty -m "Spec"
  set +e; sf review "$t" >/dev/null; set -e
  sf review "$t" >/dev/null
  set +e; sf review "$t" >/dev/null; set -e
  echo "$t"
}
iq1="$(spec_waits "Audit per role 1")"
sf approve "$iq1" >/dev/null
set +e; out="$(sf handoff "$iq1")"; rc=$?; set -e
assert_eq "the coder is refused at the specifier's refused head" "$rc" "3"
assert_match "the coder's refusal prints the checklist" "$out" "^AUDIT_REQUIRED: $iq1 at "
sf handoff "$iq1" >/dev/null
assert_eq "each role is charged its own refusals" "$(roles "$iq1")" '[["specifier",1],["specifier",null],["coder",1]]'
iq2="$(spec_waits "Audit per role 2")"
sf approve "$iq2" >/dev/null
git -C ".worktrees/$iq2" commit -q --allow-empty -m "Code"
set +e; sf handoff "$iq2" >/dev/null; set -e
sf handoff "$iq2" >/dev/null
assert_eq "a refusal the specifier left behind is not the coder's" "$(roles "$iq2" | jq -c '.[-1]')" '["coder",1]'
iq4="$(spec_waits "Audit per role 4")"
sf reject "$iq4" "again" >/dev/null
git -C ".worktrees/$iq4" commit -q --allow-empty -m "Spec 2"
set +e; sf review "$iq4" >/dev/null; set -e
sf review "$iq4" >/dev/null
assert_eq "the left refusal counts toward the specifier's next review" "$(roles "$iq4" | jq -c '.[-1]')" '["specifier",2]'
sed -i 's/^hardener  fast     subagent$/hardener fast subagent audit/' swarmforge/roles.conf
iq3="$(sf new "Audit per role 3" -m x --route coder,hardener)"
sf start "$iq3" >/dev/null
git -C ".worktrees/$iq3" commit -q --allow-empty -m "Code"
set +e; SWARMFORGE_ROLE=coder sf handoff "$iq3" --to coder >/dev/null; rc1=$?
SWARMFORGE_ROLE=hardener sf handoff "$iq3" --to coder >/dev/null; rc2=$?
SWARMFORGE_ROLE=hardener sf handoff "$iq3" --to coder >/dev/null; rc3=$?
SWARMFORGE_ROLE=coder sf handoff "$iq3" --to coder >/dev/null; rc4=$?; set -e
assert_eq "each audited role gets its own first refusal" "$rc1 $rc2 $rc3 $rc4" "3 3 0 0"
assert_eq "a role's record keeps another role's pending state" "$(roles "$iq3")" '[["hardener",1],["coder",1]]'
assert_eq "no audit state is left once no role is pending" "$(test -e ".swarmforge/audit/$iq3" && echo kept || echo cleared)" "cleared"
for legacy in 'h\n2\n' 'h\n'; do
  iq5="$(sf new "Audit roleless $legacy" -m x --route coder)"
  sf start "$iq5" >/dev/null
  git -C ".worktrees/$iq5" commit -q --allow-empty -m "Code"
  h="$(git -C ".worktrees/$iq5" rev-parse --short HEAD)"
  mkdir -p .swarmforge/audit && printf "${legacy/h/$h}" > ".swarmforge/audit/$iq5"
  set +e; sf handoff "$iq5" >/dev/null; rc1=$?; sf handoff "$iq5" >/dev/null; rc2=$?; set -e
  assert_eq "a roleless file ($legacy) admits no role" "$rc1 $rc2" "3 0"
  assert_eq "a roleless file ($legacy) adds no refusal" "$(roles "$iq5")" '[["coder",1]]'
  assert_eq "a roleless file ($legacy) is gone after the record" "$(test -e ".swarmforge/audit/$iq5" && echo kept || echo cleared)" "cleared"
done
cp "$HERE/swarmforge/roles.conf" swarmforge/roles.conf

# truncated task in a shallow clone
cd "$scratch/harvest-shallow"
set +e; shallow="$(sf harvest 2>"$scratch/harvest.err")"; rc=$?; set -e
assert_eq "harvest in a shallow clone exits 0" "$rc" "0"
assert_eq "harvest in a shallow clone warns once" "$(grep -c '^sf: shallow repository: ' "$scratch/harvest.err")" "1"
assert_eq "harvest in a shallow clone warns first" "$(sed -n 1p "$scratch/harvest.err")" \
  "sf: shallow repository: older tasks are truncated or missing; run git fetch --unshallow"
assert_eq "the truncated task's records keep their time and recorder" \
  "$(echo "$shallow" | jq '[.records[] | select(.time == null or .recorded_by == null)] | length')" "0"
assert_eq "harvest prints the cut-off task as truncated" "$(echo "$shallow" | jq -c '[.id, .truncated, .title, .request, .status]')" "[\"$hf\",true,\"\",\"\",\"finished\"]"
assert_eq "the truncated task keeps its readable records, finished last" "$(echo "$shallow" | jq -r '[.records[].type] | join(" ")')" "handoff handoff finished"
assert_eq "no commit of the truncated task is blank" "$(echo "$shallow" | jq '[.records[].commit] | index("")')" "null"
cd "$hv"
assert_match "help lists harvest" "$(sf --help)" "^  harvest  "
cd "$repo"

# spec-judge loop: sf spec-verdict and sf spec-revisions through the CLI
copy_swarmforge .
assert_match "help lists spec-verdict" "$(sf help)" "^  spec-verdict <id> <converged\|continue\|needs-operator\|stuck> <text>$"
assert_match "help lists spec-revisions" "$(sf help)" "^  spec-revisions <id> \\[--full\\]  "
assert_eq "help lists no old gate command" "$(sf help | grep -cE '^  (rounds|verdict) ' || true)" "0"
assert_eq "the old rounds command is unknown" "$(sf rounds x 2>&1; echo "rc=$?")" "$(printf "sf: unknown command 'rounds' (try sf --help)\nrc=1")"
assert_eq "the old verdict command is unknown" "$(sf verdict x continue y 2>&1; echo "rc=$?")" "$(printf "sf: unknown command 'verdict' (try sf --help)\nrc=1")"
assert_match "a route that names the spec-judge is refused" "$(sf new "Spec-judge route" -m x --route specifier,spec-judge,coder 2>&1 || true)" "'spec-judge' in route 'specifier,spec-judge,coder' is a spec-judge, not a stage"
jq_spec() {
  local t; t="$(sf new "$1" -m x --route specifier,coder)"
  sf start "$t" >/dev/null
  git -C ".worktrees/$t" commit -q --allow-empty -m "$(printf 'Spec\n1\n2\n3\n4\n5\n6\n7\n8\n9')"
  sf review "$t" >/dev/null
  echo "$t"
}
jl="$(jq_spec "Spec-judge loop")"
assert_match "dispatch refuses the spec-judge" "$(sf dispatch spec-judge "$jl" claude 2>&1 || true)" "^sf: 'spec-judge' is the spec-judge: it needs the spec revision's gate reports"
assert_match "dispatch refuses the spec-judge on the mistral backend too" "$(sf dispatch spec-judge "$jl" mistral 2>&1 || true)" "^sf: 'spec-judge' is the spec-judge"
assert_match "no backend without a CLI or an API key" "$(env -u MISTRAL_API_KEY sf dispatch coder "$jl" 2>&1 || true)" "^sf: no backend found \(claude, codex, copilot, mistral\)"
assert_match "an explicit mistral backend without a key refuses" "$(env -u MISTRAL_API_KEY sf dispatch coder "$jl" mistral 2>&1 || true)" "^sf-mistral: MISTRAL_API_KEY is not set"
assert_match "mistral is detected with an API key and no CLI, and refuses without a remote" "$(MISTRAL_API_KEY=dummy sf dispatch coder "$jl" 2>&1 || true)" "^sf-mistral: the mistral backend needs a git remote 'origin'"
assert_match "sf run with the mistral backend starts one pipeline execution" "$(MISTRAL_API_KEY=dummy sf run "$jl" mistral 2>&1 || true)" "^sf-mistral: the mistral backend needs a git remote 'origin'"
assert_eq "spec-verdict continue prints its line" "$(sf spec-verdict "$jl" continue "r1")" "SPEC-VERDICT: $jl continue"
assert_eq "start shows the send-back with its spec verdict" "$(sf start "$jl" | grep -A2 '^from: spec-judge$')" "$(printf 'from: spec-judge\nspec-verdict: continue\nreason: r1')"
assert_match "the send-back has the new trailer" "$(git log -1 --format=%B "swarm/$jl")" "^SwarmForge-Spec-Verdict: continue$"
assert_eq "the send-back has no old trailer" "$(git log -1 --format=%B "swarm/$jl" | grep -c '^SwarmForge-Verdict:' || true)" "0"
git -C ".worktrees/$jl" commit -q --allow-empty -m "Rework"
sf review "$jl" >/dev/null
sf spec-verdict "$jl" continue "r2" >/dev/null
git -C ".worktrees/$jl" commit -q --allow-empty -m "Rework again"
sf review "$jl" >/dev/null
revisions="$(sf spec-revisions "$jl")"
loop_lines='SPEC-REVISION 1 size:11 growth:0% total:0% spec-verdict:continue
SPEC-REVISION 2 size:13 growth:18% total:18% spec-verdict:continue
SPEC-REVISION 3 size:15 growth:36% total:36% spec-verdict:-'
assert_eq "spec-revisions prints three spec revisions, the first two continued" "$(echo "$revisions" | grep '^SPEC-REVISION' | sed 's/ [0-9a-f]* size:/ size:/')" "$loop_lines"
assert_eq "spec-revisions prints no send-back text" "$(echo "$revisions" | grep -c '^  ' || true)" "0"
assert_match "spec-revisions --full prints each send-back" "$(sf spec-revisions "$jl" --full)" "^  spec-judge: r2$"
assert_fails "spec-revisions refuses another argument" sf spec-revisions "$jl" --fuller
assert_match "spec-revisions names itself in its usage" "$(sf spec-revisions "$jl" --fuller 2>&1 || true)" "spec-revisions takes <id> \[--full\]"
assert_match "spec-revisions prints the cap" "$revisions" "^LIMIT: cap 2 of 2 automatic send-backs used$"
tip="$(git rev-parse "swarm/$jl")"
assert_match "continue past the cap is refused with the limit" "$(sf spec-verdict "$jl" continue x 2>&1 || true)" "^sf: LIMIT: cap 2 of 2 automatic send-backs used$"
assert_eq "the refused continue records nothing" "$(git rev-parse "swarm/$jl")" "$tip"
assert_match "a bad spec verdict is refused" "$(sf spec-verdict "$jl" maybe x 2>&1 || true)" "spec verdict must be converged, continue, needs-operator or stuck, not 'maybe'"
assert_match "a spec verdict needs a reason" "$(sf spec-verdict "$jl" stuck 2>&1 || true)" "spec verdict needs a reason"
assert_eq "stuck is recorded" "$(sf spec-verdict "$jl" stuck "cap")" "SPEC-VERDICT: $jl stuck"
assert_match "show prints the spec-verdict record" "$(sf show "$jl")" "^type: spec-verdict$"
assert_match "the spec-verdict record has the new trailer" "$(git log -1 --format=%B "swarm/$jl")" "^SwarmForge-Record: spec-verdict$"
assert_match "a second spec verdict in the spec revision is refused" "$(sf spec-verdict "$jl" converged "x" 2>&1 || true)" "spec revision 3 already has the spec verdict stuck"
assert_match "a dispatched role cannot record a spec verdict" "$(SWARMFORGE_DISPATCHED=spec-judge sf spec-verdict "$jl" converged "x" 2>&1 || true)" "^sf: spec-verdict is the operator's; the dispatched spec-judge cannot run it$"
assert_fails "a dispatched role's spec verdict fails" env SWARMFORGE_DISPATCHED=spec-judge sf spec-verdict "$jl" converged "x"
assert_eq "a dispatched role can read the spec revisions" "$(SWARMFORGE_DISPATCHED=spec-judge sf spec-revisions "$jl" | grep -c '^SPEC-REVISION')" "3"
assert_eq "harvest gives the spec verdicts" "$(sf harvest | jq -c --arg id "$jl" 'select(.id == $id) | [.records[] | .spec_verdict]')" \
  '[null,null,"continue",null,"continue",null,"stuck"]'
assert_eq "harvest gives the new record type" "$(sf harvest | jq -r --arg id "$jl" 'select(.id == $id) | .records[-1].type')" "spec-verdict"
assert_eq "harvest has no verdict key" "$(sf harvest | jq --arg id "$jl" 'select(.id == $id) | [.records[] | select(has("verdict"))] | length')" "0"
assert_match "approve hands off after a spec verdict" "$(sf approve "$jl")" "specifier -> coder"
assert_match "spec-verdict at the next stage is refused" "$(sf spec-verdict "$jl" converged "x" 2>&1 || true)" "spec verdicts are recorded at stage:specifier"
jg="$(jq_spec "Spec-judge growth")"
sf spec-verdict "$jg" continue "r1" >/dev/null
git -C ".worktrees/$jg" commit -q --allow-empty -m "$(printf 'Rework\n1\n2\n3\n4\n5')"
sf review "$jg" >/dev/null
assert_match "growth over half the baseline is a limit" "$(sf spec-revisions "$jg")" "^LIMIT: growth size 18 is more than half over 11$"
assert_match "continue over the growth limit is refused" "$(sf spec-verdict "$jg" continue x 2>&1 || true)" "^sf: LIMIT: growth"
jn="$(jq_spec "Spec-judge note")"
sf spec-verdict "$jn" needs-operator "decide" >/dev/null
sf reject "$jn" "more" >/dev/null
git -C ".worktrees/$jn" commit -q --allow-empty -m "$(printf 'Rework\n1\n2\n3\n4\n5')"
sf review "$jn" >/dev/null
assert_eq "spec-revisions totals growth over spec revision 1 across a send-back and notes it" "$(sf spec-revisions "$jn" | sed 's/ [0-9a-f]* size:/ size:/')" \
  "$(printf 'SPEC-REVISION 1 size:11 growth:0%% total:0%% spec-verdict:needs-operator\nSPEC-REVISION 2 size:18 growth:0%% total:63%% spec-verdict:-\nNOTE: total growth 63%% over spec revision 1 (11); not a limit')"
assert_eq "the NOTE does not refuse continue" "$(sf spec-verdict "$jn" continue "again")" "SPEC-VERDICT: $jn continue"
assert_eq "a task without a review has no spec revisions" "$(sf spec-revisions "$(sf new "Unreviewed" -m x)")" "NO_SPEC_REVISIONS"

# old records: type verdict and SwarmForge-Verdict read as the new ones
old_record() { # <id> <record> <verdict> <text> <needs>: an old-form record on the task branch
  local branch="swarm/$1" head tree commit
  head="$(git rev-parse "$branch")"; tree="$(git rev-parse "$head^{tree}")"
  commit="$(printf 'sf %s: %s\n\n%s\n\nSwarmForge-Task: %s\nSwarmForge-Record: %s\nSwarmForge-From: judge\nSwarmForge-Stage: specifier\nSwarmForge-Route: specifier,coder\nSwarmForge-Needs: %s\nSwarmForge-Verdict: %s\n' \
    "$2" "$1" "$4" "$1" "$2" "$5" "$3" | git commit-tree "$tree" -p "$head")"
  git update-ref "refs/heads/$branch" "$commit"
}
jo="$(jq_spec "Old loop")"
old_record "$jo" changes-requested continue "o1" none
git -C ".worktrees/$jo" merge -q --ff-only "swarm/$jo"
git -C ".worktrees/$jo" commit -q --allow-empty -m "Rework"
sf review "$jo" >/dev/null
old_record "$jo" changes-requested continue "o2" none
git -C ".worktrees/$jo" merge -q --ff-only "swarm/$jo"
git -C ".worktrees/$jo" commit -q --allow-empty -m "Rework again"
sf review "$jo" >/dev/null
old_revisions="$(sf spec-revisions "$jo")"
assert_eq "old send-backs give the same spec revision lines" "$(echo "$old_revisions" | grep '^SPEC-REVISION' | sed 's/ [0-9a-f]* size:/ size:/')" "$loop_lines"
assert_match "old send-backs count toward the cap" "$old_revisions" "^LIMIT: cap 2 of 2 automatic send-backs used$"
assert_match "continue past the cap of old send-backs is refused" "$(sf spec-verdict "$jo" continue x 2>&1 || true)" "^sf: LIMIT: cap 2 of 2"
old_record "$jo" verdict stuck "o3" approval
assert_match "an old verdict record shows as a spec-verdict" "$(sf show "$jo")" "$(printf '^type: spec-verdict$')"
assert_eq "an old verdict record shows its spec verdict" "$(sf show "$jo" | grep -A3 '^type: spec-verdict$' | tail -n 3)" "$(printf 'from: judge\nspec-verdict: stuck\nreason: o3')"
assert_match "an old send-back shows its spec verdict" "$(sf start "$jo")" "$(printf '^spec-verdict: continue$')"
assert_match "an old verdict closes its spec revision" "$(sf spec-revisions "$jo")" "^SPEC-REVISION 3 [0-9a-f]+ size:15 growth:36% total:36% spec-verdict:stuck$"
assert_match "an old verdict refuses a second one" "$(sf spec-verdict "$jo" converged x 2>&1 || true)" "spec revision 3 already has the spec verdict stuck"
assert_eq "harvest reads old records as new ones" "$(sf harvest | jq -c --arg id "$jo" 'select(.id == $id) | [.records[] | select(.spec_verdict) | [.type, .spec_verdict]]')" \
  '[["changes-requested","continue"],["changes-requested","continue"],["spec-verdict","stuck"]]'
assert_eq "sf does not rewrite old records" "$(git log -1 --format=%B "swarm/$jo" | grep -c '^SwarmForge-Verdict: stuck$')" "1"

sed -i 's/^spec-judge \(.*\) spec-judge$/spec-judge \1 judge/' swarmforge/roles.conf
assert_eq "check refuses the old judge flag" "$(sf check 2>&1 | grep -c "roles.conf: 'spec-judge' has unknown flag 'judge' (only audit, optional, gate, spec-judge)")" "1"
assert_fails "check fails on the old judge flag" sf check
copy_swarmforge .
sed -i '/^spec-judge /d' swarmforge/roles.conf
assert_match "check passes without the spec-judge" "$(sf check)" "^CHECK: ok"
assert_match "spec-verdict fails without the spec-judge" "$(sf spec-verdict "$jg" stuck x 2>&1 || true)" "roles.conf has no spec-judge"
copy_swarmforge .

# backend on records
brepo="$scratch/backend"
mkdir -p "$brepo" && cd "$brepo"
git init -q .
copy_swarmforge .
echo "x" > README.md
git add -A && git commit -q -m "seed"
sf init >/dev/null
carried() {
  git log -1 --format='%(trailers:key=SwarmForge-Backend,valueonly,separator=)|%(trailers:key=SwarmForge-Dispatched,valueonly,separator=)' "$1"
}
b1="$(env CLAUDECODE=1 sf new "Backend one" -m x --route specifier,coder)"
assert_eq "a Claude Code session records claude session" "$(carried "swarm/$b1")" "claude|session"
assert_eq "show ends the record with its backend" "$(sf show "$b1" | grep -v '^$' | tail -1)" "backend: claude session"
env CODEX_THREAD_ID=t1 sf ask "$b1" "q1" >/dev/null
assert_eq "a Codex thread alone records nothing" "$(carried "swarm/$b1")" "|"
assert_eq "show prints no backend line for a record that carries nothing" "$(sf show "$b1" | grep -v '^$' | tail -1)" "question: q1"
env CLAUDECODE=1 CODEX_THREAD_ID=t1 sf answer "$b1" "a1" >/dev/null
assert_eq "CLAUDECODE wins over a Codex thread" "$(carried "swarm/$b1")" "claude|session"
env CLAUDECODE=0 sf ask "$b1" "q2" >/dev/null
assert_eq "CLAUDECODE other than 1 records nothing" "$(carried "swarm/$b1")" "|"
env SWARMFORGE_BACKEND=codex sf answer "$b1" "a2" >/dev/null
assert_eq "SWARMFORGE_BACKEND outside dispatch records nothing" "$(carried "swarm/$b1")" "|"
env SWARMFORGE_DISPATCHED= CLAUDECODE=1 sf ask "$b1" "q3" >/dev/null
assert_eq "a blank SWARMFORGE_DISPATCHED is a session" "$(carried "swarm/$b1")" "claude|session"
sf answer "$b1" "a3" >/dev/null
env SWARMFORGE_DISPATCHED=specifier CLAUDECODE=1 sf ask "$b1" "q4" >/dev/null
assert_eq "dispatch without a backend records nothing" "$(carried "swarm/$b1")" "|"
sf answer "$b1" "a4" >/dev/null
env SWARMFORGE_DISPATCHED=specifier SWARMFORGE_BACKEND=vibe sf ask "$b1" "q5" >/dev/null
assert_eq "dispatch with an unknown backend records nothing" "$(carried "swarm/$b1")" "|"
sf answer "$b1" "a5" >/dev/null

fakes="$scratch/fake-backends"
mkdir -p "$fakes"
for b in claude codex copilot; do
  printf '#!/usr/bin/env bash
sf review "$BACKEND_TASK" -m "by %s" >/dev/null
' "$b" > "$fakes/$b"
  chmod +x "$fakes/$b"
done
env BACKEND_TASK="$b1" CLAUDECODE=1 PATH="$fakes:$PATH" sf dispatch specifier "$b1" codex
assert_eq "a dispatched role records the backend that dispatch runs" "$(carried "swarm/$b1")" "codex|dispatched"
env CLAUDECODE=1 sf approve "$b1" >/dev/null
assert_eq "the approve handoff carries the review's backend" "$(carried "swarm/$b1")" "codex|dispatched"
assert_eq "the approval carries the operator's backend" "$(carried "swarm/$b1^")" "claude|session"
b2="$(sf new "Backend two" -m x --route specifier,coder)"
env BACKEND_TASK="$b2" SWARMFORGE_BACKEND=claude PATH="$fakes:$PATH" sf dispatch specifier "$b2"
assert_eq "dispatch passes on the backend it detects" "$(carried "swarm/$b2")" "claude|dispatched"
b3="$(sf new "Backend three" -m x --route specifier,coder)"
env CLAUDECODE=1 sf review "$b3" >/dev/null
sf approve "$b3" >/dev/null
assert_eq "a clean approve records nothing on the approval" "$(carried "swarm/$b3^")" "|"
assert_eq "a clean approve hands on the review's session" "$(carried "swarm/$b3")" "claude|session"
b4="$(sf new "Backend four" -m x --route specifier,coder)"
sf review "$b4" >/dev/null
env CLAUDECODE=1 sf approve "$b4" >/dev/null
assert_eq "the approve handoff of a review that carries nothing carries nothing" "$(carried "swarm/$b4")" "|"
b5="$(sf new "Backend five" -m x --route coder)"
sf start "$b5" >/dev/null
git -C ".worktrees/$b5" commit -q --allow-empty -m "Work"
sf handoff "$b5" -m done >/dev/null
env CLAUDECODE=1 sf finish "$b5" >/dev/null
assert_eq "the finished merge carries the backend of sf finish" "$(carried HEAD)" "claude|session"
bout="$(sf harvest)"
assert_eq "harvest exports backend and dispatched" \
  "$(echo "$bout" | jq -c --arg id "$b1" 'select(.id == $id) | [.records[] | [.backend, .dispatched]] | .[0], .[1], .[-1]')" \
  "$(printf '["claude",false]\n[null,null]\n["codex",true]')"
assert_eq "harvest dispatched is null exactly when backend is null" \
  "$(echo "$bout" | jq -c '[.records[] | select((.backend == null) != (.dispatched == null))] | length' | sort -u)" "0"
assert_fails "hook refuses a SwarmForge-Backend trailer" git -C ".worktrees/$b2" commit -q --allow-empty -m "$(printf 'Forged\n\nSwarmForge-Backend: claude')"
assert_fails "hook refuses a SwarmForge-Dispatched trailer" git -C ".worktrees/$b2" commit -q --allow-empty -m "$(printf 'Forged\n\nSwarmForge-Dispatched: session')"
cd "$repo"

for pair in ".claude/skills/swarm/SKILL.md .agents/skills/swarm/SKILL.md"; do
  set -- $pair
  cmp -s "$HERE/$1" "$HERE/$2" && pass "$1 matches $2" || fail "$1 differs from $2"
done

if [ "$failures" -gt 0 ]; then echo "$failures failure(s)" >&2; exit 1; fi
echo "all tests passed"
