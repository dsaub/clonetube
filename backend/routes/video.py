import uuid
from fastapi import APIRouter, Query, HTTPException
from clients import s3_client
from pymodels import (
    PartInfo,
    CompleteMultipartBody,
    StartMultipartResponse,
    SignChunkResponse,
    CompleteMultipartResponse,
)
import settings

router = APIRouter(prefix="/api/v1/video", tags=["S3 Multipart Upload"])

# Almacén en memoria del mapping: generated_key -> original_filename
# TODO: migrar a base de datos cuando esté disponible
_filename_registry: dict[str, str] = {}


def _generate_video_key(original_filename: str) -> str:
    """Genera una clave única para S3 a partir del nombre original."""
    ext = original_filename.rsplit(".", 1)[-1] if "." in original_filename else "mp4"
    video_id = uuid.uuid4().hex
    return f"videos/{video_id}.{ext}"


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
    try:
        video_key = _generate_video_key(original_filename)
        _filename_registry[video_key] = original_filename

        response = s3_client.create_multipart_upload(
            Bucket=settings.BUCKET_NAME,
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
        presigned_url = s3_client.generate_presigned_url(
            ClientMethod="upload_part",
            Params={
                "Bucket": settings.BUCKET_NAME,
                "Key": filename,
                "UploadId": upload_id,
                "PartNumber": chunk_number,
            },
            ExpiresIn=3600,
        )

        # Reemplazar endpoint interno (Docker) por la URL pública accesible
        # desde el navegador (nginx proxy /media/ -> MinIO).
        if settings.S3_PUBLIC_ENDPOINT_URL and settings.S3_ENDPOINT_URL:
            internal_base = f"{settings.S3_ENDPOINT_URL.rstrip('/')}/{settings.BUCKET_NAME}"
            public_base = settings.S3_PUBLIC_ENDPOINT_URL.rstrip('/')
            presigned_url = presigned_url.replace(internal_base, public_base)

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
async def complete_multipart(body: CompleteMultipartBody):
    """
    Completa un multipart upload en S3.

    - **filename**: key del objeto en S3 (generada por `/start-multipart`).
    - **uploadId**: ID del multipart upload.
    - **parts**: lista de fragmentos subidos con su `PartNumber` y `ETag`.
    """
    try:
        parts_list = [part.model_dump() for part in body.parts]

        response = s3_client.complete_multipart_upload(
            Bucket=settings.BUCKET_NAME,
            Key=body.filename,
            UploadId=body.uploadId,
            MultipartUpload={"Parts": parts_list},
        )
        original_filename = _filename_registry.pop(body.filename, None)

        location = response.get("Location") or ""
        if settings.S3_PUBLIC_ENDPOINT_URL and settings.S3_ENDPOINT_URL:
            internal_base = f"{settings.S3_ENDPOINT_URL.rstrip('/')}/{settings.BUCKET_NAME}"
            public_base = settings.S3_PUBLIC_ENDPOINT_URL.rstrip('/')
            location = location.replace(internal_base, public_base)

        return CompleteMultipartResponse(
            status="success",
            location=location,
            key=body.filename,
            original_filename=original_filename or "desconocido",
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))