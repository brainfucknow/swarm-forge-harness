"""The SwarmForge workflows.

swarmforge-pipeline: one execution per task. It loops over the task's stages,
running each as one Agents SDK agent on the role's tier model, with all sf
bookkeeping on the task branch. When a specification waits for approval it
runs the gates and the spec-judge as steps that record nothing and returns
their reports and verdict with status blocked. When the task needs the
operator it returns status blocked. On done it pushes the branch and returns
done.

swarmforge-role: one execution per role or gate, for sf dispatch <role> <id>
mistral."""

from __future__ import annotations

from datetime import timedelta

import mistralai.workflows as workflows

from . import activities
from .models import (
    PipelineInput,
    PipelineResult,
    RoleInput,
    RoleOutcome,
)


@workflows.workflow.define(name="swarmforge-pipeline", execution_timeout=timedelta(hours=24))
class SwarmforgePipeline:
    @workflows.workflow.entrypoint
    async def run(self, params: PipelineInput) -> PipelineResult:
        co = await activities.sync_checkout(activities.SyncInput(ref=params))
        gated = False
        while True:
            state = await activities.read_task_state(
                activities.RefInput(main=co.main, task_id=params.task_id)
            )
            if state.stage == "done":
                await activities.publish(
                    activities.PublishInput(co=co, remote=params.remote, task_id=params.task_id)
                )
                return PipelineResult(status="done", stage="done", needs="none")
            if state.needs in ("operator", "approval"):
                result = PipelineResult(status="blocked", stage=state.stage, needs=state.needs)
                if state.needs == "approval" and not gated:
                    ref = activities.RefInput(main=co.main, task_id=params.task_id)
                    for gate in await activities.list_gates(ref):
                        result.reports.append(
                            await activities.run_gate(
                                activities.GateRunInput(co=co, task_id=params.task_id, gate=gate)
                            )
                        )
                    judge = await activities.spec_judge_role(ref)
                    if judge is not None:
                        result.verdict = await activities.run_spec_judge(
                            activities.JudgeRunInput(co=co, task_id=params.task_id, reports=result.reports)
                        )
                    gated = True
                await activities.publish(
                    activities.PublishInput(co=co, remote=params.remote, task_id=params.task_id)
                )
                return result
            outcome = await activities.run_stage_role(
                activities.StageRunInput(co=co, task_id=params.task_id, role=state.stage)
            )
            await activities.publish(
                activities.PublishInput(co=co, remote=params.remote, task_id=params.task_id)
            )
            if outcome.status == "failed":
                raise workflows.WorkflowError(
                    f"stage {state.stage} made no progress on task {params.task_id}"
                )
            if outcome.status == "blocked":
                result = PipelineResult(
                    status="blocked",
                    stage=outcome.stage,
                    needs=outcome.needs,
                    detail=outcome.report,
                )
                if outcome.needs == "approval" and not gated:
                    ref = activities.RefInput(main=co.main, task_id=params.task_id)
                    for gate in await activities.list_gates(ref):
                        result.reports.append(
                            await activities.run_gate(
                                activities.GateRunInput(co=co, task_id=params.task_id, gate=gate)
                            )
                        )
                    judge = await activities.spec_judge_role(ref)
                    if judge is not None:
                        result.verdict = await activities.run_spec_judge(
                            activities.JudgeRunInput(co=co, task_id=params.task_id, reports=result.reports)
                        )
                    gated = True
                return result


@workflows.workflow.define(name="swarmforge-role", execution_timeout=timedelta(hours=6))
class SwarmforgeRole:
    @workflows.workflow.entrypoint
    async def run(self, params: RoleInput) -> RoleOutcome:
        co = await activities.sync_checkout(activities.SyncInput(ref=params))
        ref = activities.RefInput(main=co.main, task_id=params.task_id)
        gates = await activities.list_gates(ref)
        if params.role in gates:
            report = await activities.run_gate(
                activities.GateRunInput(co=co, task_id=params.task_id, gate=params.role)
            )
            await activities.publish(
                activities.PublishInput(co=co, remote=params.remote, task_id=params.task_id)
            )
            state = await activities.read_task_state(ref)
            return RoleOutcome(
                status="advanced",
                stage=state.stage,
                needs=state.needs,
                report=report.report,
            )
        outcome = await activities.run_stage_role(
            activities.StageRunInput(co=co, task_id=params.task_id, role=params.role)
        )
        await activities.publish(
            activities.PublishInput(co=co, remote=params.remote, task_id=params.task_id)
        )
        return outcome
