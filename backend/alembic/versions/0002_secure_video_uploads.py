"""Persist multipart ownership and video publication state.

Revision ID: 0002_secure_video_uploads
Revises: 0001_add_password_reset_fields
"""
from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0002_secure_video_uploads"
down_revision: str | None = "0001"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.add_column("video", sa.Column("is_published", sa.Boolean(), nullable=False,
                                     server_default=sa.false()))
    op.create_table(
        "multipartupload",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("upload_id", sa.String(length=255), nullable=False),
        sa.Column("key", sa.String(length=255), nullable=False),
        sa.Column("original_filename", sa.String(length=255), nullable=False),
        sa.Column("owner_id", sa.Uuid(), nullable=False),
        sa.Column("status", sa.String(length=255), nullable=False),
        sa.Column("created_at", sa.DateTime(), nullable=False),
        sa.ForeignKeyConstraint(["owner_id"], ["user.id"]),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("key"),
        sa.UniqueConstraint("upload_id"),
    )
    op.create_index("ix_multipartupload_key", "multipartupload", ["key"])
    op.create_index("ix_multipartupload_owner_id", "multipartupload", ["owner_id"])
    op.create_index("ix_multipartupload_upload_id", "multipartupload", ["upload_id"])


def downgrade() -> None:
    op.drop_index("ix_multipartupload_upload_id", table_name="multipartupload")
    op.drop_index("ix_multipartupload_owner_id", table_name="multipartupload")
    op.drop_index("ix_multipartupload_key", table_name="multipartupload")
    op.drop_table("multipartupload")
    op.drop_column("video", "is_published")
