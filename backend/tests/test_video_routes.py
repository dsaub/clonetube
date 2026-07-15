import uuid
from unittest.mock import AsyncMock, MagicMock, patch

import pytest


class TestStartMultipart:
    async def test_start_multipart_success(self, client, mock_s3):
        fake_upload_id = "upload-id-12345"
        fake_key = "videos/abc123.mp4"
        mock_s3["s3_client"].create_multipart_upload.return_value = {
            "UploadId": fake_upload_id,
            "Key": fake_key,
        }

        resp = await client.post(
            "/api/v1/video/start-multipart",
            params={"original_filename": "my_video.mp4"},
        )
        assert resp.status_code == 200
        data = resp.json()
        assert data["uploadId"] == fake_upload_id
        assert "videos/" in data["key"]
        assert data["original_filename"] == "my_video.mp4"

    async def test_start_multipart_invalid_extension(self, client, mock_s3):
        resp = await client.post(
            "/api/v1/video/start-multipart",
            params={"original_filename": "image.jpg"},
        )
        assert resp.status_code == 400
        assert "no permitido" in resp.json()["detail"]

    async def test_start_multipart_no_extension(self, client, mock_s3):
        resp = await client.post(
            "/api/v1/video/start-multipart",
            params={"original_filename": "file_without_ext"},
        )
        assert resp.status_code == 400

    async def test_start_multipart_s3_error(self, client, mock_s3):
        mock_s3["s3_client"].create_multipart_upload.side_effect = Exception("S3 connection error")
        resp = await client.post(
            "/api/v1/video/start-multipart",
            params={"original_filename": "video.mp4"},
        )
        assert resp.status_code == 500

    async def test_start_multipart_missing_param(self, client, mock_s3):
        resp = await client.post("/api/v1/video/start-multipart")
        assert resp.status_code == 422

    async def test_start_multipart_generates_uuid_key(self, client, mock_s3):
        mock_s3["s3_client"].create_multipart_upload.return_value = {
            "UploadId": "uid-1",
            "Key": "videos/somekey.mp4",
        }
        resp = await client.post(
            "/api/v1/video/start-multipart",
            params={"original_filename": "video.mov"},
        )
        assert resp.status_code == 200
        key = resp.json()["key"]
        assert key.startswith("videos/")
        assert key.endswith(".mp4")

    async def test_start_multipart_allowed_extensions(self, client, mock_s3):
        mock_s3["s3_client"].create_multipart_upload.return_value = {
            "UploadId": "uid-1",
            "Key": "videos/key.mp4",
        }
        for ext in ["mp4", "mov", "avi", "mkv", "webm"]:
            resp = await client.post(
                "/api/v1/video/start-multipart",
                params={"original_filename": f"video.{ext}"},
            )
            assert resp.status_code == 200, f"Extension .{ext} should be allowed"


class TestSignChunk:
    async def test_sign_chunk_success(self, client, mock_s3):
        fake_url = "https://s3.example.com/signed-chunk"
        mock_s3["s3_client_public"].generate_presigned_url.return_value = fake_url

        resp = await client.get(
            "/api/v1/video/sign-chunk",
            params={
                "filename": "videos/abc123.mp4",
                "upload_id": "upload-123",
                "chunk_number": 1,
            },
        )
        assert resp.status_code == 200
        assert resp.json()["url"] == fake_url

        mock_s3["s3_client_public"].generate_presigned_url.assert_called_with(
            ClientMethod="upload_part",
            Params={
                "Bucket": "clonetube",
                "Key": "videos/abc123.mp4",
                "UploadId": "upload-123",
                "PartNumber": 1,
            },
            ExpiresIn=3600,
        )

    async def test_sign_chunk_s3_error(self, client, mock_s3):
        mock_s3["s3_client_public"].generate_presigned_url.side_effect = Exception("S3 error")
        resp = await client.get(
            "/api/v1/video/sign-chunk",
            params={
                "filename": "videos/abc.mp4",
                "upload_id": "uid-1",
                "chunk_number": 1,
            },
        )
        assert resp.status_code == 500

    async def test_sign_chunk_invalid_chunk_number(self, client, mock_s3):
        resp = await client.get(
            "/api/v1/video/sign-chunk",
            params={
                "filename": "videos/abc.mp4",
                "upload_id": "uid-1",
                "chunk_number": 0,
            },
        )
        assert resp.status_code == 422

    async def test_sign_chunk_missing_params(self, client, mock_s3):
        resp = await client.get("/api/v1/video/sign-chunk")
        assert resp.status_code == 422


