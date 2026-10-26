args=()
while [ $# -gt 0 ]; do
  case "$1" in
    --level)
      if [ "${2:-}" = full ]; then args+=(--level hard); else args+=("$1" "${2:-}"); fi
      shift; [ $# -gt 0 ] && shift ;;
    --workers) shift; [ $# -gt 0 ] && shift ;;
    *) args+=("$1"); shift ;;
  esac
done
args+=(--workers 4)
set -- "${args[@]}"
