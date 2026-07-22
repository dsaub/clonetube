from fastapi import Depends, HTTPException, status
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
