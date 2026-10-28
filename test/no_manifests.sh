#!/usr/bin/env bash
# Fails when the git index holds a mutation manifest: a gherkin-mutator block
# in a tracked .feature file, or a tracked file under a .metrics/ directory.
# Needs only bash and git, so CI runs it before installing anything else.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

blocks="$(git grep --cached -l -E '^# (mutation-stamp:|acceptance-mutation-manifest-begin)' -- '*.feature')" || [ $? -eq 1 ]
metrics="$(git ls-files -- ':(glob)**/.metrics/**')"

found=0
report() {
  local prefix="$1" path
  while IFS= read -r path; do
    [ -n "$path" ] || continue
    echo "$prefix$path" >&2
    found=1
  done
}
report "mutation manifest block in a tracked feature file: " <<<"$blocks"
report "tracked file under .metrics/: " <<<"$metrics"
exit "$found"
