from fastapi import FastAPI
from routes.video import router as video_router

app = FastAPI(
    title="Clonetube API",
    description=(
        "API REST para la plataforma **Clonetube**, un clon de YouTube. "
        "Proporciona endpoints para la subida de videos mediante **multipart upload** a S3."
    ),
    version="0.1.0",
)
app.include_router(video_router)

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)



