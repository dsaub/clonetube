from datetime import UTC, datetime

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.exc import IntegrityError
from sqlmodel import Session, select
from auth.mailing import enviar_message

from auth.dependencies import get_current_user
from auth.security import (
    create_access_token,
    create_reset_token,
    hash_password,
    hash_reset_token,
    verify_password,
    generar_codigo
)
from database import get_session
from models import User
from pymodels import (
    ChangePasswordRequest,
    ForgotPasswordRequest,
    ForgotPasswordResponse,
    LoginRequest,
    MessageResponse,
    RegisterRequest,
    ResetPasswordRequest,
    TokenResponse,
    UserResponse
)

router = APIRouter(prefix="/api/v1/auth", tags=["Auth"])


@router.post(
    "/register",
    summary="Registrar usuario",
    description=(
        "Crea un nuevo usuario con username, password, nombre completo y email. "
        "Devuelve un token JWT para autenticarse inmediatamente. "
        "Si el username o email ya existen devuelve un error 409 Conflict."
    ),
    responses={
        201: {"model": TokenResponse, "description": "Usuario creado exitosamente"},
        409: {"description": "El username o email ya están registrados"},
    },
    status_code=status.HTTP_201_CREATED,
)
def register(body: RegisterRequest, session: Session = Depends(get_session)):
    password_hash, password_version = hash_password(body.password)

    user = User(
        username=body.username,
        password_hash=password_hash,
        password_version=password_version,
        full_name=body.full_name,
        email=body.email,
        verify_code=generar_codigo()
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

    return {"status": "PENDING_CONFIRMATION"}


@router.post(
    "/login",
    summary="Iniciar sesión",
    description=(
        "Autentica un usuario con username y password. "
        "Devuelve un token JWT que debe enviarse como Bearer token "
        "en el header Authorization para acceder a rutas protegidas."
    ),
    responses={
        200: {"model": TokenResponse, "description": "Autenticación exitosa"},
        401: {"description": "Credenciales inválidas"},
    },
)
def login(body: LoginRequest, session: Session = Depends(get_session)) -> TokenResponse:
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
    description=(
        "Cambia la contraseña del usuario autenticado. "
        "Requiere la contraseña actual y la nueva contraseña. "
        "Invalida todos los tokens JWT anteriores incrementando el password_version."
    ),
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
    if not verify_password(body.old_password, current_user.password_hash):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Contraseña actual incorrecta",
        )

    new_hash, _ = hash_password(body.new_password)
    current_user.password_hash = new_hash
    current_user.password_version += 1
    current_user.password_reset_token_hash = None
    current_user.password_reset_expires_at = None

    session.add(current_user)
    session.commit()
    session.refresh(current_user)

    token = create_access_token(current_user.id, current_user.password_version)
    return TokenResponse(access_token=token)


@router.post(
    "/forgot-password",
    summary="Solicitar restablecimiento de contraseña",
    description=(
        "Solicita un token de restablecimiento de contraseña para un email. "
        "Si el email está registrado, se genera y almacena un token hash con "
        "expiración de 15 minutos y se devuelve en la respuesta (útil en desarrollo). "
        "El mensaje de respuesta es idéntico exista o no el email por seguridad."
    ),
    responses={
        200: {"model": ForgotPasswordResponse, "description": "Token generado (si existe el email)"},
    },
)
def forgot_password(
    body: ForgotPasswordRequest,
    session: Session = Depends(get_session),
) -> ForgotPasswordResponse:
    user = session.exec(select(User).where(User.email == body.email)).first()

    if user is None:
        return ForgotPasswordResponse(
            status="Si el email existe, se generó un enlace de recuperación"
        )

    token, token_hash, expires_at = create_reset_token()
    user.password_reset_token_hash = token_hash
    user.password_reset_expires_at = expires_at

    session.add(user)
    session.commit()

    return ForgotPasswordResponse(
        status="Si el email existe, se generó un enlace de recuperación",
        reset_token=token,
    )


@router.post(
    "/reset-password",
    summary="Restablecer contraseña con token",
    description=(
        "Restablece la contraseña usando un token de restablecimiento válido. "
        "El token tiene una validez de 15 minutos. Al restablecer se incrementa "
        "el password_version, invalidando tokens JWT anteriores."
    ),
    responses={
        200: {"model": MessageResponse, "description": "Contraseña restablecida exitosamente"},
        400: {"description": "Token inválido o expirado"},
    },
)
def reset_password(
    body: ResetPasswordRequest,
    session: Session = Depends(get_session),
) -> MessageResponse:
    token_hash = hash_reset_token(body.token)
    now = datetime.now(UTC)

    user = session.exec(
        select(User).where(
            User.password_reset_token_hash == token_hash,
            User.password_reset_expires_at.isnot(None),
            User.password_reset_expires_at > now,
        )
    ).first()

    if user is None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Token inválido o expirado",
        )

    new_hash, _ = hash_password(body.new_password)
    user.password_hash = new_hash
    user.password_version += 1
    user.password_reset_token_hash = None
    user.password_reset_expires_at = None

    session.add(user)
    session.commit()

    return MessageResponse(status="Contraseña restablecida exitosamente")


@router.get(
    "/me",
    summary="Perfil del usuario actual",
    description=(
        "Devuelve los datos del usuario autenticado mediante el token Bearer. "
        "Requiere un token JWT válido en el header Authorization."
    ),
    responses={
        200: {"model": UserResponse, "description": "Datos del usuario autenticado"},
        401: {"description": "Token inválido o expirado"},
    },
)
def me(current_user: User = Depends(get_current_user)) -> UserResponse:
    return UserResponse(
        id=current_user.id,
        username=current_user.username,
        full_name=current_user.full_name,
        email=current_user.email,
    )
@router.get(
    "/test_mail"
)
def test_mail(email: str) -> dict:
    result = enviar_message(email, "Email de prueba", "Este es un email de prueba!")
    return result