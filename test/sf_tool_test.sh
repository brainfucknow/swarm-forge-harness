#!/usr/bin/env bash
# Behavioral tests for `sf tool`, using a local tool source and a fake bb.
set -euo pipefail

HERE="$(cd "$(dirname "$0")/.." && pwd)"

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

repo="$scratch/project"
mkdir -p "$repo" && cd "$repo"
git init -q .
cp -R "$HERE/swarmforge" swarmforge
git add -A && git commit -q -m seed

fake="$scratch/fakebin"; mkdir -p "$fake"
printf '#!/usr/bin/env bash\nprintf "%%s\\n" "$@"\n' > "$fake/bb"; chmod +x "$fake/bb"
export PATH="$repo/swarmforge/bin:$PATH"
# Wrappers run the fake bb; sf itself needs the real one.
fake_bb() { PATH="$fake:$PATH" "$@"; }
src="$scratch/tool-src"; mkdir -p "$src"; echo '{}' > "$src/bb.edn"
export SWARMFORGE_TOOL_SRC="$src"

assert_match "list prints the catalogue" "$(sf tool list)" "^clj-mutate +bb +github.com/unclebob/clj-mutate$"
assert_eq "bin names the wrapper directory" "$(sf tool bin)" "$repo/.swarmforge/bin"
assert_fails "unknown tool" sf tool require nosuchtool
out="$(sf tool require clj-mutate || true)"
assert_match "require reports the first missing dependency" "$out" "^MISSING: cloverage$"

out="$(sf tool ensure clj-mutate)"
assert_match "ensure installs the dependency first" "$out" "^INSTALLED: cloverage .*/.swarmforge/bin/cloverage$"
assert_match "ensure installs the tool" "$out" "INSTALLED: clj-mutate .*/.swarmforge/bin/clj-mutate$"
assert_match "require passes after ensure" "$(sf tool require clj-mutate)" "^OK: clj-mutate $repo/.swarmforge/bin/clj-mutate$"
assert_match "cloverage wrapper runs the Clojure CLI" "$(head -2 .swarmforge/bin/cloverage | tail -1)" "^exec clojure -Sdeps .* -M -m cloverage.coverage -p src -s spec -s test -r speclj"

run="$(fake_bb .swarmforge/bin/clj-mutate --mutate-all --max-workers 9 src/a.clj | paste -sd ' ' -)"
assert_eq "mutate wrapper drops --mutate-all and forces four workers" "$run" "--config $src/bb.edn clj-mutate src/a.clj --max-workers 4"
run="$(fake_bb .swarmforge/bin/clj-mutate --scan src/a.clj | paste -sd ' ' -)"
assert_eq "mutate wrapper leaves a scan alone" "$run" "--config $src/bb.edn clj-mutate --scan src/a.clj"

sf tool ensure gherkin-mutator >/dev/null
run="$(fake_bb .swarmforge/bin/gherkin-mutator --level full --workers 8 features | paste -sd ' ' -)"
assert_eq "gherkin wrapper caps the level and workers" "$run" "--config $src/bb.edn gherkin-mutator --level hard features --workers 4"
run="$(fake_bb .swarmforge/bin/gherkin-mutator --level soft features | paste -sd ' ' -)"
assert_eq "gherkin wrapper keeps soft" "$run" "--config $src/bb.edn gherkin-mutator --level soft features --workers 4"

sf tool ensure sf-cs >/dev/null
assert_match "local wrapper execs the repository script" "$(tail -1 .swarmforge/bin/sf-cs)" "^exec '$repo/swarmforge/bin/sf-cs' "
assert_match "wrappers run from a task worktree too" "$(git worktree add -q "$scratch/wt" -b wt && cd "$scratch/wt" && sf tool require sf-cs)" "^OK: sf-cs $repo/.swarmforge/bin/sf-cs$"

if [ "$failures" -gt 0 ]; then echo "$failures failure(s)" >&2; exit 1; fi
echo "all tests passed"
