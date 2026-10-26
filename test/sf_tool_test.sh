#!/usr/bin/env bash
# Behavioral tests for `sf tool`, using local tool sources and a fake bb.
# The checks labelled "sf tool <feature> N" run test/features/sf_tool/*.feature,
# one check per scenario and example row; README.md there defines the steps.
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
git config --global init.defaultBranch main

# No real Clojure CLI may be found; the clj steps put a fake one on PATH.
path_without_clj() {
  local IFS=: dir kept=""
  for dir in $PATH; do [ -x "$dir/clj" ] || kept="$kept${kept:+:}$dir"; done
  echo "$kept"
}
BASE_PATH="$(path_without_clj)"

fake="$scratch/fakebin"; mkdir -p "$fake"
printf '#!/usr/bin/env bash\nprintf "%%s\\n" "$@"\n' > "$fake/bb"; chmod +x "$fake/bb"
fake_clj="$scratch/fakeclj"; mkdir -p "$fake_clj"
printf '#!/usr/bin/env bash\nexit 0\n' > "$fake_clj/clj"; chmod +x "$fake_clj/clj"

# --- legacy checks -----------------------------------------------------------

repo="$scratch/project"
mkdir -p "$repo" && cd "$repo"
git init -q .
cp -R "$HERE/swarmforge" swarmforge
git add -A && git commit -q -m seed

export PATH="$repo/swarmforge/bin:$fake:$BASE_PATH"
src="$scratch/tool-src"; mkdir -p "$src"
echo '{:tasks {mutate {:task (println "mutate")} gherkin-mutator aps/-main}}' > "$src/bb.edn"
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

run="$(.swarmforge/bin/clj-mutate --mutate-all --max-workers 9 src/a.clj | paste -sd ' ' -)"
assert_eq "mutate wrapper drops --mutate-all and forces four workers" "$run" "--config $src/bb.edn mutate src/a.clj --max-workers 4"
run="$(.swarmforge/bin/clj-mutate --scan src/a.clj | paste -sd ' ' -)"
assert_eq "mutate wrapper leaves a scan alone" "$run" "--config $src/bb.edn mutate --scan src/a.clj"

sf tool ensure gherkin-mutator >/dev/null
run="$(.swarmforge/bin/gherkin-mutator --level full --workers 8 features | paste -sd ' ' -)"
assert_eq "gherkin wrapper caps the level and workers" "$run" "--config $src/bb.edn gherkin-mutator --level hard features --workers 4"
run="$(.swarmforge/bin/gherkin-mutator --level soft features | paste -sd ' ' -)"
assert_eq "gherkin wrapper keeps soft" "$run" "--config $src/bb.edn gherkin-mutator --level soft features --workers 4"

sf tool ensure sf-cs >/dev/null
assert_match "local wrapper execs the repository script" "$(tail -1 .swarmforge/bin/sf-cs)" "^exec '$repo/swarmforge/bin/sf-cs' "
assert_match "wrappers run from a task worktree too" "$(git worktree add -q "$scratch/wt" -b wt && cd "$scratch/wt" && sf tool require sf-cs)" "^OK: sf-cs $repo/.swarmforge/bin/sf-cs$"

unset SWARMFORGE_TOOL_SRC

# --- feature steps -----------------------------------------------------------

projects=0
MAIN="" SRC="" WT="" WT2=""

# Given a project with swarmforge installed / a fake bb on PATH
a_project() {
  projects=$((projects + 1))
  MAIN="$scratch/p$projects"
  mkdir -p "$MAIN"
  (cd "$MAIN" && git init -q . && cp -R "$HERE/swarmforge" swarmforge && git add -A && git commit -q -m seed)
  unset SWARMFORGE_TOOL_SRC
  SRC="" WT="" WT2=""
  export PATH="$MAIN/swarmforge/bin:$fake:$BASE_PATH"
}

# Given a tool source whose bb.edn defines the tasks "A B"
tool_source_with_tasks() {
  SRC="$scratch/src$projects"; mkdir -p "$SRC"
  local task entries=""
  for task in $1; do entries="$entries $task {:task (println \"$task\")}"; done
  echo "{:paths [\"src\"] :tasks {$entries}}" > "$SRC/bb.edn"
  export SWARMFORGE_TOOL_SRC="$SRC"
}

