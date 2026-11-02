# JavaScript and TypeScript tooling

Run `swarmforge/bin/sf-js` from the task worktree beside `package.json`.
It needs Babashka, Node, `npx`, and the project's package manager. Reports go
under `./tmp/`; commands are echoed as `$ ` lines.

| Command | Behavior |
|---|---|
| `tools` | Check prerequisites, install missing development tools in one sorted package-manager command, and check `jscpd@4`. Existing package versions are left alone. |
| `test [runner args]` | Typecheck with `tsc --noEmit` when `tsconfig.json` exists, then run unit/acceptance tests excluding `*.property.test.*`. A type error skips tests. |
| `property [runner args]` | Run only `*.property.test.*`, without typechecking. Use fast-check for properties. |
| `coverage [runner args]` | Run tests excluding properties; write Istanbul JSON to `tmp/coverage/coverage-final.json`. Vitest uses `@vitest/coverage-v8`; Jest uses its default provider. |
| `crap [--all] [--base REF] [--report DIR]` | Join linter complexity with Istanbul coverage; report changed functions and fail above CRAP 10. `--all` includes every function. |
| `dry [--all] [--base REF] [--min-tokens N] [--report-only]` | Report jscpd clones touching changed lines, or all with `--all`; JSON goes under `tmp/jscpd/`. |
| `mutate [--base REF] [--all] [--report-only] [-- stryker args]` | Run StrykerJS incrementally on changed production sources; report `Survived` and `NoCoverage` mutants in those files. `--all` uses Stryker's configured scope and judges every reported mutant. |

Checks exit 0 when clean, 1 on findings, failing tests, or type errors, and 2
on usage/input errors (including missing reports, lockfiles, or runners).
Errors use the `sf-js:` stderr prefix. `--report-only` reuses the last DRY or
mutation report with the same changed-file scope.

## Package manager and runner

- `package.json`'s `packageManager` selects npm, pnpm, yarn, or bun and
  requires its lockfile. Without it, exactly one manager's lockfile must
  exist: `package-lock.json`/`npm-shrinkwrap.json`, `pnpm-lock.yaml`,
  `yarn.lock`, or `bun.lock`/`bun.lockb`.
- Project tools run through that manager (`npx --no-install`, `pnpm exec`,
  `yarn`, or `bunx`); jscpd always uses `npx --yes jscpd@4`.
- `test`, `property`, and `coverage` prefer Vitest, then Jest, from either
  dependency section. `mutate` and `tools` prefer Jest when both are listed;
  `tools` installs Vitest when neither exists.
- The current directory's `package.json` is the scope. Workspaces and
  monorepos are unsupported.

`tools` installs the runner and coverage package, TypeScript, the complexity
linter, Stryker core and runner plugin, fast-check, and Stryker's TypeScript
checker when `tsconfig.json` exists. It selects ESLint plus typescript-eslint
only when ESLint is already listed without oxlint; otherwise it selects oxlint.

Missing packages are added without version constraints. Keep compatible
versions in the project: typescript-eslint's TypeScript peer range may be
narrower than the latest release, and StrykerJS 10 fails on TypeScript 7.
Pin a compatible TypeScript before `tools` or adjust it afterwards. Test real
npm/pnpm/yarn/bun installs when upgrading these integrations; fake-tool tests
do not establish package or plugin compatibility.

## Changed code

Scope includes committed, working-tree, and untracked changes since the merge
base with `--base` (default: `SWARMFORGE_BASE`, then `main`, then `master`).
Supported extensions are `.js`, `.jsx`, `.ts`, `.tsx`, `.mjs`, `.cjs`, `.mts`,
and `.cts`; declaration files are excluded. A deletion marks the following
line changed, or line 1 at the top of a file.

CRAP and mutation exclude tests (`*.test.*`, `*.spec.*`, `*.property.test.*`,
and `__tests__/`). No changed production source means no Stryker run.
jscpd ignores `node_modules`, `dist`, `build`, `coverage`, `tmp`, `*.d.ts`,
and `*.min.js`.

## Complexity source

CRAP uses oxlint when listed, else ESLint when listed, else oxlint. Both use
wrapper-owned configurations with only the complexity rule; project linter
configuration and `eslint-disable` comments do not affect the gate.

- oxlint must be 1.62.0 or later and produce JSON with an enabled complexity
  rule. Otherwise the command exits 2 without falling back to ESLint.
- oxlint still honors its own disable directives. Directives hiding the
  complexity rule produce warnings without changing the exit status.
- ESLint requires 9.x or later and the typescript-eslint parser. Its normal
  complexity findings are not tool failures.
- Parse/semantic errors or unreadable files prevent analysis: the command
  exits 2, reports the linter error, and prints no CRAP table. Verify real
  linter-version compatibility and parity when upgrading either integration.

CRAP is `complexity² × (1 − coverage)³ + complexity`. A function counts as
changed when its span contains a changed line. Coverage counts its own
Istanbul statements, excluding nested functions' statements; a statement-free
function is covered if called. A function missing from coverage counts as
0% and spans only its head. Unnamed functions print as `(anonymous)`.
Use the `cc` column of `crap --all` to review module responsibilities;
JavaScript has no mutation scan/count mode.

## Security scanners

- Code scanner, on the changed files defined under "Changed code"; it needs
  uv and semgrep.dev:
  `uvx semgrep@1.183.0 scan --config p/javascript --config p/typescript --metrics=off --error <changed files>`
- Dependency audit, when the task changes `package.json` or a lockfile listed
  under "Package manager and runner": the detected manager's `npm audit`,
  `pnpm audit`, `yarn npm audit --all --recursive` (Yarn 2+: every workspace
  and transitive packages), `yarn audit` (Yarn 1) or `bun audit`. It needs the
  package registry.

## Mutation and output

Stryker uses four workers unless overridden, its project config when present,
and the selected runner; TypeScript projects also use its checker. Reports
and incremental state are under `tmp/stryker/`; its sandbox is `.stryker-tmp/`.
Changed filenames are treated literally, including glob characters. A comma
in a changed source filename is unsupported and exits 2 before Stryker runs.

```text
OVER crap   10.8  cc   4  cov  25%  src/ledger.ts:10 withdraw
64 tokens  src/ledger.ts:13-15 (changed)  ==  src/legacy.ts:30-32
Survived   src/ledger.ts:14  EqualityOperator  amount >= total
NoCoverage src/ledger.ts:21  BooleanLiteral  true
```

Improve coverage or split mixed-duty functions to reduce CRAP. Kill survivors
with tests that fail under their replacements; `NoCoverage` means no test
reaches the line. Mutation scores exclude `Ignored`, `CompileError`, and
`RuntimeError`; `Timeout` counts as detected.

Gherkin uses the shared Acceptance Pipeline Specification tools and the
project's JavaScript/TypeScript runtime and handlers, run through `sf-js test`.
