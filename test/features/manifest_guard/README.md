# CI manifest guard acceptance features

`manifest_guard.feature` specifies when
[`test/no_manifests.sh`](../../no_manifests.sh) rejects tracked `.metrics/`
files or gherkin-mutator blocks in tracked `.feature` files.

Run `bash test/acceptance.sh` from the repository root, or
`bash test/no_manifests_test.sh` for the direct guard tests. The
[step handlers](../../acceptance/src/acceptance/guard_steps.clj) use real Git
and Bash in scratch repositories. Fixture steps explicitly force-add files
when testing ignored paths, and distinguish unstaged, staged, and committed
content. `\n` in quoted text means a newline; output comparisons omit the
last newline.
