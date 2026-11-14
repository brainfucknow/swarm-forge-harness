"""Run one SwarmForge role as an Agents SDK agent inside a workflow activity.

The agent gets worker-side tools scoped to the task worktree through a session
input, so the model cannot touch paths outside it. Role agents may write and
run shell; gate and spec-judge agents get a read-only shell only. Every shell
runs with SWARMFORGE_DISPATCHED and SWARMFORGE_BACKEND set, so sf refuses the
operator-only commands inside the worker exactly as it does for a local
dispatched role."""

from __future__ import annotations

import os
import subprocess
from typing import Annotated

from mistralai import agents

from .catalogue import Role


class Worktree(agents.SessionInput):
    """Which task worktree this session works in, and as which role."""

    input_id = "swarmforge.worktree"
    path: str
    role: str


def _worktree_path(wt: Worktree) -> str:
    root = os.path.realpath(wt.path)
    if not os.path.isdir(root):
        raise agents.ToolError(f"worktree {wt.path} does not exist")
    return root


def _resolve(wt: Worktree, path: str) -> str:
    root = _worktree_path(wt)
    full = os.path.realpath(os.path.join(root, path))
    if full != root and not full.startswith(root + os.sep):
        raise agents.ToolError(f"path {path} escapes the task worktree")
    return full


def _shell_env(wt: Worktree) -> dict[str, str]:
    env = {**os.environ}
    env["SWARMFORGE_DISPATCHED"] = wt.role
    env["SWARMFORGE_BACKEND"] = "mistral"
    return env


def _run(command: str, wt: Worktree) -> str:
    proc = subprocess.run(
        ["bash", "-c", command],
        cwd=_worktree_path(wt),
        capture_output=True,
        text=True,
        timeout=600,
        env=_shell_env(wt),
    )
    out = (proc.stdout + proc.stderr).strip()
    return f"exit {proc.returncode}\n{out}" if out else f"exit {proc.returncode}"


@agents.tool
async def run_shell(command: str, wt: Annotated[Worktree, agents.Depends()]) -> str:
    """Run a shell command in the task worktree and return its combined output
    and exit code. Use it for swarmforge/bin/sf commands, tests, and git. The
    command runs with SWARMFORGE_DISPATCHED set to your role, so sf refuses the
    operator-only commands."""
    return _run(command, wt)


_READONLY_SF = {"show", "ls", "stage", "next", "spec-revisions", "route", "harvest", "roles", "gates", "routes", "pipeline", "check", "--help"}
_READONLY_GIT = {"log", "diff", "status", "show", "rev-parse", "branch", "ls-files", "ls-remote", "blame", "describe"}
_READONLY_UNIX = {"cat", "ls", "find", "grep", "egrep", "head", "tail", "wc", "file", "tree", "pwd", "which", "env"}
_READONLY_FORBIDDEN = ("&&", "||", ";", "|", ">", "<", "`", "$(")


@agents.tool
async def run_readonly_shell(command: str, wt: Annotated[Worktree, agents.Depends()]) -> str:
    """Run a read-only inspection command in the task worktree: sf show/ls/stage/
    spec-revisions/route/harvest, git log/diff/status/show/rev-parse/branch,
    or basic unix readers (cat, ls, find, grep, head, tail, ...). Anything
    else, and any piping or redirection, is refused."""
    stripped = command.strip()
    if any(token in stripped for token in _READONLY_FORBIDDEN):
        raise agents.ToolError(f"read-only shell refuses '{command}': no pipes, redirections, or command substitution")
    parts = stripped.split()
    if not parts:
        raise agents.ToolError("empty command")
    if parts[0] == "sf" and len(parts) > 1 and parts[1] in _READONLY_SF:
        return _run(command, wt)
    if parts[0] == "git" and len(parts) > 1 and parts[1] in _READONLY_GIT:
        return _run(command, wt)
    if parts[0] in _READONLY_UNIX:
        return _run(command, wt)
    raise agents.ToolError(
        f"read-only shell refuses '{command}': allowed are sf {sorted(_READONLY_SF)}, "
        f"git {sorted(_READONLY_GIT)}, and {sorted(_READONLY_UNIX)}"
    )


@agents.tool
async def read_file(path: str, wt: Annotated[Worktree, agents.Depends()]) -> str:
    """Read a file, given as a path relative to the task worktree root."""
    with open(_resolve(wt, path), encoding="utf-8") as fh:
        return fh.read()


@agents.tool
async def write_file(
    path: str, content: str, wt: Annotated[Worktree, agents.Depends()]
) -> str:
    """Write a whole file, given as a path relative to the task worktree root."""
    full = _resolve(wt, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8") as fh:
        fh.write(content)
    return f"wrote {path}"


def build_agent(role: Role, model: str, instructions: str, writable: bool) -> agents.Agent:
    shell_tool = run_shell if writable else run_readonly_shell
    tools = [shell_tool, read_file] + ([write_file] if writable else [])
    return agents.Agent(
        model=model,
        name=f"swarmforge-{role.name}",
        instructions=instructions,
        tools=tools,
    )


async def run_role_agent(
    agent: agents.Agent, role: str, prompt: str, worktree: str
) -> str:
    """One agent turn on the prompt; returns the final text. Raises on a
    non-completed turn so the activity retries or fails."""
    async with agents.local_runtime():
        session = await agent.start_session(
            session_inputs={Worktree.input_id: Worktree(path=worktree, role=role)}
        )
        state = await session.run(prompt)
        await session.cancel()
    if isinstance(state, agents.Completed):
        return state.text
    if isinstance(state, agents.Failed):
        raise RuntimeError(f"agent turn failed: {state.error}")
    raise RuntimeError(f"agent turn ended with outcome {type(state).__name__}")
