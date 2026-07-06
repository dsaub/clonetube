# --- Etapa 1: Builder ---
FROM python:3.14-slim AS builder

WORKDIR /app

COPY --from=ghcr.io/astral-sh/uv:latest /uv /bin/uv

# Copiamos los archivos de dependencias nativos de uv
COPY pyproject.toml uv.lock ./

RUN uv sync --frozen --no-dev


# --- Etapa 2: Imagen Final ---
FROM python:3.14-slim

WORKDIR /app

COPY --from=builder /app/.venv /app/.venv
COPY . .

ENV PATH="/app/.venv/bin:$PATH"
ENV PYTHONUNBUFFERED=1

EXPOSE 8000

CMD ["fastapi", "run", "main.py", "--port", "8000"]
