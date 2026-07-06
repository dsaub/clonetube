# Clonetube

Clon de YouTube construido con **Vue 3 + FastAPI**. Subida de videos con multipart upload a S3/MinIO, desplegable con Docker Compose.

## Stack

| Capa | Tecnologia |
|---|---|
| Frontend | Vue 3, TypeScript, Vite, Pinia, Vue Router |
| Backend | FastAPI, Python 3.14, boto3, SQLModel |
| Almacenamiento | MinIO (S3-compatible) |
| Base de datos | MariaDB 11 |
| Proxy | nginx (HTTPS con SSL termination) |
| Despliegue | Docker Compose, GitHub Actions |

## Requisitos

- [Docker](https://docs.docker.com/get-docker/) y [Docker Compose](https://docs.docker.com/compose/install/)
- [Node.js](https://nodejs.org/) >= 22 (con pnpm) — solo desarrollo local
- [Python](https://www.python.org/) >= 3.14 + [uv](https://docs.astral.sh/uv/) — solo desarrollo local

## Inicio rapido (Docker Compose)

```bash
cd deploy/
./generate-certs.sh
docker compose up -d
```

La aplicacion estara disponible en `https://localhost`.

- **Frontend**: `https://localhost`
- **API**: `https://localhost/api/v1/`
- **Swagger**: `https://localhost/api/v1/docs`
- **Consola MinIO**: `http://localhost:9001` (minioadmin / minioadmin)

## Desarrollo local

### Backend

```bash
cd backend/
cp .env.example .env
uv sync
uv run python main.py    # http://localhost:8000
```

### Frontend

```bash
cd frontend/
pnpm install
pnpm dev                 # http://localhost:5173
```

Las peticiones a `/api` se redirigen automaticamente al backend via el proxy de Vite.

## Estructura del proyecto

```
clonetube/
├── .github/workflows/    # CI/CD: build y push de imagenes Docker
├── backend/              # API REST (FastAPI + boto3)
├── frontend/             # SPA (Vue 3 + TypeScript + Vite)
├── deploy/               # Docker Compose, Dockerfiles, nginx, SSL
└── AGENTS.md             # Documentacion tecnica para agentes
```

## API — Endpoints actuales

| Metodo | Ruta | Descripcion |
|---|---|---|
| `POST` | `/api/v1/video/start-multipart` | Inicia subida multipart a S3 |
| `GET` | `/api/v1/video/sign-chunk` | Genera URL prefirmada por fragmento |
| `POST` | `/api/v1/video/complete-multipart` | Completa la subida multipart |

## CI/CD

Cada push a la rama `latest` construye y publica imagenes Docker en `ghcr.io/dsaub/clonetube-backend` y `ghcr.io/dsaub/clonetube-frontend`.

## Licencia

Privado — Proyecto personal de aprendizaje.
