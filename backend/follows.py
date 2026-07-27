"""Consultas de la relación de seguimiento entre usuarios."""

import uuid

from sqlmodel import Session, func, select

from models import User, UserFollowsUser


def followed_ids(session: Session, follower_id: uuid.UUID) -> set[uuid.UUID]:
    """Identificadores de los usuarios a los que sigue `follower_id`."""
    return set(session.exec(
        select(UserFollowsUser.followed_id).where(UserFollowsUser.follower_id == follower_id)
    ).all())


def followed_users(session: Session, follower_id: uuid.UUID) -> list[User]:
    """Usuarios seguidos, ordenados por nombre para una salida estable."""
    return list(session.exec(
        select(User)
        .join(UserFollowsUser, UserFollowsUser.followed_id == User.id)
        .where(UserFollowsUser.follower_id == follower_id)
        .order_by(User.username)
    ).all())


def is_following(session: Session, follower_id: uuid.UUID, followed_id: uuid.UUID) -> bool:
    return session.get(UserFollowsUser, (follower_id, followed_id)) is not None


def count_followers(session: Session, followed_id: uuid.UUID) -> int:
    return session.exec(
        select(func.count()).select_from(UserFollowsUser)
        .where(UserFollowsUser.followed_id == followed_id)
    ).one()


def count_following(session: Session, follower_id: uuid.UUID) -> int:
    return session.exec(
        select(func.count()).select_from(UserFollowsUser)
        .where(UserFollowsUser.follower_id == follower_id)
    ).one()


def follow(session: Session, follower_id: uuid.UUID, followed_id: uuid.UUID) -> bool:
    """Crea la relación si no existía. Devuelve True si se creó ahora."""
    if is_following(session, follower_id, followed_id):
        return False
    session.add(UserFollowsUser(follower_id=follower_id, followed_id=followed_id))
    session.commit()
    return True


def unfollow(session: Session, follower_id: uuid.UUID, followed_id: uuid.UUID) -> bool:
    """Elimina la relación si existía. Devuelve True si se eliminó ahora."""
    link = session.get(UserFollowsUser, (follower_id, followed_id))
    if link is None:
        return False
    session.delete(link)
    session.commit()
    return True
