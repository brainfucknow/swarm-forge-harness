# JavaScript and TypeScript tooling

`swarmforge/bin/sf-js` wraps the JavaScript and TypeScript tools so every role
runs the same command and reads the same output. Run it from the task worktree
(`DIR:` from `sf start`), next to `package.json`, by path or with
`swarmforge/bin` on PATH. It writes its reports under `./tmp/`. It needs
Babashka (`bb`) on PATH, plus `node`, `npx`, and the project's package manager.

| Constitution tool | Command | Behind it |
|---|---|---|
| Startup tools | `sf-js tools` | Checks that `node`, `npx`, and the package manager are on PATH, adds every missing tool package to `devDependencies` in one sorted command (`npm install --save-dev`, `pnpm add --save-dev`, `yarn add --dev`, or `bun add --dev`), and checks that `npx --yes jscpd@4` runs. Prints `all JavaScript tools present` when nothing is missing. |
| Unit and acceptance tests | `sf-js test [runner args]` | `tsc --noEmit` first when `tsconfig.json` exists (a type error exits 1 and skips the tests), then `vitest run --exclude '**/*.property.test.*'` or `jest --testPathIgnorePatterns /node_modules/ --testPathIgnorePatterns '\.property\.test\.'`. |
| Property tests | `sf-js property [runner args]` | `vitest run .property.test.` or `jest '\.property\.test\.'`. No typecheck. |
| Coverage | `sf-js coverage [runner args]` | The tests without property tests, writing Istanbul JSON to `tmp/coverage/coverage-final.json`: Vitest with `--coverage.provider=v8 --coverage.reporter=json`, Jest with `--coverage --coverageReporters=json`. A run that writes no report exits 2. |
| CRAP | `sf-js crap [--all] [--base REF] [--report DIR]` | ESLint's `complexity` rule (typescript-eslint parser) with sf-js's own config, joined with `coverage-final.json`: `comp² × (1 − coverage)³ + comp`. Lists changed functions, or all with `--all`. Exit 1 when any is over 10. |
| DRY | `sf-js dry [--all] [--base REF] [--min-tokens N]` | `npx --yes jscpd@4 --format javascript,typescript,jsx,tsx` with a JSON report under `tmp/jscpd/`, then the clones that touch a changed line. Exit 1 when any. |
| Language mutation | `sf-js mutate [--base REF] [--all] [-- stryker args]` | `stryker run` with `--mutate` set to the changed source files, `--incremental --incrementalFile tmp/stryker/incremental.json`, the runner (`--testRunner jest` when `jest` is listed, else `vitest`), `--checkers typescript` when `tsconfig.json` exists, and `--concurrency 4` unless you pass your own. The JSON report goes to `tmp/stryker/mutation.json`; every `Survived` or `NoCoverage` mutant is listed. Exit 1 when any. `--all` drops `--mutate` so Stryker's own configuration picks the files. |

Every command exits 0 when clean, 1 on a finding (including failing tests or a
type error), and 2 on a usage or input error such as a missing report, a
missing lockfile, or no test runner. Errors go to stderr prefixed `sf-js: `.
`--report-only` on `dry` and `mutate` re-reads the last report without
rerunning the tool. Every tool run is echoed first as a `$ ` line.

## Package manager and runner

- The `packageManager` field of `package.json` names the manager (`pnpm@9.1.0` means pnpm). Its lockfile must exist; another manager's lockfile alone is an error.
- Without the field, the lockfile decides: `package-lock.json` or `npm-shrinkwrap.json` for npm, `pnpm-lock.yaml` for pnpm, `yarn.lock` for yarn, `bun.lock` or `bun.lockb` for bun. No lockfile, or lockfiles of several managers, is an error.
- Project tools run through the manager: `npx --no-install`, `pnpm exec`, `yarn`, or `bunx`. jscpd always runs as `npx --yes jscpd@4`.
- `test`, `property` and `coverage` run Vitest when `package.json` lists `vitest` in `dependencies` or `devDependencies`, else Jest when it lists `jest`.
- `mutate` and `tools` pick the other way round when both are listed: Jest when `package.json` lists `jest`, else Vitest. `sf-js tools` installs Vitest when neither is listed.
- The tool packages are the runner, `@vitest/coverage-v8` for Vitest, `typescript`, `eslint`, `typescript-eslint`, `@stryker-mutator/core`, the Stryker runner plugin for the runner, `@stryker-mutator/typescript-checker` when `tsconfig.json` exists, and `fast-check`.
- `sf-js tools` adds each missing package by bare name and does not choose versions; a person keeps the project's tool versions compatible. In particular, keep `typescript` within the range `typescript-eslint` declares in its `peerDependencies` (`npm view typescript-eslint peerDependencies`). npm resolves that range by itself; pnpm, yarn and bun take the latest `typescript`, which may be newer than typescript-eslint and the Stryker TypeScript checker accept, so in those projects add a compatible `typescript` before running `sf-js tools`, or pin it afterwards.
- sf-js works on the `package.json` in the current directory. Workspaces and monorepos are out of scope.

## Changed code

"Changed" means the task: everything since the merge base with `--base`,
committed or not, plus untracked files, in files ending `.js`, `.jsx`, `.ts`,
`.tsx`, `.mjs`, `.cjs`, `.mts`, or `.cts`; declaration files (`.d.ts`,
`.d.mts`, `.d.cts`) are left out. `--base` defaults to `SWARMFORGE_BASE`, then
`main`, then `master`.

