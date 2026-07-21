from dataclasses import dataclass
from collections import defaultdict, deque
from time import monotonic
import uuid

import strawberry
from fastapi import Depends, Request, Response, status
from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint
from sqlalchemy.exc import IntegrityError
from sqlmodel import Session, select
from strawberry.fastapi import GraphQLRouter
from strawberry.types import Info

import constants

from auth.security import create_access_token, hash_password, verify_password
from auth.service import AuthenticationError, authenticate_token
from clients import s3_client, s3_client_public
from database import get_session
from models import MultipartUpload, User, Video
from settings import settings
from video_processor import is_valid_video_extension


@dataclass
class GraphQLContext:
    session: Session
    user: User | None


def get_context(
    request: Request,
    session: Session = Depends(get_session),
) -> GraphQLContext:
    """Autenticación opcional: las operaciones públicas no exigen JWT."""
    authorization = request.headers.get("Authorization", "")
    if not authorization:
        return GraphQLContext(session=session, user=None)
    scheme, _, token = authorization.partition(" ")
    if scheme.lower() != "bearer" or not token:
        raise AuthenticationError("Cabecera Authorization inválida")
    return GraphQLContext(session=session, user=authenticate_token(token, session))


def require_user(info: Info[GraphQLContext, None]) -> User:
    if info.context.user is None:
        raise AuthenticationError("Autenticación requerida")
    return info.context.user


@strawberry.type
class VideoType:
    id: uuid.UUID
    filename: str
    title: str
    description: str
    author_id: uuid.UUID

    @classmethod
    def from_model(cls, video: Video) -> "VideoType":
        return cls(id=video.id, filename=video.filename, title=video.video_name,
                   description=video.video_desc, author_id=video.author)


@strawberry.type
class ChannelType:
    id: uuid.UUID
    username: str
    display_name: str

    @classmethod
    def from_model(cls, user: User) -> "ChannelType":
        return cls(id=user.id, username=user.username, display_name=user.full_name)

    @strawberry.field
    def videos(self, info: Info[GraphQLContext, None]) -> list[VideoType]:
        rows = info.context.session.exec(select(Video).where(
            Video.author == self.id, Video.is_published == True  # noqa: E712
        )).all()
        return [VideoType.from_model(video) for video in rows]


@strawberry.type
class MyAccountType:
    id: uuid.UUID
    username: str
    display_name: str
    email: str

    @classmethod
    def from_model(cls, user: User) -> "MyAccountType":
        return cls(id=user.id, username=user.username,
                   display_name=user.full_name, email=user.email)


@strawberry.type
class AuthPayload:
    access_token: str
    account: MyAccountType


@strawberry.type
class UploadType:
    upload_id: str
    key: str
    original_filename: str
    status: str

    @classmethod
    def from_model(cls, upload: MultipartUpload) -> "UploadType":
        return cls(upload_id=upload.upload_id, key=upload.key,
                   original_filename=upload.original_filename, status=upload.status)


@strawberry.input
class RegisterInput:
    username: str
    password: str
    display_name: str
    email: str


@strawberry.input
class UpdateAccountInput:
    display_name: str | None = None
    email: str | None = None


@strawberry.input
class PartInput:
    part_number: int
    etag: str


@strawberry.type
class Query:
    @strawberry.field
    def channels(self, info: Info[GraphQLContext, None]) -> list[ChannelType]:
        return [ChannelType.from_model(user) for user in info.context.session.exec(select(User)).all()]

    @strawberry.field
    def channel(self, info: Info[GraphQLContext, None], id: uuid.UUID) -> ChannelType | None:
        user = info.context.session.get(User, id)
        return ChannelType.from_model(user) if user else None

    @strawberry.field
    def videos(self, info: Info[GraphQLContext, None]) -> list[VideoType]:
        rows = info.context.session.exec(select(Video).where(Video.is_published == True)).all()  # noqa: E712
        return [VideoType.from_model(video) for video in rows]

    @strawberry.field
    def video(self, info: Info[GraphQLContext, None], id: uuid.UUID) -> VideoType | None:
        video = info.context.session.get(Video, id)
        if video is None or not video.is_published:
            return None
        return VideoType.from_model(video)

    @strawberry.field
    def me(self, info: Info[GraphQLContext, None]) -> MyAccountType:
        return MyAccountType.from_model(require_user(info))


