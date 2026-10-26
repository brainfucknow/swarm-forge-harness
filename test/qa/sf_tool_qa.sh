#!/usr/bin/env bash
# Executable form of test/qa/sf_tool.md.
#
# Drives sf tool and the wrappers it writes from the command line, with real
# Babashka, real GitHub clones, and this repository's own Babashka code as the
# code under measurement. It checks only what a user sees: exit status,
# stdout, stderr and files on disk. Run it from the task worktree root:
#
#   bash test/qa/sf_tool_qa.sh [branch]
#
# The branch defaults to swarm/8414dd5. Needs git, bb and java on PATH, no clj,
# and network access to github.com, repo.clojars.org and Maven Central.
# Everything is built under ./tmp/qa/. The script exits non-zero when a check fails.
set -uo pipefail

WT="$(pwd)"
BRANCH="${1:-swarm/8414dd5}"
QA="$WT/tmp/qa"
P="$QA/project"
[ -x "$WT/swarmforge/bin/sf" ] || { echo "run from the task worktree root: no swarmforge/bin/sf" >&2; exit 2; }
for tool in git bb java; do
  command -v "$tool" >/dev/null || { echo "missing prerequisite on PATH: $tool" >&2; exit 2; }
done
command -v clj >/dev/null && { echo "clj is on PATH; run with a PATH that leaves it out" >&2; exit 2; }

export GIT_AUTHOR_NAME=qa GIT_AUTHOR_EMAIL=qa@example.com GIT_COMMITTER_NAME=qa GIT_COMMITTER_EMAIL=qa@example.com
unset SWARMFORGE_TOOL_SRC SWARMFORGE_ROLE

passed=0 failed=0
pass() { echo "ok   $1"; passed=$((passed + 1)); }
fail() { echo "FAIL $1" >&2; failed=$((failed + 1)); }
section() { echo; echo "== $1"; }

# run CMD...: runs a command, keeping OUT, ERR and RC for the checks below.
run() {
  "$@" >"$QA/.out" 2>"$QA/.err"
  RC=$?
  OUT="$(cat "$QA/.out")"
  ERR="$(cat "$QA/.err")"
}
show_run() { printf '%s\n--- stderr\n%s\n' "$OUT" "$ERR" | tail -n 25 | sed 's/^/     | /' >&2; }
check() { if eval "$2"; then pass "$1"; else fail "$1"; show_run; fi; }
rc_is() { [ "$RC" -eq "$1" ]; }
out_has() { grep -qF -- "$1" <<<"$OUT"; }
out_line() { grep -qxF -- "$1" <<<"$OUT"; }
out_is() { [ "$OUT" = "$1" ]; }
out_matches() { grep -qE -- "$1" <<<"$OUT"; }
err_has() { grep -qF -- "$1" <<<"$ERR"; }
both_have() { grep -qF -- "$1" <<<"$OUT$ERR"; }
both_match() { grep -qE -- "$1" <<<"$OUT$ERR"; }
neither_has() { ! grep -qF -- "$1" <<<"$OUT"$'\n'"$ERR"; }
line_before() { local a b; a="$(grep -nF -- "$1" <<<"$OUT" | head -n 1 | cut -d: -f1)"; b="$(grep -nF -- "$2" <<<"$OUT" | head -n 1 | cut -d: -f1)"; [ -n "$a" ] && [ -n "$b" ] && [ "$a" -lt "$b" ]; }
# table_row NS: a Cloverage table row (it has | columns; the name may be coloured).
table_row() { grep -F -- "$1" <<<"$OUT$ERR" | grep -qE '^\|.*\|$'; }
listed() { grep -qE "^$1 +" <<<"$OUT"; }
in_dir() { local dir="$1"; shift; (cd "$dir" && "$@"); }
quiet() { "$@" >"$QA/.setup" 2>&1 || { echo "setup failed: $*" >&2; tail -n 30 "$QA/.setup" >&2; exit 2; }; }
script_printing() { mkdir -p "$(dirname "$1")"; printf '#!/usr/bin/env bash\necho %q\n' "$2" >"$1"; chmod 755 "$1"; }
sfcs_usage() { out_has 'usage: sf-cs'; }

rm -rf "$QA" && mkdir -p "$QA"
quiet git clone -q --branch "$BRANCH" "$WT" "$P"
export PATH="$P/swarmforge/bin:$PATH"
BIN="$P/.swarmforge/bin"

