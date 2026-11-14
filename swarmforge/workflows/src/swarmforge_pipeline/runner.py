"""Run one SwarmForge role as an Agents SDK agent inside a workflow activity.

The agent gets worker-side tools scoped to the task worktree through a session
input, so the model cannot touch paths outside it. Role agents may write and
run shell; gate and spec-judge agents get read-only tools only."""

from __future__ import annotations

import os
from typing import Annotated

from mistralai import agents

from .catalogue import Role


class Worktree(agents.SessionInput):
    """Which task worktree this session works in."""

    input_id = "swarmforge.worktree"
    path: str


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


@agents.tool
async def run_shell(command: str, wt: Annotated[Worktree, agents.Depends()]) -> str:
    """Run a shell command in the task worktree and return its combined output
    and exit code. Use it for swarmforge/bin/sf commands, tests, and git."""
    import subprocess

    proc = subprocess.run(
        ["bash", "-c", command],
        cwd=_worktree_path(wt),
        capture_output=True,
        text=True,
        timeout=600,
    )
    out = (proc.stdout + proc.stderr).strip()
    return f"exit {proc.returncode}\n{out}" if out else f"exit {proc.returncode}"


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
    tools = [run_shell, read_file] + ([write_file] if writable else [])
    return agents.Agent(
        model=model,
        name=f"swarmforge-{role.name}",
        instructions=instructions,
        tools=tools,
    )


async def run_role_agent(
    agent: agents.Agent, prompt: str, worktree: str
) -> str:
    """One agent turn on the prompt; returns the final text. Raises on a
    non-completed turn so the activity retries or fails."""
    async with agents.local_runtime():
        session = await agent.start_session(
            session_inputs={Worktree.input_id: Worktree(path=worktree)}
        )
        state = await session.run(prompt)
        await session.cancel()
    if isinstance(state, agents.Completed):
        return state.text
    if isinstance(state, agents.Failed):
        raise RuntimeError(f"agent turn failed: {state.error}")
    raise RuntimeError(f"agent turn ended with outcome {type(state).__name__}")
