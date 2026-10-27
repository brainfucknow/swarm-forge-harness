# sf-js end-to-end QA procedure

This procedure runs `swarmforge/bin/sf-js` from the command line against
small real JavaScript and TypeScript projects. It uses the real tools:
Vitest, Jest, tsc, oxlint, ESLint, jscpd and StrykerJS. Check only what a user sees:
the exit status, stdout, stderr, the printed `$ ` command lines, and files
under the project. Do not call sf-js internals.

Exit statuses: 0 clean, 1 finding, 2 usage or input error.

## Prerequisites

- `git`, `bb`, `node` (18 or later), `npm`, `pnpm` and network access to the npm registry.
- `yarn` and `bun` are optional. If one is missing, mark its checks SKIPPED with the reason.
- `SFJS` is the absolute path of `swarmforge/bin/sf-js` in the task worktree.
- Work under the task worktree's `./tmp/qa/`, which is never committed.

## Fixture projects

Build each project as its own git repository. Create `main` with the base
files, commit, then `git switch -c swarm/qa`. Every sf-js command runs from
the project root with `--base main` unless the step says otherwise.

### P1: `tmp/qa/vitest-ts` (npm, Vitest, TypeScript)

- `package.json` with `"type": "module"`, no `packageManager` field, and `vitest` and `typescript@<7` in devDependencies. With oxlint nothing holds `typescript` back, and `sf-js tools` would otherwise add the latest one, which StrykerJS may not run. Run `npm install` so that `package-lock.json` exists.
- A `tsconfig.json` with `"strict": true`, `"noEmit": true` and `"include": ["src"]`.
- `src/ledger.ts` exports `deposit(total, amount)` (one statement) and `withdraw(total, amount, overdraft)`. `withdraw` has two `if` branches and throws on an overdraft that is not allowed. One blank line follows `deposit`, then a comment line `// withdraw` directly above `withdraw`'s first line.
- `src/ledger.test.ts` tests `deposit` and only the happy path of `withdraw`.
- `src/ledger.property.test.ts` holds one fast-check property on `deposit`. Its test name is `PROPERTY-ONLY`.
- `src/legacy.ts` holds a 20-line block of code.
- package.json lists neither `oxlint` nor `eslint`, so sf-js uses oxlint for complexity.
- `.oxlintrc.json` at the root and `src/.oxlintrc.json` each hold `{"rules": {"eslint/complexity": "off"}}`. If sf-js read either one, it would get no complexity. This proves that sf-js uses its own oxlint config and skips nested ones.

### P2: `tmp/qa/jest-js` (pnpm, Jest, plain JavaScript)

- `package.json` with `"packageManager": "pnpm@<installed pnpm version>"`, and `jest`, `eslint` and `typescript` in devDependencies, `typescript` pinned to the range `typescript-eslint` declares as its peer (pnpm would otherwise take a newer `typescript` than typescript-eslint accepts). Run `pnpm install` so that `pnpm-lock.yaml` exists.
- No `tsconfig.json`.
- `src/cart.js` (CommonJS) exports `total(items)` with one `if`.
- `src/cart.test.js` and `src/cart.property.test.js`, the second one fast-check.
- `eslint.config.js` contains only `throw new Error("project ESLint config was loaded")`. This proves that sf-js uses its own ESLint config.

## QA-1 Help and usage

1. `$SFJS help`, `$SFJS --help`, `$SFJS -h`, and `$SFJS` with no arguments each exit 0. Each prints a usage that names `tools`, `test`, `property`, `coverage`, `crap`, `dry` and `mutate`.
2. `$SFJS nope` exits 2. Stderr has `sf-js: unknown command 'nope'`.
3. In an empty directory that is a git repository, `$SFJS test` exits 2 and stderr has `sf-js: no package.json`.

## QA-2 Package manager detection (P1 copy)

Work on a throwaway copy of P1, `tmp/qa/pm`.

