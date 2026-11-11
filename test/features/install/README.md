# Install acceptance features

`bin_tracked.feature` verifies that installed `swarmforge/bin` files remain
trackable under project ignore rules such as Visual Studio's `[Bb]in/`.
It also checks reinstall behavior and duplicate ignore entries.
`babashka_check.feature` verifies that install.sh, with no `bb` on PATH,
refuses before it changes the target project.

Run `bash test/acceptance.sh` from the repository root. The fixtures use the
real installer, Git, and Babashka in scratch repositories. See the
[install step handlers](../../acceptance/src/acceptance/install_steps.clj)
and shared [metrics fixtures](../../acceptance/src/acceptance/metrics_steps.clj)
for operations and assertions; placeholder conventions follow the
[task-store guide](../task_store/README.md).
