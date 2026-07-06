# Clonetube — AGENTS.md

> Analisis completo de la arquitectura, componentes, dependencias y estado actual del proyecto.

---

## Estructura general del proyecto

```
clonetube/
├── .github/
│   └── workflows/
│       └── docker-build.yml     # CI/CD: build y push de imagenes Docker
├── backend/                     # API REST con FastAPI (Python 3.14+)
│   ├── main.py                  # App FastAPI + uvicorn
│   ├── settings.py              # Variables de entorno (AWS/MinIO, DB)
│   ├── clients.py               # Cliente S3 (boto3)
│   ├── pymodels.py              # Modelos Pydantic (multipart upload)
│   ├── routes/
│   │   ├── __init__.py
│   │   └── video.py             # Endpoints de subida multipart a S3
│   ├── pyproject.toml
│   ├── uv.lock
│   ├── .env.example
│   └── .python-version
├── frontend/                    # SPA con Vue 3 + TypeScript + Vite
│   ├── src/
│   │   ├── App.vue              # Componente raiz con <RouterView />
│   │   ├── main.ts              # Bootstrap: Pinia + Router
│   │   ├── router/index.ts      # Rutas (lazy loading)
│   │   ├── stores/counter.ts    # Store de ejemplo (Pinia)
│   │   ├── views/
│   │   │   └── DevView.vue      # Sandbox de desarrollo (multipart upload)
│   │   └── components/
│   │       └── UploadModal.vue   # Modal de subida multipart a S3
│   ├── index.html
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json / tsconfig.app.json / tsconfig.node.json
│   └── env.d.ts
├── deploy/                      # Configuraciones de despliegue
│   ├── docker-compose.yml       # Stack completo (MariaDB, MinIO, backend, frontend, nginx)
│   ├── backend.Dockerfile       # Imagen Python multi-stage con uv
│   ├── frontend.Dockerfile      # Imagen Node multi-stage + nginx
│   ├── nginx.conf               # Reverse proxy HTTPS con SSL termination
│   ├── generate-certs.sh        # Generador de certificados autofirmados
│   └── .gitignore               # Excluye certs/ y .env
└── AGENTS.md
```

El proyecto **clonetube** es un clon de YouTube. Tiene separacion clara entre frontend y backend. La carpeta `deploy/` contiene la configuracion completa de despliegue con Docker Compose (MariaDB, MinIO, nginx con HTTPS). CI/CD configurado con GitHub Actions.

---

## Backend (`backend/`)

### Descripcion general

API REST construida con **FastAPI** sobre **Python 3.14+**. Usa `uv` como gestor de paquetes. El backend expone endpoints para subida de videos mediante **multipart upload** a S3 (compatible con AWS S3 y MinIO). No hay persistencia en base de datos aun — se usa un diccionario en memoria para el registro de archivos.

### Archivos

| Archivo | Proposito | Estado |
|---|---|---|
| `main.py` | App FastAPI con titulo, descripcion y version. Incluye el router de video y runner uvicorn. | Implementado |
| `settings.py` | Variables de entorno: `AWS_*`, `S3_ENDPOINT_URL`, `DATABASE_URL`. Sin validacion con Pydantic Settings. | Implementado |
| `clients.py` | Cliente S3 con boto3. Soporta endpoint URL custom (MinIO) y firma v4. | Implementado |
| `pymodels.py` | Modelos Pydantic: `StartMultipartResponse`, `SignChunkResponse`, `CompleteMultipartResponse`, `PartInfo`, `CompleteMultipartBody`. | Implementado |
| `routes/video.py` | Router con prefijo `/api/v1/video` y 3 endpoints de multipart upload. | Implementado |
| `pyproject.toml` | Dependencias: fastapi[standard], sqlmodel, alembic, boto3. | Configurado |
| `.env.example` | Template de variables de entorno con valores para MinIO local y MariaDB. | Configurado |
| `uv.lock` | Lockfile de dependencias. | Generado |

### Dependencias (`pyproject.toml`)

| Dependencia | Version | Proposito |
|---|---|---|
| `fastapi[standard]` | `>=0.139.0` | Framework web asincrono. El extra `[standard]` incluye uvicorn, pydantic, etc. |
| `sqlmodel` | `>=0.0.39` | ORM que combina SQLAlchemy y Pydantic (aun sin uso en modelos de BD). |
| `alembic` | `>=1.18.5` | Migraciones de BD (aun sin configurar). |
| `boto3` | `>=1.43.40` | SDK de AWS para interactuar con S3/MinIO. |

### API — Endpoints implementados

