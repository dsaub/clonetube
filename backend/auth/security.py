import hashlib
import secrets
import uuid
from datetime import UTC, datetime, timedelta

import jwt
from passlib.context import CryptContext

from settings import settings

_pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")
_RESET_TOKEN_EXPIRE_MINUTES = 15


def hash_password(password: str) -> tuple[str, int]:
    """Hashea la contraseña y devuelve (hash, password_version).

    password_version siempre comienza en 0 para contraseñas nuevas.
    Se incrementa manualmente al cambiar la contraseña.
    """
    return _pwd_context.hash(password), 0


def verify_password(plain_password: str, hashed_password: str) -> bool:
    """Verifica una contraseña contra su hash."""
    return _pwd_context.verify(plain_password, hashed_password)


def create_access_token(user_id: uuid.UUID, password_version: int) -> str:
    """Crea un JWT con el user_id y password_version en el payload."""
    expire = datetime.now(UTC) + timedelta(minutes=settings.JWT_EXPIRE_MINUTES)
    payload = {
        "sub": str(user_id),
        "version": password_version,
        "exp": expire,
    }
    return jwt.encode(payload, settings.JWT_SECRET, algorithm=settings.JWT_ALGORITHM)


def generar_codigo(n=16):
    return ''.join(secrets.choice('0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ') for _ in range(n))

def decode_access_token(token: str) -> dict:
    """Decodifica y valida un JWT. Devuelve el payload o lanza jwt.PyJWTError."""
    return jwt.decode(token, settings.JWT_SECRET, algorithms=[settings.JWT_ALGORITHM])


def create_reset_token() -> tuple[str, str, datetime]:
    """Genera un token de restablecimiento de contraseña.

    Returns:
        Tupla con (token_plano, token_hash, datetime_expiracion).
        El token plano se devuelve al cliente; el hash se guarda en BD.
        La expiración es de 15 minutos.
    """
    token = secrets.token_urlsafe(32)
    token_hash = hashlib.sha256(token.encode("utf-8")).hexdigest()
    expires_at = datetime.now(UTC) + timedelta(minutes=_RESET_TOKEN_EXPIRE_MINUTES)
    return token, token_hash, expires_at


def hash_reset_token(token: str) -> str:
    """Hashea un token de restablecimiento con SHA-256 para buscarlo en BD."""
    return hashlib.sha256(token.encode("utf-8")).hexdigest()
