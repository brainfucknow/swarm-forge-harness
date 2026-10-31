# CI manifest guard acceptance features

The CI guard rejects a gherkin-mutator block in a tracked `.feature` file
or a tracked file under `.metrics/`.

- `manifest_guard.feature`: what `test/no_manifests.sh` reports and when it
  fails.

`test/acceptance.sh` runs it, one check per scenario and example row, with
this repository's `test/no_manifests.sh`, real git and bash in a scratch
repository. `test/no_manifests_test.sh` also exercises the guard directly.

## Placeholders

| Text | Meaning |
|---|---|
| `\n` in a quoted value or a table cell | A newline. |

## Step meanings

| Step | Meaning |
|---|---|
| `a git repository whose .gitignore has the line ".metrics/"` | A scratch git repository on `main`, git identity `tester <tester@example.com>`, whose first commit holds only a `.gitignore` with the line `.metrics/`. |
| `the file "P" holds "X"` | Writes `X` and a newline to `P` in the repository, making directories as needed. Nothing is staged. |
| `the file "P" holds "X" and is committed` | The same, then `git add -f P` and `git commit -m P`. |
| `the file "P" holds "X" and is staged` | The same, then `git add -f P`, with no commit. |
| `I run the manifest guard` | Runs `bash <this repository>/test/no_manifests.sh` with the scratch repository's root as the current directory. This is the step under test. |
| `I run the manifest guard in the directory "D"` | The same, from directory `D` inside the scratch repository. |
| `the exit status is N` | The exit status of the step under test. |
| `the output is "X"` | Stdout without its last newline equals `X`. `""` means stdout is empty. |
| `the error output is "X"` | Stderr without its last newline equals `X`. |
| `there is no output` | Stdout and stderr are both empty. |
