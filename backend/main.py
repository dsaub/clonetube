from collections.abc import AsyncGenerator
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from database import create_db_and_tables
from graphql_api import GraphQLRateLimitMiddleware, graphql_router
from routes.login import router as auth_router
from routes.video import router as video_router


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
    # Startup: crear tablas si no existen (útil en desarrollo)
    create_db_and_tables()
    yield
    # Shutdown: el engine se limpia automáticamente al salir


app = FastAPI(
    title="Clonetube API",
    description=(
        "API REST para la plataforma **Clonetube**, un clon de YouTube. "
        "Proporciona endpoints para la subida de videos mediante **multipart upload** a S3, "
        "así como autenticación mediante JWT."
    ),
    version="0.1.0",
    lifespan=lifespan,
)

# ── CORS (permitir peticiones desde el frontend) ──    
app.add_middleware(GraphQLRateLimitMiddleware)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
# ── Routers ──
app.include_router(auth_router)
app.include_router(video_router)
app.include_router(graphql_router, prefix="/graphql")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
