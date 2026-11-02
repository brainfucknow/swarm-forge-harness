# sf tool acceptance features

Executable contracts for tool installation, shared wrappers, entrypoint
guards, coverage paths, and catalogue selection. Run them with
`bash test/acceptance.sh` from the repository root; see the
[task-store runner conventions](../task_store/README.md) and
[tool step handlers](../../acceptance/src/acceptance/steps.clj).

## Fixtures

`sf` runs with real Babashka in scratch Git repositories. Generated wrappers
run a fake `bb` that records arguments and working directory, then exits 0.
`SWARMFORGE_TOOL_SRC` supplies disposable tool sources; the no-override
fixture disables interactive Git credential prompts. Snapshots compare contents, permissions, and directory membership for the
paths a scenario explicitly requires to remain unchanged.

| Placeholder | Meaning |
|---|---|
| `MAIN` | Scratch project's main checkout. |
| `SRC` | `SWARMFORGE_TOOL_SRC`, else `MAIN/.swarmforge/tools/<repo>`. |
| `CFG` | `MAIN/.swarmforge/tools/crap4clj-coverage/bb.edn`. |

Fake-tool tests do not establish upstream compatibility. Use the
[Clojure tool compatibility checks](../../../swarmforge/languages/clojure.md#tool-compatibility-checks)
when changing wrappers or pinned versions.
