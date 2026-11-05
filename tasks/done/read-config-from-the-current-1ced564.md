---
id: read-config-from-the-current-1ced564
created: 2026-11-05
stage: done
route: specifier,coder,cleaner,qa
---

# Read config from the current checkout

sf reads swarmforge/*.conf (roles.conf, routes.conf, tools.conf, adapters, verify.conf) from the main checkout, not the checkout sf is run in. So 'sf check', 'sf routes', 'sf adapters' and 'sf tool list' run inside a task worktree show main's config, not the config the task changed. This hit PRs #53, #58 and #62, where roles validated a config change against main. Operator decision: fix it in sf with a small change so these read-only catalogue/config commands read the current checkout's config. Task bookkeeping (task store, branches, finish) keeps working as today. Keep the change and any docs short; test sf behavior in test/sf_test.sh, not prompt text.

## Records

```text
type: created
from: operator
branch: swarm/read-config-from-the-current-1ced564
commit: d9e0ab5
route: specifier,coder,cleaner,qa
```

```text
type: review
from: specifier
commit: 8428339
files: -
summary: spec: check, roles, routes, adapters follow the current checkout; bookkeeping keeps main
```

```text
type: approval
from: operator
```

```text
type: handoff
from: specifier
to: coder
branch: swarm/read-config-from-the-current-1ced564
commit: 8428339
files: -
summary: specifier work complete
```

```text
type: handoff
from: coder
to: cleaner
branch: swarm/read-config-from-the-current-1ced564
commit: 6020854
files: swarmforge/README.md,swarmforge/tools/sf/spec/sf/repo_support.clj,swarmforge/tools/sf/src/sf/adapters.clj,swarmforge/tools/sf/src/sf/cli.clj,swarmforge/tools/sf/src/sf/config.clj,test/sf_test.sh
summary: check, roles, routes, adapters read the current checkout; bookkeeping keeps main
```

```text
type: handoff
from: cleaner
to: qa
branch: swarm/read-config-from-the-current-1ced564
commit: 2d573af
files: swarmforge/README.md,swarmforge/tools/sf/src/sf/cli.clj
summary: Trim task-added docs; code unchanged
```

```text
type: handoff
from: qa
to: done
branch: swarm/read-config-from-the-current-1ced564
commit: 7eb0cb9
files: swarmforge/README.md
summary: QA passed all criteria; README names the checkout-following commands
```
