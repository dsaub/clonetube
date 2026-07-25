import logging
import json
import os
import tempfile
import uuid
from datetime import UTC, datetime
from fastapi import APIRouter, BackgroundTasks, Depends, Query, HTTPException
from sqlmodel import Session, func, select
import follows
from auth.dependencies import get_current_user, get_optional_user
from database import get_session
from feed import FeedCandidate, ScoredVideo, ensure_utc, rank_candidates
from models import MultipartUpload, User, UserLikesVideo, Video
from clients import s3_client, s3_client_public
from pymodels import (
    PartInfo,
    CompleteMultipartBody,
    StartMultipartResponse,
    SignChunkResponse,
    CompleteMultipartResponse,
    FeedResponse,
    FeedVideoItem,
    VideoListItem,
    VideoListResponse,
    StudioVideoItem,
    StudioVideoResponse,
    VideoCatalogItem,
    VideoCatalogResponse,
    VideoDetail,
    VideoUpdate,
    StreamUrlResponse,
)
from settings import settings
import constants
from typing import Annotated

_BUCKET = settings.AWS_BUCKET_NAME
from video_processor import ALLOWED_VIDEO_EXTENSIONS, is_valid_video_extension, transcode_to_mp4

logger = logging.getLogger("routes.video")

router = APIRouter(prefix="/api/v1/video", tags=["S3 Multipart Upload"])

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
    current_user: Annotated[User, Depends(get_current_user)],
    session: Annotated[Session, Depends(get_session)],
    original_filename: str = Query(
        ...,
        description="Nombre original del archivo (solo para referencia). "
        "El video se almacenará internamente con un UUID.",
        examples=["mi-video.mp4"],
    )
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
        response = s3_client.create_multipart_upload(
            Bucket=settings.AWS_BUCKET_NAME,
            Key=video_key,
        )
        upload = MultipartUpload(
            upload_id=response["UploadId"], key=response["Key"],
            original_filename=original_filename, owner_id=current_user.id,
        )
        session.add(upload)
        session.commit()
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
        404: {"description": constants.LOAD_NOT_FOUND}
    },
)
async def sign_chunk(
    current_user: Annotated[User, Depends(get_current_user)],
    session: Annotated[Session, Depends(get_session)],
    filename: str = Query(
        ...,
        description="Key del objeto en S3 (devuelta por `/start-multipart`).",
        examples=["videos/a1b2c3d4e5f6....mp4"],
    ),
    upload_id: str = Query(
        ...,
        description="ID del multipart upload obtenido en `/start-multipart`.",
        examples=["example-upload-id-12345"],
    ),
    chunk_number: int = Query(
        ...,
        description="Número del fragmento a subir (empezando en 1).",
        ge=1,
        examples=[1],
    )
):
    """
    Obtiene una URL prefirmada para subir un fragmento.

    - **filename**: key del objeto en S3.
    - **upload_id**: ID del multipart upload.
    - **chunk_number**: número de parte (1‑based).
    """
    upload = session.exec(select(MultipartUpload).where(
        MultipartUpload.key == filename,
        MultipartUpload.upload_id == upload_id,
    )).first()
    if upload is None or upload.owner_id != current_user.id or upload.status != "pending":
        raise HTTPException(status_code=404, detail=constants.LOAD_NOT_FOUND)
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
        404: {"description": constants.LOAD_NOT_FOUND}
    },
)
async def complete_multipart(
    current_user: Annotated[User, Depends(get_current_user)],
    session: Annotated[Session, Depends(get_session)],
    body: CompleteMultipartBody,
    background_tasks: BackgroundTasks
):
    """
    Completa un multipart upload en S3 y lanza la transcodificación en segundo plano.

    - **filename**: key del objeto en S3 (generada por `/start-multipart`).
    - **uploadId**: ID del multipart upload.
    - **parts**: lista de fragmentos subidos con su `PartNumber` y `ETag`.
    """
    upload = session.exec(select(MultipartUpload).where(
        MultipartUpload.key == body.filename,
        MultipartUpload.upload_id == body.uploadId,
    )).first()
    if upload is None or upload.owner_id != current_user.id or upload.status != "pending":
        raise HTTPException(status_code=404, detail=constants.LOAD_NOT_FOUND)
    try:
        parts_list = [part.model_dump() for part in body.parts]

        response = s3_client.complete_multipart_upload(
            Bucket=_BUCKET,
            Key=body.filename,
            UploadId=body.uploadId,
            MultipartUpload={"Parts": parts_list},
        )
        original_filename = upload.original_filename
        upload.status = "completed"
        video = Video(
            filename=body.filename,
            author=current_user.id,
            video_name=original_filename,
            video_desc="",
            is_published=True,
            visibility="public",
        )
        session.add(upload)
        session.add(video)
        session.commit()

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