section "QA-1: the Clojure tools install"
for t in dry4clj clj-mutate crap4clj gherkin-parser; do
  run in_dir "$P" sf tool ensure "$t"
  check "QA-1 ensure $t exits 0" 'rc_is 0'
  check "QA-1 ensure $t prints INSTALLED" 'out_line "INSTALLED: $t $BIN/$t"'
  case "$t" in
    clj-mutate|crap4clj) check "QA-1 ensure $t installs cloverage first" 'line_before "INSTALLED: cloverage " "INSTALLED: $t "' ;;
  esac
done
for t in dry4clj clj-mutate crap4clj gherkin-parser; do
  run in_dir "$P" sf tool require "$t"
  check "QA-1 require $t" 'rc_is 0 && out_line "OK: $t $BIN/$t"'
done
check "QA-1 coverage config pins cloverage" 'grep -qF 61e3cac426e9907a9dd01c37597f85c71a57ff90 "$P/.swarmforge/tools/crap4clj-coverage/bb.edn"'
run in_dir "$P" sf tool list
check "QA-1 list shows dry4clj as bbmain" 'out_matches "^dry4clj +bbmain "'

section "QA-2: dry4clj runs"
run in_dir "$P" "$BIN/dry4clj" swarmforge/tools/javascript/src swarmforge/tools/core/src
check "QA-2 dry4clj exits 0" 'rc_is 0'
check "QA-2 dry4clj prints a report" 'out_has "No duplicate candidates found." || out_matches "\.clj.*[0-9]+-[0-9]+|\.clj:[0-9]+"'
check "QA-2 no File does not exist" 'neither_has "File does not exist"'
run in_dir "$P" "$BIN/dry4clj" --format edn swarmforge/tools/javascript/src
check "QA-2 --format edn prints EDN" 'rc_is 0 && [[ "$OUT" =~ ^[[:space:]]*[\[{] ]]'

section "QA-3: clj-mutate runs"
run in_dir "$P/swarmforge/tools/javascript" "$BIN/clj-mutate" src/sfjs/package.clj --scan
check "QA-3 clj-mutate --scan exits 0" 'rc_is 0'
check "QA-3 scan reports counts for package.clj" 'out_has "src/sfjs/package.clj" && out_matches "[0-9]"'
check "QA-3 no File does not exist" 'neither_has "File does not exist"'
run in_dir "$P/swarmforge/tools/javascript" "$BIN/clj-mutate" --help
check "QA-3 --help prints usage mentioning --scan" 'both_have "--scan"'

section "QA-4: crap4clj with clojure.test and no Clojure CLI"
JS="$P/swarmforge/tools/javascript"
run in_dir "$JS" "$BIN/crap4clj"
check "QA-4 crap4clj exits 0" 'rc_is 0'
check "QA-4 tests ran clean" 'both_match "Ran [0-9]+ tests" && both_have "0 failures, 0 errors"'
check "QA-4 cloverage table has sfjs.package" 'table_row sfjs.package'
check "QA-4 CRAP report has missing-packages" 'both_match "sfjs\.package.*missing-packages|missing-packages.*sfjs\.package"'
check "QA-4 lcov and crap.edn written" '[ -f "$JS/target/coverage/lcov.info" ] && [ -f "$JS/.metrics/crap.edn" ]'
rm -rf "$JS/target" "$JS/.metrics"

section "QA-5: crap4clj with speclj"
cp -R "$P/.swarmforge/tools/dry4clj" "$QA/dry"
rm -f "$QA/dry/deps.edn"
run in_dir "$QA/dry" "$BIN/crap4clj"
check "QA-5 crap4clj exits 0" 'rc_is 0'
check "QA-5 speclj ran clean" 'both_match "[0-9]+ examples, 0 failures"'
check "QA-5 cloverage table has dry4clj.core" 'table_row dry4clj.core'
check "QA-5 CRAP report has dry4clj.core functions" 'grep -E "dry4clj\.core" <<<"$OUT$ERR" | grep -vqE "\|"'

section "QA-6: crap4clj leaves a caller's coverage choice alone"
run in_dir "$JS" "$BIN/crap4clj" --use-existing-coverage --lcov does-not-exist.info
check "QA-6 no Cloverage run" '! both_match "Ran [0-9]+ tests" && neither_has "Writing LCOV report"'
rm -rf "$JS/target" "$JS/.metrics"

