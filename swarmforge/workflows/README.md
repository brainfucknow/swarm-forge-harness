# SwarmForge on Mistral Workflows

This project serves the SwarmForge pipeline on the Mistral platform for
sessions that have no subagent backend: no `claude`, `codex` or `copilot` CLI
on PATH, so `sf dispatch` would otherwise refuse with "no backend found"
(issue #114).

Two workflows:

| Workflow | Execution | Purpose |
|---|---|---|
| `swarmforge-pipeline` | one per task | runs the task's stages until done or blocked |
| `swarmforge-role` | one per role or gate | what `sf dispatch <role> <id> mistral` starts |

One workflow execution per task, one Agents SDK agent per role. The agent's
model is the role's tier mapped through the `vibe` column of
`swarmforge/tiers.conf`. All task bookkeeping goes through
`swarmforge/bin/sf` on the task branch: the worker clones the repository,
fetches `swarm/<task-id>`, works in an `sf start` worktree, and pushes the
branch when the execution ends. Gate roles and the spec-judge run as workflow
steps that record nothing (read-only agent tools plus a before/after snapshot
check); the execution result carries their reports and the spec verdict so
the operator's session can show them and record the verdict with
`sf spec-verdict`.

## sf side

`sf dispatch <role> <id> mistral` and `sf run <id> mistral` call
`swarmforge/bin/sf-mistral`, which pushes the local task branch, starts the
execution through the REST API (`POST /v1/workflows/<name>/execute`), polls
`GET /v1/workflows/executions/<id>` until it ends, and fetches the branch
back. Exit 0 on done/advanced, 2 on blocked, 1 on failure. `sf run` with the
mistral backend starts one `swarmforge-pipeline` execution per call.

The mistral backend is detected when no CLI backend is on PATH and
`MISTRAL_API_KEY` is set, or with `SWARMFORGE_BACKEND=mistral`. It needs a
git remote: the worker clones that URL. Environment:

| Variable | Default |
|---|---|
| `MISTRAL_API_KEY` | required |
| `MISTRAL_API_URL` | `https://api.mistral.ai` |
| `SWARMFORGE_REMOTE` | `origin` |
| `SWARMFORGE_MISTRAL_ROLE_WORKFLOW` | `swarmforge-role` |
| `SWARMFORGE_MISTRAL_PIPELINE_WORKFLOW` | `swarmforge-pipeline` |
| `SWARMFORGE_MISTRAL_TIMEOUT_SECONDS` | `3600` |

## Run the worker

```sh
uv sync
MISTRAL_API_KEY=... uv run python -m swarmforge_pipeline.worker
```

Deploy it as a managed workflow deployment so executions run without a local
worker (see the Mistral Workflows documentation). The `Dockerfile` builds the
worker image: Python, git, and Babashka, which `sf` needs.

## Tests

```sh
uv run pytest
```

The workflow tests call the workflow's `run` method directly with the
activities replaced by fakes, so no git, sf, model, or network runs.
