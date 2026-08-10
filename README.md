# Clonetube

Clon de YouTube construido con **Vue 3 + Spring Boot**. Subida de videos con multipart upload a Amazon S3, desplegable con Docker Compose.

## Stack

| Capa | Tecnologia |
|---|---|
| Frontend | Vue 3, TypeScript, Vite, Pinia, Vue Router |
| Backend | Springboot 4.0.5 |
| Almacenamiento | Amazon S3 |
| Base de datos | Base de datos externa compatible con MariaDB |
| Workers | Python, SQS, SMTP y FFmpeg/ffprobe |
| Proxy | nginx (HTTPS con SSL termination) |
| Despliegue | Docker Compose, GitHub Actions |

## Requisitos

- [Docker](https://docs.docker.com/get-docker/) y [Docker Compose](https://docs.docker.com/compose/install/)
- [Node.js](https://nodejs.org/) >= 22 (con pnpm) — solo desarrollo local
- [Python](https://www.python.org/) >= 3.14 + [uv](https://docs.astral.sh/uv/) — solo desarrollo local

## Inicio rapido (Docker Compose)

Configura `AWS_BUCKET_NAME`, `VIDEO_TRANSCODE_QUEUE_URL`, `SPRING_DATASOURCE_URL`,
`SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD` en el entorno. Las
credenciales de AWS pueden proporcionarse mediante variables de entorno o un rol
IAM.

El worker de vídeo se ejecuta de forma independiente con
`docker compose up -d video-worker`. En EC2 usa el instance profile para acceder
directamente a SQS y S3; no necesita claves AWS estáticas.

```bash
cd deploy/
./generate-certs.sh
docker compose up -d
```

La aplicacion estara disponible en `https://localhost`.

- **Frontend**: `https://localhost`
- **API**: `https://localhost/api/v1/`
- **Swagger**: `https://localhost/api/v1/docs`

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
| `GET` | `/api/v1/video/feed` | Feed ordenado por el algoritmo de seguidores |
| `GET` | `/api/v1/users/{username}/follow` | Estado de seguimiento y numero de seguidores |
| `POST` | `/api/v1/users/{username}/follow` | Seguir a un usuario |
| `DELETE` | `/api/v1/users/{username}/follow` | Dejar de seguir a un usuario |
| `GET` | `/api/v1/users/me/following` | Lista de usuarios seguidos |

### Algoritmo del feed

`GET /api/v1/video/feed` prioriza a los canales que sigue el usuario autenticado:

1. Los videos de autores seguidos forman un bloque que siempre va delante.
2. Dentro de cada bloque puntua la novedad (decaimiento exponencial con semivida
   de 72 h) y la popularidad (likes en escala logaritmica).
3. Se penaliza cada video adicional del mismo autor para no monopolizar la
   portada, sin que eso pueda colar a un desconocido por delante de un seguido.

Con `?only_following=true` devuelve unicamente los videos de los canales
seguidos. La logica vive en `backend/feed.py` (modulo puro y testeado).

## CI/CD

Cada push a la rama `latest` construye y publica imagenes Docker en `ghcr.io/dsaub/clonetube-backend` y `ghcr.io/dsaub/clonetube-frontend`.

## Licencia

Privado — Proyecto personal de aprendizaje.
