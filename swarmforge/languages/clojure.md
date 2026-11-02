# Clojure and Babashka tooling

The Clojure tools come from `swarmforge/tools.conf` and run through the
wrappers `sf tool ensure` writes to `.swarmforge/bin/`: clj-mutate for
mutation, crap4clj for CRAP, dry4clj for DRY. This file holds what a role
needs to know to run them on Babashka code.

## Mutation with clj-mutate

- Run clj-mutate from the worktree root. It keeps its manifest under
  `.metrics/mutate/` of the directory it runs from, and `sf start` and
  `sf handoff` load and save only the worktree root's.
- Do not give clj-mutate the test command `bb --config <path>/bb.edn spec`
  (or any `bb --config` test command). bb resolves the worker's symlinked
  `bb.edn` back to the real checkout, so it tests the unmutated sources and
  every mutant survives.
- Pass a `--test-command` that puts the worker's own copy of each `:paths`
  entry of the module's `bb.edn` on the classpath instead (for example `src`
  and `spec`, or `src`, `test`, `../core/src` and `../core/test`), with
  `--no-coverage`, which clj-mutate requires for a custom test command.

## Security scanners

- Code scanner: clj-holmes (`sf tool ensure clj-holmes`), run on each `.clj`
  file changed since the merge base, committed or not. It is unmaintained
  since 2022, has 9 security rules and reads `.clj` only, so changed `.cljc`,
  `.cljs` and `.bb` files are Not-tested. Fetching rules reaches
  api.github.com. Its defaults write to `/tmp` and the current directory;
  keep rules and output under `./tmp`:

  ```sh
  clj-holmes fetch-rules -o tmp/clj-holmes-rules \
    -r git://clj-holmes/clj-holmes-rules#fbf5e2eeebe9a85e2dc568c04b482a50fc482a02
  clj-holmes scan -p <file> -d tmp/clj-holmes-rules -t json -o tmp/clj-holmes.json --fail-on-result
  ```

- Dependency audit: nvd-clojure (`sf tool ensure nvd-clojure`), when the task
  changes `deps.edn`, `bb.edn` or `project.clj`. It needs `NVD_API_TOKEN` and
  reaches clojars.org and the NVD API. Run it from `./tmp`, which keeps the
  project's `deps.edn` out of its `-Sdeps` and its config and report in
  `./tmp`. Pass the project classpath (`clojure -Spath`, `bb print-deps
  --format classpath` or `lein classpath`):

  ```sh
  cd tmp && nvd-clojure nvd-clojure.edn "$(cd .. && clojure -Spath)"
  ```

## Tool compatibility checks

When changing wrappers or upgrading their upstream tools, use disposable
projects under `tmp/`, real GitHub sources and Maven dependencies. An upgrade
edits the ref column of `swarmforge/tools.conf` (`git ls-remote
https://github.com/<owner>/<repo>.git HEAD` prints a SHA); run these checks at
the new refs before the upgrade commit:

- Install and require `dry4clj`, `clj-mutate`, `crap4clj` and the APS tools
  through `sf tool`. Confirm DRY output, mutation scan counts and parser
  output with real inputs; fixture tests alone cannot detect upstream drift.
  A failed ensure from an older task catalogue must leave previously installed
  shared wrappers usable with their real upstream sources.
- Check Babashka Cloverage with both `clojure.test` and Speclj projects,
  including the fallback without Clojure CLI. Require a nonzero test count,
  successful tests, LCOV and a CRAP report for the intended source namespace.
- Compare `crap4clj` from the module directory with a repository-root run
  using `--source-root <module>/src`. Test counts and CRAP results must agree;
  coverage and `.metrics/crap.edn` belong to the module, with no unrelated
  root tests or root outputs. An invalid source root must fail before coverage.
- Explicit `--lcov` and `--use-existing-coverage` choices must not trigger
  another coverage run. Keep generated output and verification detail out of
  commits; record each tool's `@<ref>`, outcomes and unavailable
  checks in Lore.
