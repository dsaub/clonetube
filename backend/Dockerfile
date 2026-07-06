# --- Etapa 1: Builder ---
FROM python:3.14-slim AS builder

WORKDIR /app

# Traemos uv
COPY --from=ghcr.io/astral-sh/uv:latest /uv /bin/uv

# Copiamos los archivos de dependencias nativos de uv
COPY pyproject.toml uv.lock ./

RUN uv pip compile pyproject.toml -o requirements.txt && \
    uv pip install --no-cache-dir --system --target=/app/site-packages -r requirements.txt


# --- Etapa 2: Imagen Final (Distroless Python Oficial) ---
FROM gcr.io/distroless/python3-debian12:nonroot

WORKDIR /app

# Copiamos la carpeta de librerías puras y tu código
COPY --from=builder --chown=nonroot:nonroot /app/site-packages /app/site-packages
COPY --chown=nonroot:nonroot . .

# LE DECIMOS A PYTHON DÓNDE BUSCAR LAS LIBRERÍAS
ENV PYTHONPATH="/app/site-packages"
ENV PYTHONUNBUFFERED=1

EXPOSE 8000

# En Distroless Python, llamamos al módulo uvicorn directamente usando la sintaxis de array
CMD ["-m", "uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
