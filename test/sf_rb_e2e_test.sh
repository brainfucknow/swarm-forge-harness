#!/usr/bin/env bash
# End-to-end check of sf-rb against the real Ruby gems, on the small project in
# test/ruby_project. Needs Ruby 3.3 with Bundler, Babashka, Node, and
# rubygems.org. Never runs mutant itself: it needs a licence usage.
set -euo pipefail

HERE="$(cd "$(dirname "$0")/.." && pwd)"
SF_RB="$HERE/swarmforge/bin/sf-rb"

failures=0
pass() { echo "ok   $1"; }
fail() { echo "FAIL $1" >&2; failures=$((failures + 1)); }
assert_match() { if echo "$2" | grep -Eq "$3"; then pass "$1"; else fail "$1: '$2' does not match /$3/"; fi; }
assert_status() { if [ "$2" -eq "$3" ]; then pass "$1"; else fail "$1: expected exit $3, got $2"; fi; }

scratch="$(mktemp -d)"
trap 'rm -rf "$scratch"' EXIT
export HOME="$scratch/home"; mkdir -p "$HOME"
export GIT_CONFIG_GLOBAL="$HOME/.gitconfig"
git config --global user.name tester
git config --global user.email tester@example.com
git config --global init.defaultBranch main
# Gems install into the scratch project, so a run never writes outside it.
export BUNDLE_PATH="$scratch/gems" BUNDLE_FROZEN=true

project="$scratch/project"
mkdir -p "$project"
cd "$project"
git init -q .
cp "$HERE/test/ruby_project/Gemfile" "$HERE/test/ruby_project/Gemfile.lock" "$HERE/test/ruby_project/.gitignore" .
git add -A && git commit -q -m base
git checkout -q -b work
cp -R "$HERE/test/ruby_project/lib" "$HERE/test/ruby_project/spec" "$HERE/test/ruby_project/.mutant.yml" .
git add -A && git commit -q -m work
export SWARMFORGE_BASE=main

run() { # sets $out and $status
  status=0
  out="$("$@" 2>&1)" || status=$?
  echo "$out" | sed 's/^/     /'
}

run bundle install
assert_status "bundle install" "$status" 0

run "$SF_RB" coverage
assert_status "coverage runs the unit specs, not the property specs" "$status" 0
assert_match "coverage reports where it wrote" "$out" "coverage written to tmp/coverage/coverage.json"
test -s tmp/coverage/coverage.json && pass "SimpleCov JSON exists" || fail "SimpleCov JSON missing"

# The two long shipping methods score 19 under flog, inside its limit of 20.
for complexity in cyclomatic perceived flog; do
  run "$SF_RB" crap --complexity "$complexity"
  assert_status "crap --complexity $complexity" "$status" 0
  for method in letter passing light_cost heavy_cost; do
    assert_match "crap --complexity $complexity scores $method" "$out" "$method"
  done
done
assert_match "crap --complexity flog names its limit" "$out" "over CRAP 20"

for tool in jscpd flay; do
  run "$SF_RB" dry --tool "$tool"
  assert_status "dry --tool $tool finds the duplicate" "$status" 1
  assert_match "dry --tool $tool names both clones" "$out" "shipping\\.rb.*shipping\\.rb"
done

run "$SF_RB" mutate
assert_status "mutate without a mutant usage" "$status" 2
assert_match "mutate says mutation is optional" "$out" "mutation is optional"

[ "$failures" -eq 0 ] && echo "all sf-rb end-to-end checks passed"
exit "$failures"
