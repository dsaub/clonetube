import tempfile
import unittest
from pathlib import Path

from botocore.exceptions import ClientError

from models import VideoTranscodeMessage
from transcoder import (
    TRANSCODE_VERSION,
    HLS_PLAYLIST_CONTENT_TYPE,
    HLS_SEGMENT_CONTENT_TYPE,
    PROFILES,
    MasterVariant,
    UnsupportedVideoError,
    VideoProbe,
    VideoTranscoder,
    build_master_playlist,
    compute_bandwidth,
    hls_dir_key,
    hls_ffmpeg_command,
    master_key,
    variant_key,
    variants_for,
)


def job(source_key: str = "videos/abc.mp4") -> VideoTranscodeMessage:
    return VideoTranscodeMessage(
        type="video.transcode",
        version=1,
        id="job-1",
        video_id=1,
        source_key=source_key,
        source_etag="etag-1",
        original_filename="abc.mp4",
    )


class FakeS3:
    def __init__(self, objects=None):
        self.objects = objects or {}
        self.uploads = []

    def head_object(self, **kwargs):
        key = kwargs["Key"]
        if key not in self.objects:
            raise ClientError(
                {
                    "Error": {"Code": "404", "Message": "Not Found"},
                    "ResponseMetadata": {"HTTPStatusCode": 404},
                },
                "HeadObject",
            )
        return self.objects[key]

    def upload_file(self, filename, bucket, key, ExtraArgs=None):
        self.uploads.append((bucket, key, ExtraArgs, Path(filename)))

    def put_object(self, **kwargs):
        self.uploads.append((kwargs["Bucket"], kwargs["Key"], kwargs))


class TranscodeProfilesTest(unittest.TestCase):
    def test_1080p60_generates_requested_lower_variants(self):
        source = VideoProbe(1920, 1080, 60.0, 30.0, "h264")
        self.assertEqual(["720p", "480p", "360p", "120p"], [p.name for p in variants_for(source)])

    def test_480p_generates_360p_and_120p(self):
        source = VideoProbe(854, 480, 30.0, 30.0, "h264")
        self.assertEqual(["360p", "120p"], [p.name for p in variants_for(source)])

    def test_4k30_generates_the_complete_lower_ladder(self):
        source = VideoProbe(3840, 2160, 30.0, 30.0, "hevc")
        self.assertEqual(
            ["1080p", "720p", "480p", "360p", "120p"],
            [p.name for p in variants_for(source)],
        )

    def test_720p60_is_accepted(self):
        source = VideoProbe(1280, 720, 60.0, 30.0, "h264")
        self.assertEqual(["480p", "360p", "120p"], [p.name for p in variants_for(source)])

    def test_480p_above_30fps_is_rejected(self):
        source = VideoProbe(854, 480, 31.0, 30.0, "h264")
        with self.assertRaises(UnsupportedVideoError):
            variants_for(source)

    def test_4k_above_30fps_is_rejected(self):
        source = VideoProbe(3840, 2160, 60.0, 30.0, "h264")
        with self.assertRaises(UnsupportedVideoError):
            variants_for(source)

    def test_resolution_above_4k_is_rejected(self):
        source = VideoProbe(4096, 2160, 30.0, 30.0, "h264")
        with self.assertRaises(UnsupportedVideoError):
            variants_for(source)


class HlsKeyLayoutTest(unittest.TestCase):
    def test_hls_keys_follow_tree_layout(self):
        source = VideoProbe(1920, 1080, 60.0, 30.0, "h264")
        profile = variants_for(source)[0]
        self.assertEqual("videos/abc/hls/720p", hls_dir_key("videos/abc.mp4", profile))
        self.assertEqual("videos/abc/hls/720p/playlist.m3u8", variant_key("videos/abc.mp4", profile))
        self.assertEqual("videos/abc/hls/master.m3u8", master_key("videos/abc.mp4"))

    def test_hls_ffmpeg_command_is_deterministic(self):
        source = VideoProbe(1920, 1080, 60.0, 30.0, "h264")
        profile = variants_for(source)[0]
        command = hls_ffmpeg_command(Path("in"), Path("hls/720p"), source, profile)
        self.assertIn("libx264", command)
        self.assertIn("scale=-2:720,fps=60", command)
        self.assertEqual("hls", command[command.index("-f") + 1])
        self.assertEqual("4", command[command.index("-hls_time") + 1])
        self.assertEqual("vod", command[command.index("-hls_playlist_type") + 1])
        self.assertIn("hls/720p/seg_%05d.ts", command)
        self.assertTrue(command[-1].endswith("playlist.m3u8"))


