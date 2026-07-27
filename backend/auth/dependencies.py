from fastapi import Depends, HTTPException, Query, status
from fastapi.security import OAuth2PasswordBearer
from sqlmodel import Session

from auth.service import AuthenticationError, authenticate_token
from database import get_session
from models import User

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/api/v1/auth/login")
optional_oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/api/v1/auth/login", auto_error=False)


def get_current_user(
    token: str = Depends(oauth2_scheme),
    session: Session = Depends(get_session),
) -> User:
    """Valida el JWT, verifica password_version y devuelve el usuario autenticado."""
    try:
        return authenticate_token(token, session)
    except AuthenticationError as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=str(exc),
            headers={"WWW-Authenticate": "Bearer"},
        ) from exc


def get_optional_user(
    token: str | None = Depends(optional_oauth2_scheme),
    session: Session = Depends(get_session),
) -> User | None:
    """Devuelve el usuario si hay JWT; mantiene públicos los vídeos accesibles por enlace."""
    return _optional_user(token, session)


def get_optional_user_allowing_query_token(
    header_token: str | None = Depends(optional_oauth2_scheme),
    query_token: str | None = Query(
        default=None,
        alias="token",
        description="JWT alternativo al header Authorization, para clientes que no "
        "pueden enviar cabeceras (por ejemplo el elemento <video> del navegador).",
    ),
    session: Session = Depends(get_session),
) -> User | None:
    """Como `get_optional_user`, pero acepta también el JWT en la query string.

    El `<video src>` del navegador no permite añadir cabeceras, así que la URL de
    reproducción lleva el token incrustado igual que hacía la firma prefirmada.
    """
    return _optional_user(header_token or query_token, session)


def _optional_user(token: str | None, session: Session) -> User | None:
    if token is None:
        return None
    try:
        return authenticate_token(token, session)
    except AuthenticationError as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=str(exc),
            headers={"WWW-Authenticate": "Bearer"},
        ) from exc
