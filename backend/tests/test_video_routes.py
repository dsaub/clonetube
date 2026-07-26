from unittest.mock import MagicMock

from sqlmodel import select

from models import MultipartUpload, User, Video
from routes.video import _can_view


def test_video_visibility_access_matrix():
    creator = User(username="creator", password_hash="x", full_name="Creator", email="c@example.com")
    invited = User(username="invited", password_hash="x", full_name="Invited", email="i@example.com")
    stranger = User(username="stranger", password_hash="x", full_name="Stranger", email="s@example.com")
    video = Video(filename="videos/a.mp4", author=creator.id, video_name="A", video_desc="")

    assert _can_view(video, None)
    video.visibility = "unlisted"
    assert _can_view(video, None)
    video.visibility = "private"
    video.allowed_users = '["invited"]'
    assert _can_view(video, creator)
    assert _can_view(video, invited)
    assert not _can_view(video, stranger)
    assert not _can_view(video, None)


async def _start(client, mock_s3, auth_headers, filename="video.mp4"):
    mock_s3["s3_client"].create_multipart_upload.return_value = {
        "UploadId": "upload-123", "Key": "ignored-by-route"
    }
    return await client.post("/api/v1/video/start-multipart",
                             params={"original_filename": filename}, headers=auth_headers)


class TestProtectedUploadFlow:
    async def test_start_requires_authentication(self, client, mock_s3):
        response = await client.post("/api/v1/video/start-multipart",
                                     params={"original_filename": "video.mp4"})
        assert response.status_code == 401

    async def test_start_persists_owner(self, client, mock_s3, auth_headers, db_session):
        response = await _start(client, mock_s3, auth_headers)
        assert response.status_code == 200
        rows = db_session.exec(select(MultipartUpload)).all()
        assert len(rows) == 1
        assert rows[0].key == response.json()["key"]

    async def test_rejects_invalid_extension(self, client, mock_s3, auth_headers):
        response = await _start(client, mock_s3, auth_headers, "image.jpg")
        assert response.status_code == 400

    async def test_sign_requires_authentication(self, client, mock_s3):
        response = await client.get("/api/v1/video/sign-chunk", params={
            "filename": "videos/a.mp4", "upload_id": "u", "chunk_number": 1})
        assert response.status_code == 401

    async def test_owner_can_sign_chunk(self, client, mock_s3, auth_headers):
        started = await _start(client, mock_s3, auth_headers)
        mock_s3["s3_client_public"].generate_presigned_url.return_value = "https://signed"
        response = await client.get("/api/v1/video/sign-chunk", params={
            "filename": started.json()["key"], "upload_id": "upload-123", "chunk_number": 1},
            headers=auth_headers)
        assert response.status_code == 200
        assert response.json()["url"] == "https://signed"

    async def test_complete_requires_owner(self, client, mock_s3, auth_headers):
        response = await client.post("/api/v1/video/complete-multipart", json={
            "filename": "videos/unknown.mp4", "uploadId": "unknown",
            "parts": [{"PartNumber": 1, "ETag": "etag"}]}, headers=auth_headers)
        assert response.status_code == 404


class TestUploadChunk:
    """El fragmento viaja por la API para no exigir un almacenamiento público."""

    async def test_requires_authentication(self, client, mock_s3):
        response = await client.put("/api/v1/video/upload-chunk", params={
            "filename": "videos/a.mp4", "upload_id": "u", "chunk_number": 1},
            content=b"data")
        assert response.status_code == 401

    async def test_rejects_upload_of_another_user(self, client, mock_s3, auth_headers):
        response = await client.put("/api/v1/video/upload-chunk", params={
            "filename": "videos/unknown.mp4", "upload_id": "unknown", "chunk_number": 1},
            content=b"data", headers=auth_headers)
        assert response.status_code == 404

    async def test_forwards_chunk_and_returns_etag(self, client, mock_s3, auth_headers):
        started = await _start(client, mock_s3, auth_headers)
        key = started.json()["key"]
        mock_s3["s3_client"].upload_part.return_value = {"ETag": '"abc123"'}

        response = await client.put("/api/v1/video/upload-chunk", params={
            "filename": key, "upload_id": "upload-123", "chunk_number": 2},
            content=b"chunk-bytes", headers=auth_headers)

        assert response.status_code == 200
        assert response.json() == {"PartNumber": 2, "ETag": '"abc123"'}
        _, kwargs = mock_s3["s3_client"].upload_part.call_args
        assert kwargs["Key"] == key
        assert kwargs["UploadId"] == "upload-123"
        assert kwargs["PartNumber"] == 2
        assert kwargs["Body"] == b"chunk-bytes"

    async def test_rejects_empty_chunk(self, client, mock_s3, auth_headers):
        started = await _start(client, mock_s3, auth_headers)
        response = await client.put("/api/v1/video/upload-chunk", params={
            "filename": started.json()["key"], "upload_id": "upload-123", "chunk_number": 1},
            content=b"", headers=auth_headers)
        assert response.status_code == 400

    async def test_rejects_oversized_chunk(self, client, mock_s3, auth_headers, monkeypatch):
        import routes.video as video_routes

        monkeypatch.setattr(video_routes, "MAX_CHUNK_BYTES", 4)
        started = await _start(client, mock_s3, auth_headers)
        response = await client.put("/api/v1/video/upload-chunk", params={
            "filename": started.json()["key"], "upload_id": "upload-123", "chunk_number": 1},
            content=b"demasiados bytes", headers=auth_headers)
        assert response.status_code == 413
        mock_s3["s3_client"].upload_part.assert_not_called()


