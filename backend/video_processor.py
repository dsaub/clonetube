import asyncio
import logging
from pathlib import Path

logger = logging.getLogger("video_processor")

ALLOWED_VIDEO_EXTENSIONS = frozenset({
    ".mp4", ".mov", ".avi", ".mkv", ".webm", ".flv", ".wmv", ".m4v", ".3gp", ".ogv",
})

_VIDEO_ENCODER_PROFILES = {
    "libx264": "baseline",
    "libopenh264": "constrained_baseline",
}


def is_valid_video_extension(filename: str) -> bool:
    return Path(filename).suffix.lower() in ALLOWED_VIDEO_EXTENSIONS


def _select_video_encoder(ffmpeg_output: str) -> tuple[str, str]:
    """Elige un encoder H.264 por software disponible en la instalación local."""
    available_encoders = {
        fields[1]
        for line in ffmpeg_output.splitlines()
        if len(fields := line.split()) >= 2
    }
    for encoder, profile in _VIDEO_ENCODER_PROFILES.items():
        if encoder in available_encoders:
            return encoder, profile
    raise RuntimeError(
        "FFmpeg does not provide a supported H.264 encoder "
        "(expected libx264 or libopenh264)"
    )


async def _get_video_encoder() -> tuple[str, str]:
    process = await asyncio.create_subprocess_exec(
        "ffmpeg",
        "-hide_banner",
        "-encoders",
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )
    stdout, stderr = await process.communicate()
    if process.returncode != 0:
        detail = stderr.decode(errors="replace").strip()
        raise RuntimeError(f"Unable to inspect FFmpeg encoders: {detail}")
    return _select_video_encoder(stdout.decode(errors="replace"))


async def transcode_to_mp4(input_path: str, output_path: str) -> None:
    video_encoder, profile = await _get_video_encoder()
    cmd = [
        "ffmpeg",
        "-i", input_path,
        "-c:v", video_encoder,
        "-profile:v", profile,
        "-level", "3.0",
        "-pix_fmt", "yuv420p",
        "-c:a", "aac",
        "-b:a", "128k",
        "-movflags", "+faststart",
        "-y",
        output_path,
    ]
    logger.info("Transcoding: %s -> %s", input_path, output_path)

    process = await asyncio.create_subprocess_exec(
        *cmd,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )
    stdout, stderr = await process.communicate()

    if process.returncode != 0:
        raise RuntimeError(f"FFmpeg transcoding failed: {stderr.decode()}")

    logger.info("Transcoding complete: %s", output_path)
