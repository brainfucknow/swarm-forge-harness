import os
import subprocess

from swarmforge_pipeline import sfkit
from swarmforge_pipeline.models import TaskState


def _git(repo, *args):
    return subprocess.run(["git", *args], cwd=repo, check=True, capture_output=True, text=True)


def _make_repo(path):
    os.makedirs(path)
    _git(path, "init", "-q", "-b", "main")
    _git(path, "config", "user.email", "tester@example.com")
    _git(path, "config", "user.name", "tester")
    with open(os.path.join(path, "README.md"), "w") as fh:
        fh.write("hello\n")
    _git(path, "add", "-A")
    _git(path, "commit", "-q", "-m", "seed")


def test_task_branch():
    assert sfkit.task_branch("add-login-3f9a2c1") == "swarm/add-login-3f9a2c1"


def test_clone_or_update_clones_and_updates(tmp_path):
    src = tmp_path / "src"
    _make_repo(str(src))
    _git(str(src), "checkout", "-q", "-b", "swarm/task-1")
    with open(os.path.join(str(src), "work"), "w") as fh:
        fh.write("x")
    _git(str(src), "add", "-A")
    _git(str(src), "commit", "-q", "-m", "task work")
    _git(str(src), "checkout", "-q", "main")

    dest = tmp_path / "clone"
    main = sfkit.clone_or_update(str(src), str(dest), "origin", "main")
    assert os.path.isdir(os.path.join(main, ".git"))
    assert sfkit.git(main, "rev-parse", "--abbrev-ref", "HEAD").stdout.strip() == "main"

    # a second call fetches and stays on the base branch
    sfkit.clone_or_update(str(src), str(dest), "origin", "main")
    assert sfkit.git(main, "rev-parse", "--abbrev-ref", "HEAD").stdout.strip() == "main"

    sfkit.fetch_task(main, "origin", "task-1")
    assert sfkit.git(main, "rev-parse", "--verify", "swarm/task-1").returncode == 0
    sfkit.push_task(main, "origin", "task-1")


def test_read_state_parses_sf_ls(tmp_path, monkeypatch):
    def fake_sf(repo, *args, check=True):
        assert args == ("ls",)
        return subprocess.CompletedProcess(
            args, 0, "add-login-3f9a2c1\tcoder\t\tspecifier,coder\tAdd login\n", ""
        )

    monkeypatch.setattr(sfkit, "sf", fake_sf)
    state = sfkit.read_state(str(tmp_path), "add-login-3f9a2c1")
    assert state == TaskState(stage="coder", needs="none", route=["specifier", "coder"])

    def fake_sf_approval(repo, *args, check=True):
        return subprocess.CompletedProcess(
            args, 0, "add-login-3f9a2c1\tspecifier\tapproval\tspecifier,coder\tAdd login\n", ""
        )

    monkeypatch.setattr(sfkit, "sf", fake_sf_approval)
    assert sfkit.read_state(str(tmp_path), "add-login-3f9a2c1").needs == "approval"
