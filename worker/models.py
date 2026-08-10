from typing import Literal

from pydantic import BaseModel, ConfigDict, EmailStr, field_validator

class EmailMessage(BaseModel):
    id: str
    to: EmailStr
    subject: str
    body: str
    body_html: str | None = None


class VideoTranscodeMessage(BaseModel):
    model_config = ConfigDict(extra="forbid")

    type: Literal["video.transcode"]
    version: Literal[1]
    id: str
    video_id: int
    source_key: str
    source_etag: str | None = None
    original_filename: str

    @field_validator("source_key")
    @classmethod
    def validate_source_key(cls, value: str) -> str:
        if not value.startswith("videos/") or ".." in value.split("/"):
            raise ValueError("source_key debe pertenecer al prefijo videos/")
        return value
