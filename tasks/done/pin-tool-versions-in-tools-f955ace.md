---
id: pin-tool-versions-in-tools-f955ace
created: 2026-10-31
stage: done
route: specifier,coder,cleaner,architect,hardener,security-reviewer,qa
---

# Pin tool versions in tools.conf

Split from 'Tighten worker permissions, start directory and tool versions'. The engineering rules require the latest upstream tools, but sf tool ensure reuses an existing clone unchanged and cloverage and speclj install at Maven RELEASE. The operator chose: a ref column in tools.conf (full commit SHA for GitHub rows, Maven version for clj rows, '-' for local); sf tool ensure fetches and checks out that ref in an existing or new clone and prints it (INSTALLED: <tool> <path> @<sha>), which roles cite in Tested trailers; an upgrade is a reviewed commit that edits the column; engineering.md says 'the version pinned in tools.conf' instead of 'latest'. sf-cs, sf-js and sf-fs package versions stay out of scope.

## Records

```text
type: created
from: operator
branch: swarm/pin-tool-versions-in-tools-f955ace
commit: 678e4cc
route: specifier,coder,cleaner,architect,hardener,qa
```

```text
type: route
from: operator
route: specifier,coder,cleaner,architect,hardener,security-reviewer,qa
```

```text
type: question
from: specifier
question: 1. SWARMFORGE_TOOL_SRC (the local-source override every sf tool test uses, often a plain non-git directory): (a) recommended: used as it is, never fetched or checked out; the INSTALLED line then has no @ suffix, like local rows (ref '-'), so a Tested trailer cannot cite an unpinned source as pinned; (b) override must be a git checkout and ensure checks out the pinned ref in it (moves the developer's checkout, breaks the fixtures). 2. A clone under .swarmforge/tools with local edits or untracked files: (a) recommended: ensure discards them (forced detached checkout of the pinned SHA plus git clean of untracked, non-ignored files), since the directory is sf's cache and developers use SWARMFORGE_TOOL_SRC for edits; (b) ensure refuses and asks the user to delete the clone. Either way, a directory there that is not the top level of its own git clone fails with an error and is never touched (git would otherwise act on the project repository). 3. Network: (a) recommended: fetch from https://<source>.git only when the clone lacks the pinned commit, so ensure works offline once the commit is present and a tampered origin remote is never used; (b) fetch on every ensure, as the request text reads literally. 4. speclj and cloverage: (a) recommended: the cloverage wrapper's speclj dependency takes the speclj row's version (one place to upgrade), and the crap4clj coverage config's own pins (cloverage git 61e3cac, speclj 3.12.2, documented by crap4clj) stay in code, out of scope; (b) also move those into tools.conf; (c) leave the cloverage wrapper's speclj 3.13.0 hardcoded. 5. Injection through existing tools.conf columns: today the task column goes unquoted into the wrapper (a task like 'crap;curl${IFS}x|sh' runs at wrapper time), and source picks the clone directory name. (a) recommended, small: in this task quote the task in every wrapper, and have sf check and ensure reject a GitHub source that is not github.com/<owner>/<repo> (letters, digits, . _ -, not . or ..); (b) only validate the new ref column now, file a follow-up for the rest. 6. Column position: (a) recommended: <name> <kind> <source> <ref> <task> <rewrite> <needs>, ref next to what it pins; (b) append it as the last column.
```

```text
type: answer
from: operator
answer: All recommended: 1a SWARMFORGE_TOOL_SRC used as is, never fetched or checked out, INSTALLED line without @ suffix; 2a ensure discards local edits and untracked non-ignored files in its cache clone, and a directory that is not the top level of its own clone fails untouched; 3a fetch from https://<source>.git only when the clone lacks the pinned commit, never via the origin remote; 4a cloverage wrapper takes speclj's version from the speclj row, crap4clj's own pins stay in code and out of scope; 5a quote the task column in every wrapper, and sf check and ensure reject a GitHub source that is not github.com/<owner>/<repo> in safe characters; 6a ref column after source: <name> <kind> <source> <ref> <task> <rewrite> <needs>.
```

```text
type: review
from: specifier
commit: aa8525d
files: .claude/agents/architecture-design-reviewer.md,.claude/agents/security-reviewer.md,.codex/agents/architecture-design-reviewer.toml,.codex/agents/security-reviewer.toml,.github/agents/architecture-design-reviewer.agent.md,.github/agents/security-reviewer.agent.md,README.md,install.sh,swarmforge/README.md,swarmforge/bin/sf-vb,swarmforge/constitution/articles/engineering.md,swarmforge/constitution/articles/handoffs.md,swarmforge/constitution/articles/project.md,swarmforge/handoff-protocol.md,swarmforge/languages/fsharp.md,swarmforge/languages/vbnet.md,swarmforge/roles.conf,swarmforge/roles/architect.md,swarmforge/roles/architecture-design-reviewer.md,swarmforge/roles/cleaner.md,swarmforge/roles/coder.md,swarmforge/roles/hardener.md,swarmforge/roles/security-reviewer.md,swarmforge/tools.conf,swarmforge/tools/csharp/src/sfcs/cli.clj,swarmforge/tools/csharp/src/sfcs/cobertura.clj,swarmforge/tools/csharp/src/sfcs/project.clj,swarmforge/tools/csharp/src/sfvb/cli.clj,swarmforge/tools/csharp/test/sfcs/core_test.clj,swarmforge/tools/javascript/test/sfjs/architecture_test.clj,swarmforge/tools/sf/property/sf/flow_property.clj,swarmforge/tools/sf/property/sf/lore_property.clj,swarmforge/tools/sf/property/sf/record_property.clj,swarmforge/tools/sf/property/sf/verify_property.clj,swarmforge/tools/sf/spec/sf/adapters_spec.clj,swarmforge/tools/sf/spec/sf/architecture_spec.clj,swarmforge/tools/sf/spec/sf/config_spec.clj,swarmforge/tools/sf/spec/sf/dispatch_spec.clj,swarmforge/tools/sf/spec/sf/history_spec.clj,swarmforge/tools/sf/spec/sf/lore_spec.clj,+37 more
summary: Pinned tool refs: criteria, QA and security focus in the spec commit and sf_tool features
```

