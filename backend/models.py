import uuid
from typing import Optional

from sqlmodel import Field, SQLModel

class User(SQLModel, table=True):
    id: uuid.UUID = Field(default_factory=uuid.uuid4, primary_key=True, index=True, nullable=False)
    username: str = Field(unique=True, nullable=False)
    password_hash: str
    password_version: int = Field(default=0)
    full_name: str
    email: str = Field(unique=True, nullable=False)

class Video(SQLModel, table=True):
    id: uuid.UUID = Field(default_factory=uuid.uuid4, primary_key=True, index=True, nullable=False)
    filename: str
    author: uuid.UUID = Field(nullable=False, foreign_key="user.id")
    video_name: str
    video_desc: str

class UserLikesVideo(SQLModel, table=True):
    video_id: uuid.UUID = Field(nullable=False, primary_key=True, foreign_key="video.id")
    user_id: uuid.UUID = Field(nullable=False, primary_key=True, foreign_key="user.id")