"""Run git and swarmforge/bin/sf in a worker-local clone. Every task-state
write goes through sf on the task branch; these helpers only call it."""

from __future__ import annotations

import os
import subprocess

from .models import TaskState


class CommandError(RuntimeError):
    def __init__(self, cmd: list[str], exit: int, out: str, err: str):
        self.cmd = cmd
        self.exit = exit
        self.out = out
        self.err = err
        super().__init__(f"{' '.join(cmd)} exited {exit}: {err.strip() or out.strip()}")


def run(cmd: list[str], cwd: str | None = None, check: bool = True) -> subprocess.CompletedProcess:
    proc = subprocess.run(
        cmd, cwd=cwd, capture_output=True, text=True, env={**os.environ}
    )
    if check and proc.returncode != 0:
        raise CommandError(cmd, proc.returncode, proc.stdout, proc.stderr)
    return proc


def git(repo: str, *args: str, check: bool = True) -> subprocess.CompletedProcess:
    return run(["git", *args], cwd=repo, check=check)


def sf(repo: str, *args: str, check: bool = True) -> subprocess.CompletedProcess:
    return run([os.path.join(repo, "swarmforge", "bin", "sf"), *args], cwd=repo, check=check)


def task_branch(task_id: str) -> str:
    return f"swarm/{task_id}"


def clone_or_update(repository: str, dest: str, remote: str, base_branch: str) -> str:
    """Clone the repository into dest, or fetch it when dest already is a
    clone. Returns dest."""
    if os.path.isdir(os.path.join(dest, ".git")):
        git(dest, "fetch", remote, check=True)
        git(dest, "checkout", base_branch, check=True)
        git(dest, "reset", "--hard", f"{remote}/{base_branch}", check=True)
    else:
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        run(["git", "clone", repository, dest], check=True)
        git(dest, "remote", "rename", "origin", remote, check=False)
        git(dest, "fetch", remote, check=True)
        git(dest, "checkout", base_branch, check=True)
    return dest


def fetch_task(main: str, remote: str, task_id: str) -> None:
    """Fetch the task branch into the clone's local swarm/<id> ref. When a
    worktree already has the branch checked out, update it through a
    fast-forward merge there instead."""
    branch = task_branch(task_id)
    proc = git(main, "fetch", remote, f"+{branch}:refs/heads/{branch}", check=False)
    if proc.returncode != 0:
        git(main, "fetch", remote, branch, check=True)
        wt = worktree_dir(main, task_id)
        if os.path.isdir(wt):
            git(wt, "merge", "--ff-only", "FETCH_HEAD", check=True)
        else:
            raise CommandError(["git", "fetch", remote, branch], proc.returncode, proc.stdout, proc.stderr)


def push_task(main: str, remote: str, task_id: str) -> None:
    git(main, "push", remote, f"{task_branch(task_id)}:{task_branch(task_id)}", check=True)


def worktree_dir(main: str, task_id: str) -> str:
    return os.path.join(main, ".worktrees", task_id)


def ensure_worktree(main: str, task_id: str) -> str:
    """sf start creates or finds the task worktree; its output names DIR."""
    proc = sf(main, "start", task_id, check=False)
    if proc.returncode != 0:
        raise CommandError(
            ["sf", "start", task_id], proc.returncode, proc.stdout, proc.stderr
        )
    for line in proc.stdout.splitlines():
        if line.startswith("DIR: "):
            return line[len("DIR: "):].strip()
    return worktree_dir(main, task_id)


def read_state(main: str, task_id: str) -> TaskState:
    """The task's stage, needs, and route from sf ls. sf reads only local
    swarm/* branches, so the task branch is fetched first."""
    proc = sf(main, "ls", check=True)
    for line in proc.stdout.splitlines():
        fields = line.split("\t")
        if fields and fields[0] == task_id:
            needs = fields[2].strip() if len(fields) > 2 and fields[2].strip() else "none"
            route = fields[3].split(",") if len(fields) > 3 and fields[3].strip() else []
            return TaskState(stage=fields[1], needs=needs, route=route)
    raise CommandError(["sf", "ls"], 1, proc.stdout, f"no row for task {task_id}")


def snapshot(main: str, task_id: str) -> tuple[str, str, str]:
    """What a gate must leave as it found it: the task state, and the HEAD and
    status of the task worktree."""
    state = read_state(main, task_id)
    wt = worktree_dir(main, task_id)
    head = git(wt, "rev-parse", "HEAD", check=True).stdout.strip()
    status = git(wt, "status", "--porcelain", check=True).stdout
    return state.model_dump_json(), head, status
