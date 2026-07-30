from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
import appsignal

appsignal.start()

from routes.login import router as auth_router
from routes.social import router as social_router
from routes.video import router as video_router
from routes.points import router as points_router
app = FastAPI(
    title="Clonetube API",
    description=(
        "API REST para la plataforma **Clonetube**, un clon de YouTube. "
        "Proporciona endpoints para la subida de videos mediante **multipart upload** a S3, "
        "así como autenticación mediante JWT."
    ),
    version="0.1.0",
)

# ── CORS (permitir peticiones desde el frontend) ──
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
# ── Routers ──
app.include_router(auth_router)
app.include_router(social_router)
app.include_router(video_router)
app.include_router(points_router)

FastAPIInstrumentor().instrument_app(app)

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
