import uuid
from typing import List, Optional

from pydantic import BaseModel, EmailStr


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


class StreamUrlResponse(BaseModel):
    url: str
    key: str


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