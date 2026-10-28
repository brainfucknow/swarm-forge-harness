#!/usr/bin/env bash
# Behavioral tests for test/no_manifests.sh in a scratch repository.
set -euo pipefail

HERE="$(cd "$(dirname "$0")/.." && pwd)"

failures=0
pass() { echo "ok   $1"; }
fail() { echo "FAIL $1" >&2; failures=$((failures + 1)); }
assert_eq() { if [ "$2" = "$3" ]; then pass "$1"; else fail "$1: expected '$3', got '$2'"; fi; }

scratch="$(mktemp -d)"
trap 'rm -rf "$scratch"' EXIT
export HOME="$scratch/home"; mkdir -p "$HOME"
git config --global user.name tester
git config --global user.email tester@example.com

repo="$scratch/project"
mkdir -p "$repo/features" && cd "$repo"
git init -q .
echo ".metrics/" > .gitignore
echo "Feature: x" > features/x.feature
git add -A && git commit -q -m seed

# guard [dir]: runs the guard and sets status, out and err.
guard() {
  status=0
  (cd "${1:-.}" && bash "$HERE/test/no_manifests.sh") >"$scratch/out" 2>"$scratch/err" || status=$?
  out="$(cat "$scratch/out")"; err="$(cat "$scratch/err")"
}

guard
assert_eq "a clean repository passes" "$status:$out:$err" "0::"

printf '# mutation-stamp: sha256=ab\n\nFeature: x\n' > features/x.feature
guard
assert_eq "an unstaged block passes" "$status:$err" "0:"

git add features/x.feature
guard features
assert_eq "a staged block fails from a subdirectory" "$status" "1"
assert_eq "a staged block is named from the root" "$err" "mutation manifest block in a tracked feature file: features/x.feature"
assert_eq "a block report leaves stdout empty" "$out" ""

printf 'Feature: x\n  Given # acceptance-mutation-manifest-begin\n' > features/x.feature
printf '# acceptance-mutation-manifest-begin\n' > notes.md
git add -A && git commit -q -m "quoted markers"
guard
assert_eq "markers inside a line or outside .feature files pass" "$status:$err" "0:"

printf 'Feature: z\n# acceptance-mutation-manifest-begin\n' > z.feature
mkdir -p .metrics tools/.metrics metrics && echo x > .metrics/a.edn && echo x > tools/.metrics/b.edn && echo x > metrics/c.edn
git add -f z.feature .metrics tools metrics
guard
assert_eq "every manifest is reported, blocks first" "$status:$err" "1:mutation manifest block in a tracked feature file: z.feature
tracked file under .metrics/: .metrics/a.edn
tracked file under .metrics/: tools/.metrics/b.edn"

echo
if [ "$failures" -eq 0 ]; then echo "all no_manifests tests passed"; else echo "$failures failure(s)" >&2; exit 1; fi
