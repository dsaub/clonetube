import unittest
from pathlib import Path

from models import VideoTranscodeMessage
from transcoder import UnsupportedVideoError, VideoProbe, ffmpeg_command, variant_key, variants_for


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
        with self.assertRaises(UnsupportedVideoError):
            variants_for(VideoProbe(854, 480, 31.0, 30.0, "h264"))

    def test_4k_above_30fps_is_rejected(self):
        with self.assertRaises(UnsupportedVideoError):
            variants_for(VideoProbe(3840, 2160, 60.0, 30.0, "h264"))

    def test_resolution_above_4k_is_rejected(self):
        with self.assertRaises(UnsupportedVideoError):
            variants_for(VideoProbe(4096, 2160, 30.0, 30.0, "h264"))

    def test_variant_key_and_command_are_deterministic(self):
        source = VideoProbe(1920, 1080, 60.0, 30.0, "h264")
        profile = variants_for(source)[0]
        self.assertEqual("videos/abc/720p.mp4", variant_key("videos/abc.mp4", profile))
        command = ffmpeg_command(Path("in"), Path("out"), source, profile)
        self.assertIn("libx264", command)
        self.assertIn("scale=-2:720,fps=60", command)


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
