from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # ─── AWS / MinIO (S3-compatible) ─────────────
    AWS_ACCESS_KEY_ID: str = ""
    AWS_SECRET_ACCESS_KEY: str = ""
    AWS_REGION: str = "us-east-1"
    AWS_BUCKET_NAME: str = "clonetube"
    S3_ENDPOINT_URL: str | None = None
    S3_PUBLIC_ENDPOINT_URL: str | None = None

    # ─── Base de datos ──────────────────────────
    DATABASE_URL: str = "mysql+pymysql://clonetube:password@localhost:3306/clonetube"

    # ─── JWT ─────────────────────────────────────
    JWT_SECRET: str = "cambiar-por-clave-segura-de-al-menos-32-byts"
    JWT_ALGORITHM: str = "HS256"
    JWT_EXPIRE_MINUTES: int = 60

    # ─── GraphQL abuse controls ───────────────────
    GRAPHQL_RATE_LIMIT_PER_MINUTE: int = 120


settings = Settings()
