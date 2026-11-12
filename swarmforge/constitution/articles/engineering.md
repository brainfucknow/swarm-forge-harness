# Engineering Rules

## Startup Tools
- For applicable verification targets, procure each required CRAP, mutation, and DRY tool for the project language directly from the listed `github.com/unclebob/...` repositories at the version pinned in `swarmforge/tools.conf`, and get each one ready to run. A stage with no applicable target does not require tool installation.
- Do not rely on stale cached, vendored, or preinstalled copies, or on a copy at another version, when a fresh GitHub install/build is possible in the current environment. `SWARMFORGE_TOOL_SRC` points `sf tool` at a local, unpinned checkout for tool development; do not cite it as pinned evidence.
- Use these language tools. Before running verification, read the applicable linked guide for wrapper commands, test conventions, and language-specific mutation scope:

  | Language | Mutation | CRAP | DRY | Setup / guide |
  |---|---|---|---|---|
  | Go | `github.com/unclebob/mutate4go` | `github.com/unclebob/crap4go` | `github.com/unclebob/dry4go` | `go install` |
  | Clojure | `github.com/unclebob/clj-mutate` | `github.com/unclebob/crap4clj` | `github.com/unclebob/dry4clj` | Clojure CLI/deps.edn; [Babashka guide](../../languages/clojure.md) |
  | Java | `github.com/unclebob/mutate4java` | `github.com/unclebob/crap4java` | `github.com/unclebob/dry4java` | Maven (`mvn`) |
  | C# | Stryker.NET | Coverlet | jscpd | [`sf-cs tools`](../../languages/csharp.md) |
  | F# | Unsupported (exit 2) | Coverlet compiled methods | jscpd | [`sf-fs tools`](../../languages/fsharp.md) |
  | VB.NET | Unsupported (exit 2) | Coverlet compiled methods | jscpd | [`sf-vb tools`](../../languages/vbnet.md) |
  | JavaScript/TypeScript | StrykerJS | oxlint or ESLint + Istanbul | jscpd | [`sf-js tools`](../../languages/javascript.md) |
  | Ruby | mutant (optional: needs the project's mutant usage) | RuboCop or flog + SimpleCov | jscpd or flay | [`sf-rb`](../../languages/ruby.md) |

- Install every listed tool with `sf tool ensure <tool>` and check it with `sf tool require <tool>`; `sf tool list` prints the catalogue. Cite the `@<ref>` that ends each `INSTALLED:` line of `sf tool ensure` for each tool you ran in your `Tested` trailers. Wrappers land in `.swarmforge/bin/` at the repository root, ignored by git and shared by every task worktree, so a tool install never writes outside the project. Run tools through those wrappers (`sf tool bin` prints the directory to put on PATH); they carry the argument guardrails below.

## Language Defaults
- For Clojure projects, prefer Babashka where possible.
- For Clojure or Babashka projects, write Speclj specs, not `clojure.test`.
- For Java projects, avoid using Maven to run tests; build dedicated test runners and run those instead.
- For C#, F#, VB.NET, JavaScript/TypeScript, and Ruby, use the linked language wrappers and property-test conventions above.

## Design And Testability
- Work in small, reviewable increments.
- Prefer the simplest design that supports the current behavior and leaves clear options for the next step.
- Keep tests close to the behavior being changed.
- Separate testable modules from environmentally unsuitable modules that open GUIs, depend on external devices, throw environment errors, emit system errors, or hang under automated tests. Maximize testable code and minimize the unsuitable boundary.
- IO-near modules must call existing high-level policy and translate the result, not recompute the same domain answer.
- Exclude environmentally unsuitable adapters from any analysis that runs tests: unit, acceptance, property, coverage, mutation, CRAP, and test-running DRY checks.
- Keep property tests separate from normal verification. Do not include property-test tags in normal unit coverage, Gherkin acceptance mutation, language mutation tools, CRAP, or coverage commands unless the role owns property-test verification or the user explicitly asks for property tests.

## Acceptance Pipeline
- When executable Gherkin acceptance tests are useful, use github.com/unclebob/Acceptance-Pipeline-Specification and reuse the project's existing pipeline. Do not create acceptance scaffolding merely because a task visits a role.
- The Acceptance Pipeline Specification supplies `gherkin-parser`, `ir-dry-checker`, and `gherkin-mutator`. Install them with `sf tool ensure <tool>`; do not search `$HOME` or run `find` for binaries.
- Two-arg forms:
  - `gherkin-parser <feature> ./tmp/<stem>.json`
  - `ir-dry-checker <ir> ./tmp/<stem>.dry.json`
- Prefer Babashka APS tools; use their Go equivalents only when the Babashka versions do not work in this environment.
- Gherkin acceptance mutation means running `gherkin-mutator` to mutate Gherkin example values.
- Gherkin acceptance mutation runs must report periodic progress/status so agents can distinguish normal long-running work from a hang.

## Verification
- Verify end-to-end behavior through the public interface exercised by the approved workflow: CLI, GUI, HTTP API, or library API as appropriate. Do not substitute lower-level checks or private implementation calls for that workflow.
- Choose checks from the changed behavior and affected executable targets, within each role's ownership. Documentation-only work uses manual review and relevant existing checks, not prompt-text tests or source analyses with no target. Keep applicable source-quality gates and thresholds.
- Reuse passing results for the same relevant source revision and targets. Rerun when an edit, failure, or unresolved concern warrants it, not merely because the stage changed. Record the commands, revision, outcomes, and limits in commits so later roles can assess the evidence independently.
- Before running language, build, or test commands, prefer project-local cache/configuration paths inside the task worktree. Avoid default cache locations that write outside the project and may trigger sandbox or permission restrictions.
- Run constitution tools one at a time. Do not run CRAP, DRY, coverage, language mutation, Gherkin mutation, or structure-check concurrently.
- Tools that take a worker limit must use `--max-workers 4` or `--workers 4`.
- Function mutation is differential against the source manifest. Do not pass `--mutate-all`.
- Gherkin mutation is differential against the feature manifest (`--level hard`). Do not pass `--level full`.
- Run acceptance generation and acceptance tests sequentially.
- Avoid running whole-suite language test commands concurrently with acceptance generation.
- Before handoff, confirm the relevant local verification evidence is current for the affected targets.

## Source Quality Gates
- CRAP must be 10 or below for the targets the role owns (20 for Ruby's `sf-rb crap --complexity flog`, whose ABC score runs higher). A single `cond` or `case` answering one question may stay above 10. Split nested or mixed-duty functions over 10; extracts must own their inputs, not take booleans the caller already knew.
- Scan changed and new files with the mutation tool's scan/count mode as a hint of mixed responsibilities. Without that mode, use the wrapper's CRAP complexity column as described in its language guide. Split multi-job files, not one-job files merely to lower site counts.
- Preserve project manifests through splits. Move mutation cache files to match moved source paths under `.metrics/`; never hand-edit them or commit the cache.

## Existing Code
- When `project.md` says `Existing code: ask before refactoring`, read and obey the [existing-code refactoring policy](../existing-code.md) before working on existing code. It takes precedence over role cleanup and quality-gate instructions for existing code. Otherwise skip it.

## Guardrails
- Do not invent project-local CRAP, DRY, mutation, or coverage proxies. Install and run the constitution tools (`crap4clj` with cloverage, `dry4clj`, `clj-mutate`, speclj, `sf-cs`, `sf-fs`, `sf-vb`, `sf-js`, `sf-rb`, or the language table). Do not treat a homegrown `bb crap` / `bb coverage` / `bb mutation-count` task or a throwaway report-parsing script as those tools.
- Do not edit mutation testing or Gherkin acceptance mutation manifests by hand; allow approved mutation tools to update those manifests as part of their normal runs.
- Mutation manifests live on the branch `swarmforge/metrics`, not in tracked files: clj-mutate's under `.metrics/mutate/`, and the gherkin-mutator stamp and manifest block, which the `gherkin-mutator` wrapper keeps in `.metrics/gherkin/<feature path>` between runs. `sf start` loads them and `sf handoff` saves them. Do not commit `.metrics/` or a mutation block in a feature file.
- Do not commit unrelated local changes or generated artifacts unless required for the task.
- Before relying on an unfamiliar command, inspect local help or project documentation.
