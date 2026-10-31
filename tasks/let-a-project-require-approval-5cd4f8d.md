---
id: let-a-project-require-approval-5cd4f8d
created: 2026-10-31
---

# Let a project require approval before refactoring existing code

Operator request: some projects do not want spurious refactorings. There the preferred change is the existing code plus a small diff with additional tests, rather than many refactorings. For new code (code the task adds) refactoring still makes sense. For old code (code that existed before the task) a role must get approval from a human before refactoring it. Add a mechanism a project can switch on to get this behaviour; projects that do not switch it on keep today's behaviour. Roles that refactor today include cleaner, architect and hardener; mutation and quality gates may also push roles to restructure old code. Prefer the simplest mechanism; ask the operator where the setting lives, how 'old code' is decided, how approval is asked and recorded, and whether sf enforces it or only the role prompts.
