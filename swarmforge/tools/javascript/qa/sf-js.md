# sf-js end-to-end QA procedure

This procedure runs `swarmforge/bin/sf-js` from the command line against
small real JavaScript and TypeScript projects. It uses the real tools:
Vitest, Jest, tsc, ESLint, jscpd and StrykerJS. Check only what a user sees:
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

- `package.json` with `"type": "module"`, no `packageManager` field, and `vitest` in devDependencies. Run `npm install` so that `package-lock.json` exists.
- A `tsconfig.json` with `"strict": true`, `"noEmit": true` and `"include": ["src"]`.
- `src/ledger.ts` exports `deposit(total, amount)` (one statement) and `withdraw(total, amount, overdraft)`. `withdraw` has two `if` branches and throws on an overdraft that is not allowed.
- `src/ledger.test.ts` tests `deposit` and only the happy path of `withdraw`.
- `src/ledger.property.test.ts` holds one fast-check property on `deposit`. Its test name is `PROPERTY-ONLY`.
- `src/legacy.ts` holds a 20-line block of code.
- `eslint.config.js` contains only `throw new Error("project ESLint config was loaded")`. This proves that sf-js uses its own ESLint config.

### P2: `tmp/qa/jest-js` (pnpm, Jest, plain JavaScript)

- `package.json` with `"packageManager": "pnpm@<installed pnpm version>"` and `jest` in devDependencies. Run `pnpm install` so that `pnpm-lock.yaml` exists.
- No `tsconfig.json`.
- `src/cart.js` (CommonJS) exports `total(items)` with one `if`.
- `src/cart.test.js` and `src/cart.property.test.js`, the second one fast-check.

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

1. In P1, `$SFJS tools` exits 0. It prints `$ npm install --save-dev` followed by the missing packages in sorted order. Afterwards devDependencies in `package.json` hold all of these: `vitest`, `@vitest/coverage-v8`, `typescript`, `eslint`, `typescript-eslint`, `@stryker-mutator/core`, `@stryker-mutator/vitest-runner`, `@stryker-mutator/typescript-checker` and `fast-check`. A `$ npx --yes jscpd@4 --version` line is printed.
2. Run `$SFJS tools` in P1 again. It exits 0, prints `all JavaScript tools present`, and prints no `npm install` line.
3. In P2, `$SFJS tools` exits 0 and prints `$ pnpm add --save-dev`. devDependencies now hold `@stryker-mutator/jest-runner` and `typescript`. They hold neither `vitest` nor `@stryker-mutator/typescript-checker`.
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
2. With no change since `main`, `$SFJS crap --base main` exits 0 and ends with `0 changed function(s), 0 over CRAP 10`. It does not fail on the throwing `eslint.config.js`.
3. On `swarm/qa`, give `withdraw` four more `if` branches that the tests do not reach, and commit. Run `$SFJS coverage`, then `$SFJS crap --base main`. It exits 1. It lists an `OVER crap` line for `src/ledger.ts:<line> withdraw` with `cc` of 7 or more and coverage below 100%. It does not list `deposit`. The last line reads `1 changed function(s), 1 over CRAP 10`.
4. Add tests that cover every branch. Rerun coverage and crap. The `withdraw` line now starts `ok`, and the run exits 0 when the CRAP is 10 or below. If it is not, split the function and check again.
5. `$SFJS crap --all` lists `deposit` as well, worst CRAP first, and ends with `N function(s), M over CRAP 10`.
6. Change an anonymous arrow function, for example a `.map((x) => …)` callback, so that it is covered by no test. After coverage and crap, it shows as `(anonymous)`.
7. Delete `tmp/coverage`. `$SFJS crap --base main` exits 2 and stderr has `no coverage-final.json under tmp/coverage; run sf-js coverage first`.

## QA-7 Coverage and CRAP (P2)

1. `$SFJS coverage` exits 0, and `tmp/coverage/coverage-final.json` exists. The jest `$ ` line contains `--coverageDirectory=tmp/coverage`.
2. Change `total` on `swarm/qa` and commit. `$SFJS crap --base main` prints `$ pnpm exec eslint` and lists `src/cart.js:<line> total`.

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

## QA-10 Base branch

1. In a copy of P1 whose base branch is renamed to `master`, `$SFJS dry --report-only` (with no `--base`) works as it does with `main`.
2. With the base branch renamed to `trunk`, `$SFJS dry --report-only` exits 2 and stderr has `no base branch found`. `SWARMFORGE_BASE=trunk $SFJS dry --report-only` works.

## QA-11 Installation through sf tool

1. From the task worktree, `swarmforge/bin/sf tool ensure sf-js` succeeds. `swarmforge/bin/sf tool require sf-js` prints `OK: sf-js <path>`.

## QA-12 Release checks

1. `bb --config swarmforge/tools/csharp/bb.edn test` still passes, which shows that the shared parsers did not change sf-cs.
2. The sf-js unit tests and the sf-js acceptance tests pass with their documented commands.
3. Check by hand, without a script (these are documentation, not behavior), that the following exist:
   - `swarmforge/languages/javascript.md`, describing every command, its outputs and the conventions above, with Playwright and dependency-cruiser named as recommendations
   - the `sf-js` line in `swarmforge/tools.conf`
   - the JavaScript/TypeScript row in the engineering article's tool table and its Language Defaults bullet
   - the `swarmforge/README.md` row and the `README.md` mention
   - the CI step
