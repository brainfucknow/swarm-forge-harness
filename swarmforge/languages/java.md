# Java tools

`swarmforge/bin/sf-java` supports Java 17+ (a JDK), JUnit 5, Maven, Git, Bash
and Babashka. Run it at the Git project root beside a single-module `pom.xml`,
with production code in `src/main/java` and tests in `src/test/java`.
Gradle, Maven reactor orchestration, custom source roots and JPMS module-path
execution are outside this version.

The application declares its own JUnit 5 test dependency. The wrapper leaves
`pom.xml` unchanged. Maven compiles classes and prepares dependencies with
`-DskipTests=true -DskipITs=true`; a dedicated JUnit Platform Console runner
executes tests. Application Maven plugins still run during preparation, so
review the project's build configuration before running it.

```sh
swarmforge/bin/sf-java tools
swarmforge/bin/sf-java test
swarmforge/bin/sf-java property
swarmforge/bin/sf-java coverage
swarmforge/bin/sf-java crap --base main
swarmforge/bin/sf-java dry --base main
swarmforge/bin/sf-java mutate --base main
```

`tools` installs and checks the pinned quality JARs through `sf tool`, plus
JUnit Platform Console 1.10.2 and JaCoCo agent/CLI 0.8.12 from Maven. Runtime
dependencies and the Maven cache stay in ignored `.swarmforge/java/`;
`SFJAVA_CACHE` can select a shared project-local cache for mutation workers.
Test commands obtain missing runner JARs automatically; quality tools require
`tools` first.

Tag property tests with `@org.junit.jupiter.api.Tag("property")`. `property`
selects that tag; ordinary tests, coverage, CRAP and mutation exclude it. The
console discovers tests in `target/test-classes` with its normal test-class
name convention and fails when no matching tests are found.

Coverage deletes stale `target/jacoco.exec` and
`target/site/jacoco/jacoco.xml`, runs ordinary tests under the agent, and
creates a new XML report with the JaCoCo CLI. Missing new reports fail.

Quality commands select existing production `.java` files changed since the
merge base, including committed, working-tree and untracked changes. `--base`
defaults to `SWARMFORGE_BASE`, then `main`, then `master`. Deleted files and test
sources are excluded; invalid refs fail. CRAP analyzes methods in the selected
files with fresh coverage and fails above **10** or when coverage is missing.
DRY compares all production declarations but fails only candidates whose line
range touches changed code on either side.

Mutation invokes `mutate4java` once per changed production file, using its
own differential declaration hashes and four isolated workers. Baseline and
mutant tests compile the current source and use the dedicated ordinary-test
runner. Survivors fail. The tool writes its manifests to
`.metrics/mutate/java/<source-path>.manifest`; `sf start`/`handoff` share that
state through `swarmforge/metrics`. Do not hand-edit manifests, use
`--mutate-all`, or commit manifests in source files. Source and POM content are
restored after each invocation, including failures.

Commands return 0 for a pass, 1 for test/compilation failure or quality findings,
and 2 for usage, prerequisites or analysis failures. Diagnostics use the
`sf-java:` prefix. `help` lists the commands and supported options.

## Upstream compatibility

`swarmforge/tools.conf` pins `crap4java`, `dry4java` and `mutate4java` to full
upstream commit SHAs. The `maven` catalogue kind clones that revision, validates
and applies the tracked patches in
`swarmforge/tools/sf/resources/sf/java-patches/`, builds with Maven test execution
disabled, and publishes an executable JAR wrapper in `.swarmforge/bin/`.
`INSTALLED: ... @<SHA>` identifies the upstream revision with the tracked patch
applied. Patch/build failures do not publish a new wrapper. Reinstallation
resets the owned clone to its pin before patching.

- `crap4java`: requires the dedicated coverage command, rejects missing XML,
  and changes the gate from 8 to 10. `sf-java` supplies the command through
  `SFJAVA_COVERAGE_COMMAND`.
- `dry4java`: builds Java 17 bytecode rather than requiring Java 21.
- `mutate4java`: removes an unavailable workspace parent POM, preserves PATH
  for custom test commands, reads dedicated-runner JaCoCo coverage, stores
  manifests through `SFJAVA_MANIFEST`, and excludes Git/tool/cache directories
  from worker copies. Its mutation analysis and differential selection remain
  upstream behavior.

Prefer `sf-java crap`; direct `crap4java` runs require
`SFJAVA_COVERAGE_COMMAND`. `SWARMFORGE_TOOL_SRC` is an unpinned development
override, built as supplied without patches. Overrides must provide these
integration APIs and cannot establish pinned verification evidence.

## Security and verification

Use [Semgrep](https://semgrep.dev/) for source scanning (`semgrep --config
p/java src/main/java`) and [OWASP Dependency-Check](https://dependency-check.github.io/DependencyCheck/)
for dependency auditing (`mvn -DskipTests=true
org.owasp:dependency-check-maven:12.1.0:check`). Configure required credentials
outside Git. These tools are separate from the quality commands above.

In this harness source checkout, run the wrapper behavior specs and executable
contracts separately from its property specs:

```sh
bb --config swarmforge/tools/java/bb.edn spec
bb --config swarmforge/tools/java/bb.edn acceptance
bb --config swarmforge/tools/java/bb.edn property
```

Acceptance checks public CLI contracts with controlled external tools; real-tool
compatibility requires separate verification. Consumer installs omit development
specs, fixtures and acceptance code.
