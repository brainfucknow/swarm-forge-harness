---
id: read-config-from-the-current-1ced564
created: 2026-11-05
---

# Read config from the current checkout

sf reads swarmforge/*.conf (roles.conf, routes.conf, tools.conf, adapters, verify.conf) from the main checkout, not the checkout sf is run in. So 'sf check', 'sf routes', 'sf adapters' and 'sf tool list' run inside a task worktree show main's config, not the config the task changed. This hit PRs #53, #58 and #62, where roles validated a config change against main. Operator decision: fix it in sf with a small change so these read-only catalogue/config commands read the current checkout's config. Task bookkeeping (task store, branches, finish) keeps working as today. Keep the change and any docs short; test sf behavior in test/sf_test.sh, not prompt text.
