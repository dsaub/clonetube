# ═══════════════════════════════════════════════════════════
# .dockerignore (ensure this file exists in backend/)
# ═══════════════════════════════════════════════════════════
# .venv/
# __pycache__/
# *.pyc
# .git/
# .gitignore
# .python-version
# README.md
# ═══════════════════════════════════════════════════════════

FROM python:3.14-slim AS builder
COPY --from=ghcr.io/astral-sh/uv:latest /uv /bin/uv

WORKDIR /app

ENV UV_LINK_MODE=copy
ENV UV_COMPILE_BYTECODE=1

COPY pyproject.toml uv.lock ./
RUN --mount=type=cache,target=/root/.cache/uv \
    uv sync --frozen --no-dev

FROM python:3.14-slim

RUN groupadd --system app && \
    useradd --system --no-create-home --gid app app

WORKDIR /app

COPY --from=ghcr.io/astral-sh/uv:latest /uv /bin/uv

COPY --chown=app:app . .
COPY --from=builder --chown=app:app /app/.venv ./.venv

ENV PATH="/app/.venv/bin:$PATH"
ENV PYTHONPATH="/app"
ENV PYTHONUNBUFFERED=1

EXPOSE 8000

USER app

CMD ["uv", "run", "uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
