import uuid
from datetime import UTC, datetime
from typing import Optional

from sqlalchemy import Column, Text
from sqlmodel import Field, SQLModel

class User(SQLModel, table=True):
    id: uuid.UUID = Field(default_factory=uuid.uuid4, primary_key=True, index=True, nullable=False)
    username: str = Field(unique=True, nullable=False)
    password_hash: str
    password_version: int = Field(default=0)
    full_name: str
    email: str = Field(unique=True, nullable=False)
    password_reset_token_hash: Optional[str] = Field(default=None, nullable=True)
    password_reset_expires_at: Optional[datetime] = Field(default=None, nullable=True)

class UserFollowsUser(SQLModel, table=True):
    """Relación de seguimiento entre usuarios (follower sigue a followed)."""

    follower_id: uuid.UUID = Field(nullable=False, primary_key=True, foreign_key="user.id")
    followed_id: uuid.UUID = Field(nullable=False, primary_key=True, foreign_key="user.id", index=True)
    created_at: datetime = Field(default_factory=lambda: datetime.now(UTC), nullable=False)


class Video(SQLModel, table=True):
    id: uuid.UUID = Field(default_factory=uuid.uuid4, primary_key=True, index=True, nullable=False)
    filename: str
    author: uuid.UUID = Field(nullable=False, foreign_key="user.id")
    video_name: str
    video_desc: str
    created_at: datetime = Field(
        default_factory=lambda: datetime.now(UTC), nullable=False, index=True
    )
    is_published: bool = Field(default=True, nullable=False)
    visibility: str = Field(
        default="public", max_length=16, nullable=False, index=True
    )
    allowed_users: str = Field(
        default="[]", sa_column=Column(Text, nullable=False)
    )


class MultipartUpload(SQLModel, table=True):
    """Carga S3 persistida, vinculada a su propietario."""

    id: uuid.UUID = Field(default_factory=uuid.uuid4, primary_key=True)
    upload_id: str = Field(unique=True, index=True, nullable=False)
    key: str = Field(unique=True, index=True, nullable=False)
    original_filename: str
    owner_id: uuid.UUID = Field(nullable=False, foreign_key="user.id", index=True)
    status: str = Field(default="pending", nullable=False)
    created_at: datetime = Field(default_factory=lambda: datetime.now(UTC), nullable=False)


class UserLikesVideo(SQLModel, table=True):
    video_id: uuid.UUID = Field(nullable=False, primary_key=True, foreign_key="video.id")
    user_id: uuid.UUID = Field(nullable=False, primary_key=True, foreign_key="user.id")
