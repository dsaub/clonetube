# --- Etapa 1: Builder ---
FROM python:3.14-slim AS builder

WORKDIR /app

COPY --from=ghcr.io/astral-sh/uv:latest /uv /bin/uv

# Copiamos los archivos de dependencias nativos de uv
COPY pyproject.toml uv.lock ./

RUN uv pip export --frozen --no-dev -o requirements.txt && \
    uv pip install --no-cache-dir --target=/app/site-packages -r requirements.txt


# --- Etapa 2: Imagen Final ---
FROM python:3.14-slim

WORKDIR /app

COPY --from=builder /app/site-packages /app/site-packages
COPY . .

ENV PYTHONPATH="/app/site-packages"
ENV PYTHONUNBUFFERED=1

EXPOSE 8000

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
