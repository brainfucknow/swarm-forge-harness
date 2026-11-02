# Install acceptance features

- `bin_tracked.feature`: `install.sh` keeps the files it installs under
  `swarmforge/bin` visible to git when the project's `.gitignore` ignores
  every `bin/` directory, as the standard Visual Studio `.gitignore` does
  with `[Bb]in/`.

`test/acceptance.sh` runs it, one check per scenario and example row, with
this repository's real `install.sh`, real git and a real `bb` in a scratch
repository.

## Step meanings

Steps not listed here mean what `../metrics_cache/README.md` says.

| Step | Meaning |
|---|---|
| `the file "P" in the repository holds "X"` | Creates the directories of `P` in the scratch repository and writes `X` and a newline to `P`. Nothing is committed. |
| `git add -A in the repository stages every file under "D"` | After `git add -A` in the scratch repository, `git ls-files -- D` prints exactly the files that `find D -type f` prints there, and at least one. |
| `git add -A in the repository stages no file under "D"` | After `git add -A` in the scratch repository, `git ls-files -- D` prints nothing. |
| `the file "P" in the repository has no line that starts with "S"` | No line of `P` in the scratch repository starts with `S`. |
| `the file "P" in the repository has no line twice` | `sort P \| uniq -d` in the scratch repository prints nothing. |
| `the file "P" in the repository has the line "X"` | `grep -qxF X P` in the scratch repository succeeds. |
