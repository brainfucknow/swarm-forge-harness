---
id: add-optional-architecture-83d14d7
created: 2026-10-31
---

# Add optional architecture design reviewer

Implement an optional role named “Architecture design reviewer”, using the identifier architecture-design-reviewer.

Behavior:
- Position the role after specifier and before coder.
- Use it for migrations, public API changes and major dependency changes.
- Review approved criteria, existing code and the proposed approach. If no approach exists, outline the smallest suitable approach in the review commit; do not require a separate planning document.
- Check compatibility, dependency direction, migration/rollback needs and unnecessary complexity.
- Tie findings to concrete requirements, interfaces, dependencies or migration steps, with their consequences and recommended corrections.
- Keep the existing architect responsible for structural review after implementation.
- Use existing workflow mechanisms for questions and blockers. Do not introduce another approval or review-state subsystem.

Integration:
- Make the role genuinely opt-in. Preserve existing named routes and default/fallback workflows unless the role is explicitly enabled.
- Reuse the existing catalogue, routing and adapter generator. Add only the minimum generic support needed for optional roles.
- Use the existing strong model tier; preserve provider model settings.
- Generate adapters for Claude, Codex and Copilot.
- Verify where the generator writes before using it in a linked worktree; use an isolated normal Git repository if necessary.
- Add concise opt-in guidance to an existing reference.

Verification:
- Use existing behavioral tests for configuration, route ordering, opt-in/default behavior, dispatch and generated adapter consistency.
- Do not test role-prompt wording.
- Confirm existing architect behavior and existing routes remain intact.

Keep code and documentation lean. Record rationale and verification in commit bodies; add no separate review reports or QA Markdown. Exclude completed-task archives from the final PR.
Use Codex <codex@openai.com> as author and committer through per-command settings; preserve human/global Git configuration.
Use team route coder → architect → QA. User requested a focused PR, not a merge to the upstream base.