@router.delete("/cancel-multipart", summary="Cancelar carga multipart", responses={404: {"description": constants.LOAD_NOT_FOUND}})
async def cancel_multipart(
    current_user: Annotated[User, Depends(get_current_user)],
    session: Annotated[Session, Depends(get_session)],
    filename: str,
    upload_id: str
) -> dict[str, str]:
    upload = session.exec(select(MultipartUpload).where(
        MultipartUpload.key == filename,
        MultipartUpload.upload_id == upload_id,
    )).first()
    if upload is None or upload.owner_id != current_user.id or upload.status != "pending":
        raise HTTPException(status_code=404, detail=constants.LOAD_NOT_FOUND)
    s3_client.abort_multipart_upload(
        Bucket=_BUCKET, Key=filename, UploadId=upload_id,
    )
    upload.status = "cancelled"
    session.add(upload)
    session.commit()
    return {"status": "cancelled"}


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
async def list_videos(session: Annotated[Session, Depends(get_session)]):
    """Lista únicamente los vídeos públicos."""
    try:
        public_keys = set(session.exec(
            select(Video.filename).where(Video.visibility == "public")
        ).all())
        paginator = s3_client.get_paginator("list_objects_v2")
        pages = paginator.paginate(Bucket=_BUCKET, Prefix="videos/")

        videos: list[VideoListItem] = []
        for page in pages:
            for obj in page.get("Contents", []):
                key = obj["Key"]
                if key.endswith("/") or key not in public_keys:
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
    "/catalog",
    response_model=VideoCatalogResponse,
    summary="Listar metadatos públicos de vídeos",
    description="Devuelve el título, descripción y autor de cada vídeo público.",
)
async def video_catalog(session: Annotated[Session, Depends(get_session)]) -> VideoCatalogResponse:
    rows = session.exec(
        select(Video, User)
        .join(User, User.id == Video.author)
        .where(Video.visibility == "public")
    ).all()
    return VideoCatalogResponse(videos=[
        VideoCatalogItem(
            id=video.id, filename=video.filename, title=video.video_name,
            description=video.video_desc, author_id=author.id,
            author_username=author.username, author_name=author.full_name,
        )
        for video, author in rows
    ])


@router.get(
    "/feed",
    response_model=FeedResponse,
    summary="Feed personalizado",
    description=(
        "Devuelve los vídeos públicos ordenados por el algoritmo de recomendación: "
        "los autores que sigue el usuario autenticado suben posiciones, y a igualdad "
        "de seguimiento mandan la novedad y los likes. Sin token devuelve el mismo "
        "listado sin personalizar."
    ),
)
async def video_feed(
    session: Annotated[Session, Depends(get_session)],
    current_user: Annotated[User | None, Depends(get_optional_user)],
    limit: int = Query(default=50, ge=1, le=200, description="Número máximo de vídeos."),
    only_following: bool = Query(
        default=False,
        description="Si es `true`, devuelve solo vídeos de los autores seguidos.",
    ),
) -> FeedResponse:
    following = follows.followed_ids(session, current_user.id) if current_user else set()

    rows = session.exec(
        select(Video, User)
        .join(User, User.id == Video.author)
        .where(Video.visibility == "public")
    ).all()
    if only_following:
        rows = [row for row in rows if row[0].author in following]

    likes = _likes_by_video(session)
    videos = {video.id: (video, author) for video, author in rows}
    candidates = [
        FeedCandidate(
            video_id=video.id,
            author_id=video.author,
            created_at=video.created_at,
            likes=likes.get(video.id, 0),
        )
        for video, _ in rows
    ]

    ranked = rank_candidates(candidates, following, datetime.now(UTC), limit=limit)
    return FeedResponse(
        videos=[_feed_item(*videos[item.candidate.video_id], item) for item in ranked],
        following_count=len(following),
        personalized=bool(following),
    )


def _likes_by_video(session: Session) -> dict[uuid.UUID, int]:
    rows = session.exec(
        select(UserLikesVideo.video_id, func.count())
        .group_by(UserLikesVideo.video_id)
    ).all()
    return {video_id: count for video_id, count in rows}


def _feed_item(video: Video, author: User, scored: ScoredVideo) -> FeedVideoItem:
    return FeedVideoItem(
        id=video.id,
        filename=video.filename,
        title=video.video_name,
        description=video.video_desc,
        author_id=author.id,
        author_username=author.username,
        author_name=author.full_name,
        created_at=ensure_utc(video.created_at).isoformat(),
        likes=scored.candidate.likes,
        score=round(scored.score, 6),
        from_followed_author=scored.from_followed_author,
    )


