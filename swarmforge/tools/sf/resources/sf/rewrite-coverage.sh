settled=
if command -v clj >/dev/null 2>&1 && [ -f deps.edn ]; then settled=1; fi
args=("$@")
roots=()
k=0
while [ "$k" -lt "${#args[@]}" ]; do
  case "${args[$k]}" in
    --source-root|-s) [ -z "${args[$((k + 1))]:-}" ] || roots+=("${args[$((k + 1))]}"); k=$((k + 2)) ;;
    --lcov) k=$((k + 2)) ;;
    --coverage-command|--use-existing-coverage) settled=1; k=$((k + 1)) ;;
    *) k=$((k + 1)) ;;
  esac
done
if [ -z "$settled" ]; then
  [ "${#roots[@]}" -gt 0 ] || roots=(src)
  command="bb --config $cfg coverage"
  [ -d spec ] && command="$command -r speclj"
  for root in "${roots[@]}"; do command="$command -p $root"; done
  for tests in spec test; do [ -d "$tests" ] && command="$command -s $tests"; done
  set -- "$@" --coverage-command "$command --lcov"
fi
