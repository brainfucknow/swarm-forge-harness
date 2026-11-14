import os

import pytest

from swarmforge_pipeline import runner


class FakeWt:
    path = os.getcwd()
    role = "falsifier"


@pytest.mark.asyncio
async def test_readonly_shell_allows_read_commands(tmp_path, monkeypatch):
    monkeypatch.setattr(runner, "_worktree_path", lambda wt: str(tmp_path))
    (tmp_path / "f.txt").write_text("hello")
    out = await runner.run_readonly_shell("cat f.txt", runner.Worktree(path=str(tmp_path), role="falsifier"))
    assert "hello" in out and "exit 0" in out
    out = await runner.run_readonly_shell("git status", runner.Worktree(path=str(tmp_path), role="falsifier"))
    assert "exit" in out


@pytest.mark.asyncio
async def test_readonly_shell_refuses_writes_and_pipes(tmp_path):
    wt = runner.Worktree(path=str(tmp_path), role="falsifier")
    for cmd in ["git commit -m x", "sf handoff t-1 -m x", "cat f > g", "cat f | grep f", "rm -rf .", "echo hi; cat f"]:
        with pytest.raises(Exception):
            await runner.run_readonly_shell(cmd, wt)


def test_shell_env_marks_role_as_dispatched():
    env = runner._shell_env(runner.Worktree(path="/x", role="coder"))
    assert env["SWARMFORGE_DISPATCHED"] == "coder"
    assert env["SWARMFORGE_BACKEND"] == "mistral"
