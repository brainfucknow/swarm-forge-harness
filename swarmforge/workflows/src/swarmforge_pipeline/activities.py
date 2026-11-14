"""Workflow activities: every side effect of the pipeline lives here. The
workflow itself only sequences them."""

from __future__ import annotations

import os
import re
from datetime import timedelta

import mistralai.workflows as workflows
from pydantic import BaseModel

from . import catalogue, prompts, runner, sfkit
from .models import (
    CheckoutInfo,
    GateReport,
    JudgeVerdict,
    RoleOutcome,
    TaskRef,
    TaskState,
)

WORKSPACE = os.environ.get("SWARMFORGE_WORKSPACE", "/tmp/swarmforge-pipeline")


class SyncInput(BaseModel):
    ref: TaskRef


class RefInput(BaseModel):
    main: str
    task_id: str


class StageRunInput(BaseModel):
    co: CheckoutInfo
    task_id: str
    role: str


class GateRunInput(BaseModel):
    co: CheckoutInfo
    task_id: str
    gate: str


class JudgeRunInput(BaseModel):
    co: CheckoutInfo
    task_id: str
    reports: list[GateReport]


class PublishInput(BaseModel):
    co: CheckoutInfo
    remote: str
    task_id: str


def _load_catalogue(main: str):
    swarmforge = os.path.join(main, "swarmforge")
    roles = catalogue.load_roles(open(os.path.join(swarmforge, "roles.conf"), encoding="utf-8").read())
    tiers = catalogue.load_tiers(open(os.path.join(swarmforge, "tiers.conf"), encoding="utf-8").read())
    return roles, tiers


@workflows.activity(start_to_close_timeout=timedelta(minutes=30))
async def sync_checkout(params: SyncInput) -> CheckoutInfo:
    """Clone or update the repository, fetch the task branch, and let sf start
    create the task worktree."""
    ref = params.ref
    main = sfkit.clone_or_update(ref.repository, os.path.join(WORKSPACE, ref.task_id), ref.remote, ref.base_branch)
    sfkit.fetch_task(main, ref.remote, ref.task_id)
    worktree = sfkit.ensure_worktree(main, ref.task_id)
    return CheckoutInfo(main=main, worktree=worktree, branch=sfkit.task_branch(ref.task_id))


@workflows.activity()
async def read_task_state(params: RefInput) -> TaskState:
    return sfkit.read_state(params.main, params.task_id)


@workflows.activity()
async def list_gates(params: RefInput) -> list[str]:
    roles, _ = _load_catalogue(params.main)
    return catalogue.gates(roles)


@workflows.activity()
async def spec_judge_role(params: RefInput) -> str | None:
    roles, _ = _load_catalogue(params.main)
    return catalogue.spec_judge(roles)


@workflows.activity(start_to_close_timeout=timedelta(hours=3))
async def run_stage_role(params: StageRunInput) -> RoleOutcome:
    """Run one stage role as an Agents SDK agent on its tier's model. The agent
    works in the task worktree and hands off through sf."""
    roles, tiers = _load_catalogue(params.co.main)
    role = catalogue.role_by_name(roles, params.role)
    if role is None or role.is_gate or role.is_spec_judge:
        raise ValueError(f"'{params.role}' is not a stage role")
    before = sfkit.read_state(params.co.main, params.task_id)
    agent = runner.build_agent(
        role,
        catalogue.mistral_model(tiers, role.tier),
        prompts.agent_instructions(role, params.co.main, tiers, writable=True),
        writable=True,
    )
    report = await runner.run_role_agent(agent, prompts.role_prompt(role.name, params.task_id), params.co.worktree)
    after = sfkit.read_state(params.co.main, params.task_id)
    if after.needs in ("operator", "approval"):
        return RoleOutcome(status="blocked", stage=after.stage, needs=after.needs, report=report)
    if after.stage != before.stage:
        return RoleOutcome(status="advanced", stage=after.stage, needs=after.needs, report=report)
    return RoleOutcome(status="failed", stage=after.stage, needs=after.needs, report=report)


@workflows.activity(start_to_close_timeout=timedelta(hours=1))
async def run_gate(params: GateRunInput) -> GateReport:
    """Run one gate as a read-only agent. A gate records nothing: the snapshot
    before and after must be identical."""
    roles, tiers = _load_catalogue(params.co.main)
    gate = catalogue.role_by_name(roles, params.gate)
    if gate is None or not gate.is_gate:
        raise ValueError(f"'{params.gate}' is not a gate role")
    before = sfkit.snapshot(params.co.main, params.task_id)
    agent = runner.build_agent(
        gate,
        catalogue.mistral_model(tiers, gate.tier),
        prompts.agent_instructions(gate, params.co.main, tiers, writable=False),
        writable=False,
    )
    report = await runner.run_role_agent(agent, prompts.gate_prompt(gate.name, params.task_id), params.co.worktree)
    after = sfkit.snapshot(params.co.main, params.task_id)
    if before != after:
        raise RuntimeError(f"gate {gate.name} changed task {params.task_id}; a gate only reports")
    return GateReport(role=gate.name, report=report)


_VERDICT = re.compile(r"SPEC-VERDICT:\s*(converged|continue|needs-operator|stuck)", re.IGNORECASE)
_REASON = re.compile(r"REASON:\s*(.+)", re.IGNORECASE | re.DOTALL)


@workflows.activity(start_to_close_timeout=timedelta(hours=1))
async def run_spec_judge(params: JudgeRunInput) -> JudgeVerdict:
    """Run the spec-judge as a read-only step on this spec revision's gate
    reports. It records nothing; the operator's session records the verdict
    with sf spec-verdict."""
    roles, tiers = _load_catalogue(params.co.main)
    judge = catalogue.role_by_name(roles, catalogue.spec_judge(roles) or "")
    if judge is None:
        raise ValueError("roles.conf has no spec-judge")
    before = sfkit.snapshot(params.co.main, params.task_id)
    agent = runner.build_agent(
        judge,
        catalogue.mistral_model(tiers, judge.tier),
        prompts.agent_instructions(judge, params.co.main, tiers, writable=False),
        writable=False,
    )
    report = await runner.run_role_agent(
        agent, prompts.judge_prompt(params.task_id, params.reports), params.co.worktree
    )
    after = sfkit.snapshot(params.co.main, params.task_id)
    if before != after:
        raise RuntimeError(f"spec-judge changed task {params.task_id}; it only reports")
    verdict = _VERDICT.search(report)
    reason = _REASON.search(report)
    if not verdict:
        raise RuntimeError(f"spec-judge report has no SPEC-VERDICT: line:\n{report}")
    return JudgeVerdict(
        verdict=verdict.group(1).lower(),
        reason=reason.group(1).strip() if reason else "",
    )


@workflows.activity(start_to_close_timeout=timedelta(minutes=10))
async def publish(params: PublishInput) -> str:
    """Push the task branch so the operator's session can fetch the records."""
    sfkit.push_task(params.co.main, params.remote, params.task_id)
    return sfkit.task_branch(params.task_id)
