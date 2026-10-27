#!/usr/bin/env bash
# Gherkin acceptance mutation of the mutation-manifest features, one feature at
# a time: parse, generate the entry points, then run gherkin-mutator with the
# persistent runner adapter.
#
#   test/acceptance/manifests/mutate.sh [--level soft|hard] [feature ...]
#
# GHERKIN_MUTATOR overrides the gherkin-mutator command.
set -euo pipefail

root="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$root"
sf=swarmforge/bin/sf
bin="$($sf tool bin)"
parser="$bin/gherkin-parser"
mutator="${GHERKIN_MUTATOR:-$bin/gherkin-mutator}"
[ -x "$parser" ] || $sf tool ensure gherkin-parser >/dev/null
[ -x "$mutator" ] || $sf tool ensure gherkin-mutator >/dev/null

level=hard
if [ "${1:-}" = --level ]; then level="$2"; shift 2; fi
if [ $# -eq 0 ]; then set -- test/features/mutation_manifests/*.feature; fi

failed=0
for feature in "$@"; do
  stem="${feature%.feature}"
  ir="tmp/acceptance/manifests/ir/$stem.json"
  work="tmp/acceptance/mutation/$stem"
  rm -rf "$work"
  mkdir -p "$(dirname "$ir")" "$work"
  "$parser" "$feature" "$ir"
  bb test/acceptance/manifests/generate.clj "$ir" "$work/generated"
  "$mutator" --feature "$feature" --work-dir "$work" --generated-dir "$work/generated" \
    --runner-worker "bb test/acceptance/manifests/runner.clj" \
    --level "$level" --workers 4 --timeout 3600s --status-interval 30s || failed=1
done
exit "$failed"
