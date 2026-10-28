feature=
previous=
for arg in "$@"; do
  [ "$previous" = --feature ] && feature="$arg"
  previous="$arg"
done
block() { [ -z "$feature" ] || bb -cp {{src}} -m sf.gherkin-cache "$1" "$feature"; }
block restore || exit 1
trap 'block stash' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
{{command}}
status=$?
trap - EXIT INT TERM
block stash || exit 1
exit "$status"
