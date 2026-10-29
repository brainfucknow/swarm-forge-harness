---
id: slim-runtime-installation-and-d77462a
created: 2026-10-29
stage: done
route: coder,architect,qa
---

# Slim runtime installation and feature documentation

Use a team to fix the recommended cleanup: stop copying harness development tests, properties, fixtures and self-acceptance assets into consumer projects, while retaining those files in this repository. Preserve all supported runtime entrypoints, source/resources, required runtime configurations, roles and guidance. Verify the filtered install through real public commands in a scratch Git project and do not blindly prune pre-existing consumer content. Also trim historical task provenance and obsolete decisions from feature READMEs, preserving current behavior and step definitions; update metrics-cache retry/warning wording to implemented behavior. Keep task history and harness integrations; do not retire the compatibility sf-tool entrypoint. Use existing tests, no new QA recipe or framework, and keep process detail in commits or ignored scratch.

## Records

```text
type: created
from: operator
branch: swarm/slim-runtime-installation-and-d77462a
commit: dbf0134
route: coder,architect,qa
```

```text
type: handoff
from: coder
to: architect
branch: swarm/slim-runtime-installation-and-d77462a
commit: 6e85187
files: README.md,install.sh,swarmforge/languages/javascript.md,swarmforge/tools/javascript/features/README.md,test/features/lore_trailers/README.md,test/features/manifest_guard/README.md,test/features/metrics_cache/README.md,test/features/sf_tool/README.md,test/features/task_store/README.md,test/sf_tool_test.sh
summary: Install runtime-only assets with regression coverage; retain source tests and current behavior docs
```

```text
type: handoff
from: architect
to: qa
branch: swarm/slim-runtime-installation-and-d77462a
commit: 79a8e96
files: -
summary: Reviewed complete runtime payload, preservation and current docs; no architecture changes needed.
```

```text
type: handoff
from: qa
to: done
branch: swarm/slim-runtime-installation-and-d77462a
commit: 43ca21f
files: -
summary: Verified installed lifecycle and consumer preservation; runtime payload complete and focused evidence passes.
```
