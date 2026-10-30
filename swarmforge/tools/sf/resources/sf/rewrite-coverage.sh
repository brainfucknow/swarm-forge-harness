plan="$({{bb}} --config /dev/null -cp {{src}} -m sf.coverage-rewrite {{config}} "$@")" || exit 1
eval "$plan"
