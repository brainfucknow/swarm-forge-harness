"""Worker entrypoint: registers both SwarmForge workflows.

Run locally with MISTRAL_API_KEY set: uv run python -m swarmforge_pipeline.worker
"""

from __future__ import annotations

import asyncio

import mistralai.workflows as workflows

from .pipeline import SwarmforgePipeline, SwarmforgeRole

WORKFLOWS = [SwarmforgePipeline, SwarmforgeRole]


async def main() -> None:
    await workflows.run_worker(WORKFLOWS)


if __name__ == "__main__":
    asyncio.run(main())
