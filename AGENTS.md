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
│   ├── main.py                  # FastAPI + CORS + lifespan (crea tablas startup)
│   ├── settings.py              # Pydantic Settings (AWS, DB, JWT)
│   ├── database.py              # Engine SQLModel + get_session dependency
│   ├── models.py                # SQLModel: User, Video, UserLikesVideo
│   ├── clients.py               # Cliente S3 (boto3) con endpoint_URL custom
│   ├── pymodels.py              # Modelos Pydantic (multipart upload, auth, listado, streaming)
│   ├── video_processor.py       # Transcodificacion con ffmpeg (MP4/H.264/AAC)
│   ├── auth/
│   │   ├── security.py          # Hash bcrypt + JWT + reset token (create/decode, password_version)
│   │   ├── schemas.py           # Pydantic: RegisterRequest, LoginRequest, TokenResponse, etc.
│   │   └── dependencies.py      # get_current_user (valida JWT + password_version)
│   ├── routes/
│   │   ├── login.py             # /api/v1/auth (register, login, change-password, forgot-password, reset-password, me)
│   │   ├── video.py             # Endpoints multipart upload + list + stream + transcoding
│   │   └── auth.py             # Antiguo router de auth (reemplazado por login.py)
│   ├── alembic/                 # Migraciones con Alembic
│   │   ├── env.py               # Usa settings.DATABASE_URL + SQLModel metadata
│   │   ├── script.py.mako
│   │   └── versions/            # Migraciones generadas
│   ├── alembic.ini
│   ├── entrypoint.sh            # Ejecuta `alembic upgrade head` + CMD
│   ├── Dockerfile               # Python slim + ffmpeg + entrypoint
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
│   │       └── UploadModal.vue   # Modal de subida por fragmentos
│   ├── Dockerfile               # Imagen Node multi-stage + Caddy distroless
│   ├── index.html
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json / tsconfig.app.json / tsconfig.node.json
│   └── env.d.ts
├── docker-compose.yml           # Stack desarrollo (MariaDB, MinIO, backend, frontend, nginx)
├── deploy/                        # Configuraciones de despliegue
│   ├── docker-compose.yml       # Stack produccion (imagenes ghcr.io pre-built)
│   ├── nginx.conf               # Reverse proxy HTTPS con SSL termination
│   ├── generate-certs.sh        # Generador de certificados autofirmados
│   └── .gitignore               # Excluye certs/ y .env
└── AGENTS.md
```

El proyecto **clonetube** es un clon de YouTube. Tiene separacion clara entre frontend y backend. La carpeta `deploy/` contiene la configuracion de despliegue en produccion con Docker Compose (MariaDB, MinIO, nginx con HTTPS), y `docker-compose.yml` en la raiz para desarrollo. CI/CD configurado con GitHub Actions.

---

## Backend (`backend/`)

### Descripcion general

API REST construida con **FastAPI** sobre **Python 3.14+**. Usa `uv` como gestor de paquetes. El backend expone endpoints para subida de videos mediante **multipart upload** a S3 (compatible con AWS S3 y MinIO), autenticacion JWT, y persistencia en MariaDB mediante SQLModel + Alembic.

### Archivos

| Archivo | Proposito | Estado |
|---|---|---|---|
| `main.py` | App FastAPI con titulo, descripcion y version. Incluye el router de auth y video, CORS y lifespan (crea tablas al inicio). | Implementado |
| `settings.py` | Pydantic Settings con validacion: `AWS_*`, `S3_ENDPOINT_URL`, `DATABASE_URL`, `JWT_*`. | Implementado |
| `database.py` | Engine SQLModel + `get_session` como dependency de FastAPI. | Implementado |
| `models.py` | Tablas SQLModel: `User`, `Video`, `UserLikesVideo`. User incluye `password_reset_token_hash` y `password_reset_expires_at`. | Implementado |
| `clients.py` | Cliente S3 con boto3. Soporta endpoint URL custom (MinIO) y firma v4. | Implementado |
| `pymodels.py` | Modelos Pydantic: multipart upload, auth (register, login, change-password, forgot-password, reset-password), listado y streaming. | Implementado |
| `video_processor.py` | Transcodificacion con ffmpeg (MP4/H.264/AAC) + validacion de extensiones. | Implementado |
| `auth/security.py` | Hash bcrypt + JWT (create/decode) con soporte de `password_version` + `create_reset_token` y `hash_reset_token`. | Implementado |
| `auth/schemas.py` | Pydantic: `RegisterRequest`, `LoginRequest`, `ChangePasswordRequest`, `TokenResponse`, `UserResponse`. | Implementado |
| `auth/dependencies.py` | `get_current_user`: valida JWT + verifica `password_version` contra BD. | Implementado |
| `routes/video.py` | Router con prefijo `/api/v1/video`: multipart upload, listado, streaming, transcodificacion. | Implementado |
| `routes/login.py` | Router con prefijo `/api/v1/auth`: register, login, change-password, forgot-password, reset-password, me. | Implementado |
| `alembic/` | Migraciones con Alembic: `env.py` usa `settings.DATABASE_URL` + metadata de SQLModel. | Configurado |
| `alembic.ini` | Configuracion de Alembic. | Configurado |
| `entrypoint.sh` | Script que ejecuta `alembic upgrade head` antes de iniciar la app. | Implementado |
| `pyproject.toml` | Dependencias: fastapi[standard], sqlmodel, alembic, boto3, passlib, pyjwt, bcrypt, pymysql. | Configurado |
| `.env.example` | Template de variables de entorno con valores para MinIO local y MariaDB. | Configurado |
| `uv.lock` | Lockfile de dependencias. | Generado |

### Dependencias (`pyproject.toml`)

| Dependencia | Version | Proposito |
|---|---|---|---|
| `fastapi[standard]` | `>=0.139.0` | Framework web asincrono. El extra `[standard]` incluye uvicorn, pydantic, etc. |
| `sqlmodel` | `>=0.0.39` | ORM que combina SQLAlchemy y Pydantic. |
| `alembic` | `>=1.18.5` | Migraciones de BD. |
| `boto3` | `>=1.43.40` | SDK de AWS para interactuar con S3/MinIO. |
| `passlib[bcrypt]` | `>=1.7.4` | Hashing de contraseñas con bcrypt. |
| `pyjwt[crypto]` | `>=2.10.0` | Creacion y validacion de tokens JWT. |
| `bcrypt` | `==4.1.3` | Algoritmo bcrypt para passlib. |
| `pymysql` | `>=1.1.1` | Driver MySQL para SQLAlchemy. |

### API — Endpoints implementados

| Metodo | Ruta | Proposito |
|---|---|---|
| `POST` | `/api/v1/video/start-multipart` | Inicia un multipart upload en S3. Recibe `original_filename` por query. Genera key UUID. Devuelve `uploadId`, `key`, `original_filename`. |
| `PUT` | `/api/v1/video/upload-chunk` | Recibe el fragmento y lo reenvia a S3 con el cliente interno. Parametros: `filename`, `upload_id`, `chunk_number`. Devuelve el `ETag`. Es el camino que usa el frontend. |
| `GET` | `/api/v1/video/sign-chunk` | Genera URL prefirmada (1h) para subir un fragmento. Parametros: `filename`, `upload_id`, `chunk_number`. Solo sirve si el bucket es accesible desde el navegador. |
| `POST` | `/api/v1/video/complete-multipart` | Completa el multipart upload. Recibe `filename`, `uploadId`, `parts[]` en el body. |
| `GET` | `/api/v1/video/list` | Lista videos en S3 con metadatos (size, last_modified, original_filename). |
| `GET` | `/api/v1/video/stream-url` | Devuelve la URL de reproduccion (`/api/v1/video/stream`). Para videos no publicos incluye el JWT en la query string. |
| `GET` | `/api/v1/video/stream` | Sirve el video desde S3 con soporte de `Range`. Acepta el JWT por header o por query param `token`. |
| `POST` | `/api/v1/auth/register` | Registra un nuevo usuario. Devuelve token JWT. |
| `POST` | `/api/v1/auth/login` | Inicia sesion con username/password. Devuelve token JWT. |
| `POST` | `/api/v1/auth/change-password` | Cambia la contraseña (requiere auth). Incrementa `password_version` invalidando tokens anteriores. |
| `POST` | `/api/v1/auth/forgot-password` | Solicita restablecimiento de contraseña. Genera token hash con expiracion de 15 min. |
| `POST` | `/api/v1/auth/reset-password` | Restablece contraseña con token. Incrementa `password_version`. |
| `GET` | `/api/v1/auth/me` | Devuelve datos del usuario autenticado. |
| `GET` | `/api/v1/video/feed` | Feed personalizado. Prioriza a los autores seguidos; admite `limit` y `only_following`. |
| `GET` | `/api/v1/users/{username}/follow` | Estado de seguimiento (auth opcional) y numero de seguidores. |
| `POST` | `/api/v1/users/{username}/follow` | Seguir a un usuario (idempotente). |
| `DELETE` | `/api/v1/users/{username}/follow` | Dejar de seguir a un usuario (idempotente). |
| `GET` | `/api/v1/users/me/following` | Usuarios seguidos por el usuario autenticado. |

### Algoritmo del feed (`feed.py`)

Modulo puro, sin acceso a BD ni a S3, para poder probarlo aislado:

- `recency_score`: decaimiento exponencial con semivida de 72 h.
- `popularity_score`: `log1p(likes)`, crecimiento sublineal.
- `score_candidate`: suma novedad, popularidad y `FOLLOW_BOOST`.
- `rank_candidates`: los seguidos forman un bloque que va siempre delante
  (criterio de orden, no sumando) y dentro de cada bloque una seleccion voraz
  penaliza al autor que ya ha colocado videos (`AUTHOR_DIVERSITY_PENALTY`).
  Los empates se resuelven por `video_id` para que el orden sea determinista.

Las consultas de la relacion de seguimiento viven en `follows.py`.

### Analisis tecnico

- **FastAPI** elegido por rendimiento asincrono, validacion con Pydantic y OpenAPI/Swagger automatico.
- **boto3** con soporte para `endpoint_url` permite usar MinIO local o cualquier S3-compatible.
- Las claves de video se generan con `uuid4` dentro de `videos/` para evitar colisiones.
- El registro `_filename_registry` es un `dict` en memoria (no persiste entre reinicios).
- `models.py` define tablas SQLModel (`User`, `Video`, `UserLikesVideo`) mapeadas a MariaDB. User incluye campos de reset de contraseña.
- `alembic/` configurado con `env.py` que lee `settings.DATABASE_URL` y usa `SQLModel.metadata` para autogenerate. Con migracion `0001_add_password_reset_fields` generada.
- CORS configurado en `main.py` con `CORSMiddleware` (allow all origins).
- Autenticacion JWT implementada: registro, login, cambio de contraseña con `password_version`, forgot/reset password con token de 15 min, proteccion de rutas via `get_current_user`.
- `routes/login.py` reemplaza a `routes/auth.py` con 6 endpoints completos.
- No hay tests.

### Tareas pendientes

- [ ] Migrar `_filename_registry` a base de datos.
- [ ] Implementar endpoints REST adicionales (listado, busqueda, streaming).
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
  2. Por cada chunk: `PUT /api/v1/video/upload-chunk` — el fragmento viaja por la API,
     que lo reenvia a S3. Asi la subida no depende de que el bucket sea accesible
     desde el navegador (era el origen del 404 al subir).
  3. `POST /api/v1/video/complete-multipart` — completa la subida.
- **UI**: drop zone, barra de progreso, logs en tiempo real, pantalla de exito, pantalla de error.
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

Configuracion completa de despliegue con Docker Compose. Stack de 6 servicios. `docker-compose.yml` en raiz para desarrollo (build local), `deploy/docker-compose.yml` para produccion (imagenes ghcr.io).

### Servicios (`docker-compose.yml`)

| Servicio | Imagen | Puerto | Proposito |
|---|---|---|---|
| `mariadb` | `mariadb:11` | — | Base de datos relacional |
| `minio` | `minio/minio:latest` | `9000` (API), `9001` (consola) | Almacenamiento S3-compatible |
| `minio-init` | `minio/mc:latest` | — | Crea el bucket automaticamente al iniciar |
| `backend` | `ghcr.io/dsaub/clonetube-backend:latest` | `8000` (interno) | API FastAPI |
| `frontend` | `ghcr.io/dsaub/clonetube-frontend:latest` | `8080` (interno) | SPA servida por nginx distroless |
| `nginx` | `nginx:alpine` | `80`, `443` | Reverse proxy HTTPS con SSL termination |

### Dockerfiles

Los Dockerfiles estan en `backend/Dockerfile` y `frontend/Dockerfile` (no en `deploy/`).

#### `backend/Dockerfile`

Build multi-stage con imagenes distroless (Google distroless):
1. **builder**: `python:3.14-slim` + `uv`. Copia `pyproject.toml` y `uv.lock`, ejecuta `uv sync --frozen --no-dev`.
2. **runtime**: `python:3.14-slim` con ffmpeg instalado via apt. Copia `.venv` del builder, el codigo y `entrypoint.sh`. Comando: `fastapi run main.py --port 8000`. Entrypoint: `/entrypoint.sh` que ejecuta `alembic upgrade head` antes de iniciar.

#### `frontend/Dockerfile`

Build multi-stage con imagenes distroless (Google distroless):
1. **build**: `node:24-slim` + `pnpm`. Instala dependencias, ejecuta `pnpm run build`. Descarga binario estatico de Caddy v2.9.1 y genera Caddyfile.
2. **runtime**: `gcr.io/distroless/static-debian12:nonroot` (distroless: sin shell, sin gestor de paquetes, usuario `nonroot`). Copia `dist/` a `/usr/share/nginx/html`, binario de Caddy y Caddyfile. Caddy escucha en puerto 8080, proxy reverso a `backend:8000` para `/api/`, SPA fallback a `index.html`.

### nginx.conf

Configuracion de nginx como reverse proxy HTTPS:
- Puerto 80 → redirect 301 a HTTPS.
- Puerto 443 → SSL termination con certificados en `/etc/nginx/certs/`.
- `client_max_body_size 10G` (para subida de videos grandes).
- Rutas: `/api/` → `backend:8000`, assets estaticos con cache 1y, resto → `frontend:8080`.

### generate-certs.sh

Script bash que genera certificados autofirmados para desarrollo local:
- `privkey.pem` (clave privada RSA 2048).
- `fullchain.pem` (certificado x509).
- Valido por 365 dias.
- Subject: `localhost` con SAN: `DNS:localhost`, `DNS:*.localhost`, `IP:127.0.0.1`.
- No sobrescribe si ya existen.

### entrypoint.sh

Script bash que ejecuta `alembic upgrade head` antes de iniciar la aplicacion FastAPI. Se usa como ENTRYPOINT en el Dockerfile de produccion.

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
| `JWT_SECRET` | `cambiar-por-clave-segura-de-al-menos-32-byts` | Secreto para firmar tokens JWT |
| `JWT_ALGORITHM` | `HS256` | Algoritmo de firma JWT |
| `JWT_EXPIRE_MINUTES` | `60` | Tiempo de expiracion del token en minutos |

---

## CI/CD (`.github/workflows/docker-build.yml`)

Workflow de GitHub Actions que construye y publica imagenes Docker en `ghcr.io`.

- **Trigger**: push a `latest`, `workflow_dispatch` (manual).
- **Estrategia**: matrix sobre `[backend, frontend]`.
- **Pasos**: checkout → login a ghcr.io → setup buildx → build & push.
- **Tags**: `latest` y `${{ github.sha }}`.
- **Cache**: GitHub Actions cache para acelerar builds.
- **Contexto**: `./<service>` con Dockerfile en `./<service>/Dockerfile`.

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
        G --> K[(MariaDB)]
        G --> L[routes/login.py]
        L --> M[auth/security.py]
        L --> N[(MariaDB - Users)]
    end

    subgraph Deploy["Deploy (Docker Compose)"]
        O[nginx :443] --> P[frontend :80]
        O --> Q[backend :8000]
        Q --> R[(mariadb)]
        Q --> S[(minio)]
    end

    Frontend -->|HTTP REST API| Backend
    Backend -->|JSON| Frontend
    CI[GitHub Actions] -->|push images| T[ghcr.io]
```

---

## Resumen de madurez del proyecto

| Capa | Estado | Progreso |
|---|---|---|
| Backend — Framework | FastAPI configurado con router | 15% |
| Backend — API Multipart Upload | 3 endpoints implementados | 60% |
| Backend — Modelos BD | SQLModel definido (User, Video, UserLikesVideo) | 30% |
| Backend — Autenticacion JWT | Register, login, change-password, me, password_version, forgot/reset | 40% |
| Backend — Migraciones | Alembic configurado con env.py + metadata SQLModel | 30% |
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

*Ultima actualizacion: 2026-07-09*
