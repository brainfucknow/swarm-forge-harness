feature=
previous=
for arg in "$@"; do
  [ "$previous" = --feature ] && feature="$arg"
  previous="$arg"
done
if [ -n "$feature" ]; then {{sf}} manifest block-in "$feature" || exit 1; fi
{{command}}
status=$?
if [ -n "$feature" ]; then {{sf}} manifest block-out "$feature" || exit 1; fi
exit "$status"