class MasterPlaylistTest(unittest.TestCase):
    def test_build_master_playlist_orders_variants_descending(self):
        master = build_master_playlist([
            MasterVariant("1080p", 4_000_000, 1920, 1080),
            MasterVariant("720p", 2_000_000, 1280, 720),
        ])
        self.assertEqual(
            "#EXTM3U\n#EXT-X-VERSION:3\n"
            "#EXT-X-STREAM-INF:BANDWIDTH=4000000,RESOLUTION=1920x1080\n1080p/playlist.m3u8\n"
            "#EXT-X-STREAM-INF:BANDWIDTH=2000000,RESOLUTION=1280x720\n720p/playlist.m3u8\n",
            master,
        )

    def test_compute_bandwidth_uses_real_segment_bytes(self):
        self.assertEqual(2_000_000, compute_bandwidth([500_000, 500_000], 4.0))
        self.assertEqual(0, compute_bandwidth([100], 0.0))


class HlsIdempotencyTest(unittest.TestCase):
    def test_master_present_with_matching_etag_is_complete(self):
        fake = FakeS3({
            "videos/abc/hls/master.m3u8": {
                "Metadata": {"source-etag": "etag-1", "clonetube-profile": TRANSCODE_VERSION},
            },
        })
        transcoder = VideoTranscoder(fake, "clonetube", 60, 10**9)
        self.assertTrue(transcoder._is_complete("videos/abc/hls/master.m3u8", "etag-1"))

    def test_missing_master_is_not_complete(self):
        transcoder = VideoTranscoder(FakeS3(), "clonetube", 60, 10**9)
        self.assertFalse(transcoder._is_complete("videos/abc/hls/master.m3u8", "etag-1"))

    def test_stale_etag_is_not_complete(self):
        fake = FakeS3({
            "videos/abc/hls/master.m3u8": {
                "Metadata": {"source-etag": "etag-1", "clonetube-profile": TRANSCODE_VERSION},
            },
        })
        transcoder = VideoTranscoder(fake, "clonetube", 60, 10**9)
        self.assertFalse(transcoder._is_complete("videos/abc/hls/master.m3u8", "etag-2"))

    def test_wrong_profile_version_is_not_complete(self):
        fake = FakeS3({
            "videos/abc/hls/master.m3u8": {
                "Metadata": {"source-etag": "etag-1", "clonetube-profile": "h264-mp4-v1"},
            },
        })
        transcoder = VideoTranscoder(fake, "clonetube", 60, 10**9)
        self.assertFalse(transcoder._is_complete("videos/abc/hls/master.m3u8", "etag-1"))


class HlsUploadTest(unittest.TestCase):
    def test_upload_hls_tree_sets_correct_content_types(self):
        profile = PROFILES[1]
        with tempfile.TemporaryDirectory() as temp_dir:
            local_dir = Path(temp_dir) / "hls" / profile.name
            local_dir.mkdir(parents=True)
            (local_dir / "playlist.m3u8").write_text("#EXTM3U\n")
            (local_dir / "seg_00000.ts").write_bytes(b"\x47" * 188)
            (local_dir / "seg_00001.ts").write_bytes(b"\x47" * 188)

            fake = FakeS3()
            transcoder = VideoTranscoder(fake, "clonetube", 60, 10**9)
            uploaded = transcoder.upload_hls_tree(
                local_dir, "videos/abc/hls/720p", "etag-1", profile, job()
            )

            self.assertEqual(
                [
                    "videos/abc/hls/720p/playlist.m3u8",
                    "videos/abc/hls/720p/seg_00000.ts",
                    "videos/abc/hls/720p/seg_00001.ts",
                ],
                uploaded,
            )
            by_key = {entry[1]: entry for entry in fake.uploads}
            self.assertEqual(
                HLS_PLAYLIST_CONTENT_TYPE,
                by_key["videos/abc/hls/720p/playlist.m3u8"][2]["ContentType"],
            )
            self.assertEqual(
                HLS_SEGMENT_CONTENT_TYPE,
                by_key["videos/abc/hls/720p/seg_00000.ts"][2]["ContentType"],
            )
            metadata = by_key["videos/abc/hls/720p/seg_00000.ts"][2]["Metadata"]
            self.assertEqual("etag-1", metadata["source-etag"])
            self.assertEqual("hls-ts-v1-720p", metadata["clonetube-profile"])
            self.assertEqual("job-1", metadata["transcode-job-id"])


class VideoMessageTest(unittest.TestCase):
    def test_message_rejects_keys_outside_video_prefix(self):
        with self.assertRaises(ValueError):
            VideoTranscodeMessage.model_validate({
                "type": "video.transcode",
                "version": 1,
                "id": "job-1",
                "video_id": 1,
                "source_key": "../secret",
                "source_etag": "etag",
                "original_filename": "video.mp4",
            })


if __name__ == "__main__":
    unittest.main()
