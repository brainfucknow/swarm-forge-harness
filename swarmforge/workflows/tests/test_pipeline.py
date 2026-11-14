"""Orchestration tests for the SwarmForge workflows: call the workflow's run
method directly with the activities replaced by plain fakes, so no git, sf,
or model runs."""

from __future__ import annotations

import pytest

from swarmforge_pipeline import activities, pipeline
from swarmforge_pipeline.models import (
    CheckoutInfo,
    GateReport,
    JudgeVerdict,
    PipelineInput,
    PipelineResult,
    RoleInput,
    RoleOutcome,
    TaskState,
)

CO = CheckoutInfo(main="/fake", worktree="/fake/.worktrees/t-1", branch="swarm/t-1")


class World:
    """The fake task state the stubbed activities read and write."""

    def __init__(self, states: list[TaskState]):
        self.states = list(states)
        self.gate_runs: list[str] = []
        self.judge_runs = 0
        self.published = 0

    def state(self) -> TaskState:
        return self.states[0]


@pytest.fixture
def world(monkeypatch):
    holder = {}

    async def sync_checkout(params):
        return CO

    async def read_task_state(params):
        return holder["world"].state()

    async def list_gates(params):
        return ["falsifier"]

    async def spec_judge_role(params):
        return "spec-judge"

    async def run_stage_role(params):
        w = holder["world"]
        before = w.states[0]
        if len(w.states) > 1:
            w.states.pop(0)
        after = w.states[0]
        if after.needs in ("operator", "approval"):
            return RoleOutcome(status="blocked", stage=after.stage, needs=after.needs, report="role report")
        if after.stage != before.stage:
            return RoleOutcome(status="advanced", stage=after.stage)
        return RoleOutcome(status="failed", stage=after.stage)

    async def run_gate(params):
        w = holder["world"]
        w.gate_runs.append(params.gate)
        return GateReport(role=params.gate, report=f"{params.gate} report: no CRITICAL findings")

    async def run_spec_judge(params):
        w = holder["world"]
        w.judge_runs += 1
        assert [r.role for r in params.reports] == ["falsifier"]
        return JudgeVerdict(verdict="converged", reason="gates are clean")

    async def publish(params):
        holder["world"].published += 1
        return params.co.branch

    for name, fn in {
        "sync_checkout": sync_checkout,
        "read_task_state": read_task_state,
        "list_gates": list_gates,
        "spec_judge_role": spec_judge_role,
        "run_stage_role": run_stage_role,
        "run_gate": run_gate,
        "run_spec_judge": run_spec_judge,
        "publish": publish,
    }.items():
        monkeypatch.setattr(activities, name, fn)

    def make(states: list[TaskState]) -> World:
        w = World(states)
        holder["world"] = w
        return w

    return make


INPUT = PipelineInput(repository="/fake/repo", task_id="t-1", remote="origin", base_branch="main")


def as_model(cls, raw):
    return cls(**raw) if isinstance(raw, dict) else raw


async def test_pipeline_runs_stages_until_done(world):
    w = world(
        [
            TaskState(stage="coder", needs="none", route=["coder", "qa"]),
            TaskState(stage="qa", needs="none", route=["coder", "qa"]),
            TaskState(stage="done", needs="none", route=["coder", "qa"]),
        ]
    )
    result = as_model(PipelineResult, await pipeline.SwarmforgePipeline().run(INPUT))
    assert result.status == "done"
    assert result.stage == "done"
    assert w.published >= 1
    assert w.gate_runs == []


async def test_pipeline_runs_gates_and_spec_judge_on_approval(world):
    w = world([TaskState(stage="specifier", needs="approval", route=["specifier", "coder"])])
    result = as_model(PipelineResult, await pipeline.SwarmforgePipeline().run(INPUT))
    assert result.status == "blocked"
    assert result.needs == "approval"
    assert [r.role for r in result.reports] == ["falsifier"]
    assert result.verdict is not None and result.verdict.verdict == "converged"
    assert w.judge_runs == 1
    assert w.published == 1


async def test_pipeline_reports_operator_block_without_gates(world):
    w = world([TaskState(stage="coder", needs="operator", route=["coder"])])
    result = as_model(PipelineResult, await pipeline.SwarmforgePipeline().run(INPUT))
    assert result.status == "blocked"
    assert result.needs == "operator"
    assert result.reports == [] and result.verdict is None
    assert w.gate_runs == []


async def test_pipeline_blocks_with_gates_when_stage_ends_in_approval(world):
    w = world(
        [
            TaskState(stage="specifier", needs="none", route=["specifier", "coder"]),
            TaskState(stage="specifier", needs="approval", route=["specifier", "coder"]),
        ]
    )
    result = as_model(PipelineResult, await pipeline.SwarmforgePipeline().run(INPUT))
    assert result.status == "blocked" and result.needs == "approval"
    assert w.gate_runs == ["falsifier"] and w.judge_runs == 1


async def test_pipeline_fails_when_a_stage_makes_no_progress(world):
    world([TaskState(stage="coder", needs="none", route=["coder"])])
    with pytest.raises(Exception, match="made no progress"):
        await pipeline.SwarmforgePipeline().run(INPUT)


async def test_role_workflow_runs_a_gate_and_records_nothing(world):
    w = world([TaskState(stage="specifier", needs="approval", route=["specifier"])])
    result = as_model(RoleOutcome, await pipeline.SwarmforgeRole().run(
        RoleInput(repository="/fake/repo", task_id="t-1", role="falsifier")
    ))
    assert w.gate_runs == ["falsifier"]
    assert result.status == "advanced" and result.needs == "approval"
    assert "falsifier report" in result.report
    assert w.published == 1


async def test_role_workflow_runs_a_stage(world):
    world(
        [
            TaskState(stage="coder", needs="none", route=["coder", "qa"]),
            TaskState(stage="qa", needs="none", route=["coder", "qa"]),
        ]
    )
    result = as_model(RoleOutcome, await pipeline.SwarmforgeRole().run(
        RoleInput(repository="/fake/repo", task_id="t-1", role="coder")
    ))
    assert result.status == "advanced" and result.stage == "qa"