@strawberry.type
class Mutation:
    @strawberry.mutation
    def register(self, info: Info[GraphQLContext, None], input: RegisterInput) -> AuthPayload:
        password_hash, version = hash_password(input.password)
        user = User(username=input.username, password_hash=password_hash,
                    password_version=version, full_name=input.display_name, email=input.email)
        try:
            info.context.session.add(user)
            info.context.session.commit()
            info.context.session.refresh(user)
        except IntegrityError as exc:
            info.context.session.rollback()
            raise ValueError("El nombre de usuario o el email ya están registrados") from exc
        return AuthPayload(create_access_token(user.id, version), MyAccountType.from_model(user))

    @strawberry.mutation
    def login(self, info: Info[GraphQLContext, None], username: str, password: str) -> AuthPayload:
        user = info.context.session.exec(select(User).where(User.username == username)).first()
        if user is None or not verify_password(password, user.password_hash):
            raise AuthenticationError("Credenciales inválidas")
        return AuthPayload(create_access_token(user.id, user.password_version), MyAccountType.from_model(user))

    @strawberry.mutation
    def update_account(self, info: Info[GraphQLContext, None], input: UpdateAccountInput) -> MyAccountType:
        user = require_user(info)
        if input.display_name is not None:
            user.full_name = input.display_name
        if input.email is not None:
            user.email = input.email
        info.context.session.add(user)
        info.context.session.commit()
        info.context.session.refresh(user)
        return MyAccountType.from_model(user)

    @strawberry.mutation
    def update_video(self, info: Info[GraphQLContext, None], id: uuid.UUID,
                     title: str | None = None, description: str | None = None,
                     published: bool | None = None) -> VideoType:
        user = require_user(info)
        video = info.context.session.get(Video, id)
        if video is None or video.author != user.id:
            raise ValueError("Vídeo no encontrado")
        if title is not None:
            video.video_name = title
        if description is not None:
            video.video_desc = description
        if published is not None:
            video.is_published = published
        info.context.session.add(video)
        info.context.session.commit()
        info.context.session.refresh(video)
        return VideoType.from_model(video)

    @strawberry.mutation
    def delete_video(self, info: Info[GraphQLContext, None], id: uuid.UUID) -> bool:
        user = require_user(info)
        video = info.context.session.get(Video, id)
        if video is None or video.author != user.id:
            raise ValueError("Vídeo no encontrado")
        s3_client.delete_object(Bucket=settings.AWS_BUCKET_NAME, Key=video.filename)
        info.context.session.delete(video)
        info.context.session.commit()
        return True

    @strawberry.mutation
    def start_multipart(self, info: Info[GraphQLContext, None], original_filename: str) -> UploadType:
        user = require_user(info)
        if not is_valid_video_extension(original_filename):
            raise ValueError("Formato de archivo no permitido")
        key = f"videos/{uuid.uuid4().hex}.mp4"
        response = s3_client.create_multipart_upload(Bucket=settings.AWS_BUCKET_NAME, Key=key)
        upload = MultipartUpload(upload_id=response["UploadId"], key=key,
                                 original_filename=original_filename, owner_id=user.id)
        info.context.session.add(upload)
        info.context.session.commit()
        return UploadType.from_model(upload)

    @strawberry.mutation
    def sign_chunk(self, info: Info[GraphQLContext, None], upload_id: str,
                   key: str, part_number: int) -> str:
        upload = _owned_upload(info, upload_id, key)
        if part_number < 1 or upload.status != "pending":
            raise ValueError("Fragmento o carga inválidos")
        return s3_client_public.generate_presigned_url(
            ClientMethod="upload_part", Params={"Bucket": settings.AWS_BUCKET_NAME,
            "Key": key, "UploadId": upload_id, "PartNumber": part_number}, ExpiresIn=3600)

    @strawberry.mutation
    def complete_multipart(self, info: Info[GraphQLContext, None], upload_id: str,
                           key: str, parts: list[PartInput]) -> VideoType:
        user = require_user(info)
        upload = _owned_upload(info, upload_id, key)
        if upload.status != "pending":
            raise ValueError(constants.LOAD_NOT_FOUND)
        s3_client.complete_multipart_upload(Bucket=settings.AWS_BUCKET_NAME, Key=key,
            UploadId=upload_id, MultipartUpload={"Parts": [
                {"PartNumber": part.part_number, "ETag": part.etag} for part in parts]})
        upload.status = "completed"
        video = Video(filename=key, author=user.id, video_name=upload.original_filename,
                      video_desc="", is_published=False)
        info.context.session.add(upload)
        info.context.session.add(video)
        info.context.session.commit()
        info.context.session.refresh(video)
        return VideoType.from_model(video)

    @strawberry.mutation
    def cancel_multipart(self, info: Info[GraphQLContext, None], upload_id: str, key: str) -> bool:
        upload = _owned_upload(info, upload_id, key)
        if upload.status != "pending":
            raise ValueError(constants.LOAD_NOT_FOUND)
        s3_client.abort_multipart_upload(Bucket=settings.AWS_BUCKET_NAME, Key=key, UploadId=upload_id)
        upload.status = "cancelled"
        info.context.session.add(upload)
        info.context.session.commit()
        return True


def _owned_upload(info: Info[GraphQLContext, None], upload_id: str, key: str) -> MultipartUpload:
    user = require_user(info)
    upload = info.context.session.exec(select(MultipartUpload).where(
        MultipartUpload.upload_id == upload_id, MultipartUpload.key == key)).first()
    if upload is None or upload.owner_id != user.id:
        raise ValueError(constants.LOAD_NOT_FOUND)
    return upload


schema = strawberry.Schema(
    query=Query,
    mutation=Mutation,
    extensions=[
        strawberry.extensions.MaxTokensLimiter(max_token_count=1_000),
        strawberry.extensions.QueryDepthLimiter(max_depth=10),
    ],
)
graphql_router = GraphQLRouter(schema, context_getter=get_context)


class GraphQLRateLimitMiddleware(BaseHTTPMiddleware):
    """Límite básico por IP para reducir abuso del endpoint público."""

    def __init__(self, app: object) -> None:
        super().__init__(app)
        self._requests: dict[str, deque[float]] = defaultdict(deque)

    async def dispatch(self, request: Request, call_next: RequestResponseEndpoint) -> Response:
        if request.url.path != "/graphql":
            return await call_next(request)
        now = monotonic()
        key = request.client.host if request.client else "unknown"
        timestamps = self._requests[key]
        while timestamps and timestamps[0] <= now - 60:
            timestamps.popleft()
        if len(timestamps) >= settings.GRAPHQL_RATE_LIMIT_PER_MINUTE:
            return Response("Demasiadas solicitudes", status_code=status.HTTP_429_TOO_MANY_REQUESTS)
        timestamps.append(now)
        return await call_next(request)
