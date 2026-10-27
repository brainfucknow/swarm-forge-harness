# Mutation manifest acceptance features

Gherkin for task ec13ecd. Mutation manifests leave the tree and go into the
git notes ref `refs/notes/mutate`. These manifests are the clj-mutate files
under `.metrics/mutate/` and the gherkin-mutator stamp and manifest block at
the top of each `.feature`. The features specify the `sf` side:

- `sf manifest load|save|tested <id>`.
- Loading in `sf start` and saving in `sf handoff` (and `sf approve`).
- The block move in the `gherkin-mutator` wrapper.
- `.metrics/` in the `.gitignore` that `install.sh` writes.

A test script under `test/` runs them. It makes one check per scenario and
example row, and labels each check with the scenario name (for example
`sf manifest save 3: ...`). Each check runs the real `sf`, real git and real
git-bug in a scratch git repository that has a copy of `swarmforge/`. The
end-to-end procedure is `../../qa/mutation_manifests.md`.

## Settled with the operator

The operator accepted option (a) for each question:

1. A note annotates the task-branch commit that is HEAD when the save
   happens. To load a path, sf takes the newest copy among the notes on the
   commits that HEAD reaches. This copy survives `sf finish` and GitHub
   merge-commit PRs. After a squash or rebase merge, the next mutation run is
   a full run.
2. The manifests move automatically. `sf start` loads, `sf handoff` saves, and
   the `gherkin-mutator` wrapper moves the feature block in and out around
   each run. Run the mutation tools from the worktree root. The explicit
   commands `sf manifest load|save <id>` are also available.
3. `sf manifest tested <id>` prints the `Tested:` trailer lines that the
   hardener puts on its commit.
4. Only clj-mutate and gherkin-mutator are in scope. The footer manifests of
   mutate4go and mutate4java stay in their source files.
5. sf does not push or fetch notes. `handoff-protocol.md` tells how to do it.
6. There is no migration command. `install.sh` ignores `.metrics/`, and
   `sf start` warns when `.metrics/` is not ignored.

## Not covered by these features (prompt and document changes)

Per `AGENTS.md`, the text of prompts and documents is not pinned by tests.
The task also changes the items below. QA reads them by hand (see the QA
procedure).

- `swarmforge/constitution/articles/engineering.md`:
  - Mutation manifests are kept in `refs/notes/mutate`, not in tracked
    files. Do not commit `.metrics/` or the mutation block of a feature.
  - `sf start` loads the manifests and `sf handoff` saves them.
  - Run clj-mutate and gherkin-mutator from the worktree root, through the
    wrappers.
  - Differential mutation and "do not edit manifests by hand" stay as they
    are.
- `swarmforge/roles/hardener.md`:
  - Before the last commit, run `sf manifest tested <id>` and put its lines as
    trailers on that commit.
  - The sentence "Manifest edits still move forward with the task" now
    means: `sf handoff` saves them to the notes.
- `swarmforge/roles/hardener.md` and `cleaner.md`: when a split moves code to
  a new source file, keep the manifest state under `.metrics/` (for example,
  move the manifest file to the new path). Do not commit it.
- `swarmforge/handoff-protocol.md`, Sharing:
  - Notes travel only when they are pushed explicitly:
    `git push origin refs/notes/mutate`.
  - To fetch, fetch into a separate ref and merge
    (`git fetch origin refs/notes/mutate:refs/notes/mutate-origin`, then
    `git notes --ref=mutate merge refs/notes/mutate-origin`).
  - A squash or rebase merge leaves the notes behind. The next mutation run is
    then a full run.
- `.gitignore` of this repository: add `.metrics/`.
- `sf --help` lists `manifest load|save|tested <id>`. `swarmforge/README.md`
  names the notes ref.

## Compatibility with task df7e1a7 (Lore trailers)

- The `MANIFESTS:` line of `sf start` comes before the `---` line, so the
  `LORE:` lines stay at the end of the output.
- `sf handoff` still prints one line.
- The `Tested:` lines are Lore trailers, so the commit-msg hook gives the
  hardener's commit a `Lore-id`.

## Placeholders

| Text | Meaning |
|---|---|
| `\n` in a quoted value or a table cell | A newline. |
| `A`, `B`, `C` as a word in a command or in an expected output | The id that `sf new` printed for the task of that name. |
| `HEAD` in an expected output | The abbreviated sha (`git rev-parse --short HEAD`) of HEAD in the worktree of the task that the command names. |

## Step meanings