| Metodo | Ruta | Proposito |
|---|---|---|
| `POST` | `/api/v1/video/start-multipart` | Inicia un multipart upload en S3. Recibe `original_filename` por query. Genera key UUID. Devuelve `uploadId`, `key`, `original_filename`. |
| `GET` | `/api/v1/video/sign-chunk` | Genera URL prefirmada (1h) para subir un fragmento. Parametros: `filename`, `upload_id`, `chunk_number`. |
| `POST` | `/api/v1/video/complete-multipart` | Completa el multipart upload. Recibe `filename`, `uploadId`, `parts[]` en el body. |

### Analisis tecnico

- **FastAPI** elegido por rendimiento asincrono, validacion con Pydantic y OpenAPI/Swagger automatico.
- **boto3** con soporte para `endpoint_url` permite usar MinIO local o cualquier S3-compatible.
- Las claves de video se generan con `uuid4` dentro de `videos/` para evitar colisiones.
- El registro `_filename_registry` es un `dict` en memoria (no persiste entre reinicios).
- No hay modelos de BD ni migraciones configuradas (sqlmodel y alembic instalados pero sin usar).
- No hay CORS configurado.
- No hay autenticacion.
- No hay tests.

### Tareas pendientes

- [ ] Agregar CORS (`CORSMiddleware`) para peticiones desde el frontend.
- [ ] Migrar `settings.py` a Pydantic Settings con validacion.
- [ ] Definir modelos de BD con SQLModel (User, Video, Comment, etc.).
- [ ] Configurar Alembic y crear migracion inicial.
- [ ] Migrar `_filename_registry` a base de datos.
- [ ] Implementar endpoints REST adicionales (listado, busqueda, streaming).
- [ ] Agregar autenticacion (JWT).
- [ ] Agregar tests con `pytest` + `httpx`.

---

## Frontend (`frontend/`)

### Descripcion general

SPA construida con **Vue 3** (Composition API + `<script setup>`), **TypeScript 6.0**, **Vite 8**, **Pinia** para estado global y **Vue Router 5** para enrutamiento. Tema oscuro con CSS custom. El unico flujo implementado es la vista de desarrollo (`/dev`) con el modal de subida multipart a S3.

### Estructura de `src/`

```
src/
├── App.vue                    # <RouterView /> + reset CSS + tema oscuro
├── main.ts                    # Bootstrap: Pinia + Router
├── router/
│   └── index.ts               # Ruta /dev con lazy loading
├── stores/
│   └── counter.ts             # Store de ejemplo (no funcional)
├── views/
│   └── DevView.vue            # Sandbox: interfaz de prueba de multipart upload
└── components/
    └── UploadModal.vue        # Modal completo de subida: seleccion, progreso, logs, resultado
```

### Analisis por archivo fuente

#### `App.vue`

```vue
<script setup lang="ts"></script>
<template>
  <RouterView />
</template>
<style>
  /* Reset CSS universal + tema oscuro (#0f0f1a) */
</style>
```

- `<RouterView />` implementado. Sin layout compartido (header, sidebar).
- Estilos globales: reset de box-sizing, fondo oscuro, fuente Inter.

#### `router/index.ts`

- Ruta unica: `/dev` → `DevView.vue` con lazy loading.
- `createWebHistory` (sin `#` en URL).

#### `DevView.vue`

Pagina sandbox para probar el flujo multipart upload. Incluye:
- Header con badge "DEV" y enlace de vuelta.
- Hero section con descripcion del flujo y boton "Subir video".
- Diagrama de los 4 pasos del flujo (seleccionar, fragmentar, subir, completar).
- Listado de los 3 endpoints utilizados con metodo y descripcion.
- Seccion de requisitos.
- Renderiza `<UploadModal>` condicionalmente.

#### `UploadModal.vue`

Componente modal completo que implementa el flujo multipart upload cliente:
- **Estado**: maquina de estados (`idle`, `uploading`, `done`, `error`).
- **Fragmentacion**: chunks de 5 MB.
- **Flujo**:
  1. `POST /api/v1/video/start-multipart` — inicia la subida.
  2. Por cada chunk: `GET /api/v1/video/sign-chunk` → `PUT <presigned-url>`.
  3. `POST /api/v1/video/complete-multipart` — completa la subida.
- **UI**: drop zone, barra de progreso, logs en tiempo real, pantalla de exito con key y location, pantalla de error.
- Estilos scoped con diseño moderno (modal, overlay con backdrop-blur, gradientes).

### Dependencias

| Dependencia | Version | Tipo | Proposito |
|---|---|---|---|
| `vue` | `^3.5.38` | runtime | Framework reactivo de UI |
| `vue-router` | `^5.1.0` | runtime | Enrutamiento SPA |
| `pinia` | `^3.0.4` | runtime | Gestion de estado global |
| `vite` | `^8.0.16` | dev | Build tool y dev server |
| `@vitejs/plugin-vue` | `^6.0.7` | dev | Soporte de Vue SFC en Vite |
| `vite-plugin-vue-devtools` | `^8.1.2` | dev | Vue DevTools en Vite |
| `typescript` | `~6.0.0` | dev | TypeScript |
| `vue-tsc` | `^3.3.5` | dev | Type checking para `.vue` |
| `npm-run-all2` | `^9.0.2` | dev | Ejecutar scripts en paralelo/secuencia |

