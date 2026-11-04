---
id: add-reek-as-an-optional-ruby-70191c4
created: 2026-11-04
---

# Add reek as an optional Ruby code-smell check

Operator request, after the Ruby support task (add-ruby-support-cac94d7): add reek (MIT, maintained) as an optional code-smell check for Ruby projects, run by the cleaner on the task's changed .rb files, pinned like the other Ruby gems through the project's Gemfile. Decide in the spec: whether it is a documented command in ruby.md or an sf-rb subcommand, which reek findings act as a gate versus advice, how it respects 'Existing code: ask before refactoring' (findings in pre-existing code are recorded, not fixed without approval), and whether a project config (.reek.yml) is honoured. Keep it small and the guide short. Build on the Ruby support branch once that task is finished.
