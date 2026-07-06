# --- Etapa 1: Builder ---
FROM python:3.14-slim AS builder

# Traer uv desde su imagen oficial
COPY --from=ghcr.io/astral-sh/uv:latest /uv /bin/uv

WORKDIR /app

# Optimizaciones de uv para contenedores
ENV UV_LINK_MODE=copy
ENV UV_COMPILE_BYTECODE=1

# Instalar dependencias usando el caché de uv
COPY pyproject.toml uv.lock ./
RUN --mount=type=cache,target=/root/.cache/uv \
    uv sync --frozen --no-dev

# --- Etapa 2: Imagen Final (Distroless) ---
FROM gcr.io/distroless/cc-debian12:nonroot

WORKDIR /app

# Copiar solo los binarios de Python esenciales de la etapa anterior
COPY --from=builder /usr/local/bin/python3.14 /usr/local/bin/python3.14
COPY --from=builder /usr/local/bin/python3 /usr/local/bin/python3
COPY --from=builder /usr/local/lib/ /usr/local/lib/

# Copiar el entorno virtual (.venv) creado por uv y el código de la app
COPY --from=builder --chown=nonroot:nonroot /app/.venv /app/.venv
COPY --chown=nonroot:nonroot . .

# Variables de entorno requeridas
ENV PATH="/app/.venv/bin:/usr/local/bin:$PATH"
ENV PYTHONPATH="/app"
ENV PYTHONUNBUFFERED=1

EXPOSE 8000

# CORRECCIÓN: Ejecución explícita sin depender del shell de PATH
CMD ["/app/.venv/bin/python", "-m", "uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]