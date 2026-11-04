# Ruby tooling

Run `swarmforge/bin/sf-rb` from the task worktree beside `Gemfile`. It needs
Babashka (`bb`), Ruby with Bundler, and Node with `npx` for jscpd. Gems run
through `bundle exec`; reports go under `./tmp/`. Tests use RSpec; Minitest is
unsupported.

Add the gems once, to the development and test group; `Gemfile.lock` pins
them. Only the gems of the sources you use are needed.

```sh
bundle add rspec --version 3.13.2 --group "development,test"
bundle add simplecov --version 1.3.2 --group "development,test"
bundle add rubocop --version 1.92.2 --group "development,test"      # crap: cyclomatic, perceived; Security cops
bundle add flog --version 4.9.4 --group "development,test"          # crap --complexity flog
bundle add flay --version 2.14.4 --group "development,test"         # dry --tool flay
bundle add mutant-rspec --version 0.17.1 --group "development,test"  # mutate
bundle add pbt --version 0.7.0 --group "development,test"           # property tests
bundle add bundler-audit --version 0.9.3 --group "development,test"
bundle add brakeman --version 8.1.0 --group "development,test"      # optional
```

| Command | Behavior |
|---|---|
| `bundle exec rspec --exclude-pattern '**/*_property_spec.rb'` | Unit and acceptance tests. |
| `bundle exec rspec --pattern 'spec/**/*_property_spec.rb'` | Property tests: files named `*_property_spec.rb`, suggested library pbt. |
| `coverage [rspec args]` | Tests without property specs, with SimpleCov line and method coverage in `tmp/coverage/coverage.json`. sf-rb starts SimpleCov itself; do not start it in `spec_helper.rb` for these runs. Exit status is rspec's. |
| `crap [--complexity S] [--all] [--base REF] [--report DIR]` | CRAP per changed method from `tmp/coverage/`: complexity² × (1 − coverage)³ + complexity; fail above 10. |
| `dry [--tool T] [--all] [--base REF] [--min-tokens N] [--mass N] [--report-only]` | Clones touching changed lines. New runs discard the last report; `--report-only` rereads the one of the same tool. |
| `mutate [--base REF] [--all] [-- mutant args]` | `mutant run --jobs 4 --since <merge base>`, without property specs; exit 1 on alive mutants. |

Checks exit 0 when clean, 1 on findings or failing tests, and 2 on usage or
input errors, with an `sf-rb:` stderr prefix. Scope is the committed,
working-tree and untracked `.rb` changes since the merge base with `--base`
(default `SWARMFORGE_BASE`, `main`, then `master`); `--all` drops it. CRAP and
mutation skip specs (`spec/`, `*_spec.rb`).

## Selection

| Option | Values | Default |
|---|---|---|
| `crap --complexity` | `cyclomatic` (RuboCop `Metrics/CyclomaticComplexity`), `perceived` (RuboCop `Metrics/PerceivedComplexity`), `flog` (flog's ABC-style method score) | `cyclomatic` |
| `dry --tool` | `jscpd` (`jscpd@4 --format ruby`, tokens), `flay` (flay's structural mass, threshold `--mass`, default 16) | `jscpd` |

A project that picks a non-default records the command it uses in
`project.md`'s Verification section.

RuboCop runs with sf-rb's own configuration, targeting the running Ruby;
project RuboCop settings and disable comments do not change the gate. A
method's coverage is the share of SimpleCov's relevant lines after its
definition line that ran, less nested methods' lines; a method with no such
line counts as covered when called. A source the analyser cannot parse, an
unknown source, a missing gem or a missing coverage report exits 2 with no
table. Use the `cc` column of `crap --all` to review mixed-duty files;
mutant has no scan/count mode. jscpd and flay skip `node_modules`, `dist`,
`build`, `coverage`, `tmp` and `vendor`.

```text
OVER crap   20.8  cc 15.3  cov  71%  lib/ledger.rb:6 Ledger#balance
166 tokens  lib/a.rb:2-12 (changed)  ==  lib/b.rb:2-12
34 mass  lib/d.rb:2-4 (changed)  ==  lib/d.rb:5-7 (changed)
```

## Mutation

mutant reads usage, includes, requires, integration (`rspec`) and subjects
from the project's `mutant.yml`, `config/mutant.yml` or `.mutant.yml`; add
`.mutant/` to `.gitignore`. sf-rb never sets `--usage`: mutant's licence
allows `usage: opensource` only on public open-source repositories, and other
code needs `usage: commercial` (paid). Mutation is optional. Without a usage
key `sf-rb mutate` exits 2 before mutant starts; record
`Not-tested: mutation: no mutant usage` and continue.

## Security scanners

- Code scanner, on the changed `.rb` files; it needs uv and semgrep.dev:
  `uvx semgrep@1.183.0 scan --config p/ruby --metrics=off --error <changed files>`
- Optional extras; act only on findings in changed files:
  `bundle exec brakeman --no-pager -q` (Rails apps; add `--force` otherwise;
  its README makes it free for non-commercial use) and
  `bundle exec rubocop --force-default-config --only Security <changed files>`.
- Dependency audit, when `Gemfile`, `Gemfile.lock` or a `*.gemspec` changes;
  it reaches github.com:
  `bundle exec bundle-audit check --update --database tmp/ruby-advisory-db`
