# Engineering Rules

## Startup Tools
- For applicable verification targets, procure the latest version of each required CRAP, mutation, and DRY tool for the project language directly from the listed `github.com/unclebob/...` repositories and get each one ready to run. A stage with no applicable target does not require tool installation.
- Resolve each listed repository at its latest available upstream version before installing or building it.
- Do not rely on stale cached, vendored, or preinstalled copies when a fresh GitHub install/build is possible in the current environment.
- Language tool table:
  - Go: install with `go install`; mutation `github.com/unclebob/mutate4go`, CRAP `github.com/unclebob/crap4go`, DRY `github.com/unclebob/dry4go`.
  - Clojure: install with Clojure CLI/deps.edn; mutation `github.com/unclebob/clj-mutate`, CRAP `github.com/unclebob/crap4clj`, DRY `github.com/unclebob/dry4clj`. `swarmforge/languages/clojure.md` explains how to run them on Babashka code.
  - Java: install with Maven (`mvn`); mutation `github.com/unclebob/mutate4java`, CRAP `github.com/unclebob/crap4java`, DRY `github.com/unclebob/dry4java`.
  - C#: install with `sf-cs tools`; mutation `sf-cs mutate` (Stryker.NET), CRAP `sf-cs crap` (coverlet Cobertura), DRY `sf-cs dry` (jscpd). `swarmforge/languages/csharp.md` maps every constitution tool to its `sf-cs` command and explains the output.
  - JavaScript/TypeScript: install with `sf-js tools`; tests `sf-js test` (Vitest, else Jest, after `tsc --noEmit`), mutation `sf-js mutate` (StrykerJS), CRAP `sf-js crap` (oxlint or ESLint complexity with Istanbul coverage), DRY `sf-js dry` (jscpd). `swarmforge/languages/javascript.md` maps every constitution tool to its `sf-js` command and explains the output.
- Install every listed tool with `sf tool ensure <tool>` and check it with `sf tool require <tool>`; `sf tool list` prints the catalogue. Wrappers land in `.swarmforge/bin/` at the repository root, ignored by git and shared by every task worktree, so a tool install never writes outside the project. Run tools through those wrappers (`sf tool bin` prints the directory to put on PATH); they carry the argument guardrails below.

## Language Defaults
- For Clojure projects, prefer Babashka where possible.
- For Clojure or Babashka projects, write Speclj specs, not `clojure.test`.
- For Java projects, avoid using Maven to run tests; build dedicated test runners and run those instead.
- For C# projects, run tests, coverage, CRAP, DRY, and mutation through `sf-cs`; mark property tests with `[Trait("Category", "Property")]`.
- For JavaScript and TypeScript projects, run tests, coverage, CRAP, DRY, and mutation through `sf-js`; write property tests with fast-check in files named `*.property.test.*`.

## Design And Testability
- Work in small, reviewable increments.
- Prefer the simplest design that supports the current behavior and leaves clear options for the next step.
- Keep tests close to the behavior being changed.
- Separate testable modules from environmentally unsuitable modules that open GUIs, depend on external devices, throw environment errors, emit system errors, or hang under automated tests. Maximize testable code and minimize the unsuitable boundary.
- IO-near modules must not reimplement a domain question. If a high-level module already answers it, call that module and translate the result. Walking the same facts again is a defect even when the dependency arrow already points inward.
- Only testable modules should participate in tools that run tests, including unit tests, acceptance tests, coverage, mutation testing, CRAP analysis, DRY analysis that invokes tests, and property tests.
- Keep property tests separate from normal verification. Do not include property-test tags in normal unit coverage, Gherkin acceptance mutation, language mutation tools, CRAP, or coverage commands unless the role owns property-test verification or the user explicitly asks for property tests.

