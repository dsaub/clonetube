FROM python:3.14-alpine AS builder

WORKDIR /app

COPY --from=ghcr.io/astral-sh/uv:0.11.32@sha256:df4cae8f3a96d175e2e5f992e597550000edbe78fdc2594d5cd8de1a217f504c /uv /bin/uv

COPY pyproject.toml uv.lock ./

RUN uv sync --no-dev --no-build --locked

FROM python:3.14-alpine 

RUN apk upgrade --no-cache \
    && apk add --no-cache ffmpeg \
    && ffmpeg -version >/dev/null \
    && ffprobe -version >/dev/null \
    && ffmpeg -hide_banner -encoders 2>&1 | grep -q libx264 \
    && /usr/local/bin/python -m pip uninstall -y pip \
    && adduser -D -u 10001 app

WORKDIR /app

COPY --from=builder /app/.venv /app/.venv
COPY clients.py constants.py mailer.py main.py models.py settings.py telemetry.py transcoder.py video_settings.py video_worker.py ./

ENV PATH="/app/.venv/bin:$PATH"
ENV PYTHONUNBUFFERED=1

USER app

CMD ["python", "main.py"]
