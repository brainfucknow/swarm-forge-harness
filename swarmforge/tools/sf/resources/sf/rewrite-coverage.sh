plan="$({{bb}} -cp {{src}} -m sf.coverage-rewrite {{config}} "$@")" || exit 1
eval "$plan"
