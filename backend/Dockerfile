# --- Etapa 1: Builder ---
FROM python:3.14-slim AS builder

WORKDIR /app

COPY --from=ghcr.io/astral-sh/uv:latest /uv /bin/uv

# Copiamos los archivos de dependencias nativos de uv
COPY pyproject.toml uv.lock ./

RUN uv sync --no-dev


# --- Etapa 2: Imagen Final ---
FROM python:3.14-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg && \
    rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/.venv /app/.venv
COPY . .
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENV PATH="/app/.venv/bin:$PATH"
ENV PYTHONUNBUFFERED=1

EXPOSE 8000

ENTRYPOINT ["/entrypoint.sh"]
CMD ["fastapi", "run", "main.py", "--port", "8000"]
