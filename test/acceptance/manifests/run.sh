#!/usr/bin/env bash
# Runs the mutation-manifest acceptance features: gherkin-parser, the
# entrypoint generator, then the generated tests.
#
#   test/acceptance/manifests/run.sh [feature ...]
set -euo pipefail

root="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$root"
sf=swarmforge/bin/sf
parser="$($sf tool bin)/gherkin-parser"
[ -x "$parser" ] || $sf tool ensure gherkin-parser >/dev/null

out=tmp/acceptance/manifests
rm -rf "$out"
mkdir -p "$out/ir" "$out/generated"

if [ $# -eq 0 ]; then set -- test/features/mutation_manifests/*.feature; fi
for feature in "$@"; do
  ir="$out/ir/${feature%.feature}.json"
  mkdir -p "$(dirname "$ir")"
  "$parser" "$feature" "$ir"
  bb test/acceptance/manifests/generate.clj "$ir" "$out/generated"
done

failed=0
for test in "$out"/generated/*_test.clj; do
  bb -cp test "$test" || failed=1
done
exit "$failed"
