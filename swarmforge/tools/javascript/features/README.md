# sf-js acceptance features

Each feature drives the real `sf-js` command in a scratch git repository with
fake `node`, `npm`, `npx`, `pnpm`, `yarn`, `bun`
and `bunx` on PATH. The fakes record their arguments, exit with a chosen
status, and write the reports a scenario sets up. Real-tool release checks
are in the [JavaScript guide](../../../languages/javascript.md#tool-compatibility-checks).

## Example tables

`gherkin-mutator` changes one example cell at a time, so the tables follow
two rules.

- When a Then step shows an input back (an error that names the command, a
  passed-through argument), the expected text has its own column, even when
  it repeats the input. A mutated input then no longer matches it.
- A value whose every small change gives the same result (a line inside or
  outside a range, a file that is not a source, a runner exit status above
  0, a version after `@`) is not a parameter. Such cases are plain
  Scenarios that share the outline's index, or a fixed value in the step.

## Step meanings

| Step | Meaning |
|---|---|
| `a task worktree branched from main` | A scratch repository whose `main` holds a base commit, checked out on a task branch made from it. The quoted form `branched from "<base>"` names the base branch instead. |
| `a main branch where "F" has N lines` | `F` exists on `main` with N lines before the task branch is made. |
| `fake JavaScript tools on PATH` | The fakes above come first on PATH. By default every fake exits 0. A fake runner run for coverage writes a coverage report. A fake jscpd writes an empty report. A fake Stryker writes an empty report unless a scenario sets one up. A fake ESLint reports no messages. A fake oxlint prints, on stdout, a JSON report with no diagnostics and `"number_of_rules": 1`. |
| `the fake T exits with status N` | The fake for tool `T` (vitest, jest, tsc, npm, …) exits N. |
| `the fake stryker exits with status N without a report` | Stryker exits N and leaves no `tmp/stryker/mutation.json`. |
| `the fake oxlint exits with status N without a report` | oxlint prints nothing on stdout and exits N. |
| `the fake oxlint runs no rules` | oxlint prints a JSON report with no diagnostics and `"number_of_rules": 0`, as an oxlint without the complexity rule does. |
| `package.json lists "P" in S` | `P` is a key in `S` (`dependencies` or `devDependencies`) of `./package.json`. |
| `package.json lists the devDependencies "A B"` | `devDependencies` holds exactly the space-separated names, and no other section lists a runner. An empty value means an empty `devDependencies`. |
| `package.json declares packageManager "X"` | `"packageManager": "X"` in `./package.json`. If no step declares it, the field is absent. |
| `the worktree has the lockfile "L"` | An empty file `L` at the worktree root, and a `package.json` (empty unless other steps fill it). |
| `tsconfig.json is present/absent` | Whether `./tsconfig.json` exists. |
| `"F" is added on the task branch` | A new 20-line file `F` committed on the task branch. |
| `line N of "F" changed on the task branch` | Line N of `F` is rewritten and committed on the task branch. The changes feature also uses "changed but not committed" and "changed on main after the task began". |
| `lines A to B of "F" are deleted on the task branch` | Lines A to B of `F` are removed and the change is committed on the task branch. |
| `line N of "F" reads "T" on the task branch` | Line N of `F` is replaced by the text `T` and committed on the task branch. |
| `"F" is untracked with N lines` | A new N-line file `F` that is not added to git. |
| `SWARMFORGE_BASE is "X"` | The environment variable for the sf-js run. An empty value means unset. |
| `ESLint reports "NAME" at line N of "F" with complexity C` | The fake ESLint reports a `complexity` message for `F` at line N. The message is `Function 'NAME' has a complexity of C. Maximum allowed is 0.` For `"(anonymous)"` it is `Arrow function has a complexity of C. Maximum allowed is 0.` |
| `ESLint reports a fatal "M" at line N of "F"` | The fake ESLint reports, for `F`, a message `M` at line N with `"fatal": true`, severity 2 and no `ruleId`, as ESLint does for a parse error. |
| `ESLint reports a non-fatal "M" at line N of "F"` | The same message with severity 1, no `fatal` field and no `ruleId`. |
| `oxlint reports "NAME" at line N of "F" with complexity C` | The fake oxlint's report holds a diagnostic with `"code": "eslint(complexity)"`, `"filename": "F"` (relative to the worktree root), one label whose span has `"line": N`, and the message ``function `NAME` has a complexity of C. Maximum allowed is 0.`` For `"(anonymous)"` it is `function has a complexity of C. Maximum allowed is 0.` |
| `oxlint reports "M" with code "K" at line N of "F"` | The same diagnostic with message `M` and code `K`. |
| `oxlint reports an error "M" at line N of "F"` | The fake oxlint's report holds a diagnostic with no `code`, `"severity": "error"`, message `M`, `"filename": "F"` (relative to the worktree root) and one label whose span has `"line": N`, as oxlint reports a file it could not parse. |
| `oxlint reports an error "M" with no file` | The fake oxlint's report holds a diagnostic with no `code`, `"severity": "error"`, message `M`, no `filename` and empty `labels`, as oxlint reports a file it could not open. |
| `Istanbul covers K of S statements of "NAME" on lines A to B of "F"` | `tmp/coverage/coverage-final.json` has a function `NAME` spanning lines A to B of `F`. It holds S statements of its own, K of them with a hit count above 0. The function's own hit count is above 0 when K is. |
| `Istanbul counts N calls of "NAME" on lines A to B of "F" with no statements` | The same, with no statements and a function hit count of N. |
| `the coverage report is moved to "D"` | The coverage report is moved to `D/coverage-final.json`. |
| `the jscpd report has a T-token clone of "A" lines a to b and "B" lines c to d` | `tmp/jscpd/jscpd-report.json` holds that duplicate. |
| `the Stryker report has …` | `tmp/stryker/mutation.json` (mutation-testing-elements schema) holds those mutants, and a fake Stryker run writes that same report instead of an empty one. |
| `"C" is not on PATH` | The fake for `C` is removed, and no real `C` can be found. |
| `I run "sf tool ensure sf-js" and then "sf tool require sf-js"` | Runs both `sf` commands in a scratch repository with `swarmforge/` installed. The step reports the second command's status and output. |
| `I run sf-js with "ARGS"` | Runs `swarmforge/bin/sf-js ARGS` in the worktree, split on spaces. |
| `sf-js ran "X"` | sf-js printed a `$ ` line whose command starts with `X`. |
| `sf-js ran "X" before "Y"` | Both lines were printed, and X's line came first. |
| `sf-js did not run "X"` | No printed `$ ` line contains `X`. |
| `sf-js added the devDependencies "A B"` | One add command line was printed: `npm install --save-dev`, `pnpm add --save-dev`, `yarn add --dev` or `bun add --dev`. The arguments that follow it are exactly `A B`, in that order. |
| `sf-js ran nothing` | sf-js printed no `$ ` line. |
| `the command line that runs "T" …` | The printed `$ ` line that runs tool `T`. `contains` and `does not contain` test it as a substring; `ends with` tests its end. |
| `the output has the line "X"` / `ends with the line` / `output line N is` | Stdout lines, compared exactly. `output line N` counts only lines that are not `$ ` command lines. |
| `the output mentions "X"` / `does not mention` | Substring of stdout. |
| `the error output contains "X"` / `does not contain` | Substring of stderr. |