## Acceptance Pipeline
- When executable Gherkin acceptance tests are useful, use github.com/unclebob/Acceptance-Pipeline-Specification and reuse the project's existing pipeline. Do not create acceptance scaffolding merely because a task visits a role.
- The Acceptance Pipeline Specification supplies `gherkin-parser`, `ir-dry-checker`, and `gherkin-mutator`. Install them with `sf tool ensure <tool>`; do not search `$HOME` or run `find` for binaries.
- Two-arg forms:
  - `gherkin-parser <feature> ./tmp/<stem>.json`
  - `ir-dry-checker <ir> ./tmp/<stem>.dry.json`
- Prefer the Babashka APS tools for `gherkin-parser`, `ir-dry-checker`, `gherkin-mutator`, and related APS support commands.
- Use Go-based APS tools only if the Babashka APS tools do not work in the current project environment.
- Project-specific acceptance pipeline components are the acceptance entrypoint generator, acceptance runtime, project step handlers, runner adapter, and convenience scripts.
- Gherkin acceptance mutation means running `gherkin-mutator` to mutate Gherkin example values.
- Gherkin acceptance mutation runs must report periodic progress/status so agents can distinguish normal long-running work from a hang.

## Verification
- Verify end-to-end behavior through the project's supported public interface: CLI, GUI, HTTP API, or library API as appropriate. Do not bypass that boundary by calling private implementation modules.
- Choose checks from the changed behavior and affected executable targets, within each role's ownership. Documentation-only work uses manual review and relevant existing checks, not prompt-text tests or source analyses with no target. Keep applicable source-quality gates and thresholds.
- Reuse passing results for the same relevant source revision and targets. Rerun when an edit, failure, or unresolved concern warrants it, not merely because the stage changed. Record the commands, revision, outcomes, and limits in commits so later roles can assess the evidence independently.
- Before running language, build, or test commands, prefer project-local cache/configuration paths inside the task worktree. Avoid default cache locations that write outside the project and may trigger sandbox or permission restrictions.
- Run constitution tools one at a time. Do not run CRAP, DRY, coverage, language mutation, Gherkin mutation, or structure-check concurrently.
- Tools that take a worker limit must use `--max-workers 4` or `--workers 4`.
- Function mutation is differential against the source manifest. Do not pass `--mutate-all`.
- Scan changed and new source files with the language mutation tool's scan/count mode as a hint that a module may mix jobs. Split a source file when it has more than one job. Do not split a one-job module to chase a mutation-site count. Preserve mutation manifests across the split; do not hand-edit them. C# has no scan/count mode; use the complexity column of `sf-cs crap --all` as that hint. JavaScript has none either; use the `cc` column of `sf-js crap --all`.
- For C#, "differential against the manifest" means `sf-cs mutate` without `--all`: Stryker mutates only files changed since the base branch.
- For JavaScript and TypeScript, it means `sf-js mutate` without `--all`: StrykerJS mutates only the changed source files, incrementally.
- Gherkin mutation is differential against the feature manifest (`--level hard`). Do not pass `--level full`.
- Run acceptance generation and acceptance tests sequentially.
- Avoid running whole-suite language test commands concurrently with acceptance generation.
- Before handoff, confirm the relevant local verification evidence is current for the affected targets.

## Guardrails
- Do not invent project-local CRAP, DRY, mutation, or coverage proxies. Install and run the constitution tools (`crap4clj` with cloverage, `dry4clj`, `clj-mutate`, speclj, `sf-cs`, `sf-js`, or the language table). Do not treat a homegrown `bb crap` / `bb coverage` / `bb mutation-count` task or a throwaway report-parsing script as those tools.
- Do not edit mutation testing or Gherkin acceptance mutation manifests by hand; allow approved mutation tools to update those manifests as part of their normal runs.
- Mutation manifests live on the branch `swarmforge/metrics`, not in tracked files: clj-mutate's under `.metrics/mutate/`, and the gherkin-mutator stamp and manifest block, which the `gherkin-mutator` wrapper keeps in `.metrics/gherkin/<feature path>` between runs. `sf start` loads them and `sf handoff` saves them. Do not commit `.metrics/` or a mutation block in a feature file.
- Do not commit unrelated local changes or generated artifacts unless required for the task.
- Before relying on an unfamiliar command, inspect local help or project documentation.