### Scripts

| Script | Comando | Proposito |
|---|---|---|
| `dev` | `vite` | Servidor de desarrollo con HMR |
| `build` | `run-p type-check "build-only {@}" --` | Build: type-check + vite build en paralelo |
| `preview` | `vite preview` | Previsualizar build de produccion |
| `build-only` | `vite build` | Solo build sin type-check |
| `type-check` | `vue-tsc --build` | Verificacion de tipos |

### Analisis tecnico

- **Vite 8**: HMR instantaneo, build con Rollup.
- **TypeScript 6.0** con `noUncheckedIndexedAccess`.
- **Pinia** con Setup Store syntax.
- **Vite proxy**: las peticiones a `/api` se redirigen al backend (configurado en `vite.config.ts` o via nginx en produccion).
- El frontend implementa el flujo completo de subida multipart desde el navegador.
- No usa librerias externas de UI — todos los estilos son CSS scoped manual.
- Tema oscuro consistente en toda la app.

### Tareas pendientes

- [ ] Crear layout principal con header, sidebar y `<RouterView />`.
- [ ] Crear vistas: `HomeView`, `WatchView`, `ChannelView`, `SearchView`.
- [ ] Definir rutas adicionales con lazy loading.
- [ ] Crear stores reales: `useAuthStore`, `useVideoStore`.
- [ ] Agregar componentes UI: `VideoCard`, `CommentSection`, `VideoPlayer`, `Sidebar`.
- [ ] Eliminar store de ejemplo `counter.ts`.
- [ ] Agregar vistas para login/registro.
- [ ] Integrar reproductor de video (HLS/DASH).

---

## Deploy (`deploy/`)

### Estado actual

Configuracion completa de despliegue con Docker Compose. Stack de 6 servicios.

### Servicios (`docker-compose.yml`)

| Servicio | Imagen | Puerto | Proposito |
|---|---|---|---|
| `mariadb` | `mariadb:11` | — | Base de datos relacional |
| `minio` | `minio/minio:latest` | `9000` (API), `9001` (consola) | Almacenamiento S3-compatible |
| `minio-init` | `minio/mc:latest` | — | Crea el bucket automaticamente al iniciar |
| `backend` | `ghcr.io/dsaub/clonetube-backend:latest` | `8000` (interno) | API FastAPI |
| `frontend` | `ghcr.io/dsaub/clonetube-frontend:latest` | `80` (interno) | SPA servida por nginx |
| `nginx` | `nginx:alpine` | `80`, `443` | Reverse proxy HTTPS con SSL termination |

### Dockerfiles

#### `backend.Dockerfile`

Build multi-stage:
1. **builder**: `python:3.14-slim` + `uv`. Copia `pyproject.toml` y `uv.lock`, ejecuta `uv sync --frozen --no-dev`.
2. **runtime**: `python:3.14-slim`. Copia `.venv` del builder, copia codigo fuente. Ejecuta con usuario no-root `app`. Comando: `uv run uvicorn main:app --host 0.0.0.0 --port 8000`.

Requiere `.dockerignore` en `backend/` (documentado en comentarios del Dockerfile).

#### `frontend.Dockerfile`

Build multi-stage:
1. **build**: `node:24-slim` + `pnpm`. Instala dependencias, ejecuta `pnpm run build`.
2. **runtime**: `nginx:alpine`. Copia `dist/` a `/usr/share/nginx/html`. Configura nginx inline con proxy reverso a `backend:8000` para `/api/`, SPA fallback a `index.html`, y cache de assets estaticos.

### nginx.conf

Configuracion de nginx como reverse proxy HTTPS:
- Puerto 80 → redirect 301 a HTTPS.
- Puerto 443 → SSL termination con certificados en `/etc/nginx/certs/`.
- `client_max_body_size 10G` (para subida de videos grandes).
- Rutas: `/api/` → `backend:8000`, assets estaticos con cache 1y, resto → `frontend:80`.

### generate-certs.sh

Script bash que genera certificados autofirmados para desarrollo local:
- `privkey.pem` (clave privada RSA 2048).
- `fullchain.pem` (certificado x509).
- Valido por 365 dias.
- Subject: `localhost` con SAN: `DNS:localhost`, `DNS:*.localhost`, `IP:127.0.0.1`.
- No sobrescribe si ya existen.

### Variables de entorno requeridas

