"""Pruebas de los endpoints de seguidores (`/api/v1/users`)."""

from typing import Any

from httpx import AsyncClient

SECOND_USER = {
    "username": "creator",
    "password": "Str0ng!Pass",
    "full_name": "Creadora",
    "email": "creator@example.com",
}


async def _register(client: AsyncClient, payload: dict[str, str]) -> dict[str, str]:
    response = await client.post("/api/v1/auth/register", json=payload)
    assert response.status_code == 201
    return {"Authorization": f"Bearer {response.json()['access_token']}"}


class TestFollowUser:
    async def test_follow_requires_authentication(self, client: AsyncClient):
        await _register(client, SECOND_USER)
        response = await client.post("/api/v1/users/creator/follow")
        assert response.status_code == 401

    async def test_follow_marks_the_relationship(
        self, client: AsyncClient, auth_headers: dict[str, str]
    ):
        await _register(client, SECOND_USER)

        response = await client.post("/api/v1/users/creator/follow", headers=auth_headers)

        assert response.status_code == 200
        assert response.json() == {"username": "creator", "following": True, "followers": 1}

    async def test_following_twice_is_idempotent(
        self, client: AsyncClient, auth_headers: dict[str, str]
    ):
        await _register(client, SECOND_USER)
        await client.post("/api/v1/users/creator/follow", headers=auth_headers)

        response = await client.post("/api/v1/users/creator/follow", headers=auth_headers)

        assert response.status_code == 200
        assert response.json()["followers"] == 1

    async def test_cannot_follow_yourself(
        self, client: AsyncClient, registered_user: dict[str, Any], auth_headers: dict[str, str]
    ):
        response = await client.post("/api/v1/users/testuser/follow", headers=auth_headers)
        assert response.status_code == 400

    async def test_unknown_user_returns_404(
        self, client: AsyncClient, auth_headers: dict[str, str]
    ):
        response = await client.post("/api/v1/users/fantasma/follow", headers=auth_headers)
        assert response.status_code == 404


class TestUnfollowUser:
    async def test_unfollow_clears_the_relationship(
        self, client: AsyncClient, auth_headers: dict[str, str]
    ):
        await _register(client, SECOND_USER)
        await client.post("/api/v1/users/creator/follow", headers=auth_headers)

        response = await client.request(
            "DELETE", "/api/v1/users/creator/follow", headers=auth_headers
        )

        assert response.status_code == 200
        assert response.json() == {"username": "creator", "following": False, "followers": 0}

    async def test_unfollowing_someone_you_do_not_follow_is_idempotent(
        self, client: AsyncClient, auth_headers: dict[str, str]
    ):
        await _register(client, SECOND_USER)

        response = await client.request(
            "DELETE", "/api/v1/users/creator/follow", headers=auth_headers
        )

        assert response.status_code == 200
        assert response.json()["following"] is False


class TestFollowState:
    async def test_anonymous_visitors_see_the_follower_count(
        self, client: AsyncClient, auth_headers: dict[str, str]
    ):
        await _register(client, SECOND_USER)
        await client.post("/api/v1/users/creator/follow", headers=auth_headers)

        response = await client.get("/api/v1/users/creator/follow")

        assert response.status_code == 200
        assert response.json() == {"username": "creator", "following": False, "followers": 1}

    async def test_state_reflects_the_authenticated_viewer(
        self, client: AsyncClient, auth_headers: dict[str, str]
    ):
        await _register(client, SECOND_USER)
        await client.post("/api/v1/users/creator/follow", headers=auth_headers)

        response = await client.get("/api/v1/users/creator/follow", headers=auth_headers)

        assert response.json()["following"] is True


class TestFollowingList:
    async def test_lists_followed_users_alphabetically(
        self, client: AsyncClient, auth_headers: dict[str, str]
    ):
        await _register(client, SECOND_USER)
        await _register(client, {**SECOND_USER, "username": "artista", "email": "a@example.com"})
        await client.post("/api/v1/users/creator/follow", headers=auth_headers)
        await client.post("/api/v1/users/artista/follow", headers=auth_headers)

        response = await client.get("/api/v1/users/me/following", headers=auth_headers)

        assert response.status_code == 200
        assert [user["username"] for user in response.json()["users"]] == ["artista", "creator"]

    async def test_requires_authentication(self, client: AsyncClient):
        response = await client.get("/api/v1/users/me/following")
        assert response.status_code == 401

    async def test_is_empty_for_a_new_account(
        self, client: AsyncClient, auth_headers: dict[str, str]
    ):
        response = await client.get("/api/v1/users/me/following", headers=auth_headers)
        assert response.json() == {"users": []}
