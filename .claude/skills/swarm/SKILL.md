---
name: swarm
description: Run the SwarmForge pipeline on a feature request. Use when the operator asks to swarm, specify, or forge a feature, or to check or resume a swarm task.
---

Act as the SwarmForge orchestrator. Read `swarmforge/roles/orchestrator.md`
and follow it for the request below.

Forms:
- `/swarm <feature request>`: create a task and start the pipeline.
- `/swarm status`: print `sf ls` and explain each row.
- `/swarm resume <id>`: continue a task from its current stage.
- `/swarm approve <id>`, `/swarm reject <id> <reason>`, `/swarm answer <id> <text>`: operator decisions.

Request: $ARGUMENTS