1. Delete `package-lock.json`. `$SFJS test` exits 2 and stderr has `no lockfile`. No `$ ` line is printed.
2. Restore it and add `"packageManager": "pnpm@9.1.0"`. `$SFJS test` exits 2. Stderr has `packageManager names pnpm but the lockfile is package-lock.json`.
3. Remove the field and add an empty `yarn.lock`. `$SFJS test` exits 2. Stderr has `lockfiles for several package managers: package-lock.json, yarn.lock`.
4. Remove `yarn.lock` and set `"packageManager": "deno@2.0.0"`. `$SFJS test` exits 2 and stderr has `unknown packageManager 'deno@2.0.0'`.
5. Remove the field. `$SFJS test` prints a `$ npx --no-install vitest run` line.
6. If `yarn` is installed: run `yarn import` or `yarn install` to get a `yarn.lock`, remove `package-lock.json`, and set `"packageManager": "yarn@<version>"`. `$SFJS test` prints `$ yarn vitest run`. Do the same with `bun` (`bun install`, giving `bun.lock`) and expect `$ bunx vitest run`.

## QA-3 Tools (P1, P2)

1. In P1, `$SFJS tools` exits 0. It prints `$ npm install --save-dev` followed by the missing packages in sorted order. Afterwards devDependencies in `package.json` hold all of these: `vitest`, `@vitest/coverage-v8`, `typescript`, `oxlint`, `@stryker-mutator/core`, `@stryker-mutator/vitest-runner`, `@stryker-mutator/typescript-checker` and `fast-check`. They hold neither `eslint` nor `typescript-eslint`. A `$ npx --yes jscpd@4 --version` line is printed.
2. Run `$SFJS tools` in P1 again. It exits 0, prints `all JavaScript tools present`, and prints no `npm install` line.
3. In P2, `$SFJS tools` exits 0 and prints `$ pnpm add --save-dev`. devDependencies now hold `@stryker-mutator/jest-runner`, `typescript` and `typescript-eslint`. They hold none of `vitest`, `@stryker-mutator/typescript-checker` and `oxlint`.
4. Run with a PATH that has no `node` (for example `env PATH=/usr/bin:/bin $SFJS tools`, provided node is not in those directories). It exits 2, stderr has `missing on PATH: node`, and nothing is installed.

## QA-4 Tests and typecheck (P1)

1. `$SFJS test` exits 0. Its `$ npx --no-install tsc --noEmit` line comes before its `$ npx --no-install vitest run` line. Vitest's output lists `src/ledger.test.ts` and does not list `PROPERTY-ONLY`.
2. `$SFJS test --reporter=verbose` passes `--reporter=verbose` through; it is at the end of the vitest `$ ` line.
3. Add `const n: number = "x";` to `src/ledger.ts`. `$SFJS test` exits 1, shows the tsc error, and prints no vitest line. Revert the change.
4. Break an assertion in `src/ledger.test.ts`. `$SFJS test` exits 1. Revert it.
5. `$SFJS property` exits 0. It runs only `src/ledger.property.test.ts` (`PROPERTY-ONLY` appears) and prints no `tsc` line.

## QA-5 Tests with Jest (P2)

1. `$SFJS test` exits 0. It prints `$ pnpm exec jest`, runs `src/cart.test.js`, and does not run the property test. It prints no `tsc` line.
2. `$SFJS property` exits 0 and runs only `src/cart.property.test.js`.

## QA-6 Coverage and CRAP (P1)

