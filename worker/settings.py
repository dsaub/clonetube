from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    queue_url: str
    aws_region: str = "eu-west-3"

    smtp_host: str
    smtp_port: int = 465
    smtp_username: str
    smtp_password: str
    smtp_from: str

    visibility_timeout: int = 60
    wait_time_seconds: int = 20

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False
    )

settings = Settings()