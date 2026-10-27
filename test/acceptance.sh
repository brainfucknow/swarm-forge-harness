#!/usr/bin/env bash
# Runs the Gherkin acceptance features (all under test/features, or the ones
# named): gherkin-parser, the entrypoint generator, then the generated tests.
# Output goes to ./tmp/acceptance/.
set -euo pipefail

HERE="$(cd "$(dirname "$0")/.." && pwd)"
cd "$HERE"
command -v git-bug >/dev/null || { echo "git-bug not found on PATH" >&2; exit 1; }
export PATH="$(swarmforge/bin/sf tool bin):$PATH"
command -v gherkin-parser >/dev/null || { echo "gherkin-parser missing; run: swarmforge/bin/sf tool ensure gherkin-parser" >&2; exit 1; }

out=tmp/acceptance
rm -rf "$out/ir" "$out/generated"
mkdir -p "$out/ir" "$out/generated"
if [ "$#" -gt 0 ]; then features=("$@"); else mapfile -t features < <(find test/features -name '*.feature' | sort); fi

for feature in "${features[@]}"; do
  stem="$(echo "${feature%.feature}" | sed 's#^test/features/##; s#/#-#g')"
  gherkin-parser "$feature" "$out/ir/$stem.json"
  ACCEPTANCE_FEATURE="$feature" bb --config test/acceptance/bb.edn -m acceptance.generate "$out/ir/$stem.json" "$out/generated"
done

failed=0
for test in "$out"/generated/*_acceptance.clj; do
  echo "== $test"
  bb --config test/acceptance/bb.edn "$test" || failed=1
done
[ "$failed" -eq 0 ] && echo "all acceptance tests passed"
exit "$failed"
