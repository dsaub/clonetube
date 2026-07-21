import uuid

import jwt
from sqlmodel import Session, select

from auth.security import decode_access_token
from models import User


class AuthenticationError(Exception):
    """Credenciales ausentes, inválidas o revocadas."""


def authenticate_token(token: str, session: Session) -> User:
    """Valida un JWT y su versión contra la base de datos."""
    try:
        payload = decode_access_token(token)
        user_id = uuid.UUID(payload["sub"])
        token_version = payload["version"]
    except (jwt.PyJWTError, KeyError, TypeError, ValueError) as exc:
        raise AuthenticationError("Token inválido o expirado") from exc

    user = session.exec(select(User).where(User.id == user_id)).first()
    if user is None:
        raise AuthenticationError("Usuario no encontrado")
    if user.password_version != token_version:
        raise AuthenticationError("Token invalidado por cambio de contraseña")
    return user
