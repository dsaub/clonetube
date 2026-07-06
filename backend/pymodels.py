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