| Variable | Default | Proposito |
|---|---|---|
| `MARIADB_ROOT_PASSWORD` | `rootpassword` | Password root de MariaDB |
| `MARIADB_DATABASE` | `clonetube` | Nombre de la BD |
| `MARIADB_USER` | `clonetube` | Usuario de la BD |
| `MARIADB_PASSWORD` | `password` | Password del usuario |
| `MINIO_ROOT_USER` | `minioadmin` | Usuario root de MinIO |
| `MINIO_ROOT_PASSWORD` | `minioadmin` | Password root de MinIO |
| `AWS_ACCESS_KEY_ID` | `minioadmin` | Access key S3 |
| `AWS_SECRET_ACCESS_KEY` | `minioadmin` | Secret key S3 |
| `AWS_REGION` | `us-east-1` | Region AWS |
| `AWS_BUCKET_NAME` | `clonetube` | Nombre del bucket S3 |
| `S3_ENDPOINT_URL` | `http://minio:9000` | Endpoint S3 (MinIO) |
| `DATABASE_URL` | `mysql+pymysql://clonetube:password@mariadb:3306/clonetube` | Cadena de conexion BD |

---

## CI/CD (`.github/workflows/docker-build.yml`)

Workflow de GitHub Actions que construye y publica imagenes Docker en `ghcr.io`.

- **Trigger**: push a `latest`, `workflow_dispatch` (manual).
- **Estrategia**: matrix sobre `[backend, frontend]`.
- **Pasos**: checkout → login a ghcr.io → setup buildx → build & push.
- **Tags**: `latest` y `${{ github.sha }}`.
- **Cache**: GitHub Actions cache para acelerar builds.
- **Contexto**: `./<service>` con Dockerfile en `./deploy/<service>.Dockerfile`.

---

## Arquitectura general

```mermaid
graph TD
    subgraph Frontend["Frontend (Vue 3 + Vite)"]
        A[App.vue] --> B[RouterView]
        B --> C[DevView]
        C --> D[UploadModal]
        F[Pinia Stores]
    end

    subgraph Backend["Backend (FastAPI)"]
        G[FastAPI App]
        G --> H[routes/video.py]
        H --> I[boto3 S3 Client]
        I --> J[(MinIO / S3)]
        G --> K[(MariaDB - pendiente)]
    end

    subgraph Deploy["Deploy (Docker Compose)"]
        L[nginx :443] --> M[frontend :80]
        L --> N[backend :8000]
        N --> O[(mariadb)]
        N --> P[(minio)]
    end

    Frontend -->|HTTP REST API| Backend
    Backend -->|JSON| Frontend
    CI[GitHub Actions] -->|push images| R[ghcr.io]
```

---

## Resumen de madurez del proyecto

| Capa | Estado | Progreso |
|---|---|---|
| Backend — Framework | FastAPI configurado con router | 15% |
| Backend — API Multipart Upload | 3 endpoints implementados | 60% |
| Backend — Modelos BD | No iniciado (sqlmodel sin usar) | 0% |
| Backend — Autenticacion | No iniciado | 0% |
| Backend — Tests | No iniciado | 0% |
| Frontend — Estructura | App.vue con RouterView + tema oscuro | 15% |
| Frontend — Componente Upload | UploadModal completo con flujo multipart | 80% |
| Frontend — Vistas/Rutas | Solo `/dev` (sandbox) | 5% |
| Frontend — Stores | Template de ejemplo | 2% |
| Deploy — Docker | Stack completo con 6 servicios | 90% |
| Deploy — CI/CD | GitHub Actions funcional | 80% |

---

## Convenciones y guias para agentes

### Backend
- Usar **type hints** de Python en todo el codigo.
- Los modelos de BD se definen con `SQLModel` (hereda de `SQLAlchemy` y `Pydantic`).
- Las migraciones se gestionan con **Alembic**.
- Seguir estructura modular: `models/`, `routers/`, `schemas/`, `services/`, `core/`.
- Usar `uv` para gestion de dependencias: `uv add <paquete>`, `uv sync`.
- Las variables de entorno se leen desde `settings.py`.

### Frontend
- Usar **`<script setup lang="ts">`** en todos los SFC de Vue.
- Stores de Pinia con **Setup Store syntax** (funciones composables).
- Rutas con **lazy loading**: `() => import('@/views/...')`.
- Estilos con **CSS scoped**.
- Alias `@` mapea a `src/`.
- Usar `pnpm` como package manager.
- Mantener el tema oscuro (`#0f0f1a` fondo, `#e0e0e0` texto, `#6c63ff` accent, `#2a2a4a` bordes).

### General
- Commits en espanol.
- PRs pequenos y enfocados en un solo cambio.
- No incluir secretos en el codigo (usar variables de entorno).
- Las imagenes Docker se publican en `ghcr.io/dsaub/clonetube-<service>`.

---

*Ultima actualizacion: 2026-07-06*
