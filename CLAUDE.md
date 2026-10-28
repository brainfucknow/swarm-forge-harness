@AGENTS.md

In Claude Code, `/swarm <feature request>` starts the pipeline. The subagents
in `.claude/agents/` carry their model tier (`inherit` for the specifier,
`opus` for strong roles, `sonnet` for fast roles); keep those settings when
delegating. After the operator answers a specifier question, continue the
same specifier agent rather than starting a new one.