def _allowed_usernames(video: Video) -> set[str]:
    return set(json.loads(video.allowed_users))


def _can_view(video: Video, user: User | None) -> bool:
    return (
        video.visibility in {"public", "unlisted"}
        or user is not None
        and (video.author == user.id or user.username in _allowed_usernames(video))
    )


def _find_accessible_video(session: Session, key: str, user: User | None) -> Video:
    video = session.exec(select(Video).where(Video.filename == key)).first()
    if video is None or not _can_view(video, user):
        raise HTTPException(status_code=404, detail="Vídeo no encontrado")
    return video


@router.get("/detail", response_model=VideoDetail, summary="Obtener los datos de un vídeo")
async def video_detail(
    session: Annotated[Session, Depends(get_session)],
    current_user: Annotated[User | None, Depends(get_optional_user)],
    key: str,
) -> VideoDetail:
    video = _find_accessible_video(session, key, current_user)
    author = session.get(User, video.author)
    if author is None:
        raise HTTPException(status_code=404, detail="Canal no encontrado")
    return VideoDetail(
        id=video.id, key=video.filename, title=video.video_name,
        description=video.video_desc, visibility=video.visibility,
        author_id=author.id, author_username=author.username, author_name=author.full_name,
    )


@router.get("/studio", response_model=StudioVideoResponse, summary="Listar mis vídeos")
async def studio_videos(
    current_user: Annotated[User, Depends(get_current_user)],
    session: Annotated[Session, Depends(get_session)],
) -> StudioVideoResponse:
    videos = session.exec(select(Video).where(Video.author == current_user.id)).all()
    return StudioVideoResponse(videos=[_studio_item(video) for video in videos])


def _studio_item(video: Video) -> StudioVideoItem:
    try:
        head = s3_client.head_object(Bucket=_BUCKET, Key=video.filename)
    except Exception:
        logger.warning("No se pudieron leer los metadatos S3 de %s", video.filename)
        head = {}
    return StudioVideoItem(
        id=video.id, key=video.filename, size=head.get("ContentLength", 0),
        last_modified=head.get("LastModified", datetime.now(UTC)).isoformat(),
        original_filename=head.get("Metadata", {}).get("original-filename", video.video_name),
        title=video.video_name, description=video.video_desc,
        visibility=video.visibility, allowed_users=sorted(_allowed_usernames(video)),
    )


@router.patch("/{video_id}", response_model=StudioVideoItem, summary="Editar un vídeo")
async def update_video(
    video_id: uuid.UUID,
    body: VideoUpdate,
    current_user: Annotated[User, Depends(get_current_user)],
    session: Annotated[Session, Depends(get_session)],
) -> StudioVideoItem:
    video = session.get(Video, video_id)
    if video is None or video.author != current_user.id:
        raise HTTPException(status_code=404, detail="Vídeo no encontrado")
    allowed_users = sorted({username.strip() for username in body.allowed_users if username.strip()})
    if body.visibility == "private" and allowed_users:
        found = set(session.exec(select(User.username).where(User.username.in_(allowed_users))).all())
        missing = sorted(set(allowed_users) - found)
        if missing:
            raise HTTPException(status_code=422, detail=f"Usuarios inexistentes: {', '.join(missing)}")
    title = body.title.strip()
    if not title:
        raise HTTPException(status_code=422, detail="El título no puede estar vacío")
    video.video_name = title
    video.video_desc = body.description
    video.visibility = body.visibility
    video.allowed_users = json.dumps(allowed_users) if body.visibility == "private" else "[]"
    video.is_published = body.visibility == "public"
    session.add(video)
    session.commit()
    session.refresh(video)
    return _studio_item(video)


@router.delete("/{video_id}", status_code=204, summary="Eliminar un vídeo")
async def delete_video(
    video_id: uuid.UUID,
    current_user: Annotated[User, Depends(get_current_user)],
    session: Annotated[Session, Depends(get_session)],
) -> None:
    video = session.get(Video, video_id)
    if video is None or video.author != current_user.id:
        raise HTTPException(status_code=404, detail="Vídeo no encontrado")
    s3_client.delete_object(Bucket=_BUCKET, Key=video.filename)
    session.delete(video)
    session.commit()


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
    session: Annotated[Session, Depends(get_session)],
    current_user: Annotated[User | None, Depends(get_optional_user)],
    key: str = Query(
        ...,
        description="Key del video en S3 (ej: videos/abc123.mp4).",
        examples=["videos/a1b2c3d4e5f6....mp4"],
    ),
):
    """Devuelve una URL prefirmada válida por 24h para streaming del video."""
    _find_accessible_video(session, key, current_user)
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
