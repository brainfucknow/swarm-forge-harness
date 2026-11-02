---
id: have-the-cleaner-trim-task-5b6012c
created: 2026-11-02
---

# Have the cleaner trim task-added Markdown

Operator request: PRs #52 and #53 added wordy Markdown (languages/fsharp.md, engineering.md, clojure.md, the tools.conf header, feature READMEs and Feature descriptions) and no role trimmed it. The cleaner prompt (swarmforge/roles/cleaner.md) only covers task-added comments and API docs in code and tests. Extend the cleaner to task-added prose in Markdown and other documentation (language guides, constitution articles, protocol docs, READMEs, Feature descriptions, conf-file header comments, usage text) with the same rules it applies to comments: cut restatements, repetition and narration; keep non-obvious rules, contracts and constraints concise; leave pre-existing text unchanged; report task-added doc lines before -> after. It must not change scenario steps, step meanings that step definitions rely on, or behaviour. Keep the change small and language-neutral; do not test prompt text. Also consider that tasks on routes without a cleaner (e.g. coder,qa) then get no trim; the specifier should ask whether anything should change there.