1. `$SFJS coverage` exits 0 and prints `coverage written to tmp/coverage/coverage-final.json`. The file exists and is Istanbul JSON: each file's entry has `fnMap`, `statementMap`, `s` and `f`.
2. With no change since `main`, `$SFJS crap --base main` exits 0 and ends with `0 changed function(s), 0 over CRAP 10`. It runs no linter, so it prints no `$ ` line.
3. On `swarm/qa`, give `withdraw` four more `if` branches that the tests do not reach, and commit. Run `$SFJS coverage`, then `$SFJS crap --base main`. It exits 1. It lists an `OVER crap` line for `src/ledger.ts:<line> withdraw` with `cc` of 7 or more and coverage below 100%. It does not list `deposit`. The last line reads `1 changed function(s), 1 over CRAP 10`. It prints a `$ npx --no-install oxlint` line and no `eslint` line, and prints no oxlint JSON (stdout does not contain `"diagnostics"`). The two `.oxlintrc.json` files did not hide it.
4. Add tests that cover every branch. Rerun coverage and crap. The `withdraw` line now starts `ok`, and the run exits 0 when the CRAP is 10 or below. If it is not, split the function and check again.
5. `$SFJS crap --all` lists `deposit` as well, worst CRAP first, and ends with `N function(s), M over CRAP 10`.
6. Change an anonymous arrow function, for example a `.map((x) => …)` callback, so that it is covered by no test. After coverage and crap, it shows as `(anonymous)`.
7. Delete `tmp/coverage`. `$SFJS crap --base main` exits 2 and stderr has `no coverage-final.json under tmp/coverage; run sf-js coverage first`.

## QA-7 Coverage and CRAP (P2)

1. `$SFJS coverage` exits 0, and `tmp/coverage/coverage-final.json` exists. The jest `$ ` line contains `--coverageDirectory=tmp/coverage`.
2. Change `total` on `swarm/qa` and commit. `$SFJS crap --base main` prints `$ pnpm exec eslint` and lists `src/cart.js:<line> total`. It prints no `oxlint` line and does not fail on the throwing `eslint.config.js`.

## QA-8 DRY (P1)

1. On `swarm/qa`, copy the 20-line block from `src/legacy.ts` into a new committed file `src/copy.ts`. `$SFJS dry --base main` prints `$ npx --yes jscpd@4` with `--format javascript,typescript,jsx,tsx` and exits 1. It lists a line `<tokens> tokens  src/copy.ts:<a>-<b> (changed)  ==  src/legacy.ts:<c>-<d>` (the sides may be in either order). The last line reads `1 of 1 clone(s) touch changed lines`.
2. `$SFJS dry --report-only --base main` prints the same findings without a `$ ` line.
3. Copy the same block into `node_modules/x/index.js`, `dist/out.js` and `src/types.d.ts`. `$SFJS dry --base main` reports no clone that involves those files.
4. Delete `src/copy.ts` and commit. `$SFJS dry --base main` exits 0.
5. `$SFJS dry --base main --min-tokens 500` prints `--min-tokens 500` on the jscpd line.
6. Leave `src/copy2.ts`, holding the block, untracked. `$SFJS dry --base main` exits 1 and marks `src/copy2.ts` as `(changed)`.

## QA-9 Mutation (P1)

1. Change `withdraw`'s comparison on `swarm/qa`, for example `>` to `>=`. Do not add a test that tells the two apart, and commit. `$SFJS mutate --base main` prints `$ npx --no-install stryker run`. That line contains `--mutate src/ledger.ts`, `--incremental`, `--testRunner vitest`, `--checkers typescript` and `--concurrency 4`. The run exits 1 and lists at least one `Survived` or `NoCoverage` line for `src/ledger.ts`. The last line starts `mutation score `. `tmp/stryker/mutation.json` and `tmp/stryker/incremental.json` exist.
2. `$SFJS mutate --report-only` prints the same findings and runs no Stryker.
3. Add tests that kill the listed mutants. `$SFJS mutate --base main` exits 0. The run reuses the incremental file: Stryker's progress shows mutants reused from the incremental report.
4. On a fresh branch from `main`, change only `src/ledger.test.ts` and commit. `$SFJS mutate --base main` exits 0, prints `no changed source files to mutate`, and runs no Stryker.
5. `$SFJS mutate --base main -- --concurrency 2` ends the Stryker line with `--concurrency 2` and does not contain `--concurrency 4`.
6. In P2, change `src/cart.js`, commit, and run `$SFJS mutate --base main`. The line runs `pnpm exec stryker run` with `--testRunner jest` and without `--checkers`.

