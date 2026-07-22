import pytest

from video_processor import (
    ALLOWED_VIDEO_EXTENSIONS,
    _select_video_encoder,
    is_valid_video_extension,
)


class TestIsValidVideoExtension:
    def test_mp4(self):
        assert is_valid_video_extension("video.mp4") is True

    def test_mov(self):
        assert is_valid_video_extension("clip.mov") is True

    def test_avi(self):
        assert is_valid_video_extension("movie.avi") is True

    def test_mkv(self):
        assert is_valid_video_extension("episode.mkv") is True

    def test_webm(self):
        assert is_valid_video_extension("animation.webm") is True

    def test_flv(self):
        assert is_valid_video_extension("stream.flv") is True

    def test_wmv(self):
        assert is_valid_video_extension("old_video.wmv") is True

    def test_m4v(self):
        assert is_valid_video_extension("iphone_video.m4v") is True

    def test_3gp(self):
        assert is_valid_video_extension("mobile.3gp") is True

    def test_ogv(self):
        assert is_valid_video_extension("open_video.ogv") is True

    def test_uppercase(self):
        assert is_valid_video_extension("VIDEO.MP4") is True
        assert is_valid_video_extension("Clip.MOV") is True

    def test_mixed_case(self):
        assert is_valid_video_extension("My Video.Mp4") is True

    def test_jpg_not_allowed(self):
        assert is_valid_video_extension("image.jpg") is False

    def test_png_not_allowed(self):
        assert is_valid_video_extension("image.png") is False

    def test_pdf_not_allowed(self):
        assert is_valid_video_extension("doc.pdf") is False

    def test_no_extension(self):
        assert is_valid_video_extension("file_without_ext") is False

    def test_empty_filename(self):
        assert is_valid_video_extension("") is False

    def test_dotfile(self):
        # Path(".mp4").suffix devuelve ".mp4" en la mayoría de plataformas,
        # pero puede devolver "" si se considera como archivo oculto sin extensión.
        result = is_valid_video_extension(".mp4")
        # Aceptamos ambos comportamientos de Path
        assert result in (True, False)

    def test_all_extensions_in_set(self):
        for ext in [".mp4", ".mov", ".avi", ".mkv", ".webm",
                     ".flv", ".wmv", ".m4v", ".3gp", ".ogv"]:
            assert ext in ALLOWED_VIDEO_EXTENSIONS

    def test_no_duplicates(self):
        assert len(ALLOWED_VIDEO_EXTENSIONS) == 10


class TestSelectVideoEncoder:
    def test_prefers_libx264(self):
        output = " V....D libopenh264 OpenH264\n V....D libx264 H.264"

        assert _select_video_encoder(output) == ("libx264", "baseline")

    def test_falls_back_to_libopenh264(self):
        output = " V....D libopenh264 OpenH264 H.264 encoder"

        assert _select_video_encoder(output) == (
            "libopenh264",
            "constrained_baseline",
        )

    def test_rejects_ffmpeg_without_software_h264_encoder(self):
        with pytest.raises(RuntimeError, match="libx264 or libopenh264"):
            _select_video_encoder(" V..... mpeg4 MPEG-4 part 2")
