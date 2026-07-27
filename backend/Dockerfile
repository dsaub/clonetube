# --- Etapa 1: Builder ---
FROM python:3.14-alpine AS builder

WORKDIR /app

# uv fijado a una version concreta: `latest` haria que cada build dependiese de
# una imagen mutable de terceros.
COPY --from=ghcr.io/astral-sh/uv:0.11.32 /uv /bin/uv

# Copiamos los archivos de dependencias nativos de uv
COPY pyproject.toml uv.lock ./

RUN uv sync --no-dev --no-build --locked


# --- Etapa 2: Imagen Final ---
FROM python:3.14-alpine

WORKDIR /app

# Alpine en lugar de Debian slim: saca de la imagen perl, glibc, apt/dpkg,
# systemd y el resto de la base GNU, que aportaban la mayor parte de los CVEs
# sin parche. `apk upgrade` aplica los parches publicados despues de la imagen
# base. El ffmpeg de Alpine trae libx264 y el encoder aac que usa
# video_processor.
RUN apk upgrade --no-cache && \
    apk add --no-cache ffmpeg && \
    adduser -D -u 10001 app

COPY --from=builder /app/.venv /app/.venv
COPY . .
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENV PATH="/app/.venv/bin:$PATH"
ENV PYTHONUNBUFFERED=1

# El proceso no necesita root en ejecucion.
USER app

EXPOSE 8000

ENTRYPOINT ["/entrypoint.sh"]
CMD ["fastapi", "run", "main.py", "--port", "8000"]
