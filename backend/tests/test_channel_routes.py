"""Pruebas de la página de canal (`/api/v1/users/{username}/channel` y `/videos`)."""

import uuid
from datetime import UTC, datetime, timedelta

import pytest
from httpx import AsyncClient
from sqlmodel import Session

from models import User, Video


@pytest.fixture
def creator(user_payload: dict[str, str]) -> dict[str, str]:
    """Segundo usuario del que cuelga el canal, con las credenciales de prueba."""
    return {
        **user_payload,
        "username": "creator",
        "full_name": "Creadora",
        "email": "creator@example.com",
    }


async def _register(client: AsyncClient, payload: dict[str, str]) -> dict[str, str]:
    response = await client.post("/api/v1/auth/register", json=payload)
    assert response.status_code == 201
    return {"Authorization": f"Bearer {response.json()['access_token']}"}


def _author_id(session: Session, username: str) -> uuid.UUID:
    from sqlmodel import select

    return session.exec(select(User).where(User.username == username)).one().id


def _add_videos(
    session: Session,
    author_id: uuid.UUID,
    count: int,
    visibility: str = "public",
    prefix: str = "video",
) -> None:
    """Crea vídeos con fechas decrecientes: `prefix 1` es el más reciente."""
    base = datetime(2026, 1, 1, tzinfo=UTC)
    for index in range(count):
        session.add(Video(
            filename=f"videos/{prefix}-{index + 1}.mp4",
            author=author_id,
            video_name=f"{prefix} {index + 1}",
            video_desc="",
            visibility=visibility,
            created_at=base - timedelta(hours=index),
        ))
    session.commit()


class TestChannelProfile:
    async def test_returns_the_public_profile(
        self, creator: dict[str, str], client: AsyncClient
    ):
        await _register(client, creator)

        response = await client.get("/api/v1/users/creator/channel")

        assert response.status_code == 200
        body = response.json()
        assert body["username"] == "creator"
        assert body["full_name"] == "Creadora"
        assert body["following"] is False
        assert body["followers"] == 0
        assert body["following_count"] == 0
        assert body["video_count"] == 0

    async def test_counts_followers_and_videos(
        self,
        creator: dict[str, str],
        client: AsyncClient,
        db_session: Session,
        auth_headers: dict[str, str],
    ):
        await _register(client, creator)
        await client.post("/api/v1/users/creator/follow", headers=auth_headers)
        _add_videos(db_session, _author_id(db_session, "creator"), 3)

        response = await client.get("/api/v1/users/creator/channel", headers=auth_headers)

        body = response.json()
        assert body["followers"] == 1
        assert body["following"] is True
        assert body["video_count"] == 3

    async def test_unknown_channel_returns_404(self, client: AsyncClient):
        response = await client.get("/api/v1/users/fantasma/channel")
        assert response.status_code == 404


class TestChannelVideos:
    async def test_pages_have_twenty_videos_by_default(
        self, creator: dict[str, str], client: AsyncClient, db_session: Session
    ):
        await _register(client, creator)
        _add_videos(db_session, _author_id(db_session, "creator"), 25)

        response = await client.get("/api/v1/users/creator/videos")

        assert response.status_code == 200
        body = response.json()
        assert len(body["videos"]) == 20
        assert body == {
            **body,
            "page": 1,
            "page_size": 20,
            "total": 25,
            "pages": 2,
        }

    async def test_second_page_returns_the_rest_without_repeating(
        self, creator: dict[str, str], client: AsyncClient, db_session: Session
    ):
        await _register(client, creator)
        _add_videos(db_session, _author_id(db_session, "creator"), 25)

        first = await client.get("/api/v1/users/creator/videos")
        second = await client.get("/api/v1/users/creator/videos", params={"page": 2})

        assert len(second.json()["videos"]) == 5
        first_keys = {video["key"] for video in first.json()["videos"]}
        second_keys = {video["key"] for video in second.json()["videos"]}
        assert first_keys.isdisjoint(second_keys)

    async def test_orders_from_newest_to_oldest(
        self, creator: dict[str, str], client: AsyncClient, db_session: Session
    ):
        await _register(client, creator)
        _add_videos(db_session, _author_id(db_session, "creator"), 3)

        response = await client.get("/api/v1/users/creator/videos")

        assert [video["title"] for video in response.json()["videos"]] == [
            "video 1", "video 2", "video 3",
        ]

    async def test_page_beyond_the_last_one_is_empty(
        self, creator: dict[str, str], client: AsyncClient, db_session: Session
    ):
        await _register(client, creator)
        _add_videos(db_session, _author_id(db_session, "creator"), 3)

        response = await client.get("/api/v1/users/creator/videos", params={"page": 9})

        assert response.json()["videos"] == []
        assert response.json()["pages"] == 1

    async def test_an_empty_channel_still_has_one_page(
        self, creator: dict[str, str], client: AsyncClient
    ):
        await _register(client, creator)

        response = await client.get("/api/v1/users/creator/videos")

        assert response.json() == {
            "videos": [], "page": 1, "page_size": 20, "total": 0, "pages": 1,
        }

    async def test_visitors_do_not_see_private_or_unlisted_videos(
        self,
        creator: dict[str, str],
        client: AsyncClient,
        db_session: Session,
        auth_headers: dict[str, str],
    ):
        await _register(client, creator)
        author_id = _author_id(db_session, "creator")
        _add_videos(db_session, author_id, 1)
        _add_videos(db_session, author_id, 1, visibility="private", prefix="privado")
        _add_videos(db_session, author_id, 1, visibility="unlisted", prefix="oculto")

        response = await client.get("/api/v1/users/creator/videos", headers=auth_headers)

        body = response.json()
        assert [video["title"] for video in body["videos"]] == ["video 1"]
        assert body["total"] == 1

    async def test_the_owner_sees_the_whole_channel(
        self, creator: dict[str, str], client: AsyncClient, db_session: Session
    ):
        headers = await _register(client, creator)
        author_id = _author_id(db_session, "creator")
        _add_videos(db_session, author_id, 1)
        _add_videos(db_session, author_id, 1, visibility="private", prefix="privado")

        response = await client.get("/api/v1/users/creator/videos", headers=headers)

        body = response.json()
        assert body["total"] == 2
        assert {video["visibility"] for video in body["videos"]} == {"public", "private"}

    async def test_rejects_an_invalid_page(self, creator: dict[str, str], client: AsyncClient):
        await _register(client, creator)

        response = await client.get("/api/v1/users/creator/videos", params={"page": 0})

        assert response.status_code == 422

    async def test_rejects_an_oversized_page(self, creator: dict[str, str], client: AsyncClient):
        await _register(client, creator)

        response = await client.get("/api/v1/users/creator/videos", params={"page_size": 500})

        assert response.status_code == 422

    async def test_unknown_channel_returns_404(self, client: AsyncClient):
        response = await client.get("/api/v1/users/fantasma/videos")
        assert response.status_code == 404
