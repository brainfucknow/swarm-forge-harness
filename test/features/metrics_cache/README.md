# Metrics cache acceptance features

Contracts for the mutation cache described in the
[handoff protocol](../../../swarmforge/handoff-protocol.md#sharing):

| Feature | Coverage |
|---|---|
| `metrics_load.feature` | Loading missing cache files into task worktrees. |
| `metrics_save.feature` | Saving manifests at handoff and approval. |
| `gherkin_block.feature` | Keeping mutator blocks outside tracked feature files. |
| `install_ignore.feature` | Ignoring `.metrics/` on installation. |

Run `bash test/acceptance.sh` from the repository root. Shared fixtures and
placeholders follow the [task-store conventions](../task_store/README.md);
`M1` names a metrics-branch tip. Cache-specific operations are in the
[metrics step handlers](../../acceptance/src/acceptance/metrics_steps.clj).

The fixtures use real Git and `sf`; only gherkin-mutator is faked. Its fake
records the feature it received, writes a supplied manifest block, and
returns a chosen status. This checks wrapper transport and failure handling,
not real mutation behavior.
