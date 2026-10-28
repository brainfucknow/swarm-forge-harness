feature=
previous=
for arg in "$@"; do
  [ "$previous" = --feature ] && feature="$arg"
  previous="$arg"
done
block() { [ -z "$feature" ] || bb -cp {{src}} -m sf.gherkin-cache "$1" "$feature"; }
block restore || exit 1
{{command}}
status=$?
block stash || exit 1
exit "$status"
