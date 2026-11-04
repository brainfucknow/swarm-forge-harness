---
id: add-reek-as-an-optional-ruby-70191c4
created: 2026-11-04
stage: done
route: specifier,coder,cleaner,qa
---

# Add reek as an optional Ruby code-smell check

Operator request, after the Ruby support task (add-ruby-support-cac94d7): add reek (MIT, maintained) as an optional code-smell check for Ruby projects, run by the cleaner on the task's changed .rb files, pinned like the other Ruby gems through the project's Gemfile. Decide in the spec: whether it is a documented command in ruby.md or an sf-rb subcommand, which reek findings act as a gate versus advice, how it respects 'Existing code: ask before refactoring' (findings in pre-existing code are recorded, not fixed without approval), and whether a project config (.reek.yml) is honoured. Keep it small and the guide short. Build on the Ruby support branch once that task is finished.

## Records

```text
type: created
from: operator
branch: swarm/add-reek-as-an-optional-ruby-70191c4
commit: 562ed0f
route: specifier,coder,cleaner,architect,hardener,qa
```

```text
type: answer
from: operator
answer: Operator decision before specifying: reek is encouraged, not merely optional, matching the .NET code-smell task: ruby.md recommends it and the cleaner runs it on the task's changed .rb files whenever it is available; a missing reek does not block; with 'Existing code: ask before refactoring', findings in pre-existing code are recorded, not fixed without approval.
```

```text
type: question
from: specifier
question: Checked: reek 6.6.0 (MIT, released 2026-10-02, Ruby >= 3.2) resolves with every pin in ruby.md on Ruby 3.3.6 without moving any of them (bundle add reek --version 6.6.0 on a copy of test/ruby_project). Exit status: 0 clean, 2 on smells, but also 0 when a file has a syntax error or does not exist (message on stderr), so the guide must say that leaves the file Not-tested. 1. Form. (a) A documented command in a short ruby.md 'Code-smell check' section: bundle exec reek --no-progress --no-color -s --force-exclusion <changed .rb files> > tmp/reek.log, plus a bundle add reek --version 6.6.0 line marked recommended. No sf-rb code, no tests. (b) An sf-rb smell [--base REF] [--all] subcommand that finds the changed files and turns parse errors into exit 2; needs Babashka code and sf-rb tests. Recommended: (a), as PR #60 did for .NET. 2. Which smells count. (a) reek's default detectors on all changed .rb files, specs included, every finding advice under PR #60's cleaner bullet (fix in new code where reasonable, record the rest), ignoring IrresponsibleModule because it asks for a comment on every class and the cleaner keeps comments to non-obvious rationale. (b) As (a) but skipping spec/ like CRAP and mutation. (c) A shipped sf config limited to size smells (TooManyStatements, LongParameterList, TooManyMethods, TooManyInstanceVariables, NestedIterators), passed with -c. (d) Any of these, but findings in new code are a gate the cleaner must clear. Recommended: (a). 3. Project .reek.yml. (a) Honoured: reek finds it from the worktree upward with no flag, --force-exclusion applies its exclude_paths, and inline :reek: comments work; the check is advice, as PR #60 lets a project's own rule severity win. (b) Ignored: sf ships a config passed with -c, since reek reads one config only, like sf-rb's RuboCop gate. Recommended: (a). 4. Route. Now feature (specifier,coder,cleaner,architect,hardener,qa). (a) bugfix: specifier,coder,cleaner,qa, as PR #60 used; the change is ruby.md only, nothing for architect or hardener. (b) Keep feature. Recommended: (a). 5. PR #60's cleaner.md bullet and engineering.md Existing Code wording. (a) This PR touches ruby.md only and depends on PR #60, which merges first; its cleaner bullet already runs 'the code-smell check the project language's guide names' and engineering.md already lists code-smell; recorded as Depends-on. (b) Merge claude/dotnet-code-smell into this branch, as was done for Ruby support, so the branch carries the bullet; the PR diff shows PR #60 until it merges. (c) Repeat byte-identical lines here; git merges identical changes cleanly, but reviewers see them twice. Recommended: (a).
```

