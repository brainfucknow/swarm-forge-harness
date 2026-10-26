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

stale=0
for round in $(seq 1 "$rounds"); do
  label="probe:$round"
  before_ref="$(git rev-parse "$ref")"
  git-bug bug label new "$full" "$label" >/dev/null
  after_file="$(cat ".git/$ref" 2>/dev/null || echo missing)"
  after_git="$(git rev-parse "$ref")"
  seen="$(git-bug bug show "$full" --field labels | grep -cx "$label" || true)"
  if [ "$seen" = 1 ]; then
    echo "round $round: visible at once ($before_ref -> $after_git)"
  else
    stale=$((stale + 1))
    later="$(sleep 1; git-bug bug show "$full" --field labels | grep -cx "$label" || true)"
    echo "round $round: NOT visible; ref file=$after_file git=$after_git before=$before_ref; visible after 1s: $later"
    echo "  for-each-ref: $(git for-each-ref "$ref" | tr '\t' ' ')"
    echo "  packed-refs: $(grep -c "$full" .git/packed-refs 2>/dev/null || echo none)"
    echo "  lock: $(cat .git/git-bug/lock 2>/dev/null || echo none)"
    echo "  commits on ref: $(git rev-list --count "$ref")"
  fi
done
echo "stale reads: $stale / $rounds"
