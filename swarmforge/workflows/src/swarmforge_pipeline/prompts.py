"""The prompts the workflow sends to its role agents. They mirror the prompts
sf dispatch builds for the local backends (swarmforge/tools/sf/src/sf/dispatch.clj)."""

from __future__ import annotations

import os

from .catalogue import Role, mistral_model, role_by_name
from .models import GateReport


def role_prompt(role: str, task_id: str) -> str:
    return (
        f"You are the SwarmForge '{role}' role. Read swarmforge/constitution.md and "
        f"follow its reading instructions, then read swarmforge/roles/{role}.md, then "
        f"do your role's work for task {task_id}. Start with: swarmforge/bin/sf start {task_id}"
    )


def gate_prompt(role: str, task_id: str) -> str:
    return (
        f"You are the SwarmForge '{role}' gate. Read swarmforge/roles/{role}.md, then "
        f"audit the specification of task {task_id} that waits for approval. Start with: "
        f"swarmforge/bin/sf show {task_id}. Change nothing: no edits, no commits, no sf "
        f"command that records. End with your report."
    )


def judge_prompt(task_id: str, gate_reports: list[GateReport]) -> str:
    reports = "\n\n".join(f"{r.role}:\n{r.report}" for r in gate_reports) or "(no gate reports)"
    return (
        f"Task {task_id}. Run: swarmforge/bin/sf spec-revisions {task_id} --full\n"
        f"Then read these gate reports of this spec revision verbatim:\n\n{reports}\n"
        f"End your report with SPEC-VERDICT: <converged|continue|needs-operator|stuck> "
        f"and REASON: <why>. Record nothing."
    )


def agent_instructions(role: Role, repo: str, tiers: dict[str, dict[str, str]], writable: bool) -> str:
    """The standing instructions of one role agent: the constitution, the role
    prompt file, and how its tools map onto the task worktree."""
    model = mistral_model(tiers, role.tier)
    role_file = os.path.join(repo, "swarmforge", "roles", f"{role.name}.md")
    role_text = open(role_file, encoding="utf-8").read() if os.path.isfile(role_file) else ""
    tools = (
        "run_shell runs a shell command in the task worktree; read_file and write_file "
        "work on paths relative to the worktree root."
        if writable
        else "run_shell runs read-only inspection commands (sf show, sf ls, git log, "
        "git diff, cat) in the task worktree; read_file reads files relative to the "
        "worktree root. You have no write tool and must record nothing."
    )
    return (
        f"You are a SwarmForge role agent on model {model}. You work in a clone of the "
        f"project repository; the task worktree is your working directory. {tools}\n\n"
        f"Read swarmforge/constitution.md and obey every article it lists.\n\n"
        f"Your role prompt:\n\n{role_text}"
    )