```text
type: approval
from: operator
```

```text
type: handoff
from: specifier
to: coder
branch: swarm/pin-tool-versions-in-tools-f955ace
commit: aa8525d
files: -
summary: specifier work complete
```

```text
type: handoff
from: coder
to: cleaner
branch: swarm/pin-tool-versions-in-tools-f955ace
commit: 6eaca73
files: swarmforge/constitution/articles/engineering.md,swarmforge/languages/clojure.md,swarmforge/tools.conf,swarmforge/tools/sf/spec/sf/architecture_spec.clj,swarmforge/tools/sf/spec/sf/catalogue_spec.clj,swarmforge/tools/sf/spec/sf/clone_spec.clj,swarmforge/tools/sf/spec/sf/config_repo_spec.clj,swarmforge/tools/sf/spec/sf/config_spec.clj,swarmforge/tools/sf/spec/sf/tool_spec.clj,swarmforge/tools/sf/spec/sf/wrapper_spec.clj,swarmforge/tools/sf/src/sf/catalogue.clj,swarmforge/tools/sf/src/sf/clone.clj,swarmforge/tools/sf/src/sf/config.clj,swarmforge/tools/sf/src/sf/tool.clj,swarmforge/tools/sf/src/sf/wrapper.clj,test/acceptance/src/acceptance/steps.clj,test/sf_test.sh,test/sf_tool_test.sh
summary: Pinned refs in tools.conf; ensure puts clones at the pin; clj versions pinned; task quoted
```

```text
type: handoff
from: cleaner
to: architect
branch: swarm/pin-tool-versions-in-tools-f955ace
commit: 9bfff41
files: swarmforge/tools/sf/spec/sf/catalogue_spec.clj,swarmforge/tools/sf/src/sf/catalogue.clj,swarmforge/tools/sf/src/sf/clone.clj,swarmforge/tools/sf/src/sf/config.clj,swarmforge/tools/sf/src/sf/tool.clj
summary: Cleanup: tightened docstrings, one tools.conf read; CRAP/DRY clean
```

```text
type: handoff
from: architect
to: hardener
branch: swarm/pin-tool-versions-in-tools-f955ace
commit: 5b6f698
files: swarmforge/tools/sf/property/sf/catalogue_property.clj,swarmforge/tools/sf/spec/sf/catalogue_spec.clj,swarmforge/tools/sf/src/sf/catalogue.clj,swarmforge/tools/sf/src/sf/config.clj,swarmforge/tools/sf/src/sf/tool.clj
summary: Row rules all in sf.catalogue, pin rule in one place; catalogue property tests
```

```text
type: handoff
from: hardener
to: security-reviewer
branch: swarm/pin-tool-versions-in-tools-f955ace
commit: b4af79a
files: swarmforge/tools/sf/spec/sf/catalogue_spec.clj,swarmforge/tools/sf/spec/sf/clone_spec.clj,swarmforge/tools/sf/spec/sf/config_spec.clj,swarmforge/tools/sf/spec/sf/tool_spec.clj
summary: Hardened pinned tool refs: mutation, Gherkin mutation, CRAP max 9.6, DRY clean; four specs added, no source change
```

```text
type: handoff
from: security-reviewer
to: qa
branch: swarm/pin-tool-versions-in-tools-f955ace
commit: ea89f42
files: swarmforge/tools/sf/spec/sf/catalogue_spec.clj,swarmforge/tools/sf/spec/sf/clone_spec.clj,swarmforge/tools/sf/src/sf/catalogue.clj,swarmforge/tools/sf/src/sf/clone.clj
summary: Security review: clone config reset, replace refs off, name and clj coordinate validated
```

```text
type: handoff
from: qa
to: done
branch: swarm/pin-tool-versions-in-tools-f955ace
commit: dd6d7d7
files: swarmforge/tools/sf/spec/sf/clone_spec.clj,swarmforge/tools/sf/src/sf/clone.clj,swarmforge/tools/sf/src/sf/shell.clj
summary: QA pass: real upstream pins, tamper, offline, upgrade, old-clone and bad-ref checks; fixed caller GIT_* vars reaching clone git
```
