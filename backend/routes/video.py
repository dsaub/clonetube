import logging
import os
import tempfile
import uuid
from fastapi import APIRouter, BackgroundTasks, Query, HTTPException
from clients import s3_client, s3_client_public
from pymodels import (
    PartInfo,
    CompleteMultipartBody,
    StartMultipartResponse,
    SignChunkResponse,
    CompleteMultipartResponse,
    VideoListItem,
    VideoListResponse,
    StreamUrlResponse,
)
from settings import settings

_BUCKET = settings.AWS_BUCKET_NAME
from video_processor import ALLOWED_VIDEO_EXTENSIONS, is_valid_video_extension, transcode_to_mp4

logger = logging.getLogger("routes.video")

router = APIRouter(prefix="/api/v1/video", tags=["S3 Multipart Upload"])

# Almacén en memoria del mapping: generated_key -> original_filename
# TODO: migrar a base de datos cuando esté disponible
_filename_registry: dict[str, str] = {}


def _generate_video_key(original_filename: str) -> str:
    """Genera una clave única para S3 a partir del nombre original."""
    video_id = uuid.uuid4().hex
    return f"videos/{video_id}.mp4"


@router.post(
    "/start-multipart",
    summary="Iniciar carga multipart",
    description=(
        "Crea un **multipart upload** en S3. El archivo se guarda con un "
        "**identificador único** (UUID) en lugar del nombre original, "
        "evitando colisiones y sobrescrituras.\n\n"
        "Devuelve el `uploadId` necesario para las llamadas posteriores "
        "de firma de fragmentos y finalización."
    ),
    response_model=StartMultipartResponse,
    responses={
        500: {"description": "Error al iniciar el multipart upload en S3"},
    },
)
async def start_multipart(
    original_filename: str = Query(
        ...,
        description="Nombre original del archivo (solo para referencia). "
        "El video se almacenará internamente con un UUID.",
        example="mi-video.mp4",
    ),
):
    """
    Inicia un multipart upload en S3 con clave aleatoria.

    - **original_filename**: nombre real del archivo (se guarda solo como referencia).
    - El **key** real en S3 se genera automáticamente con un UUID.
    """
    if not is_valid_video_extension(original_filename):
        raise HTTPException(
            status_code=400,
            detail=f"Formato de archivo no permitido: {original_filename}. "
                   f"Formatos aceptados: {', '.join(sorted(ALLOWED_VIDEO_EXTENSIONS))}",
        )

    try:
        video_key = _generate_video_key(original_filename)
        _filename_registry[video_key] = original_filename

        response = s3_client.create_multipart_upload(
            Bucket=settings.AWS_BUCKET_NAME,
            Key=video_key,
        )
        return StartMultipartResponse(
            uploadId=response["UploadId"],
            key=response["Key"],
            original_filename=original_filename,
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get(
    "/sign-chunk",
    summary="Firmar fragmento",
    description=(
        "Genera una **URL prefirmada** (presigned URL) para subir un fragmento "
        "específico del video al multipart upload. La URL expira en **1 hora**."
    ),
    response_model=SignChunkResponse,
    responses={
        500: {"description": "Error al generar la URL prefirmada"},
    },
)
async def sign_chunk(
    filename: str = Query(
        ...,
        description="Key del objeto en S3 (devuelta por `/start-multipart`).",
        example="videos/a1b2c3d4e5f6....mp4",
    ),
    upload_id: str = Query(
        ...,
        description="ID del multipart upload obtenido en `/start-multipart`.",
        example="example-upload-id-12345",
    ),
    chunk_number: int = Query(
        ...,
        description="Número del fragmento a subir (empezando en 1).",
        ge=1,
        example=1,
    ),
):
    """
    Obtiene una URL prefirmada para subir un fragmento.

    - **filename**: key del objeto en S3.
    - **upload_id**: ID del multipart upload.
    - **chunk_number**: número de parte (1‑based).
    """
    try:
        presigned_url = s3_client_public.generate_presigned_url(
            ClientMethod="upload_part",
            Params={
                "Bucket": _BUCKET,
                "Key": filename,
                "UploadId": upload_id,
                "PartNumber": chunk_number,
            },
            ExpiresIn=3600,
        )

        return SignChunkResponse(url=presigned_url)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post(
    "/complete-multipart",
    summary="Completar carga multipart",
    description=(
        "Ensambla todos los fragmentos subidos y **completa** el multipart upload en S3. "
        "Debes enviar la lista de partes con sus respectivos `ETag` y `PartNumber`, "
        "obtenidos tras subir cada fragmento con la URL prefirmada."
    ),
    response_model=CompleteMultipartResponse,
    responses={
        500: {"description": "Error al completar el multipart upload en S3"},
    },
)
async def complete_multipart(body: CompleteMultipartBody, background_tasks: BackgroundTasks):
    """
    Completa un multipart upload en S3 y lanza la transcodificación en segundo plano.

    - **filename**: key del objeto en S3 (generada por `/start-multipart`).
    - **uploadId**: ID del multipart upload.
    - **parts**: lista de fragmentos subidos con su `PartNumber` y `ETag`.
    """
    try:
        parts_list = [part.model_dump() for part in body.parts]

        response = s3_client.complete_multipart_upload(
            Bucket=_BUCKET,
            Key=body.filename,
            UploadId=body.uploadId,
            MultipartUpload={"Parts": parts_list},
        )
        original_filename = _filename_registry.pop(body.filename, None)

        location = response.get("Location") or ""
        location = _rewrite_public_url(location)

        background_tasks.add_task(_transcode_and_replace, body.filename, original_filename)

        return CompleteMultipartResponse(
            status="success",
            location=location,
            key=body.filename,
            original_filename=original_filename or "desconocido",
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


async def _transcode_and_replace(key: str, original_filename: str | None = None) -> None:
    """Descarga el video de S3, lo transcodifica a MP4/H.264/AAC y lo reemplaza."""
    tmp_input = None
    tmp_output = None
    try:
        tmp_fd, tmp_input = tempfile.mkstemp(suffix=".mp4")
        os.close(tmp_fd)
        logger.info("Downloading %s from S3 for transcoding", key)
        s3_client.download_file(settings.AWS_BUCKET_NAME, key, tmp_input)

        tmp_fd, tmp_output = tempfile.mkstemp(suffix=".mp4")
        os.close(tmp_fd)
        await transcode_to_mp4(tmp_input, tmp_output)

        logger.info("Uploading transcoded video to S3: %s", key)
        extra_args = {}
        if original_filename:
            extra_args["Metadata"] = {"original-filename": original_filename}
        s3_client.upload_file(tmp_output, _BUCKET, key, ExtraArgs=extra_args)

        logger.info("Transcoding complete for %s", key)
    except Exception:
        logger.exception("Transcoding failed for %s", key)
    finally:
        for path in (tmp_input, tmp_output):
            if path:
                try:
                    os.unlink(path)
                except OSError:
                    pass


def _rewrite_public_url(url: str) -> str:
    """Reemplaza el endpoint interno de S3 por la URL pública (solo el host)."""
    if settings.S3_PUBLIC_ENDPOINT_URL and settings.S3_ENDPOINT_URL:
        internal_base = settings.S3_ENDPOINT_URL.rstrip('/')
        public_base = settings.S3_PUBLIC_ENDPOINT_URL.rstrip('/')
        return url.replace(internal_base, public_base, 1)
    return url


@router.get(
    "/list",
    summary="Listar videos",
    description="Devuelve la lista de videos almacenados en S3 con sus metadatos.",
    response_model=VideoListResponse,
    responses={
        500: {"description": "Error al listar los videos en S3"},
    },
)
async def list_videos():
    """Lista todos los objetos en el bucket S3 bajo el prefijo `videos/`."""
    try:
        paginator = s3_client.get_paginator("list_objects_v2")
        pages = paginator.paginate(Bucket=_BUCKET, Prefix="videos/")

        videos: list[VideoListItem] = []
        for page in pages:
            for obj in page.get("Contents", []):
                key = obj["Key"]
                if key.endswith("/"):
                    continue

                head = s3_client.head_object(Bucket=_BUCKET, Key=key)
                original_filename = head.get("Metadata", {}).get(
                    "original-filename", key.rsplit("/", 1)[-1]
                )

                videos.append(VideoListItem(
                    key=key,
                    size=obj["Size"],
                    last_modified=obj["LastModified"].isoformat(),
                    original_filename=original_filename,
                ))

        return VideoListResponse(videos=videos)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get(
    "/stream-url",
    summary="Obtener URL de streaming",
    description="Genera una URL prefirmada para reproducir el video directamente desde S3.",
    response_model=StreamUrlResponse,
    responses={
        500: {"description": "Error al generar la URL de streaming"},
    },
)
async def stream_url(
    key: str = Query(
        ...,
        description="Key del video en S3 (ej: videos/abc123.mp4).",
        example="videos/a1b2c3d4e5f6....mp4",
    ),
):
    """Devuelve una URL prefirmada válida por 24h para streaming del video."""
    try:
        url = s3_client_public.generate_presigned_url(
            ClientMethod="get_object",
            Params={
                "Bucket": _BUCKET,
                "Key": key,
            },
            ExpiresIn=86400,
        )
        return StreamUrlResponse(url=url, key=key)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))