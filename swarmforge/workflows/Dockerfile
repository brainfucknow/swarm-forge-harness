# Worker image for the SwarmForge workflows: Python plus git and Babashka,
# which swarmforge/bin/sf needs inside the cloned repository.
FROM python:3.12-bookworm

RUN apt-get update \
    && apt-get install -y --no-install-recommends git curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && curl -fsSL -o /tmp/bb.tar.gz \
       https://github.com/babashka/babashka/releases/download/v1.13.229/babashka-1.13.229-linux-amd64.tar.gz \
    && tar -xzf /tmp/bb.tar.gz -C /usr/local/bin \
    && rm /tmp/bb.tar.gz \
    && bb --version

WORKDIR /app
COPY pyproject.toml uv.lock ./
# The project is installed from src/, so the sources must exist before uv sync.
COPY src ./src
RUN pip install uv && uv sync --frozen --no-dev
COPY . .

CMD ["uv", "run", "python", "-m", "swarmforge_pipeline.worker"]
