"""Consultas del canal de un usuario: vídeos paginados y recuentos."""

import uuid
from math import ceil

from sqlmodel import Session, func, select

from models import User, UserLikesVideo, Video

# El canal pagina de 20 en 20; el tope evita que un cliente pida el canal entero.
DEFAULT_PAGE_SIZE = 20
MAX_PAGE_SIZE = 50


def _conditions(channel_id: uuid.UUID, viewer: User | None) -> list:
    """El dueño ve todo su canal; cualquier otro visitante solo lo público."""
    conditions = [Video.author == channel_id]
    if viewer is None or viewer.id != channel_id:
        conditions.append(Video.visibility == "public")
    return conditions


def count_videos(session: Session, channel_id: uuid.UUID, viewer: User | None) -> int:
    return session.exec(
        select(func.count()).select_from(Video).where(*_conditions(channel_id, viewer))
    ).one()


def page_of_videos(
    session: Session,
    channel_id: uuid.UUID,
    viewer: User | None,
    page: int,
    page_size: int,
) -> list[Video]:
    """Página de vídeos del canal, del más reciente al más antiguo.

    El desempate por `id` mantiene el orden estable cuando dos vídeos comparten
    marca de tiempo, para que un mismo vídeo no aparezca en dos páginas.
    """
    return list(session.exec(
        select(Video)
        .where(*_conditions(channel_id, viewer))
        .order_by(Video.created_at.desc(), Video.id)
        .offset((page - 1) * page_size)
        .limit(page_size)
    ).all())


def likes_by_video(session: Session, video_ids: list[uuid.UUID]) -> dict[uuid.UUID, int]:
    """Likes de los vídeos de la página, en una sola consulta."""
    if not video_ids:
        return {}

    rows = session.exec(
        select(UserLikesVideo.video_id, func.count())
        .where(UserLikesVideo.video_id.in_(video_ids))
        .group_by(UserLikesVideo.video_id)
    ).all()
    return {video_id: count for video_id, count in rows}


def total_pages(total: int, page_size: int) -> int:
    """Número de páginas; un canal vacío sigue teniendo una página."""
    return max(1, ceil(total / page_size))
