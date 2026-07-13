from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.exc import IntegrityError
from sqlmodel import Session, select

from auth.dependencies import get_current_user
from auth.schemas import (
    ChangePasswordRequest,
    LoginRequest,
    RegisterRequest,
    TokenResponse,
    UserResponse,
)
from auth.security import (
    create_access_token,
    hash_password,
    verify_password,
)
from database import get_session
from models import User

router = APIRouter(prefix="/api/v1/auth", tags=["Auth"])


@router.post(
    "/register",
    summary="Registrar usuario",
    description="Crea un nuevo usuario y devuelve un token de acceso.",
    responses={
        201: {"model": TokenResponse, "description": "Usuario creado exitosamente"},
        409: {"description": "El username o email ya existen"},
    },
    status_code=status.HTTP_201_CREATED,
)
def register(body: RegisterRequest, session: Session = Depends(get_session)) -> TokenResponse:
    """Registra un nuevo usuario públicamente."""
    password_hash, password_version = hash_password(body.password)

    user = User(
        username=body.username,
        password_hash=password_hash,
        password_version=password_version,
        full_name=body.full_name,
        email=body.email,
    )

    try:
        session.add(user)
        session.commit()
        session.refresh(user)
    except IntegrityError:
        session.rollback()
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="El nombre de usuario o el email ya están registrados",
        )

    token = create_access_token(user.id, user.password_version)
    return TokenResponse(access_token=token)


@router.post(
    "/login",
    summary="Iniciar sesión",
    description="Autentica un usuario con username y password, devuelve un token de acceso.",
    responses={
        200: {"model": TokenResponse, "description": "Autenticación exitosa"},
        401: {"description": "Credenciales inválidas"},
    },
)
def login(body: LoginRequest, session: Session = Depends(get_session)) -> TokenResponse:
    """Inicia sesión con username y contraseña."""
    user = session.exec(select(User).where(User.username == body.username)).first()

    if user is None or not verify_password(body.password, user.password_hash):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Credenciales inválidas",
            headers={"WWW-Authenticate": "Bearer"},
        )

    token = create_access_token(user.id, user.password_version)
    return TokenResponse(access_token=token)


@router.post(
    "/change-password",
    summary="Cambiar contraseña",
    description="Cambia la contraseña del usuario autenticado. Invalida todos los tokens anteriores.",
    responses={
        200: {"model": TokenResponse, "description": "Contraseña cambiada exitosamente"},
        401: {"description": "Contraseña actual incorrecta o token inválido"},
    },
)
def change_password(
    body: ChangePasswordRequest,
    current_user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
) -> TokenResponse:
    """Cambia la contraseña. Incrementa password_version → invalida tokens anteriores."""
    if not verify_password(body.old_password, current_user.password_hash):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Contraseña actual incorrecta",
        )

    new_hash, _ = hash_password(body.new_password)
    current_user.password_hash = new_hash
    current_user.password_version += 1

    session.add(current_user)
    session.commit()
    session.refresh(current_user)

    token = create_access_token(current_user.id, current_user.password_version)
    return TokenResponse(access_token=token)


@router.get(
    "/me",
    summary="Perfil del usuario actual",
    description="Devuelve los datos del usuario autenticado mediante el token Bearer.",
    responses={
        200: {"model": UserResponse, "description": "Datos del usuario"},
        401: {"description": "Token inválido o expirado"},
    },
)
def me(current_user: User = Depends(get_current_user)) -> UserResponse:
    """Devuelve la información del usuario autenticado."""
    return UserResponse(
        id=current_user.id,
        username=current_user.username,
        full_name=current_user.full_name,
        email=current_user.email,
    )