`crap` and `mutate` look only at production sources: test files
(`*.test.*`, `*.spec.*`, `*.property.test.*`, anything under `__tests__/`) are
neither scored nor mutated. A changed set with no production source prints
`no changed source files to mutate` and runs no Stryker.

## Reading the output

```text
OVER crap   10.8  cc   4  cov  25%  src/ledger.ts:10 withdraw
ok   crap    2.0  cc   1  cov   0%  src/ledger.ts:22 (anonymous)
2 changed function(s), 1 over CRAP 10
```

A function counts as changed when any line from its first to its last line
changed. Its coverage is the share of its own Istanbul statements that ran;
statements of functions nested in it count only for those. A function with no
statements is covered when it was called. A function missing from the coverage
report counts as 0% and spans only its head. Unnamed functions show as
`(anonymous)`. Reduce CRAP by covering the function or splitting it.

sf-js lints with its own config, written to `tmp/eslint/eslint.config.mjs`
(the typescript-eslint parser and only the `complexity` rule, inline
`eslint-disable` comments ignored), so the project's ESLint setup neither
helps nor breaks the gate. ESLint 9.x or later is assumed.

```text
64 tokens  src/ledger.ts:13-15 (changed)  ==  src/legacy.ts:30-32
1 of 3 clone(s) touch changed lines
```

jscpd ignores `node_modules`, `dist`, `build`, `coverage`, `tmp`, `*.d.ts`,
and `*.min.js`. A clone with only unchanged sides is pre-existing and not
listed unless you pass `--all`.

```text
Survived   src/ledger.ts:14  EqualityOperator  amount >= total
NoCoverage src/ledger.ts:21  BooleanLiteral  true
mutation score 50.0% (Killed 2, NoCoverage 1, Survived 1)
```

Kill a survivor with a test that fails under the replacement. `NoCoverage`
means no test reaches the line. `Ignored`, `CompileError`, and `RuntimeError`
mutants do not count toward the score; `Timeout` counts as detected.

sf-js runs Stryker with `tmp/stryker/stryker.config.mjs`, which loads the
project's own Stryker config file (`stryker.config.*`, `stryker.conf.*`, or
their dotted forms) when there is one and redirects the JSON report. Under a
linked `node_modules` (pnpm), where Stryker cannot find the project's runner
and checker plugins from its own location, it appends them to `appendPlugins`.
Stryker's sandbox stays in its default `.stryker-tmp/`.

## Conventions the tools rely on

- Property tests use fast-check and live in files named `*.property.test.*`, so `sf-js test` and `sf-js coverage` leave them out and `sf-js property` runs only them.
- Vitest projects use the v8 coverage provider (`@vitest/coverage-v8`); Jest uses its default Istanbul provider.
- Gherkin acceptance tests use the Acceptance Pipeline Specification tools; the project's acceptance runtime and step handlers are JavaScript or TypeScript tests that `sf-js test` runs.
- There is no mutation scan/count mode for JavaScript. Use the `cc` column of `sf-js crap --all` as the hint that a file may mix jobs.

## Recommendations

- QA: drive user-facing web UI checks with [Playwright](https://playwright.dev).
- Architect: check module boundaries with [dependency-cruiser](https://github.com/sverweij/dependency-cruiser), for example a rule that domain modules never import adapters.

## Testing sf-js itself

```sh
bb --config swarmforge/tools/javascript/bb.edn test
bb --config swarmforge/tools/javascript/bb.edn property
swarmforge/bin/sf tool ensure gherkin-parser
bb --config swarmforge/tools/javascript/bb.edn acceptance [feature ...]
bash swarmforge/tools/javascript/qa/sf-js-qa.sh
```

The first runs the unit tests against fixture reports, including
`sfjs.architecture-test`, which reads the `ns` forms under
`swarmforge/tools/` and fails when a policy namespace requires an IO one
(`sfcore.shell`, `sfcore.git`, a CLI, `babashka.fs`, `babashka.process`), when
the shared core requires a language namespace, or on an import cycle. The
second runs the test.check properties under `property/` for sf-js and the
shared core. The third and fourth parse `swarmforge/tools/javascript/features/*.feature`, generate entry points
under `tmp/sf-js/acceptance/`, and drive the real `sf-js` in scratch
repositories with fake `node`, `npm`, `npx`, `pnpm`, `yarn`, `bun`, and `bunx`
on PATH. For Gherkin acceptance mutation, generate first with the
`acceptance` task, then pass the adapter and that feature's generated
directory to the mutator:

```sh
gherkin-mutator --feature swarmforge/tools/javascript/features/crap.feature \
  --generated-dir tmp/sf-js/acceptance/generated/crap \
  --work-dir tmp/sf-js/mutation/crap \
  --runner-worker "bb --config swarmforge/tools/javascript/bb.edn acceptance-worker"
```

The last runs the end-to-end QA procedure `qa/sf-js.md` against real
npm, pnpm, yarn and bun projects under `tmp/qa/`, with the real Vitest, Jest,
tsc, ESLint, jscpd and StrykerJS; it needs the npm registry.

The shared core in `swarmforge/tools/core/` serves `sf-cs` as well. Its
policy namespaces are pure: `sfcore.changes` (diff parsing, path matching),
`sfcore.crap`, `sfcore.jscpd`, `sfcore.stryker` (each with the `review` both
wrappers print), and `sfcore.outcome` (exit statuses, usage errors). Its IO
lives in `sfcore.git` (the task's changed lines) and `sfcore.shell`
(processes, PATH, printing).