## QA-9a Mutation scope (P1)

Continue from the end of QA-9 step 3, where every mutant in `src/ledger.ts` is killed.

1. `$SFJS mutate --all` exits 1. It lists at least one `Survived` or `NoCoverage` line for `src/legacy.ts`, which no test covers. The incremental file now holds those mutants.
2. Make one more committed change to `src/ledger.ts` that the tests still pin, for example rename a local variable. `$SFJS mutate --base main` exits 0. It lists no line for `src/legacy.ts`. Its last line starts `mutation score 100.0%` and has no `Survived` or `NoCoverage` count.
3. `$SFJS mutate --report-only` prints the same findings and the same last line as step 2, and runs no Stryker.
4. `$SFJS mutate --report-only --all` exits 1 and lists the `src/legacy.ts` lines again.

## QA-9b Literal mutate paths (P1)

Work on a fresh branch from `main`.

1. Add `src/pages/[id].ts` and `src/pages/[...slug].ts`, each exporting a function with a comparison, and a test file that calls both without telling `>` from `>=`. Commit. `$SFJS mutate --base main` prints a Stryker line that contains `--mutate src/pages/[[]...slug[]].ts,src/pages/[[]id[]].ts`. Stryker reports mutants for both files: the run lists `Survived` lines for `src/pages/[id].ts` and `src/pages/[...slug].ts`, and `tmp/stryker/mutation.json` has an entry for each.
2. Add and commit `src/a,b.ts`, a small module. `$SFJS mutate --base main` exits 2. Stderr has `cannot mutate src/a,b.ts: Stryker splits --mutate on commas`. No `$ ` Stryker line is printed.
3. Rename it to `src/a-b.ts` and commit. `$SFJS mutate --base main` runs Stryker again.

## QA-10 Base branch

1. In a copy of P1 whose base branch is renamed to `master`, `$SFJS dry --report-only` (with no `--base`) works as it does with `main`.
2. With the base branch renamed to `trunk`, `$SFJS dry --report-only` exits 2 and stderr has `no base branch found`. `SWARMFORGE_BASE=trunk $SFJS dry --report-only` works.

## QA-10a CRAP after deleting lines (P1)

Each step starts on a fresh branch from `main` and runs `$SFJS coverage` before `crap`.

1. Delete only the overdraft guard of `withdraw` (its `if` line and the `throw` inside it) and commit. No other line changes. `$SFJS crap --base main` lists `src/ledger.ts:<line> withdraw` and not `deposit`. The last line reads `1 changed function(s), N over CRAP 10`.
2. Delete only the blank line after `deposit` and commit. The comment line now follows the deletion, and it is outside both functions. `$SFJS crap --base main` exits 0 and ends with `0 changed function(s), 0 over CRAP 10`.
3. Delete only the `// withdraw` comment line and commit. `withdraw`'s first line now follows the deletion. `$SFJS crap --base main` lists `withdraw` and not `deposit`.
4. Repeat step 1 without committing. `$SFJS crap --base main` lists `withdraw` as in step 1.

## QA-10b ESLint analysis errors (P1 copy)

Work on a throwaway copy of P1, `tmp/qa/lint`, after `$SFJS coverage` has written `tmp/coverage/coverage-final.json`. Run `npm uninstall oxlint` and `npm install --save-dev eslint typescript-eslint`, so that sf-js takes complexity from ESLint.

1. Commit a syntax error inside `withdraw`, for example a line `const = ;`. `$SFJS crap --base main` exits 2. Stderr has `eslint could not analyze src/ledger.ts:<line>: Parsing error:` with the line of the syntax error. Stdout has no `function(s)` line.
2. `$SFJS crap --all` fails the same way.
3. Revert the syntax error. Break ESLint itself, for example by removing `node_modules/typescript-eslint`. `$SFJS crap --base main` exits 2 and stderr has `eslint exited 2`.
4. Restore `node_modules` (`npm install`). `$SFJS crap --base main` works again, and ESLint's exit status 1 from complexity messages does not fail it.

