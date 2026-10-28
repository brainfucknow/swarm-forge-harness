---
id: keep-sf-tool-ensure-from-ff06da1
created: 2026-10-28
---

# Keep sf tool ensure from writing a broken wrapper

A task worktree whose branch predates main's tools.conf ran sf tool ensure and replaced the shared wrappers in .swarmforge/bin with broken ones (unknown kind 'bbmain', unknown rewrite 'coverage'). It happened twice. ensure must check the kind and rewrite before it writes anything, and never replace a working wrapper with one it cannot build.