| Step | Meaning |
|---|---|
| `a project with swarmforge initialized and .metrics/ ignored` | A scratch git repository on `main`. Its first commit has a copy of `swarmforge/` and a `.gitignore` with the lines `.worktrees/`, `.swarmforge/`, `tmp/` and `.metrics/`. `sf init` ran in it. git-bug is on PATH. |
| `task "T" was created` | `sf new T -m T --route specifier` ran in the main checkout. |
| `task "T" was started` | `task "T" was created`, then `sf start T`. |
| `task "T" on route "R" was started` | `sf new T -m T --route R`, then `sf start T`. |
| `task "T" was finished` | `sf review T`, `sf approve T`, then `sf finish T` ran in the main checkout. |
| `task "T" finished with "P" saved as "X"` | `task "T" was started`, ``the worktree file "P" of task "T" is written with "X"``, `the manifests of task "T" were saved at a new commit`, then `task "T" was finished`. |
| `the worktree file "P" of task "T" is written with "X"` | Writes `X` and a newline to `P` in the worktree of `T`. Makes the directories that are necessary. |
| `the worktree file "P" of task "T" was deleted` | Removes `P` from the worktree of `T`. |
| `the worktree of task "T" has N manifest files` | Writes N files `.metrics/mutate/f1.edn` … `fN.edn`, each with different content. |
| `the manifests of task "T" were saved at a new commit` | `git commit --allow-empty -m save` in the worktree of `T`, then `sf manifest save T`. |
| `a commit was made in the worktree of task "T"` | `git commit --allow-empty -m work` in the worktree of `T`. |
| `the main checkout committed a .gitignore without .metrics/` | Removes the `.metrics/` line from `.gitignore` in the main checkout and commits it. |
| `role "R" has the audit flag in roles.conf` | In the main checkout, the line of `R` in `swarmforge/roles.conf` gets the fourth field `audit`. |
| `the clj-mutate manifest "P" of task "T" names source "S" with forms killed "K", survived "V" and uncovered "U"` | Writes `P` as the EDN map `{:version 2 :source "S" :forms [...]}`. Each form is `{:killed k :survived v :uncovered u}`, from the comma lists K, V and U, position by position. |
| `the gherkin-mutator manifest of feature "F" in task "T" has scenarios killed "K" and survived "V"` | Writes `.metrics/gherkin/F` as three lines: `# acceptance-mutation-manifest-begin`, `# <json>`, `# acceptance-mutation-manifest-end`. `<json>` has `"feature_path":"F"` and one scenario per item of the comma lists K and V, with `"result":{"Total":k+v,"Killed":k,"Survived":v,"Errors":0}`. |
| `the worktree of task "T" has the committed file "P" that holds "X"` | Writes `X` and a newline to `P` in the worktree of `T` and commits it. |
| `gherkin-mutator is a fake that writes the block "B" and exits with status N` | `SWARMFORGE_TOOL_SRC` points at a scratch tool source. Its `bb.edn` task `gherkin-mutator` does these things: it copies the `--feature` file to `tmp/seen.feature`; it removes any block from the file; it writes `B`, a newline, a blank line and the rest of the file; it exits with N. `sf tool ensure gherkin-mutator` ran in the main checkout. |
| `I ran "C"` | Runs command `C` from the main checkout. Output and status are not checked. |
| `I run "C"` | Runs command `C` from the main checkout. Placeholders are filled in and the command is split on spaces. This is the step under test. |
| `I run "C" in the worktree of task "T"` | The same, from the worktree of `T`. |
| `the exit status is N` | The exit status of the step under test. |
| `the output is "L"` | Stdout, without its trailing newline, is equal to `L` after placeholders are filled in. |
| `the output is empty` | Stdout is empty. |
| `the output has the line "L"` | One stdout line is equal to `L`. |
| `the output has the line "L" before the line "---"` | One stdout line is equal to `L`, and it comes before the first `---` line. |
| `the error output has the line "L"` | One stderr line is equal to `L` after placeholders are filled in. |
| `the worktree file "P" of task "T" holds "X"` | `P` in the worktree of `T` holds `X` and a newline. |
| `the worktree file "P" of task "T" does not exist` | There is no `P` in the worktree of `T`. |
| `the HEAD commit of task "T" has a note in refs/notes/mutate` / `has no note` | `git notes --ref=mutate list <HEAD of T's worktree>` exits 0 / non-zero. |
| `the notes' copy of "P" for task "T" is "X"` | Removes `P` from the worktree of `T`, runs `sf manifest load T`, and then `P` holds `X` and a newline. |
| `the fake saw the feature file holding "X"` | `tmp/seen.feature` in the worktree holds `X` and a newline. |
| `the file "P" of task "T" is as committed` | `git status --porcelain -- P` in the worktree of `T` prints nothing. |
| `a git repository with one commit and no swarmforge` | A scratch git repository with one commit and no `swarmforge/`. |
| `I run install.sh into it N times` | Runs this repository's `install.sh <scratch repository>` N times. The exit status is the status of the last run. |
| `the .gitignore of the repository has the line ".metrics/" exactly once` | `grep -cxF .metrics/ .gitignore` prints 1. |
