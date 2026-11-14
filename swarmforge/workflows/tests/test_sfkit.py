import os
import subprocess

import pytest

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


def test_git_env_carries_token_on_push_only(monkeypatch):
    monkeypatch.setenv("GITHUB_TOKEN", "secret-token")
    push = sfkit._git_env(["git", "push", "origin", "swarm/x:swarm/x"])
    assert push["GIT_CONFIG_KEY_0"] == "http.https://github.com/.extraHeader"
    assert push["GIT_CONFIG_VALUE_0"] == "Authorization: Bearer secret-token"
    assert push["GIT_CONFIG_COUNT"] == "1"
    for read_cmd in (["git", "clone", "https://github.com/a/b.git", "/tmp/x"],
                     ["git", "fetch", "origin", "swarm/x"],
                     ["git", "status"]):
        assert "GIT_CONFIG_COUNT" not in sfkit._git_env(read_cmd), read_cmd
    monkeypatch.delenv("GITHUB_TOKEN")
    monkeypatch.setenv("SWARMFORGE_GITHUB_TOKEN", "alt-token")
    assert sfkit._git_env(["git", "push", "origin", "x"])["GIT_CONFIG_VALUE_0"] == "Authorization: Bearer alt-token"
    monkeypatch.delenv("SWARMFORGE_GITHUB_TOKEN")
    assert "GIT_CONFIG_COUNT" not in sfkit._git_env(["git", "push", "origin", "x"])


def test_run_does_not_leak_token_into_error(monkeypatch, tmp_path):
    monkeypatch.setenv("GITHUB_TOKEN", "secret-token")
    with pytest.raises(sfkit.CommandError) as err:
        sfkit.run(["git", "push", "https://github.com/x/y.git", "main"], cwd=str(tmp_path))
    assert "secret-token" not in str(err.value)
    assert "secret-token" not in err.value.cmd.__str__()


def test_clone_or_update_replaces_a_clone_of_another_repository(tmp_path):
    import subprocess

    def git(repo, *args):
        return subprocess.run(["git", *args], cwd=repo, check=True, capture_output=True, text=True)

    src_a, src_b = tmp_path / "a", tmp_path / "b"
    for src in (src_a, src_b):
        os.makedirs(src)
        git(str(src), "init", "-q", "-b", "main")
        git(str(src), "config", "user.email", "t@e.c")
        git(str(src), "config", "user.name", "t")
        (src / "f").write_text(src.name)
        git(str(src), "add", "-A")
        git(str(src), "commit", "-q", "-m", "seed")

    dest = tmp_path / "clone"
    sfkit.clone_or_update(str(src_a), str(dest), "origin", "main")
    assert (dest / "f").read_text() == "a"
    # same dest path, different repository: the cached clone must be replaced
    sfkit.clone_or_update(str(src_b), str(dest), "origin", "main")
    assert (dest / "f").read_text() == "b"


def test_checkout_dir_namespaces_by_repository():
    from swarmforge_pipeline import activities

    a = activities.checkout_dir("https://github.com/x/a.git", "t-1")
    b = activities.checkout_dir("https://github.com/x/b.git", "t-1")
    assert a != b and a.endswith("t-1") and b.endswith("t-1")
