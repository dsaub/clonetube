from sqlmodel import select

from models import MultipartUpload


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


class TestPublicVideoReads:
    async def test_list_remains_public(self, client, mock_s3):
        paginator = mock_s3["s3_client"].get_paginator.return_value
        paginator.paginate.return_value = []
        response = await client.get("/api/v1/video/list")
        assert response.status_code == 200
        assert response.json() == {"videos": []}

    async def test_stream_url_remains_public(self, client, mock_s3):
        mock_s3["s3_client_public"].generate_presigned_url.return_value = "https://stream"
        response = await client.get("/api/v1/video/stream-url", params={"key": "videos/a.mp4"})
        assert response.status_code == 200
