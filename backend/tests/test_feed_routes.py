"""Pruebas del endpoint `/api/v1/video/feed`."""

import uuid
from datetime import UTC, datetime, timedelta

from httpx import AsyncClient
from sqlmodel import Session

from models import User, UserLikesVideo, Video

NOW = datetime.now(UTC)


def _create_author(session: Session, username: str) -> User:
    author = User(
        username=username,
        password_hash="x",
        password_version=0,
        full_name=username.capitalize(),
        email=f"{username}@example.com",
    )
    session.add(author)
    session.commit()
    return author


def _create_video(
    session: Session,
    author: User,
    *,
    title: str,
    hours_ago: float = 0.0,
    visibility: str = "public",
    likes: int = 0,
) -> Video:
    video = Video(
        filename=f"videos/{uuid.uuid4().hex}.mp4",
        author=author.id,
        video_name=title,
        video_desc="",
        created_at=NOW - timedelta(hours=hours_ago),
        visibility=visibility,
    )
    session.add(video)
    session.commit()
    for _ in range(likes):
        session.add(UserLikesVideo(video_id=video.id, user_id=uuid.uuid4()))
    session.commit()
    return video


async def _follow(client: AsyncClient, username: str, headers: dict[str, str]) -> None:
    response = await client.post(f"/api/v1/users/{username}/follow", headers=headers)
    assert response.status_code == 200


class TestAnonymousFeed:
    async def test_returns_public_videos_sorted_by_recency(
        self, client: AsyncClient, db_session: Session
    ):
        author = _create_author(db_session, "autora")
        _create_video(db_session, author, title="Antiguo", hours_ago=200)
        _create_video(db_session, author, title="Nuevo", hours_ago=1)

        response = await client.get("/api/v1/video/feed")

        assert response.status_code == 200
        body = response.json()
        assert [video["title"] for video in body["videos"]] == ["Nuevo", "Antiguo"]
        assert body["personalized"] is False
        assert body["following_count"] == 0

    async def test_hides_non_public_videos(self, client: AsyncClient, db_session: Session):
        author = _create_author(db_session, "autora")
        _create_video(db_session, author, title="Privado", visibility="private")
        _create_video(db_session, author, title="Oculto", visibility="unlisted")
        _create_video(db_session, author, title="Visible")

        response = await client.get("/api/v1/video/feed")

        assert [video["title"] for video in response.json()["videos"]] == ["Visible"]

    async def test_empty_catalog_returns_empty_feed(self, client: AsyncClient):
        response = await client.get("/api/v1/video/feed")
        assert response.json() == {"videos": [], "following_count": 0, "personalized": False}


class TestPersonalizedFeed:
    async def test_followed_authors_are_prioritized_over_newer_videos(
        self, client: AsyncClient, db_session: Session, auth_headers: dict[str, str]
    ):
        followed = _create_author(db_session, "seguida")
        stranger = _create_author(db_session, "desconocida")
        _create_video(db_session, followed, title="De quien sigo", hours_ago=240)
        _create_video(db_session, stranger, title="De un desconocido", hours_ago=1)
        await _follow(client, "seguida", auth_headers)

        response = await client.get("/api/v1/video/feed", headers=auth_headers)

        body = response.json()
        assert [video["title"] for video in body["videos"]] == [
            "De quien sigo",
            "De un desconocido",
        ]
        assert body["videos"][0]["from_followed_author"] is True
        assert body["videos"][1]["from_followed_author"] is False
        assert body["personalized"] is True
        assert body["following_count"] == 1

    async def test_unfollowing_restores_the_chronological_order(
        self, client: AsyncClient, db_session: Session, auth_headers: dict[str, str]
    ):
        followed = _create_author(db_session, "seguida")
        stranger = _create_author(db_session, "desconocida")
        _create_video(db_session, followed, title="De quien sigo", hours_ago=240)
        _create_video(db_session, stranger, title="De un desconocido", hours_ago=1)
        await _follow(client, "seguida", auth_headers)
        await client.request("DELETE", "/api/v1/users/seguida/follow", headers=auth_headers)

        response = await client.get("/api/v1/video/feed", headers=auth_headers)

        assert [video["title"] for video in response.json()["videos"]] == [
            "De un desconocido",
            "De quien sigo",
        ]

    async def test_only_following_filters_out_the_rest(
        self, client: AsyncClient, db_session: Session, auth_headers: dict[str, str]
    ):
        followed = _create_author(db_session, "seguida")
        stranger = _create_author(db_session, "desconocida")
        _create_video(db_session, followed, title="De quien sigo")
        _create_video(db_session, stranger, title="De un desconocido")
        await _follow(client, "seguida", auth_headers)

        response = await client.get(
            "/api/v1/video/feed", params={"only_following": True}, headers=auth_headers
        )

        assert [video["title"] for video in response.json()["videos"]] == ["De quien sigo"]

    async def test_only_following_without_follows_returns_nothing(
        self, client: AsyncClient, db_session: Session, auth_headers: dict[str, str]
    ):
        author = _create_author(db_session, "autora")
        _create_video(db_session, author, title="Cualquiera")

        response = await client.get(
            "/api/v1/video/feed", params={"only_following": True}, headers=auth_headers
        )

        assert response.json()["videos"] == []

    async def test_likes_are_reported_and_break_ties(
        self, client: AsyncClient, db_session: Session
    ):
        author = _create_author(db_session, "autora")
        _create_video(db_session, author, title="Sin likes", hours_ago=1)
        _create_video(db_session, author, title="Con likes", hours_ago=1, likes=25)

        videos = (await client.get("/api/v1/video/feed")).json()["videos"]

        assert videos[0]["title"] == "Con likes"
        assert videos[0]["likes"] == 25
        assert videos[1]["likes"] == 0

    async def test_limit_caps_the_number_of_videos(
        self, client: AsyncClient, db_session: Session
    ):
        author = _create_author(db_session, "autora")
        for index in range(5):
            _create_video(db_session, author, title=f"Video {index}", hours_ago=index)

        response = await client.get("/api/v1/video/feed", params={"limit": 2})

        assert len(response.json()["videos"]) == 2

    async def test_invalid_limit_is_rejected(self, client: AsyncClient):
        response = await client.get("/api/v1/video/feed", params={"limit": 0})
        assert response.status_code == 422

    async def test_items_expose_the_author_and_the_score(
        self, client: AsyncClient, db_session: Session, auth_headers: dict[str, str]
    ):
        author = _create_author(db_session, "seguida")
        video = _create_video(db_session, author, title="Con metadatos")
        await _follow(client, "seguida", auth_headers)

        item = (await client.get("/api/v1/video/feed", headers=auth_headers)).json()["videos"][0]

        assert item["filename"] == video.filename
        assert item["author_username"] == "seguida"
        assert item["author_name"] == "Seguida"
        assert item["score"] > 0
        assert item["created_at"].startswith(str(NOW.year))
