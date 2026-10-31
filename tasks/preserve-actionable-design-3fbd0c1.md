---
id: preserve-actionable-design-3fbd0c1
created: 2026-10-31
---

# Preserve actionable design review in handoff

Address the valid review comment on PR #49: https://github.com/brainfucknow/swarm-forge-harness/pull/49#discussion_r4433067403

sf start prints task records and Lore trailers, not ordinary role commit bodies; downstream roles are instructed to use the printed information. Tighten architecture-design-reviewer handoff instructions so every actionable finding, recommended correction, constraint, and smallest proposed approach needed by the coder is carried in Directive, Constraint or Rejected Lore trailers. Record the design review/rationale in the commit without a separate planning/review report. Keep the change lean; do not test prompt wording or introduce new review state. Preserve routes, tier/provider settings, existing architect behavior and generated adapters. Use existing behavioral verification to confirm actionable review content survives sf start (add only if existing coverage is insufficient).

Use route coder → architect → QA. Work in the task worktree, record rationale and verification in commit bodies, no review/QA Markdown or completed-task archives in the final PR. Use Codex <codex@openai.com> as author and committer through per-command settings; never modify human/global Git configuration. Final action is updating existing PR #49, not merging upstream.
