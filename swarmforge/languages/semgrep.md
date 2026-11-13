# Shared Semgrep setup

Before running Semgrep, read this setup and the project's language guide.
That guide determines whether Semgrep is required or optional, which changed
files it can read, and which rule configuration to use.

Semgrep needs uv. Fetch the pinned rules under `./tmp/`; never commit them
(Semgrep Rules License). The fetch and first uvx run reach github.com and
pypi.org; the scan runs offline.

```sh
git init -q tmp/semgrep-rules
git -C tmp/semgrep-rules fetch -q --depth 1 https://github.com/semgrep/semgrep-rules.git 16b9e6d45f10649f6a531ec4838ed9924372c3bc
git -C tmp/semgrep-rules checkout -q FETCH_HEAD
```

Run the scan with the configuration flags and changed files from the language
guide:

```sh
uvx semgrep@1.183.0 scan <config flags> --metrics=off --disable-version-check --error <changed files>
```

Exit 0 is clean, 1 is findings, and any other exit leaves the scan Not-tested.
If an optional scan cannot run, record `Not-tested: semgrep: <reason>` and
continue. For a required scanner, follow the [security-reviewer's missing-tool
policy](../roles/security-reviewer.md#scanners).
