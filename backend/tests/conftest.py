from collections.abc import Generator
from typing import Any
from unittest.mock import MagicMock, patch

import pytest
from fastapi import FastAPI
from httpx import ASGITransport, AsyncClient
from sqlmodel import Session, SQLModel, create_engine

from database import get_session
from main import app as _app
from models import User
from settings import settings

_TEST_DB_URL = "sqlite:///./test_clonetube.db"
_engine = create_engine(_TEST_DB_URL, echo=False)


def _override_get_session() -> Generator[Session, None, None]:
    with Session(_engine) as session:
        yield session


def _create_tables() -> None:
    SQLModel.metadata.create_all(_engine)


def _drop_tables() -> None:
    SQLModel.metadata.drop_all(_engine)


@pytest.fixture(autouse=True)
def _setup_db() -> Generator[None, None, None]:
    _create_tables()
    yield
    _drop_tables()


@pytest.fixture
def app() -> FastAPI:
    _app.dependency_overrides[get_session] = _override_get_session
    return _app


@pytest.fixture
async def client(app: FastAPI) -> AsyncClient:
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac


@pytest.fixture
def mock_s3() -> Generator[dict[str, MagicMock], None, None]:
    mocks = {
        "s3_client": MagicMock(),
        "s3_client_public": MagicMock(),
    }
    with (
        patch("routes.video.s3_client", mocks["s3_client"]),
        patch("routes.video.s3_client_public", mocks["s3_client_public"]),
    ):
        yield mocks


@pytest.fixture
def db_session() -> Generator[Session, None, None]:
    with Session(_engine) as session:
        yield session


_USER_PAYLOAD = {
    "username": "testuser",
    "password": "Str0ng!Pass",
    "full_name": "Test User",
    "email": "test@example.com",
}


@pytest.fixture
def user_payload() -> dict[str, str]:
    return dict(_USER_PAYLOAD)


@pytest.fixture
async def registered_user(client: AsyncClient, user_payload: dict[str, str]) -> dict[str, Any]:
    resp = await client.post("/api/v1/auth/register", json=user_payload)
    assert resp.status_code == 201
    resp = await client.post("/api/v1/auth/login", json={
        "username": user_payload["username"],
        "password": user_payload["password"],
    })
    assert resp.status_code == 200
    data = resp.json()
    data["password"] = user_payload["password"]
    return data


@pytest.fixture
async def auth_headers(registered_user: dict[str, Any]) -> dict[str, str]:
    return {"Authorization": f"Bearer {registered_user['access_token']}"}
