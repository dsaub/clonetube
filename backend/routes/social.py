"""Endpoints de la red social: canales, seguir y dejar de seguir usuarios."""

from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlmodel import Session, select

import channels
import follows
from auth.dependencies import get_current_user, get_optional_user
from database import get_session
from feed import ensure_utc
from models import User
from pymodels import (
    ChannelResponse,
    ChannelVideoItem,
    ChannelVideosResponse,
    FollowingListResponse,
    FollowStateResponse,
    PublicUser,
)

router = APIRouter(prefix="/api/v1/users", tags=["Seguidores"])

USER_NOT_FOUND = "Usuario no encontrado"


def _find_user(session: Session, username: str) -> User:
    user = session.exec(select(User).where(User.username == username)).first()
    if user is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=USER_NOT_FOUND)
    return user


def _state(session: Session, target: User, viewer: User | None) -> FollowStateResponse:
    return FollowStateResponse(
        username=target.username,
        following=viewer is not None and follows.is_following(session, viewer.id, target.id),
        followers=follows.count_followers(session, target.id),
    )


@router.get(
    "/me/following",
    response_model=FollowingListResponse,
    summary="Listar a quién sigo",
    description="Devuelve los usuarios seguidos por el usuario autenticado.",
)
def list_following(
    current_user: Annotated[User, Depends(get_current_user)],
    session: Annotated[Session, Depends(get_session)],
) -> FollowingListResponse:
    return FollowingListResponse(users=[
        PublicUser(id=user.id, username=user.username, full_name=user.full_name)
        for user in follows.followed_users(session, current_user.id)
    ])


@router.get(
    "/{username}/channel",
    response_model=ChannelResponse,
    summary="Datos del canal",
    description=(
        "Devuelve la ficha pública del canal `username`: nombre, seguidores, "
        "canales seguidos, número de vídeos y si el visitante ya lo sigue."
    ),
    responses={404: {"description": USER_NOT_FOUND}},
)
def channel_profile(
    username: str,
    session: Annotated[Session, Depends(get_session)],
    current_user: Annotated[User | None, Depends(get_optional_user)],
) -> ChannelResponse:
    channel = _find_user(session, username)
    return ChannelResponse(
        id=channel.id,
        username=channel.username,
        full_name=channel.full_name,
        following=current_user is not None
        and follows.is_following(session, current_user.id, channel.id),
        followers=follows.count_followers(session, channel.id),
        following_count=follows.count_following(session, channel.id),
        video_count=channels.count_videos(session, channel.id, current_user),
    )


@router.get(
    "/{username}/videos",
    response_model=ChannelVideosResponse,
    summary="Vídeos del canal",
    description=(
        "Vídeos del canal `username` paginados de "
        f"{channels.DEFAULT_PAGE_SIZE} en {channels.DEFAULT_PAGE_SIZE}, del más "
        "reciente al más antiguo. Solo el propietario ve sus vídeos ocultos y "
        "privados; el resto de visitantes ve únicamente los públicos."
    ),
    responses={404: {"description": USER_NOT_FOUND}},
)
def channel_videos(
    username: str,
    session: Annotated[Session, Depends(get_session)],
    current_user: Annotated[User | None, Depends(get_optional_user)],
    page: Annotated[int, Query(ge=1, description="Página solicitada (empieza en 1).")] = 1,
    page_size: Annotated[int, Query(
        ge=1,
        le=channels.MAX_PAGE_SIZE,
        description="Vídeos por página.",
    )] = channels.DEFAULT_PAGE_SIZE,
) -> ChannelVideosResponse:
    channel = _find_user(session, username)
    total = channels.count_videos(session, channel.id, current_user)
    videos = channels.page_of_videos(session, channel.id, current_user, page, page_size)
    likes = channels.likes_by_video(session, [video.id for video in videos])

    return ChannelVideosResponse(
        videos=[
            ChannelVideoItem(
                id=video.id,
                key=video.filename,
                title=video.video_name,
                description=video.video_desc,
                visibility=video.visibility,
                created_at=ensure_utc(video.created_at).isoformat(),
                likes=likes.get(video.id, 0),
            )
            for video in videos
        ],
        page=page,
        page_size=page_size,
        total=total,
        pages=channels.total_pages(total, page_size),
    )


@router.get(
    "/{username}/follow",
    response_model=FollowStateResponse,
    summary="Estado de seguimiento",
    description=(
        "Indica si el usuario autenticado sigue a `username` y cuántos "
        "seguidores tiene. Sin token devuelve `following: false`."
    ),
    responses={404: {"description": USER_NOT_FOUND}},
)
def follow_state(
    username: str,
    session: Annotated[Session, Depends(get_session)],
    current_user: Annotated[User | None, Depends(get_optional_user)],
) -> FollowStateResponse:
    return _state(session, _find_user(session, username), current_user)


@router.post(
    "/{username}/follow",
    response_model=FollowStateResponse,
    summary="Seguir a un usuario",
    description="Empieza a seguir a `username`. Es idempotente: seguir dos veces no falla.",
    responses={
        400: {"description": "No puedes seguirte a ti mismo"},
        404: {"description": USER_NOT_FOUND},
    },
)
def follow_user(
    username: str,
    current_user: Annotated[User, Depends(get_current_user)],
    session: Annotated[Session, Depends(get_session)],
) -> FollowStateResponse:
    target = _find_user(session, username)
    if target.id == current_user.id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="No puedes seguirte a ti mismo",
        )
    follows.follow(session, current_user.id, target.id)
    return _state(session, target, current_user)


@router.delete(
    "/{username}/follow",
    response_model=FollowStateResponse,
    summary="Dejar de seguir a un usuario",
    description="Deja de seguir a `username`. Es idempotente.",
    responses={404: {"description": USER_NOT_FOUND}},
)
def unfollow_user(
    username: str,
    current_user: Annotated[User, Depends(get_current_user)],
    session: Annotated[Session, Depends(get_session)],
) -> FollowStateResponse:
    target = _find_user(session, username)
    follows.unfollow(session, current_user.id, target.id)
    return _state(session, target, current_user)
