# --- Etapa 1: Builder ---
FROM ghcr.io/astral-sh/uv:latest AS uv_bin
FROM python:3.14-slim AS builder

WORKDIR /app

# Copiamos uv directamente desde su imagen oficial
COPY --from=uv_bin /uv /bin/uv

ENV UV_LINK_MODE=copy
ENV UV_COMPILE_BYTECODE=1

# Instalar dependencias en el .venv
COPY pyproject.toml uv.lock ./
RUN --mount=type=cache,target=/root/.cache/uv \
    uv sync --frozen --no-dev

# --- Etapa 2: Imagen Final (Distroless puro) ---
FROM gcr.io/distroless/cc-debian12:nonroot

WORKDIR /app

# 1. Copiamos ÚNICAMENTE el .venv y tu código. Nada de /usr/local/lib
COPY --from=builder --chown=nonroot:nonroot /app/.venv /app/.venv
COPY --chown=nonroot:nonroot . .

# Variables de entorno apuntando al entorno virtual
ENV PATH="/app/.venv/bin:$PATH"
ENV PYTHONPATH="/app"
ENV PYTHONUNBUFFERED=1

EXPOSE 8000

# Ejecutamos usando el Python interno del .venv
CMD ["/app/.venv/bin/python", "-m", "uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]