"""Add video visibility

Revision ID: 8bca13ab9d22
Revises: 55189b6b06b9
"""

from typing import Sequence

from alembic import op
import sqlalchemy as sa

revision: str = "8bca13ab9d22"
down_revision: str | Sequence[str] | None = "55189b6b06b9"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    inspector = sa.inspect(op.get_bind())
    columns = {column["name"] for column in inspector.get_columns("video")}

    # MySQL DDL is non-transactional, so a failed run may leave individual
    # operations applied even though Alembic did not advance the revision.
    if "visibility" not in columns:
        op.add_column(
            "video",
            sa.Column(
                "visibility",
                sa.String(16),
                nullable=False,
                server_default="public",
            ),
        )
    if "allowed_users" not in columns:
        op.add_column(
            "video",
            sa.Column(
                "allowed_users",
                sa.Text(),
                nullable=True,
            ),
        )

    op.execute("UPDATE video SET allowed_users = '[]' WHERE allowed_users IS NULL")
    op.alter_column(
        "video",
        "allowed_users",
        existing_type=sa.Text(),
        nullable=False,
    )
    op.execute("UPDATE video SET visibility = 'private' WHERE is_published = 0")
    indexes = {index["name"] for index in inspector.get_indexes("video")}
    if op.f("ix_video_visibility") not in indexes:
        op.create_index(
            op.f("ix_video_visibility"),
            "video",
            ["visibility"],
            unique=False,
        )


def downgrade() -> None:
    op.drop_index(op.f("ix_video_visibility"), table_name="video")
    op.drop_column("video", "allowed_users")
    op.drop_column("video", "visibility")
