from pydantic import BaseModel
from typing import List, Optional


class PartInfo(BaseModel):
    PartNumber: int
    ETag: str


class CompleteMultipartBody(BaseModel):
    filename: str
    uploadId: str
    parts: List[PartInfo]


# ─── Response Models ────────────────────────────────────────────────

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