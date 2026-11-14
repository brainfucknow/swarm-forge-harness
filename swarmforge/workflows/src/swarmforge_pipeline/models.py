"""Input, output, and state models of the SwarmForge workflows. All
JSON-serializable so they cross the workflow/activity boundary."""

from __future__ import annotations

from pydantic import BaseModel, Field


class TaskRef(BaseModel):
    """Where a task lives: the repository to clone and the task branch."""

    repository: str
    task_id: str
    remote: str = "origin"
    base_branch: str = "main"


class PipelineInput(TaskRef):
    """One swarmforge-pipeline execution drives one task."""


class RoleInput(TaskRef):
    """One swarmforge-role execution runs one role or gate on a task."""

    role: str


class CheckoutInfo(BaseModel):
    """The worker-local clone and the task worktree inside it."""

    main: str
    worktree: str
    branch: str


class TaskState(BaseModel):
    """The task's stage, needs, and route as sf reports them."""

    stage: str
    needs: str = "none"
    route: list[str] = Field(default_factory=list)


class GateReport(BaseModel):
    """One gate's report. A gate records nothing; this is all it returns."""

    role: str
    report: str


class JudgeVerdict(BaseModel):
    """The spec-judge's spec verdict and reason. It records nothing; the
    operator's session records it with sf spec-verdict."""

    verdict: str
    reason: str


class RoleOutcome(BaseModel):
    """What one role or gate run left behind."""

    status: str  # advanced | blocked | failed
    stage: str
    needs: str = "none"
    report: str = ""


class PipelineResult(BaseModel):
    """The execution's outcome. blocked carries the gate reports and the spec
    verdict of the specification that waits for approval, so the operator's
    session can show them and record the verdict itself."""

    status: str  # done | blocked
    stage: str = ""
    needs: str = "none"
    reports: list[GateReport] = Field(default_factory=list)
    verdict: JudgeVerdict | None = None
    detail: str = ""
