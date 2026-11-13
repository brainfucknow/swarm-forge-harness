@AGENTS.md

In Claude Code, `/swarm <feature request>` starts the pipeline. The subagents
in `.claude/agents/` carry the model settings generated from
`swarmforge/tiers.conf`; keep those settings when delegating. After the operator
answers a specifier question, continue the same specifier agent rather than
starting a new one.
