from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class VideoSettings(BaseSettings):
    video_queue_url: str
    aws_region: str = "eu-west-3"
    aws_bucket_name: str
    s3_endpoint_url: str | None = None
    visibility_timeout: int = Field(default=900, ge=60, le=43200)
    visibility_heartbeat_seconds: int = Field(default=60, ge=10)
    wait_time_seconds: int = Field(default=20, ge=0, le=20)
    transcode_timeout_seconds: int = Field(default=21600, ge=60)
    max_video_bytes: int = Field(default=107_374_182_400, gt=0)

    @field_validator("s3_endpoint_url", mode="before")
    @classmethod
    def empty_endpoint_uses_aws_default(cls, value):
        return value or None

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )


settings = VideoSettings()