## QA-11 Installation through sf tool

1. From the task worktree, `swarmforge/bin/sf tool ensure sf-js` succeeds. `swarmforge/bin/sf tool require sf-js` prints `OK: sf-js <path>`.

## QA-12 Release checks

1. `bb --config swarmforge/tools/csharp/bb.edn test` still passes, which shows that the shared parsers did not change sf-cs.
2. The sf-js unit tests and the sf-js acceptance tests pass with their documented commands.
3. Check by hand, without a script (these are documentation, not behavior), that the following exist:
   - `swarmforge/languages/javascript.md`, describing every command, its outputs and the conventions above, with Playwright and dependency-cruiser named as recommendations
   - in it, oxlint as the complexity source (when package.json lists oxlint, or lists neither linter), ESLint when only eslint is listed, the oxlint 1.62.0 minimum, the `oxlint-disable` warning, and the tool packages `sf-js tools` adds for each case
   - the `sf-js` line in `swarmforge/tools.conf`
   - the JavaScript/TypeScript row in the engineering article's tool table and its Language Defaults bullet
   - the `swarmforge/README.md` row and the `README.md` mention
   - the CI step

## QA-13 oxlint and ESLint parity (P1 copy)

Work on a copy of P1 after QA-6, `tmp/qa/parity`, so that `withdraw` has its extra branches and the anonymous arrow function exists.

1. `$SFJS coverage`, then `$SFJS crap --all`. Save its function rows (every stdout line starting `OVER` or `ok`) as A. None of them names `src/ledger.test.ts`, `src/ledger.property.test.ts`, or a file under `node_modules`.
2. Run `npm uninstall oxlint` and `npm install --save-dev eslint typescript-eslint`. `$SFJS crap --all` prints a `$ npx --no-install eslint` line and no `oxlint` line. Its function rows, as a set, equal A: the same names, files, lines, `cc`, `cov` and `crap`.
3. Run `npm install --save-dev oxlint`, so that package.json lists both linters. `$SFJS crap --all` prints a `$ npx --no-install oxlint` line and no `eslint` line, and its rows equal A again.
4. Put `// eslint-disable-next-line complexity` on the line above `withdraw` and commit. `$SFJS crap --base main` still lists `withdraw` and stderr has no `warning`.
5. Change that comment to `// oxlint-disable-next-line complexity` and commit. `$SFJS crap --base main` no longer lists `withdraw`. Stderr has `sf-js: warning: src/ledger.ts:<line of the comment> has an oxlint-disable directive that can hide a function from CRAP`. The exit status is the one the remaining rows give: 1 only when one of them is `OVER`.
6. Change it to `// oxlint-disable-next-line no-debugger` and commit. `$SFJS crap --base main` lists `withdraw` again and stderr has no `warning`.

## QA-14 oxlint versions and a missing oxlint (P1 copy)

Work on another copy of P1 after QA-6, `tmp/qa/oxlint-version`. Each step changes `src/ledger.ts` on `swarm/qa` if it has to, so that `withdraw` is changed, and runs `$SFJS crap --base main`.

1. `npm install --save-dev oxlint@1.61.0`. crap exits 2. Stderr has `sf-js needs oxlint 1.62.0 or later`.
2. `npm install --save-dev oxlint@1.36.0`, a version without the complexity rule. crap exits 2. Stderr has `sf-js needs oxlint 1.62.0 or later`.
3. `npm install --save-dev oxlint@1.62.0`. crap lists `withdraw` and exits 0 or 1 by its CRAP.
4. `npm uninstall oxlint`, so that package.json lists neither linter. crap still prints a `$ npx --no-install oxlint` line, prints no `eslint` line, and exits 2. Stderr has `sf-js needs oxlint 1.62.0 or later`.
5. `$SFJS tools` prints `$ npm install --save-dev` with `oxlint` among the packages and without `eslint` or `typescript-eslint`. crap then lists `withdraw` again.
