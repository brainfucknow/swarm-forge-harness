# Ruby tooling

Run `swarmforge/bin/sf-rb` from the task worktree beside `Gemfile`. It needs
Babashka, Ruby with Bundler, and Node with `npx` for jscpd. Gems run through
`bundle exec`; reports go under `./tmp/`. Tests use RSpec; Minitest is
unsupported.

Add the gems you use to the project's development and test group once;
`Gemfile.lock` pins them:

```sh
bundle add rspec --version 3.13.2 --group "development,test"
bundle add simplecov --version 1.3.2 --group "development,test"
bundle add rubocop --version 1.92.2 --group "development,test"      # crap, Security cops
bundle add flog --version 4.9.4 --group "development,test"          # crap --complexity flog
bundle add flay --version 2.14.4 --group "development,test"         # dry --tool flay
bundle add reek --version 6.6.0 --group "development,test"          # code-smell check (recommended)
bundle add mutant-rspec --version 0.17.1 --group "development,test"
bundle add pbt --version 0.7.0 --group "development,test"           # property tests
bundle add bundler-audit --version 0.9.3 --group "development,test"
bundle add brakeman --version 8.1.0 --group "development,test"      # optional
```

| Command | Behavior |
|---|---|
| `bundle exec rspec --exclude-pattern '**/*_property_spec.rb'` | Unit and acceptance tests. |
| `bundle exec rspec --pattern 'spec/**/*_property_spec.rb'` | Property tests (`*_property_spec.rb`; pbt suggested). |
| `coverage [rspec args]` | Tests without property specs, with SimpleCov JSON in `tmp/coverage/`. sf-rb starts SimpleCov itself; do not start it in `spec_helper.rb`. Exit status is rspec's. |
| `crap [--complexity cyclomatic\|perceived\|flog] [--all] [--base REF] [--report DIR]` | CRAP per changed method from `tmp/coverage/`; fails above 10, or above 20 with `flog`. Complexity comes from RuboCop `Metrics/CyclomaticComplexity` (default), RuboCop `Metrics/PerceivedComplexity`, or flog's score. |
| `dry [--tool jscpd\|flay] [--all] [--base REF] [--min-tokens N] [--mass N] [--report-only]` | Clones touching changed lines. jscpd (default) counts tokens; flay reports structural mass (`--mass`, default 16). |
| `mutate [--base REF] [--all] [-- mutant args]` | `mutant run --jobs 4 --since <merge base>` without property specs; exit 1 on alive mutants. |

Exit status is 0 when clean, 1 on findings or failing tests, and 2 on usage or
input errors, which print an `sf-rb:` message on stderr. Scope is the `.rb`
changes since the merge base with `--base` (default `SWARMFORGE_BASE`, `main`,
then `master`); `--all` drops it. CRAP and mutation skip specs. A non-default
`--complexity` or `--tool` goes in `project.md`'s Verification section.

RuboCop runs with sf-rb's own configuration, so project settings and disable
comments do not change the gate. A method's coverage is the share of its own
relevant lines that ran; a method with none counts as covered when called.
Review mixed-duty files with the `cc` column of `crap --all`; mutant has no
scan mode.

## Mutation

mutant reads its configuration from `mutant.yml`, `config/mutant.yml` or
`.mutant.yml`; add `.mutant/` to `.gitignore`. sf-rb never sets `--usage`:
`usage: opensource` is licensed for public open-source repositories only, and
other code needs `usage: commercial` (paid). Mutation is optional: without a
usage key `sf-rb mutate` exits 2, so record `Not-tested: mutation: no mutant
usage` and continue.

## Security scanners

- Code scanner on the changed `.rb` files (needs uv and semgrep.dev):
  `uvx semgrep@1.183.0 scan --config p/ruby --metrics=off --error <changed files>`
- Optional, acting only on findings in changed files:
  `bundle exec brakeman --no-pager -q` (Rails apps, `--force` otherwise; free
  for non-commercial use only) and
  `bundle exec rubocop --force-default-config --only Security <changed files>`.
- Dependency audit when `Gemfile`, `Gemfile.lock` or a `*.gemspec` changes
  (reaches github.com):
  `bundle exec bundle-audit check --update --database tmp/ruby-advisory-db`

## Code-smell check

reek (MIT) is recommended; it honours `.reek.yml` and inline `:reek:` comments.
Run it on the changed `.rb` files, specs included, deleted files left out:
`mkdir -p tmp && bundle exec reek --no-progress --no-color -s --force-exclusion <changed .rb files> > tmp/reek.log 2>&1`
Exit 0 means clean and 2 smells; ignore IrresponsibleModule. A file the log
reports as "cannot be processed" is Not-tested, even on exit 0.
