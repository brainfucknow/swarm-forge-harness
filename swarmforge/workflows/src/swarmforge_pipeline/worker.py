"""Worker entrypoint: registers both SwarmForge workflows.

Run locally with MISTRAL_API_KEY set: uv run python -m swarmforge_pipeline.worker
"""

from __future__ import annotations

import asyncio
import os
from pathlib import Path

import mistralai.workflows as workflows

from .pipeline import SwarmforgePipeline, SwarmforgeRole

WORKFLOWS = [SwarmforgePipeline, SwarmforgeRole]


def ensure_api_key() -> None:
    """A managed worker has no API key; the platform mounts the deployment's
    service-account token at MISTRAL_SA_TOKEN_PATH. The Agents SDK's local
    runtime reads MISTRAL_API_KEY, so expose the token there when no key is
    set."""
    if os.environ.get("MISTRAL_API_KEY"):
        return
    path = os.environ.get("MISTRAL_SA_TOKEN_PATH")
    if not path:
        return
    token = Path(path).read_text(encoding="utf-8").strip()
    if token:
        os.environ["MISTRAL_API_KEY"] = token


async def main() -> None:
    ensure_api_key()
    await workflows.run_worker(WORKFLOWS)


if __name__ == "__main__":
    asyncio.run(main())
