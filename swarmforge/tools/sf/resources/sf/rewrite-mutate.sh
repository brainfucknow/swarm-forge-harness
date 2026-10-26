args=()
scan=
while [ $# -gt 0 ]; do
  case "$1" in
    --mutate-all) shift ;;
    --scan|--update-manifest) scan=1; args+=("$1"); shift ;;
    --max-workers) shift; [ $# -gt 0 ] && shift ;;
    *) args+=("$1"); shift ;;
  esac
done
[ -n "$scan" ] || args+=(--max-workers 4)
set -- "${args[@]}"