# Given the tool source has the namespace "N"
tool_source_namespace() {
  local file; file="$SRC/src/$(echo "$1" | tr . / | tr - _).clj"
  mkdir -p "$(dirname "$file")"
  echo "(ns $1)" > "$file"
}

# Given a tool source without bb.edn
tool_source_without_bb_edn() {
  SRC="$scratch/src$projects"; mkdir -p "$SRC"
  echo readme > "$SRC/README.md"
  export SWARMFORGE_TOOL_SRC="$SRC"
}

# Given a task worktree / a second task worktree without "F"
a_task_worktree() {
  WT="$MAIN/.worktrees/t1"
  git -C "$MAIN" worktree add -q "$WT" -b swarm/t1
}
a_second_task_worktree_without() {
  WT2="$MAIN/.worktrees/t2"
  git -C "$MAIN" worktree add -q "$WT2" -b swarm/t2
  git -C "$WT2" rm -q "$1" && git -C "$WT2" commit -q -m "drop $1"
}

# Given the <checkout>'s "F" prints "X"
script_prints() {
  mkdir -p "$(dirname "$1/$2")"
  printf '#!/usr/bin/env bash\necho %s\n' "$3" > "$1/$2"; chmod +x "$1/$2"
}

checkout_dir() {
  case "$1" in
    "main checkout") echo "$MAIN" ;;
    "task worktree") echo "$WT" ;;
    "second task worktree") echo "$WT2" ;;
    "directory outside git") mkdir -p "$scratch/outside"; echo "$scratch/outside" ;;
  esac
}

# When I run "C" in <dir>: sets status, out and err.
run_in() {
  local dir="$1"; shift
  status=0
  (cd "$dir" && GIT_CEILING_DIRECTORIES="$scratch" "$@") >"$scratch/out" 2>"$scratch/err" || status=$?
  out="$(cat "$scratch/out")"; err="$(cat "$scratch/err")"
}

# When I run the T wrapper with "ARGS"
run_wrapper() {
  local tool="$1"; shift
  run_in "$MAIN" "$MAIN/.swarmforge/bin/$tool" "$@"
}

fill() {
  local text="$1"
  text="${text//CFG/$MAIN/.swarmforge/tools/crap4clj-coverage/bb.edn}"
  text="${text//MAIN/$MAIN}"
  [ -z "$SRC" ] || text="${text//SRC/$SRC}"
  echo "$text"
}

