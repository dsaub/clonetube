"""add_password_reset_fields

Revision ID: 0001
Revises:
Create Date: 2026-07-09 00:00:00.000000
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = "0001"
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column("user", sa.Column("password_reset_token_hash", sa.String(), nullable=True))
    op.add_column("user", sa.Column("password_reset_expires_at", sa.DateTime(), nullable=True))


def downgrade() -> None:
    op.drop_column("user", "password_reset_expires_at")
    op.drop_column("user", "password_reset_token_hash")