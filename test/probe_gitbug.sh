#!/usr/bin/env bash
# Probe: is a label written by git-bug visible to the very next git-bug
# process? Prints one line per round, and the raw ref and git views when not.
set -euo pipefail

rounds="${1:-30}"
scratch="$(mktemp -d)"
trap 'rm -rf "$scratch"' EXIT
export HOME="$scratch/home"; mkdir -p "$HOME"
git config --global user.name tester
git config --global user.email tester@example.com
git config --global init.defaultBranch main

cd "$scratch" && mkdir repo && cd repo
git init -q .
git commit -q --allow-empty -m seed
git-bug user new --non-interactive -n op -e op@example.com >/dev/null
full="$(git-bug bug new --non-interactive -t probe -m probe 2>/dev/null | awk '{print $1}')"
full="$(git-bug bug show "$full" --field id)"
ref="refs/bugs/$full"

# Each round adds a label, reads, removes it, reads again, and re-adds it the
# way `sf reject` then `sf review` do, checking every read against the write
# that preceded it.
stale=0
check() {
  local round="$1" step="$2" label="$3" expect="$4"
  local seen later
  seen="$(git-bug bug show "$full" --field labels | grep -cx "$label" || true)"
  if [ "$seen" = "$expect" ]; then
    echo "round $round $step: ok"
    return 0
  fi
  stale=$((stale + 1))
  later="$(sleep 1; git-bug bug show "$full" --field labels | grep -cx "$label" || true)"
  echo "round $round $step: STALE (expected $expect, read $seen; after 1s: $later) ref=$(git rev-parse "$ref")"
  echo "  file: $(cat ".git/$ref" 2>/dev/null || echo missing)"
  echo "  for-each-ref: $(git for-each-ref "$ref" | tr '\t' ' ')"
  echo "  lock: $(cat .git/git-bug/lock 2>/dev/null || echo none)"
  echo "  commits on ref: $(git rev-list --count "$ref")"
}
for round in $(seq 1 "$rounds"); do
  label="probe:$round"
  git-bug bug label new "$full" "$label" >/dev/null
  check "$round" add "$label" 1
  git-bug bug label rm "$full" "$label" >/dev/null
  check "$round" rm "$label" 0
  git-bug bug label new "$full" "$label" >/dev/null
  check "$round" re-add "$label" 1
done
echo "stale reads: $stale / $((rounds * 3))"