# One check per scenario or example row collects every Then.
problems=()
begin() { label="$1"; problems=(); }
expect() { problems+=("$1"); }
finish() {
  if [ "${#problems[@]}" -eq 0 ]; then pass "$label"
  else fail "$label: $(printf '%s; ' "${problems[@]}")"; fi
}
then_status() { [ "$status" = "$1" ] || expect "exit status $status, expected $1 (stderr: $err)"; }
then_line() { printf '%s\n' "$out" | grep -Fxq -- "$(fill "$1")" || expect "no line '$(fill "$1")' in '$out'"; }
then_output_is() { [ "$out" = "$1" ] || expect "output '$out', expected '$1'"; }
then_err_contains() { case "$err" in *"$(fill "$1")"*) ;; *) expect "stderr '$err' lacks '$(fill "$1")'" ;; esac; }
then_err_lacks() { case "$err" in *"$1"*) expect "stderr mentions '$1': '$err'" ;; esac; }
then_no_mention() { case "$out$err" in *"$1"*) expect "output mentions '$1'" ;; esac; }
then_no_wrapper() { [ ! -e "$MAIN/.swarmforge/bin/$1" ] || expect "wrapper $1 still exists"; }
then_listed() {
  local found=no
  printf '%s\n' "$out" | grep -Eq "^$1 +" && found=yes
  [ "$found" = "$2" ] || expect "$1 listed: $found, expected $2"
}
then_prints() { # checkout dir, command words..., expected line last
  local dir="$1" expected; expected="$(fill "${*: -1}")"
  local printed; printed="$(cd "$dir" && "${@:2:$#-2}" 2>/dev/null || true)"
  printf '%s\n' "$printed" | grep -Fxq -- "$expected" || expect "'${*:2:$#-2}' printed '$printed', expected '$expected'"
}
then_bb_received() {
  local words expected
  read -ra words <<< "$(fill "$1")"; expected="${words[*]}"
  [ "$(printf '%s\n' "$out" | paste -sd ' ' -)" = "$expected" ] || expect "bb received '$(printf '%s\n' "$out" | paste -sd ' ' -)', expected '$expected'"
}
then_bb_received_with_coverage() {
  local expected=()
  read -ra expected <<< "$(fill "$1")"
  expected+=(--coverage-command "$(fill "$2")")
  [ "$out" = "$(printf '%s\n' "${expected[@]}")" ] || expect "bb received '$out', expected '$(printf '%s\n' "${expected[@]}")'"
}

# --- Feature: sf tool wrappers -----------------------------------------------

wrappers_background() {
  a_project
  tool_source_with_tasks "mutate crap gherkin-parser"
  tool_source_namespace dry4clj.core
}

while IFS='|' read -r tool args received; do
  wrappers_background
  begin "sf tool wrappers 1: $tool"
  run_in "$MAIN" sf tool ensure "$tool"
  then_status 0
  # shellcheck disable=SC2086
  run_wrapper "$tool" $args
  then_bb_received "$received"
  finish
done <<'EOF'
clj-mutate|src/a.clj --mutate-all|--config SRC/bb.edn mutate src/a.clj --max-workers 4
crap4clj|--use-existing-coverage|--config SRC/bb.edn crap --use-existing-coverage
dry4clj|src spec|-cp SRC/src -m dry4clj.core src spec
gherkin-parser|a.feature tmp/a.json|--config SRC/bb.edn gherkin-parser a.feature tmp/a.json
EOF

wrappers_background
begin "sf tool wrappers 2"
run_in "$MAIN" sf tool ensure dry4clj
then_status 0
then_line "INSTALLED: dry4clj MAIN/.swarmforge/bin/dry4clj"
finish

wrappers_background
begin "sf tool wrappers 3"
run_in "$MAIN" sf check
then_no_mention "tools.conf"
finish

# --- Feature: sf tool entry point guard ----------------------------------------

while IFS='|' read -r tool tasks error; do
  a_project
  tool_source_with_tasks "$tasks"
  mkdir -p "$MAIN/.swarmforge/bin"
  script_prints "$MAIN" ".swarmforge/bin/$tool" stale
  begin "sf tool entry point guard 1: $tool"
  run_in "$MAIN" sf tool ensure "$tool"
  then_status 1
  then_err_contains "$error"
  then_no_wrapper "$tool"
  then_prints "$MAIN" sf tool require "$tool" "MISSING: $tool"
  finish
done <<'EOF'
clj-mutate|spec clj-mutate|sf tool: clj-mutate: SRC/bb.edn has no task 'mutate'
crap4clj|crap4clj int|sf tool: crap4clj: SRC/bb.edn has no task 'crap'
dry4clj|deintroverter|sf tool: dry4clj: SRC/src has no namespace dry4clj.core
EOF

for tool in crap4go mutate4java; do
  a_project
  tool_source_without_bb_edn
  begin "sf tool entry point guard 2: $tool"
  run_in "$MAIN" sf tool ensure "$tool"
  then_status 1
  then_err_contains "sf tool: $tool: no bb.edn in SRC"
  then_no_wrapper "$tool"
  finish
done

a_project
mkdir -p "$MAIN/.swarmforge/tools/dry4go" && echo readme > "$MAIN/.swarmforge/tools/dry4go/README.md"
begin "sf tool entry point guard 3"
run_in "$MAIN" sf tool ensure dry4go
then_status 1
then_err_contains "sf tool: dry4go: no bb.edn in MAIN/.swarmforge/tools/dry4go"
then_err_lacks "clone"
finish

# --- Feature: sf tool crap4clj coverage ----------------------------------------

coverage_background() {
  a_project
  tool_source_with_tasks crap
  (cd "$MAIN" && sf tool ensure crap4clj >/dev/null)
}

# Given clj is present|absent on PATH / deps.edn is present|absent / the directories exist
coverage_given() {
  [ "$1" = present ] && export PATH="$fake_clj:$PATH"
  [ "$2" = present ] && echo '{}' > "$MAIN/deps.edn"
  local dir; for dir in $3; do mkdir -p "$MAIN/$dir"; done
}

coverage_background
begin "sf tool crap4clj coverage 1"
cfg="$(fill CFG)"
[ -f "$cfg" ] || expect "no file $cfg"
grep -Fq 61e3cac426e9907a9dd01c37597f85c71a57ff90 "$cfg" 2>/dev/null || expect "CFG does not pin cloverage"
grep -Fq crap4clj.bb-coverage "$cfg" 2>/dev/null || expect "CFG does not mention crap4clj.bb-coverage"
finish

while IFS='|' read -r clj deps dirs args command; do
  coverage_background
  coverage_given "$clj" "$deps" "$dirs"
  begin "sf tool crap4clj coverage 2: clj $clj, deps.edn $deps, dirs $dirs, args '$args'"
  # shellcheck disable=SC2086
  run_wrapper crap4clj $args
  then_bb_received_with_coverage "--config SRC/bb.edn crap $args" "$command"
  finish
done <<'EOF'
absent|absent|src test||bb --config CFG coverage -p src -s test --lcov
absent|absent|src spec||bb --config CFG coverage -r speclj -p src -s spec --lcov
absent|absent|lib spec test|--source-root lib -s scripts|bb --config CFG coverage -r speclj -p lib -p scripts -s spec -s test --lcov
absent|present|src||bb --config CFG coverage -p src --lcov
present|absent|src test||bb --config CFG coverage -p src -s test --lcov
EOF

while IFS='|' read -r clj deps args; do
  coverage_background
  coverage_given "$clj" "$deps" "src test"
  begin "sf tool crap4clj coverage 3: clj $clj, deps.edn $deps, args '$args'"
  # shellcheck disable=SC2086
  run_wrapper crap4clj $args
  then_bb_received "--config SRC/bb.edn crap $args"
  finish
done <<'EOF'
present|present|
absent|absent|--coverage-command make-lcov
absent|absent|--use-existing-coverage
EOF

# --- Feature: sf tool catalogue location -----------------------------------------

catalogue_background() {
  a_project
  a_task_worktree
  echo "probe local swarmforge/bin/probe - - -" >> "$WT/swarmforge/tools.conf"
  git -C "$WT" commit -q -am "add probe"
  script_prints "$WT" swarmforge/bin/probe probe
}

while IFS='|' read -r checkout listed; do
  catalogue_background
  begin "sf tool catalogue location 1: $checkout"
  run_in "$(checkout_dir "$checkout")" sf tool list
  then_status 0
  then_listed probe "$listed"
  finish
done <<'EOF'
task worktree|yes
main checkout|no
EOF

catalogue_background
begin "sf tool catalogue location 2"
run_in "$WT" sf tool ensure probe
then_status 0
then_line "INSTALLED: probe MAIN/.swarmforge/bin/probe"
then_prints "$WT" sf tool require probe "OK: probe MAIN/.swarmforge/bin/probe"
finish

catalogue_background
begin "sf tool catalogue location 3"
run_in "$MAIN" sf tool ensure probe
then_status 1
then_err_contains "unknown tool 'probe'"
finish

while IFS='|' read -r place printed; do
  catalogue_background
  script_prints "$MAIN" swarmforge/bin/sf-cs main
  script_prints "$WT" swarmforge/bin/sf-cs task
  a_second_task_worktree_without swarmforge/bin/sf-cs
  (cd "$MAIN" && sf tool ensure sf-cs >/dev/null)
  begin "sf tool catalogue location 4: $place"
  run_in "$(checkout_dir "$place")" "$MAIN/.swarmforge/bin/sf-cs"
  then_output_is "$printed"
  finish
done <<'EOF'
task worktree|task
main checkout|main
second task worktree|main
directory outside git|main
EOF

if [ "$failures" -gt 0 ]; then echo "$failures failure(s)" >&2; exit 1; fi
echo "all tests passed"
