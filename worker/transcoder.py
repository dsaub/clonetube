import json
import logging
import subprocess
import tempfile
from dataclasses import dataclass
from fractions import Fraction
from pathlib import Path

from botocore.exceptions import ClientError

from models import VideoTranscodeMessage

logger = logging.getLogger("video-worker.transcoder")

TRANSCODE_VERSION = "h264-mp4-v1"


class UnsupportedVideoError(ValueError):
    pass


@dataclass(frozen=True)
class VideoProbe:
    width: int
    height: int
    fps: float
    duration: float
    codec: str

    @property
    def resolution(self) -> int:
        return min(self.width, self.height)


@dataclass(frozen=True)
class TranscodeProfile:
    height: int
    max_fps: int
    crf: int
    audio_bitrate: str
    level: str

    @property
    def name(self) -> str:
        return f"{self.height}p"


PROFILES = (
    TranscodeProfile(1080, 60, 20, "192k", "4.2"),
    TranscodeProfile(720, 60, 21, "160k", "4.1"),
    TranscodeProfile(480, 30, 22, "128k", "3.1"),
    TranscodeProfile(360, 30, 23, "96k", "3.0"),
    TranscodeProfile(120, 30, 28, "64k", "3.0"),
)


def parse_frame_rate(value: str | None) -> float:
    if not value or value == "0/0":
        return 0.0
    try:
        return float(Fraction(value))
    except (ValueError, ZeroDivisionError):
        return 0.0


def validate_source(probe: VideoProbe) -> None:
    long_side = max(probe.width, probe.height)
    resolution = probe.resolution
    if long_side > 3840 or resolution > 2160:
        raise UnsupportedVideoError("La resolución máxima admitida es 4K (3840x2160)")

    if resolution > 1080:
        max_fps = 30
    elif resolution > 480:
        max_fps = 60
    else:
        max_fps = 30
    if probe.fps > max_fps + 0.1:
        raise UnsupportedVideoError(
            f"{resolution}p admite como máximo {max_fps} fps; recibido {probe.fps:.2f} fps"
        )


def variants_for(probe: VideoProbe) -> tuple[TranscodeProfile, ...]:
    validate_source(probe)
    return tuple(profile for profile in PROFILES if profile.height < probe.resolution)


def variant_key(source_key: str, profile: TranscodeProfile) -> str:
    stem = source_key.rsplit(".", 1)[0]
    return f"{stem}/{profile.name}.mp4"


def ffmpeg_command(
    input_path: Path,
    output_path: Path,
    source: VideoProbe,
    profile: TranscodeProfile,
) -> list[str]:
    output_fps = min(source.fps, profile.max_fps) if source.fps > 0 else profile.max_fps
    scale = f"scale=-2:{profile.height}" if source.width >= source.height else f"scale={profile.height}:-2"
    return [
        "ffmpeg", "-hide_banner", "-loglevel", "error", "-nostdin", "-y", "-i", str(input_path),
        "-map", "0:v:0", "-map", "0:a:0?", "-sn", "-dn",
        "-vf", f"{scale},fps={output_fps:.6g}",
        "-c:v", "libx264", "-preset", "medium", "-crf", str(profile.crf),
        "-profile:v", "high", "-level:v", profile.level, "-pix_fmt", "yuv420p",
        "-c:a", "aac", "-b:a", profile.audio_bitrate,
        "-movflags", "+faststart", str(output_path),
    ]


def run_command(command: list[str], timeout: int) -> str:
    result = subprocess.run(command, capture_output=True, text=True, timeout=timeout, check=False)
    if result.returncode != 0:
        details = (result.stderr or result.stdout)[-8000:]
        raise RuntimeError(f"{command[0]} terminó con código {result.returncode}: {details}")
    return result.stdout


def probe_video(path: Path, timeout: int) -> VideoProbe:
    output = run_command([
        "ffprobe", "-v", "error", "-select_streams", "v:0",
        "-show_entries", "stream=codec_name,width,height,avg_frame_rate,r_frame_rate:format=duration",
        "-of", "json", str(path),
    ], timeout)
    payload = json.loads(output)
    streams = payload.get("streams", [])
    if not streams:
        raise UnsupportedVideoError("El archivo no contiene una pista de vídeo")
    stream = streams[0]
    fps = parse_frame_rate(stream.get("avg_frame_rate")) or parse_frame_rate(stream.get("r_frame_rate"))
    return VideoProbe(
        width=int(stream["width"]),
        height=int(stream["height"]),
        fps=fps,
        duration=float(payload.get("format", {}).get("duration") or 0),
        codec=str(stream.get("codec_name") or ""),
    )


class VideoTranscoder:
    def __init__(self, s3_client, bucket: str, timeout: int, max_video_bytes: int):
        self.s3 = s3_client
        self.bucket = bucket
        self.timeout = timeout
        self.max_video_bytes = max_video_bytes

    def process(self, job: VideoTranscodeMessage) -> list[str]:
        source_head = self.s3.head_object(Bucket=self.bucket, Key=job.source_key)
        source_etag = str(source_head.get("ETag", "")).strip('"')
        expected_etag = (job.source_etag or "").strip('"')
        if expected_etag and expected_etag != source_etag:
            raise UnsupportedVideoError("El objeto fuente ya no coincide con el trabajo SQS")
        if int(source_head.get("ContentLength", 0)) > self.max_video_bytes:
            raise UnsupportedVideoError("El vídeo supera el tamaño máximo configurado")

        completed: list[str] = []
        with tempfile.TemporaryDirectory(prefix="clonetube-video-") as temp_dir:
            input_path = Path(temp_dir) / "source"
            output_path = Path(temp_dir) / "variant.mp4"
            self.s3.download_file(self.bucket, job.source_key, str(input_path))
            source = probe_video(input_path, self.timeout)
            profiles = variants_for(source)
            logger.info(
                "Fuente validada key=%s resolution=%sx%s fps=%.3f variants=%s",
                job.source_key, source.width, source.height, source.fps,
                [profile.name for profile in profiles],
            )

            for profile in profiles:
                key = variant_key(job.source_key, profile)
                if self._is_complete(key, source_etag, profile):
                    logger.info("Variante ya existente, se omite key=%s", key)
                    completed.append(key)
                    continue

                output_path.unlink(missing_ok=True)
                run_command(ffmpeg_command(input_path, output_path, source, profile), self.timeout)
                output_probe = probe_video(output_path, self.timeout)
                if output_probe.codec != "h264" or output_probe.resolution > profile.height + 2:
                    raise RuntimeError(f"La variante {profile.name} no superó la validación de salida")

                self.s3.upload_file(
                    str(output_path), self.bucket, key,
                    ExtraArgs={
                        "ContentType": "video/mp4",
                        "Metadata": {
                            "clonetube-profile": f"{TRANSCODE_VERSION}-{profile.name}",
                            "source-etag": source_etag,
                            "transcode-job-id": job.id,
                        },
                    },
                )
                completed.append(key)
                logger.info("Variante subida key=%s", key)
        return completed

    def _is_complete(self, key: str, source_etag: str, profile: TranscodeProfile) -> bool:
        try:
            head = self.s3.head_object(Bucket=self.bucket, Key=key)
        except ClientError as error:
            status = error.response.get("ResponseMetadata", {}).get("HTTPStatusCode")
            if status in (403, 404):
                return False
            raise
        metadata = head.get("Metadata", {})
        return (
            metadata.get("source-etag") == source_etag
            and metadata.get("clonetube-profile") == f"{TRANSCODE_VERSION}-{profile.name}"
        )