section "QA-7: a tool without a Babashka entry point fails clearly"
run in_dir "$P" sf tool ensure crap4go
check "QA-7 ensure crap4go exits 1" 'rc_is 1'
check "QA-7 names the missing bb.edn" 'err_has "sf tool: crap4go: no bb.edn in $P/.swarmforge/tools/crap4go"'
check "QA-7 no crap4go wrapper" '[ ! -e "$BIN/crap4go" ]'
run in_dir "$P" sf tool require crap4go
check "QA-7 require reports MISSING" 'rc_is 1 && out_line "MISSING: crap4go"'
run in_dir "$P" sf tool ensure crap4go
check "QA-7 second ensure same error, no clone" 'rc_is 1 && err_has "sf tool: crap4go: no bb.edn in $P/.swarmforge/tools/crap4go" && ! grep -qi clone <<<"$ERR"'

section "QA-8: a missing task fails clearly"
mkdir -p "$QA/fake-src"
echo '{:tasks {spec (println "spec")}}' >"$QA/fake-src/bb.edn"
script_printing "$BIN/clj-mutate" stale
run in_dir "$P" env SWARMFORGE_TOOL_SRC="$QA/fake-src" sf tool ensure clj-mutate
check "QA-8 ensure exits 1" 'rc_is 1'
check "QA-8 names the missing task" 'err_has "sf tool: clj-mutate: $QA/fake-src/bb.edn has no task '"'"'mutate'"'"'"'
check "QA-8 stale wrapper removed" '[ ! -e "$BIN/clj-mutate" ]'
run in_dir "$P" sf tool ensure clj-mutate
check "QA-8 reinstall without override exits 0" 'rc_is 0'

section "QA-9: the catalogue comes from the checkout"
quiet git -C "$P" worktree add -q .worktrees/q1 -b swarm/q1
Q1="$P/.worktrees/q1"
echo 'probe local swarmforge/bin/probe - - -' >>"$Q1/swarmforge/tools.conf"
script_printing "$Q1/swarmforge/bin/probe" "probe ran"
run in_dir "$Q1" sf tool list
check "QA-9 worktree list has probe" 'rc_is 0 && listed probe'
run in_dir "$P" sf tool list
check "QA-9 main list has no probe" 'rc_is 0 && ! listed probe'
run in_dir "$Q1" sf tool ensure probe
check "QA-9 ensure probe in worktree" 'rc_is 0 && out_line "INSTALLED: probe $BIN/probe"'
run in_dir "$Q1" sf tool require probe
check "QA-9 require probe in worktree" 'rc_is 0 && out_line "OK: probe $BIN/probe"'
run in_dir "$Q1" "$BIN/probe"
check "QA-9 probe wrapper runs the worktree script" 'out_is "probe ran"'
run in_dir "$P" sf tool ensure probe
check "QA-9 main checkout does not know probe" 'rc_is 1 && err_has "unknown tool '"'"'probe'"'"'"'

section "QA-10: a local wrapper runs the caller's copy of the script"
run in_dir "$P" sf tool ensure sf-cs
check "QA-10 ensure sf-cs" 'rc_is 0'
script_printing "$Q1/swarmforge/bin/sf-cs" "task copy"
mkdir -p "$QA/outside"
run in_dir "$Q1" "$BIN/sf-cs"
check "QA-10 worktree copy runs" 'out_is "task copy"'
run in_dir "$P" "$BIN/sf-cs" --help
check "QA-10 main checkout runs the real sf-cs" 'sfcs_usage && ! out_has "task copy"'
run in_dir "$QA/outside" env GIT_CEILING_DIRECTORIES="$QA" "$BIN/sf-cs" --help
check "QA-10 outside git runs the real sf-cs" 'sfcs_usage && ! out_has "task copy"'
quiet git -C "$P" worktree add -q .worktrees/q2 -b swarm/q2
rm -f "$P/.worktrees/q2/swarmforge/bin/sf-cs"
run in_dir "$P/.worktrees/q2" "$BIN/sf-cs" --help
check "QA-10 worktree without sf-cs runs the real sf-cs" 'sfcs_usage && ! out_has "task copy"'

section "QA-11: the gherkin-parser wrapper and sf check"
mkdir -p "$P/tmp"
run in_dir "$P" "$BIN/gherkin-parser" test/features/sf_tool/wrappers.feature ./tmp/wrappers.json
check "QA-11 gherkin-parser exits 0" 'rc_is 0'
check "QA-11 IR names the feature" 'grep -qF "sf tool wrappers" "$P/tmp/wrappers.json"'
run in_dir "$P" sf check
check "QA-11 sf check says nothing about tools.conf" 'neither_has "tools.conf"'

echo
echo "passed $passed, failed $failed"
[ "$failed" -eq 0 ]