class TestPublicVideoReads:
    async def test_list_remains_public(self, client, mock_s3):
        paginator = mock_s3["s3_client"].get_paginator.return_value
        paginator.paginate.return_value = []
        response = await client.get("/api/v1/video/list")
        assert response.status_code == 200
        assert response.json() == {"videos": []}

    async def test_stream_url_remains_public(self, client, mock_s3, db_session):
        _add_video(db_session)
        response = await client.get("/api/v1/video/stream-url", params={"key": "videos/a.mp4"})
        assert response.status_code == 200
        assert response.json()["url"] == "/api/v1/video/stream?key=videos%2Fa.mp4"


class TestStreaming:
    """La reproducción pasa por la API: el almacenamiento no se expone al navegador."""

    async def test_public_url_carries_no_token(self, client, mock_s3, db_session, auth_headers):
        _add_video(db_session)
        response = await client.get("/api/v1/video/stream-url",
                                    params={"key": "videos/a.mp4"}, headers=auth_headers)
        assert response.status_code == 200
        assert "token" not in response.json()["url"]

    async def test_private_url_carries_the_token(
        self, client, mock_s3, db_session, registered_user, auth_headers
    ):
        owner = db_session.exec(select(User).where(User.username == "testuser")).one()
        _add_video(db_session, author=owner.id, visibility="private")
        response = await client.get("/api/v1/video/stream-url",
                                    params={"key": "videos/a.mp4"}, headers=auth_headers)
        assert response.status_code == 200
        assert f"token={registered_user['access_token']}" in response.json()["url"]

    async def test_streams_whole_video(self, client, mock_s3, db_session):
        _add_video(db_session)
        _mock_object(mock_s3, b"video-bytes")

        response = await client.get("/api/v1/video/stream", params={"key": "videos/a.mp4"})

        assert response.status_code == 200
        assert response.content == b"video-bytes"
        assert response.headers["accept-ranges"] == "bytes"
        assert "Range" not in mock_s3["s3_client"].get_object.call_args.kwargs

    async def test_forwards_range_requests(self, client, mock_s3, db_session):
        _add_video(db_session)
        _mock_object(mock_s3, b"deo-b", content_range="bytes 2-6/11")

        response = await client.get("/api/v1/video/stream", params={"key": "videos/a.mp4"},
                                    headers={"Range": "bytes=2-6"})

        assert response.status_code == 206
        assert response.headers["content-range"] == "bytes 2-6/11"
        assert mock_s3["s3_client"].get_object.call_args.kwargs["Range"] == "bytes=2-6"

    async def test_private_video_needs_the_query_token(
        self, client, mock_s3, db_session, registered_user
    ):
        owner = db_session.exec(select(User).where(User.username == "testuser")).one()
        _add_video(db_session, author=owner.id, visibility="private")
        _mock_object(mock_s3, b"secreto")

        anonymous = await client.get("/api/v1/video/stream", params={"key": "videos/a.mp4"})
        assert anonymous.status_code == 404

        authorized = await client.get("/api/v1/video/stream", params={
            "key": "videos/a.mp4", "token": registered_user["access_token"]})
        assert authorized.status_code == 200
        assert authorized.content == b"secreto"

    async def test_missing_object_is_not_found(self, client, mock_s3, db_session):
        _add_video(db_session)
        mock_s3["s3_client"].get_object.side_effect = Exception("NoSuchKey")
        response = await client.get("/api/v1/video/stream", params={"key": "videos/a.mp4"})
        assert response.status_code == 404


def _add_video(db_session, author=None, visibility="public") -> Video:
    if author is None:
        user = User(username="author", password_hash="x", full_name="Author", email="a@example.com")
        db_session.add(user)
        author = user.id
    video = Video(filename="videos/a.mp4", author=author, video_name="A", video_desc="",
                  visibility=visibility)
    db_session.add(video)
    db_session.commit()
    return video


def _mock_object(mock_s3, payload: bytes, content_range: str | None = None) -> None:
    body = MagicMock()
    body.iter_chunks.return_value = iter([payload])
    obj = {"Body": body, "ContentLength": len(payload), "ContentType": "video/mp4"}
    if content_range:
        obj["ContentRange"] = content_range
    mock_s3["s3_client"].get_object.return_value = obj
