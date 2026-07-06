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

FROM gcr.io/distroless/cc-debian12:nonroot

COPY --from=builder /usr/local/bin/python3.14 /usr/local/bin/python3.14
COPY --from=builder /usr/local/bin/python3 /usr/local/bin/python3
COPY --from=builder /usr/local/bin/python /usr/local/bin/python
COPY --from=builder /usr/local/lib/ /usr/local/lib/
COPY --from=builder /usr/lib/x86_64-linux-gnu/ /usr/lib/x86_64-linux-gnu/
COPY --from=builder /lib/x86_64-linux-gnu/ /lib/x86_64-linux-gnu/

WORKDIR /app
COPY --chown=nonroot:nonroot . .
COPY --from=builder --chown=nonroot:nonroot /app/.venv ./.venv

ENV PATH="/app/.venv/bin:/usr/local/bin:$PATH"
ENV PYTHONPATH="/app"
ENV PYTHONUNBUFFERED=1

EXPOSE 8000

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
