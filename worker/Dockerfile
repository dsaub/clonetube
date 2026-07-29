FROM python:3.14-alpine AS builder

WORKDIR /app

COPY --from=ghcr.io/astral-sh/uv:0.11.32 /uv /bin/uv

COPY pyproject.toml uv.lock ./

RUN uv sync --no-dev --no-build --locked

FROM python:3.14-alpine 

RUN adduser -D -u 10001 app

WORKDIR /app

COPY --from=builder /app/.venv /app/.venv
COPY . .

ENV PATH="/app/.venv/bin:$PATH"
ENV PYTHONUNBUFFERED=1

USER app

CMD ["python", "main.py"]