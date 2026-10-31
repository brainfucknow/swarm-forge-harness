---
id: tighten-worker-permissions-9504d91
created: 2026-10-31
stage: done
route: specifier,coder,qa
---

# Tighten worker permissions, start directory and tool versions

From an external review the operator accepted, three small fixes: (1) the Copilot backend always passes --allow-all-tools although the README says headless permissions come from the user; make it an explicit configuration choice. (2) Workers start in the repository root and rely on the prompt to move into the task worktree; create and validate the worktree and start them there. (3) The engineering rules require the latest upstream tools, but sf tool ensure reuses an existing checkout unchanged; resolve tools to explicit versions or SHAs, record them with verification evidence, and make upgrades deliberate.

## Records

```text
type: created
from: operator
branch: swarm/tighten-worker-permissions-9504d91
commit: c587a51
route: specifier,coder,qa
```

```text
type: question
from: specifier
question: 1. Copilot permissions (fix 1). Which form? (a) Drop --allow-all-tools; sf dispatch runs 'copilot -p <prompt> --model <m>' and the operator grants permissions through the existing SWARMFORGE_COPILOT_ARGS (e.g. --allow-all-tools, or --allow-tool 'shell(git:*)'), as the README already says. No new configuration. [recommended, simplest] (b) An opt-in variable such as SWARMFORGE_COPILOT_ALLOW_ALL=1 that adds the flag. (c) A setting in a file under swarmforge/ (a column or a new backends.conf). Also: keep Claude's built-in '--permission-mode acceptEdits' and Codex's '--full-auto' as they are (both are bounded: edits only / workspace sandbox) [recommended], or move them to the *_ARGS variables too?
  2. Start directory (fix 2), confirm these defaults: sf dispatch (and so sf run) creates the task worktree when missing, as sf start does; refuses before launching the backend when the worktree is on a branch other than swarm/<id>; runs the backend with .worktrees/<id> as its working directory; does not refuse uncommitted changes, since a resumed role may have work in progress. Harness subagents are unchanged. Yes, or what differs?
  3. Tool versions (fix 3) is larger than 1 and 2 together: a new tools.conf column, fetch/checkout logic in sf tool ensure, picking initial SHAs for 12 GitHub rows, the Maven RELEASE versions of cloverage and speclj, engineering.md wording, and an upgrade path. I recommend splitting it into its own task and specifying only 1 and 2 here. Split? For the follow-up, which scheme: (a) a <ref> column in tools.conf: full commit SHA for GitHub rows, Maven version for clj rows, '-' for local; ensure fetches and checks out that SHA in an existing or new clone and prints it ('INSTALLED: <tool> <path> @<sha>'), which roles cite in Tested trailers; an upgrade is a reviewed commit that edits the column; engineering.md says 'the version pinned in tools.conf' instead of 'latest' [recommended]; (b) the same with a separate lock file written by a new 'sf tool upgrade' command; (c) release tags instead of SHAs (the unclebob repositories may not tag releases). Leave sf-cs/sf-js/sf-fs package versions out of scope (javascript.md already makes them a person's choice)?
```

```text
type: answer
from: operator
answer: 1 (a) drop --allow-all-tools; the operator grants permissions through SWARMFORGE_COPILOT_ARGS; keep Claude's acceptEdits and Codex's --full-auto. 2 yes, the proposed start-directory defaults. 3 split tool versions into its own task with the SHA column scheme; specify only fixes 1 and 2 here.
```

```text
type: review
from: specifier
commit: 1d5d12c
files: -
summary: Spec: Copilot permissions via SWARMFORGE_COPILOT_ARGS only; dispatch runs backends in the validated task worktree
```

```text
type: approval
from: operator
```

```text
type: handoff
from: specifier
to: coder
branch: swarm/tighten-worker-permissions-9504d91
commit: 1d5d12c
files: -
summary: specifier work complete
```

```text
type: handoff
from: coder
to: qa
branch: swarm/tighten-worker-permissions-9504d91
commit: e3254c4
files: README.md,swarmforge/tools/sf/spec/sf/architecture_spec.clj,swarmforge/tools/sf/spec/sf/dispatch_spec.clj,swarmforge/tools/sf/src/sf/dispatch.clj,swarmforge/tools/sf/src/sf/task.clj
summary: Dispatch runs backends in the validated task worktree; Copilot permissions only via SWARMFORGE_COPILOT_ARGS
```

```text
type: handoff
from: qa
to: done
branch: swarm/tighten-worker-permissions-9504d91
commit: 8bce97a
files: -
summary: QA passed: Copilot permissions only via SWARMFORGE_COPILOT_ARGS; dispatch runs in the validated task worktree
```
