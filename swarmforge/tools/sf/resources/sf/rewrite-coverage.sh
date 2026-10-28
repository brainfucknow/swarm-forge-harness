absolute() {
  local path parts part out=
  case "$1" in /*) path="$1" ;; *) path="$here/$1" ;; esac
  IFS=/ read -ra parts <<< "$path"
  for part in "${parts[@]}"; do
    case "$part" in
      ''|.) ;;
      ..) out="${out%/*}" ;;
      *) out="$out/$part" ;;
    esac
  done
  printf '%s' "$out"
}
physical() {
  local path dir
  path="$(absolute "$1")"
  if [ -d "$path" ]; then (cd "$path" && pwd -P)
  elif [ -d "${path%/*}" ]; then dir="$(cd "${path%/*}" && pwd -P)"; printf '%s/%s' "${dir%/}" "${path##*/}"
  else printf '%s' "$path"; fi
}
relative() {
  local base="$2" up= rest
  while [ "$1" != "$base" ] && [ "${1#"$base"/}" = "$1" ]; do base="${base%/*}"; up="../$up"; done
  rest="${1#"$base"}"; rest="$up${rest#/}"; rest="${rest%/}"
  printf '%s' "${rest:-.}"
}
here="$(pwd -P)"
args=("$@")
dir=
k=0
while [ "$k" -lt "${#args[@]}" ]; do
  case "${args[$k]}" in
    --source-root|-s)
      root="${args[$((k + 1))]:-}"
      if [ -n "$root" ]; then
        root="$(physical "$root")"
        if [ -z "$dir" ]; then
          dir="${root%/*}"
          cd "${dir:-/}" 2>/dev/null || { echo "${0##*/}: no such directory: ${dir:-/}" >&2; exit 1; }
        fi
        args[$((k + 1))]="$(relative "$root" "$dir")"
      fi
      k=$((k + 2)) ;;
    --lcov) k=$((k + 2)) ;;
    *) k=$((k + 1)) ;;
  esac
done
set -- "${args[@]}"
settled=
if command -v clj >/dev/null 2>&1 && [ -f deps.edn ]; then settled=1; fi
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