class TestCompleteMultipart:
    async def test_complete_multipart_success(self, client, mock_s3):
        mock_s3["s3_client"].complete_multipart_upload.return_value = {
            "Location": "http://minio:9000/clonetube/videos/key.mp4",
        }

        with patch("routes.video._filename_registry", {"videos/key.mp4": "original.mp4"}):
            resp = await client.post(
                "/api/v1/video/complete-multipart",
                json={
                    "filename": "videos/key.mp4",
                    "uploadId": "upload-123",
                    "parts": [
                        {"PartNumber": 1, "ETag": '"etag1"'},
                        {"PartNumber": 2, "ETag": '"etag2"'},
                    ],
                },
            )
        assert resp.status_code == 200
        data = resp.json()
        assert data["status"] == "success"
        assert data["key"] == "videos/key.mp4"
        assert data["original_filename"] == "original.mp4"

    async def test_complete_multipart_s3_error(self, client, mock_s3):
        mock_s3["s3_client"].complete_multipart_upload.side_effect = Exception("S3 error")
        resp = await client.post(
            "/api/v1/video/complete-multipart",
            json={
                "filename": "videos/key.mp4",
                "uploadId": "upload-123",
                "parts": [{"PartNumber": 1, "ETag": '"etag1"'}],
            },
        )
        assert resp.status_code == 500

    async def test_complete_multipart_empty_parts(self, client, mock_s3):
        mock_s3["s3_client"].complete_multipart_upload.return_value = {"Location": ""}
        resp = await client.post(
            "/api/v1/video/complete-multipart",
            json={
                "filename": "videos/key.mp4",
                "uploadId": "upload-123",
                "parts": [],
            },
        )
        assert resp.status_code == 200

    async def test_complete_multipart_missing_fields(self, client, mock_s3):
        resp = await client.post(
            "/api/v1/video/complete-multipart",
            json={"filename": "videos/key.mp4"},
        )
        assert resp.status_code == 422

    async def test_complete_multipart_unknown_filename(self, client, mock_s3):
        mock_s3["s3_client"].complete_multipart_upload.return_value = {"Location": ""}
        resp = await client.post(
            "/api/v1/video/complete-multipart",
            json={
                "filename": "videos/unknown.mp4",
                "uploadId": "upload-123",
                "parts": [{"PartNumber": 1, "ETag": '"e1"'}],
            },
        )
        assert resp.status_code == 200
        assert resp.json()["original_filename"] == "desconocido"


class TestListVideos:
    async def test_list_videos_empty(self, client, mock_s3):
        paginator_mock = MagicMock()
        paginator_mock.paginate.return_value = []
        mock_s3["s3_client"].get_paginator.return_value = paginator_mock

        resp = await client.get("/api/v1/video/list")
        assert resp.status_code == 200
        assert resp.json()["videos"] == []

    async def test_list_videos_with_items(self, client, mock_s3):
        from datetime import datetime, timezone

        page = {
            "Contents": [
                {"Key": "videos/video1.mp4", "Size": 1024, "LastModified": datetime(2026, 1, 1, tzinfo=timezone.utc)},
                {"Key": "videos/video2.mp4", "Size": 2048, "LastModified": datetime(2026, 6, 15, tzinfo=timezone.utc)},
            ]
        }
        paginator_mock = MagicMock()
        paginator_mock.paginate.return_value = [page]
        mock_s3["s3_client"].get_paginator.return_value = paginator_mock

        def head_side_effect(Bucket, Key):
            head = MagicMock()
            if Key == "videos/video1.mp4":
                head.get.return_value = {"original-filename": "my_video.mp4"}
            else:
                head.get.return_value = {}
            return head
        mock_s3["s3_client"].head_object.side_effect = head_side_effect

        resp = await client.get("/api/v1/video/list")
        assert resp.status_code == 200
        data = resp.json()
        assert len(data["videos"]) == 2
        assert data["videos"][0]["original_filename"] == "my_video.mp4"
        assert data["videos"][1]["original_filename"] == "video2.mp4"

    async def test_list_videos_excludes_prefixes(self, client, mock_s3):
        from datetime import datetime, timezone

        page = {
            "Contents": [
                {"Key": "videos/", "Size": 0, "LastModified": datetime(2026, 1, 1, tzinfo=timezone.utc)},
                {"Key": "videos/real.mp4", "Size": 512, "LastModified": datetime(2026, 1, 1, tzinfo=timezone.utc)},
            ]
        }
        paginator_mock = MagicMock()
        paginator_mock.paginate.return_value = [page]
        mock_s3["s3_client"].get_paginator.return_value = paginator_mock
        head = MagicMock()
        head.get.return_value = {}
        mock_s3["s3_client"].head_object.return_value = head

        resp = await client.get("/api/v1/video/list")
        assert resp.status_code == 200
        assert len(resp.json()["videos"]) == 1

    async def test_list_videos_s3_error(self, client, mock_s3):
        mock_s3["s3_client"].get_paginator.side_effect = Exception("S3 error")
        resp = await client.get("/api/v1/video/list")
        assert resp.status_code == 500


class TestStreamUrl:
    async def test_stream_url_success(self, client, mock_s3):
        fake_url = "https://s3.example.com/stream"
        mock_s3["s3_client_public"].generate_presigned_url.return_value = fake_url

        resp = await client.get(
            "/api/v1/video/stream-url",
            params={"key": "videos/abc123.mp4"},
        )
        assert resp.status_code == 200
        data = resp.json()
        assert data["url"] == fake_url
        assert data["key"] == "videos/abc123.mp4"

        mock_s3["s3_client_public"].generate_presigned_url.assert_called_with(
            ClientMethod="get_object",
            Params={
                "Bucket": "clonetube",
                "Key": "videos/abc123.mp4",
            },
            ExpiresIn=86400,
        )

    async def test_stream_url_s3_error(self, client, mock_s3):
        mock_s3["s3_client_public"].generate_presigned_url.side_effect = Exception("S3 error")
        resp = await client.get(
            "/api/v1/video/stream-url",
            params={"key": "videos/abc.mp4"},
        )
        assert resp.status_code == 500

    async def test_stream_url_missing_key(self, client, mock_s3):
        resp = await client.get("/api/v1/video/stream-url")
        assert resp.status_code == 422
