import asyncio
import logging
from pathlib import Path

logger = logging.getLogger("video_processor")

ALLOWED_VIDEO_EXTENSIONS = frozenset({
    ".mp4", ".mov", ".avi", ".mkv", ".webm", ".flv", ".wmv", ".m4v", ".3gp", ".ogv",
})


def is_valid_video_extension(filename: str) -> bool:
    return Path(filename).suffix.lower() in ALLOWED_VIDEO_EXTENSIONS


async def transcode_to_mp4(input_path: str, output_path: str) -> None:
    cmd = [
        "ffmpeg",
        "-i", input_path,
        "-c:v", "libx264",
        "-profile:v", "baseline",
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
