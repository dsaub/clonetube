"""Add user follows and video created_at

Revision ID: c3f1a9d24b7e
Revises: 8bca13ab9d22
"""

from typing import Sequence

from alembic import op
import sqlalchemy as sa

revision: str = "c3f1a9d24b7e"
down_revision: str | Sequence[str] | None = "8bca13ab9d22"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    inspector = sa.inspect(op.get_bind())

    # MySQL DDL is non-transactional, so a failed run may leave individual
    # operations applied even though Alembic did not advance the revision.
    if "userfollowsuser" not in set(inspector.get_table_names()):
        op.create_table(
            "userfollowsuser",
            sa.Column("follower_id", sa.Uuid(), nullable=False),
            sa.Column("followed_id", sa.Uuid(), nullable=False),
            sa.Column("created_at", sa.DateTime(), nullable=False),
            sa.ForeignKeyConstraint(["follower_id"], ["user.id"]),
            sa.ForeignKeyConstraint(["followed_id"], ["user.id"]),
            sa.PrimaryKeyConstraint("follower_id", "followed_id"),
        )
        op.create_index(
            op.f("ix_userfollowsuser_followed_id"),
            "userfollowsuser",
            ["followed_id"],
            unique=False,
        )

    columns = {column["name"] for column in inspector.get_columns("video")}
    if "created_at" not in columns:
        op.add_column(
            "video",
            sa.Column(
                "created_at",
                sa.DateTime(),
                nullable=False,
                server_default=sa.text("CURRENT_TIMESTAMP"),
            ),
        )
    indexes = {index["name"] for index in inspector.get_indexes("video")}
    if op.f("ix_video_created_at") not in indexes:
        op.create_index(op.f("ix_video_created_at"), "video", ["created_at"], unique=False)


def downgrade() -> None:
    op.drop_index(op.f("ix_video_created_at"), table_name="video")
    op.drop_column("video", "created_at")
    op.drop_index(op.f("ix_userfollowsuser_followed_id"), table_name="userfollowsuser")
    op.drop_table("userfollowsuser")