```text
type: answer
from: operator
answer: All recommended: 1a documented command in a short ruby.md Code-smell check section (bundle exec reek --no-progress --no-color -s --force-exclusion <changed .rb files> > tmp/reek.log) plus a recommended bundle add reek --version 6.6.0; no sf-rb code; a file reek cannot parse is Not-tested, not clean. 2a reek's default detectors on all changed .rb files including specs, every finding advice under the cleaner's code-smell bullet, IrresponsibleModule ignored. 3a a project's .reek.yml is honoured. 4a route specifier,coder,cleaner,qa. 5a only ruby.md changes; PR #60's cleaner bullet and engineering.md code-smell gate are already on main, so merge origin/main into the branch (PR #61 Ruby support is merged too).
```

```text
type: route
from: operator
route: specifier,coder,cleaner,qa
```

```text
type: review
from: specifier
commit: 6a4a53e
files: .github/workflows/test.yml,README.md,install.sh,swarmforge/README.md,swarmforge/bin/sf-rb,swarmforge/constitution/articles/engineering.md,swarmforge/languages/csharp.md,swarmforge/languages/fsharp.md,swarmforge/languages/ruby.md,swarmforge/languages/vbnet.md,swarmforge/roles/cleaner.md,swarmforge/tools.conf,swarmforge/tools/core/property/property_support.clj,swarmforge/tools/core/property/sfcore/core_property.clj,swarmforge/tools/core/src/sfcore/crap.clj,swarmforge/tools/core/src/sfcore/jscpd.clj,swarmforge/tools/core/src/sfcore/shell.clj,swarmforge/tools/core/test/sfcore/core_test.clj,swarmforge/tools/csharp/resources/fsharplint.json,swarmforge/tools/csharp/resources/smell.globalconfig,swarmforge/tools/csharp/resources/smell.targets,swarmforge/tools/csharp/src/sfcs/cli.clj,swarmforge/tools/csharp/src/sfcs/cobertura.clj,swarmforge/tools/csharp/test/sfcs/core_test.clj,swarmforge/tools/javascript/bb.edn,swarmforge/tools/javascript/property/sfjs/sfjs_property.clj,swarmforge/tools/javascript/src/sfjs/cli.clj,swarmforge/tools/javascript/src/sfjs/crap.clj,swarmforge/tools/javascript/test/sfjs/architecture_test.clj,swarmforge/tools/javascript/test/sfjs/reports_test.clj,swarmforge/tools/ruby/bb.edn,swarmforge/tools/ruby/fixtures/coverage.json,swarmforge/tools/ruby/fixtures/flay-report.json,swarmforge/tools/ruby/fixtures/flog.txt,swarmforge/tools/ruby/fixtures/jscpd-report.json,swarmforge/tools/ruby/fixtures/ledger.rb,swarmforge/tools/ruby/fixtures/rubocop-cyclomatic.json,swarmforge/tools/ruby/fixtures/rubocop-perceived.json,swarmforge/tools/ruby/fixtures/rubocop-syntax-error.json,swarmforge/tools/ruby/property/sfrb/sfrb_property.clj,+27 more
summary: Spec: reek 6.6.0 as Ruby's encouraged code-smell check, ruby.md only
```

```text
type: approval
from: operator
```

```text
type: handoff
from: specifier
to: coder
branch: swarm/add-reek-as-an-optional-ruby-70191c4
commit: 6a4a53e
files: -
summary: specifier work complete
```

```text
type: handoff
from: coder
to: cleaner
branch: swarm/add-reek-as-an-optional-ruby-70191c4
commit: 06dba46
files: README.md,swarmforge/README.md,swarmforge/languages/ruby.md,swarmforge/roles/orchestrator.md,swarmforge/roles/security-reviewer.md,swarmforge/roles/specifier.md,swarmforge/routes.conf,test/sf_test.sh
summary: reek 6.6.0 gem line and Code-smell check section in ruby.md; origin/main merged
```

```text
type: handoff
from: cleaner
to: qa
branch: swarm/add-reek-as-an-optional-ruby-70191c4
commit: 8ed93e3
files: swarmforge/languages/ruby.md
summary: Trimmed ruby.md reek section
```

```text
type: handoff
from: qa
to: done
branch: swarm/add-reek-as-an-optional-ruby-70191c4
commit: 03d96f3
files: swarmforge/languages/ruby.md
summary: QA verified reek section; restored (MIT) and exit-0 wording per spec
```
