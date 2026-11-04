---
id: add-ruby-support-cac94d7
created: 2026-11-04
stage: done
route: specifier,coder,cleaner,architect,hardener,qa
---

# Add Ruby support

Operator request: add Ruby support, like the existing language packs (Clojure, JavaScript/TypeScript, C#, F#, VB.NET). Roles working on a Ruby project need: tests and property tests, coverage, CRAP (complexity with coverage), DRY, mutation testing, and the security code scanner and dependency audit that the security reviewer runs (see swarmforge/languages/*.md and the constitution's engineering article). Add a swarmforge/languages/ruby.md guide and whatever wrapper or tools.conf rows the chosen tools need, pinned like the others. Prefer existing maintained Ruby tools and the simplest wrapper; keep the guide short; no language-specific text in general role prompts. The specifier should verify each candidate tool and ask the operator to choose where there is a real choice (test runner, mutation tool, how CRAP is computed, DRY tool, scanners, whether a wrapper like sf-js is needed).

## Records

```text
type: created
from: operator
branch: swarm/add-ruby-support-cac94d7
commit: 1ac8c80
route: specifier,coder,cleaner,architect,hardener,qa
```

```text
type: question
from: specifier
question: Ruby pack: ten choices. Verified here: Ruby 3.3.6, bundler and rubygems.org reachable; GitHub git reachable; semgrep.dev blocked. Versions are current rubygems.org releases, installed and run on a scratch project unless marked.
  
  1. Test runner. (a) RSpec 3.13.2 only, Minitest out of scope [recommended: mutant-rspec works with no test changes; mutant-minitest needs a 'cover' line in every test class]; (b) Minitest 6.0.6 only; (c) both, detected from Gemfile.lock.
  
  2. Property tests. Convention: property specs are named *_property_spec.rb; normal, coverage and mutation runs pass --exclude-pattern '**/*_property_spec.rb' (works in rspec and through mutant --integration-argument; an RSpec :property tag does NOT exclude them from mutant). Library: (a) name pbt 0.7.0 (MIT, released 2026-04) as the suggested library, project may use another [recommended]; (b) rantly 3.0.0 (2024-10); (c) prop_check 1.0.2 (2025-03).
  
  3. Wrapper. CRAP and change-scoped DRY need report joining; the constitution forbids homegrown proxies, so they need wrapper code. (a) Lean sf-rb (Babashka, on tools/core like sf-cs) with only 'crap' and 'dry'; tests, property, coverage, mutation and scanners are documented commands in ruby.md [recommended: smallest; mutant already has --since and exit 1 on alive mutants]; (b) lean plus 'coverage' and 'mutate' commands that own the guardrails (SimpleCov setup, merge-base --since, --jobs 4, property exclusion); (c) full sf-cs-style command set (tools, test, property, coverage, crap, dry, mutate).
  
  4. CRAP. (a) Cyclomatic complexity from RuboCop 1.92.2 Metrics/CyclomaticComplexity with a wrapper-owned config (only that cop, Max 0, JSON: every method reported with its line span) joined with SimpleCov 1.3.2 JSON line coverage of the method body; same formula and limit 10 as the other packs [recommended]; (b) same with Metrics/PerceivedComplexity (a case counts less); (c) flog 4.9.4 ABC score as complexity (not cyclomatic: grade() scored 5.4 flog vs 4 cyclomatic, so the limit 10 means something else); (d) skunk 0.6.0 SkunkScore (different formula, pins rubycritic < 5).
  
  5. Coverage set-up (if 3a). (a) The project's spec_helper starts SimpleCov with the JSON formatter into tmp/coverage when COVERAGE=1; ruby.md shows the 3-line snippet; sf-rb crap reads tmp/coverage/coverage.json and exits 2 when it is missing [recommended]; (b) sf-rb injects SimpleCov itself (needs 3b).
  
  6. DRY. (a) jscpd@4 --format ruby through the shared tools/core jscpd reader (found a Ruby clone here) [recommended]; (b) flay 2.14.4 (AST-based, MIT, maintained; new text-output parser, reports mass not tokens).
  
  7. Mutation. mutant 0.17.1 is the only maintained Ruby mutation tool (mutest last 2019, heckle 2009). Licence: proprietary EULA; '--usage opensource' is free only on public open-source repositories, anything else needs a paid subscription ('--usage commercial'); without a usage it refuses to run. (a) Use mutant-rspec; the project sets usage, includes, requires and integration in its mutant.yml; SwarmForge never chooses the usage; the documented command adds --since <merge base> --jobs 4 and the property exclusion; a project without a usage cannot run the gate and its role records Not-tested with that reason; ruby.md says to ignore .mutant/ (mutant writes results there) [recommended]; (b) mutation unsupported, as for F# and VB.NET.
  
  8. Gem pinning. (a) Gems go in the project's Gemfile (development/test group) via a documented 'bundle add' with the verified exact versions; Gemfile.lock pins them; tools.conf gets only an 'sf-rb local' row; jscpd@4 and semgrep pinned in the commands as sf-js does [recommended: mutant, SimpleCov and RuboCop must load inside the project's bundle anyway]; (b) a new tools.conf 'gem' kind installing into .swarmforge/ (new sf code, and it cannot serve the in-bundle tools).
  
  9. Security. Code scanner: (a) uvx semgrep@1.183.0 scan --config p/ruby --metrics=off --error <changed .rb files>, as javascript.md does [recommended: broadest rules, Rails included, same tool as JS; cannot run here because semgrep.dev is blocked]; (b) brakeman 8.1.0 (Rails apps; plain Ruby needs --force; README: free for non-commercial use; caught eval and command injection, missed YAML.load, Marshal.load and Kernel#open); (c) bundle exec rubocop --only Security (7 cops, already in the bundle; caught those four, missed command injection). Dependency audit: bundler-audit 0.9.3 (GPL-3.0, released 2025-11), 'bundle-audit check --update --database tmp/ruby-advisory-db' when Gemfile, Gemfile.lock or a *.gemspec changes; it clones rubysec/ruby-advisory-db from GitHub; verified here: rack 2.0.1 reported, exit 1 [recommended; the alternative ruby_audit 3.1.0 also checks the Ruby version].
  
  10. Scope and route. Scope: ruby.md; sf-rb with its runtime and tests under swarmforge/tools/ruby reusing tools/core; the sf-rb tools.conf row; the engineering article's table row, language-defaults line and differential-mutation line; README.md and swarmforge/README.md discovery; install.sh copying the new bb.edn; sf_tool_test.sh and the architecture test extended. Out: Rails specifics, Sorbet/RBS, JRuby, monorepos, a Ruby Gherkin runtime. Route: (a) keep specifier,coder,cleaner,architect,hardener,qa [recommended: the change runs local commands only, and the harness's own Clojure scanners (clj-holmes, nvd-clojure) cannot reach Clojars here]; (b) add security-reviewer.
```

```text
type: answer
from: operator
answer: 3b: lean sf-rb with crap and dry plus coverage and mutate commands that own the guardrails (SimpleCov setup, merge-base --since, --jobs 4, property exclusion); tests, property and scanners stay documented commands. 4: support all four complexity sources to begin with (RuboCop cyclomatic, RuboCop perceived, flog ABC, skunk), selectable in sf-rb crap; the specifier proposes the default and the option. 6: both jscpd@4 (a) and flay (b). 7a: mutant-rspec with the project's mutant.yml usage, but mutation is optional: a project without a mutant usage records Not-tested with that reason and the role continues, it does not block. 8a: gems in the project's Gemfile via documented bundle add with exact versions; tools.conf gets only an sf-rb local row. 9: code scanner semgrep p/ruby (required), plus optional brakeman and RuboCop Security cops; dependency audit bundler-audit. Not answered, so recommended: 1a RSpec only; 2a pbt suggested, property specs named *_property_spec.rb; 5 follows from 3b (sf-rb coverage owns the SimpleCov setup, the specifier states how); 10a scope as listed and the full feature route.
```

```text
type: review
from: specifier
commit: 481c711
files: -
summary: Ruby spec in 481c711: sf-rb coverage/crap (--complexity, default cyclomatic)/dry (--tool, default jscpd)/mutate (optional), ruby.md with scanners
```

```text
type: changes-requested
from: operator
reason: Operator: drop skunk, otherwise approve. Remove --complexity skunk and the per-file CRAP rule; sf-rb crap supports cyclomatic, perceived and flog (default cyclomatic). With skunk gone, pin the latest flog 4.9.4 and flay 2.14.4 (skunk 0.6.0 pins path_expander < 2.0, which is what forced flog 4.8.0 and flay 2.13.3). Change nothing else.
```

```text
type: review
from: specifier
commit: 05c7b60
files: -
summary: Ruby spec revised in 05c7b60: skunk dropped; crap --complexity cyclomatic|perceived|flog; flog 4.9.4, flay 2.14.4
```

```text
type: approval
from: operator
```

```text
type: handoff
from: specifier
to: coder
branch: swarm/add-ruby-support-cac94d7
commit: 05c7b60
files: -
summary: specifier work complete
```

```text
type: handoff
from: coder
to: cleaner
branch: swarm/add-ruby-support-cac94d7
commit: 6bf192f
files: .github/workflows/test.yml,README.md,install.sh,swarmforge/README.md,swarmforge/bin/sf-rb,swarmforge/constitution/articles/engineering.md,swarmforge/languages/ruby.md,swarmforge/tools.conf,swarmforge/tools/core/src/sfcore/crap.clj,swarmforge/tools/core/src/sfcore/jscpd.clj,swarmforge/tools/core/src/sfcore/shell.clj,swarmforge/tools/core/test/sfcore/core_test.clj,swarmforge/tools/javascript/property/sfjs/sfjs_property.clj,swarmforge/tools/javascript/src/sfjs/cli.clj,swarmforge/tools/javascript/src/sfjs/crap.clj,swarmforge/tools/javascript/test/sfjs/architecture_test.clj,swarmforge/tools/ruby/bb.edn,swarmforge/tools/ruby/fixtures/coverage.json,swarmforge/tools/ruby/fixtures/flay-report.json,swarmforge/tools/ruby/fixtures/flog.txt,swarmforge/tools/ruby/fixtures/jscpd-report.json,swarmforge/tools/ruby/fixtures/ledger.rb,swarmforge/tools/ruby/fixtures/rubocop-cyclomatic.json,swarmforge/tools/ruby/fixtures/rubocop-perceived.json,swarmforge/tools/ruby/fixtures/rubocop-syntax-error.json,swarmforge/tools/ruby/resources/sfrb/flay_report.rb,swarmforge/tools/ruby/resources/sfrb/simplecov.rb,swarmforge/tools/ruby/src/sfrb/cli.clj,swarmforge/tools/ruby/src/sfrb/commands.clj,swarmforge/tools/ruby/src/sfrb/crap.clj,swarmforge/tools/ruby/src/sfrb/flay.clj,swarmforge/tools/ruby/src/sfrb/flog.clj,swarmforge/tools/ruby/src/sfrb/mutant.clj,swarmforge/tools/ruby/src/sfrb/rubocop.clj,swarmforge/tools/ruby/test/sfrb/cli_test.clj,swarmforge/tools/ruby/test/sfrb/commands_test.clj,swarmforge/tools/ruby/test/sfrb/crap_test.clj,swarmforge/tools/ruby/test/sfrb/flay_test.clj,swarmforge/tools/ruby/test/sfrb/flog_test.clj,swarmforge/tools/ruby/test/sfrb/mutant_test.clj,+2 more
summary: sf-rb coverage/crap/dry/mutate, ruby.md, discovery, install and tests
```

```text
type: handoff
from: cleaner
to: architect
branch: swarm/add-ruby-support-cac94d7
commit: b523b26
files: swarmforge/languages/ruby.md,swarmforge/tools/core/src/sfcore/shell.clj,swarmforge/tools/csharp/src/sfcs/cli.clj,swarmforge/tools/javascript/src/sfjs/cli.clj,swarmforge/tools/ruby/resources/sfrb/flay_report.rb,swarmforge/tools/ruby/resources/sfrb/simplecov.rb,swarmforge/tools/ruby/src/sfrb/cli.clj
summary: cleanup: shared file helpers, shorter ruby.md
```

```text
type: handoff
from: architect
to: hardener
branch: swarm/add-ruby-support-cac94d7
commit: 752560f
files: .github/workflows/test.yml,swarmforge/tools/core/property/property_support.clj,swarmforge/tools/core/property/sfcore/core_property.clj,swarmforge/tools/core/src/sfcore/jscpd.clj,swarmforge/tools/core/src/sfcore/shell.clj,swarmforge/tools/javascript/bb.edn,swarmforge/tools/javascript/property/sfjs/sfjs_property.clj,swarmforge/tools/ruby/bb.edn,swarmforge/tools/ruby/property/sfrb/sfrb_property.clj,swarmforge/tools/ruby/src/sfrb/commands.clj
summary: core property support shared; sf-rb property tests for readers, CRAP join, sources, mutant args
```

```text
type: handoff
from: hardener
to: qa
branch: swarm/add-ruby-support-cac94d7
commit: 2958614
files: .github/workflows/test.yml,swarmforge/tools/ruby/test/sfrb/cli_test.clj,swarmforge/tools/ruby/test/sfrb/crap_test.clj,swarmforge/tools/ruby/test/sfrb/rubocop_test.clj,test/ruby_project/.gitignore,test/ruby_project/.mutant.yml,test/ruby_project/Gemfile,test/ruby_project/Gemfile.lock,test/ruby_project/lib/grade.rb,test/ruby_project/lib/shipping.rb,test/ruby_project/spec/grade_property_spec.rb,test/ruby_project/spec/grade_spec.rb,test/ruby_project/spec/shipping_spec.rb,test/sf_rb_e2e_test.sh
summary: hardened: sf-rb mutation survivors killed, Ruby end-to-end in CI
```

```text
type: question
from: qa
question: QA verdict at db0d65a: fail. The only blocker is the flog CRAP limit. The orchestrator relayed this requirement: sf-rb crap --complexity flog fails above 20; cyclomatic and perceived stay at 10; ruby.md names the flog limit next to the option. The coder must also update the flog assertion in test/sf_rb_e2e_test.sh. Everything else passes. The QA role prompt does not direct --to, and handoffs.md says not to send work backwards, so I have not run sf handoff --to coder. Please record the flog limit of 20 with sf answer and choose one: (a) authorize QA to run sf handoff add-ruby-support-cac94d7 --to coder with this requirement; (b) the orchestrator re-routes the task to the coder another way; (c) QA makes the limit change itself as a QA-owned fix and re-verifies.
```

```text
type: answer
from: operator
answer: Operator decisions: sf-rb crap --complexity flog fails above 20; cyclomatic and perceived stay at 10; ruby.md names the flog limit next to the option; update the flog assertion in test/sf_rb_e2e_test.sh. Routing: (a) QA hands the task back with sf handoff --to coder, and it continues along its route from there. Also address QA's notes: the shared crap-limit, the 'over CRAP 10' summary and help text must reflect the per-source limit, and engineering.md's 'CRAP must be 10 or below' notes the Ruby flog exception in one clause.
```

```text
type: handoff
from: qa
to: coder
branch: swarm/add-ruby-support-cac94d7
commit: db0d65a
files: -
summary: QA fail at db0d65a: flog CRAP limit 20 (cyclomatic/perceived 10), ruby.md, e2e flog assertion, per-source limit in summary/help, engineering.md clause
```

```text
type: handoff
from: coder
to: cleaner
branch: swarm/add-ruby-support-cac94d7
commit: a85b74e
files: swarmforge/constitution/articles/engineering.md,swarmforge/languages/ruby.md,swarmforge/tools/core/property/sfcore/core_property.clj,swarmforge/tools/core/src/sfcore/crap.clj,swarmforge/tools/core/test/sfcore/core_test.clj,swarmforge/tools/csharp/src/sfcs/cli.clj,swarmforge/tools/csharp/src/sfcs/cobertura.clj,swarmforge/tools/csharp/test/sfcs/core_test.clj,swarmforge/tools/javascript/src/sfjs/cli.clj,swarmforge/tools/javascript/test/sfjs/reports_test.clj,swarmforge/tools/ruby/src/sfrb/cli.clj,swarmforge/tools/ruby/src/sfrb/crap.clj,swarmforge/tools/ruby/test/sfrb/cli_test.clj,test/sf_rb_e2e_test.sh
summary: sf-rb crap: flog limit 20, cyclomatic/perceived 10; per-source limit in summary, help, ruby.md, engineering.md; e2e flog passes
```

```text
type: handoff
from: cleaner
to: architect
branch: swarm/add-ruby-support-cac94d7
commit: a85b74e
files: -
summary: cleanup of a85b74e delta: no changes needed
```

```text
type: handoff
from: architect
to: hardener
branch: swarm/add-ruby-support-cac94d7
commit: c71b3cd
files: swarmforge/tools/core/property/sfcore/core_property.clj
summary: a85b74e delta reviewed: limit placement sound; core CRAP properties cover any limit
```

```text
type: handoff
from: hardener
to: qa
branch: swarm/add-ruby-support-cac94d7
commit: 43fe55d
files: -
summary: delta hardened: no survivors, DRY clean, e2e passes; crap4clj unavailable
```

```text
type: handoff
from: qa
to: done
branch: swarm/add-ruby-support-cac94d7
commit: e038aad
files: -
summary: QA pass at e038aad: flog CRAP limit 20, cyclomatic/perceived 10; all suites and e2e pass
```
