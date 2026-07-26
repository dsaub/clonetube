import uuid
from typing import List, Literal, Optional

from pydantic import BaseModel, EmailStr, Field


# ─── Multipart Upload ─────────────────────────────────────────────

class PartInfo(BaseModel):
    PartNumber: int
    ETag: str


class CompleteMultipartBody(BaseModel):
    filename: str
    uploadId: str
    parts: List[PartInfo]


# ─── Response Models ──────────────────────────────────────────────

class StartMultipartResponse(BaseModel):
    uploadId: str
    key: str
    original_filename: str


class SignChunkResponse(BaseModel):
    url: str


class UploadChunkResponse(BaseModel):
    PartNumber: int
    ETag: str


class CompleteMultipartResponse(BaseModel):
    status: str
    location: Optional[str] = None
    key: str
    original_filename: str


class VideoListItem(BaseModel):
    key: str
    size: int
    last_modified: str
    original_filename: str


class VideoListResponse(BaseModel):
    videos: List[VideoListItem]


VideoVisibility = Literal["public", "unlisted", "private"]


class StudioVideoItem(VideoListItem):
    id: uuid.UUID
    title: str
    description: str
    visibility: VideoVisibility
    allowed_users: list[str]


class StudioVideoResponse(BaseModel):
    videos: list[StudioVideoItem]


class VideoCatalogItem(BaseModel):
    id: uuid.UUID
    filename: str
    title: str
    description: str
    author_id: uuid.UUID
    author_username: str
    author_name: str


class VideoCatalogResponse(BaseModel):
    videos: list[VideoCatalogItem]


class VideoDetail(BaseModel):
    id: uuid.UUID
    key: str
    title: str
    description: str
    visibility: VideoVisibility
    author_id: uuid.UUID
    author_username: str
    author_name: str


class VideoUpdate(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    description: str = Field(default="", max_length=5000)
    visibility: VideoVisibility
    allowed_users: list[str] = Field(default_factory=list, max_length=50)


class StreamUrlResponse(BaseModel):
    url: str
    key: str


# ─── Feed ─────────────────────────────────────────────────────────

class FeedVideoItem(VideoCatalogItem):
    created_at: str
    likes: int
    score: float
    from_followed_author: bool


class FeedResponse(BaseModel):
    videos: list[FeedVideoItem]
    following_count: int
    personalized: bool


# ─── Seguidores ───────────────────────────────────────────────────

class PublicUser(BaseModel):
    id: uuid.UUID
    username: str
    full_name: str


class FollowStateResponse(BaseModel):
    username: str
    following: bool
    followers: int


class FollowingListResponse(BaseModel):
    users: list[PublicUser]


# ─── Canal ────────────────────────────────────────────────────────

class ChannelResponse(BaseModel):
    id: uuid.UUID
    username: str
    full_name: str
    following: bool
    followers: int
    following_count: int
    video_count: int


class ChannelVideoItem(BaseModel):
    id: uuid.UUID
    key: str
    title: str
    description: str
    visibility: VideoVisibility
    created_at: str
    likes: int


class ChannelVideosResponse(BaseModel):
    videos: list[ChannelVideoItem]
    page: int
    page_size: int
    total: int
    pages: int


# ─── Auth ─────────────────────────────────────────────────────────

class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"


class RegisterRequest(BaseModel):
    username: str
    password: str
    full_name: str
    email: EmailStr


class LoginRequest(BaseModel):
    username: str
    password: str


class ChangePasswordRequest(BaseModel):
    old_password: str
    new_password: str


class ForgotPasswordRequest(BaseModel):
    email: EmailStr


class ForgotPasswordResponse(BaseModel):
    status: str
    reset_token: Optional[str] = None


class ResetPasswordRequest(BaseModel):
    token: str
    new_password: str


class MessageResponse(BaseModel):
    status: str


class UserResponse(BaseModel):
    id: uuid.UUID
    username: str
    full_name: str
    email: str
