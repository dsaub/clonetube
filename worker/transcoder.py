import json
import logging
import subprocess
import tempfile
from dataclasses import dataclass
from fractions import Fraction
from pathlib import Path

from botocore.exceptions import ClientError
from opentelemetry.trace import Status, StatusCode

from models import VideoTranscodeMessage
from telemetry import get_tracer

tracer = get_tracer("transcoder")

logger = logging.getLogger("video-worker.transcoder")

TRANSCODE_VERSION = "hls-ts-v1"

HLS_PLAYLIST_CONTENT_TYPE = "application/vnd.apple.mpegurl"
HLS_SEGMENT_CONTENT_TYPE = "video/mp2t"


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


@dataclass(frozen=True)
class MasterVariant:
    name: str
    bandwidth: int
    width: int
    height: int


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


def _stem(source_key: str) -> str:
    return source_key.rsplit(".", 1)[0]


def hls_dir_key(source_key: str, profile: TranscodeProfile) -> str:
    return f"{_stem(source_key)}/hls/{profile.name}"


def variant_key(source_key: str, profile: TranscodeProfile) -> str:
    return f"{hls_dir_key(source_key, profile)}/playlist.m3u8"


def master_key(source_key: str) -> str:
    return f"{_stem(source_key)}/hls/master.m3u8"


def hls_ffmpeg_command(
    input_path: Path,
    output_dir: Path,
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
        "-f", "hls", "-hls_time", "4", "-hls_playlist_type", "vod",
        "-hls_segment_filename", str(output_dir / "seg_%05d.ts"),
        str(output_dir / "playlist.m3u8"),
    ]


def compute_bandwidth(segment_sizes: list[int], duration: float) -> int:
    if duration <= 0:
        return 0
    return int(8 * sum(segment_sizes) / duration)


def build_master_playlist(variants: list[MasterVariant]) -> str:
    lines = ["#EXTM3U", "#EXT-X-VERSION:3"]
    for variant in variants:
        lines.append(
            f"#EXT-X-STREAM-INF:BANDWIDTH={variant.bandwidth},"
            f"RESOLUTION={variant.width}x{variant.height}"
        )
        lines.append(f"{variant.name}/playlist.m3u8")
    return "\n".join(lines) + "\n"


def run_command(command: list[str], timeout: int) -> str:
    result = subprocess.run(command, capture_output=True, text=True, timeout=timeout, check=False)
    if result.returncode != 0:
        details = (result.stderr or result.stdout)[-8000:]
        raise RuntimeError(f"{command[0]} terminó con código {result.returncode}: {details}")
    return result.stdout


def _probe(path: Path, timeout: int, extra_args: list[str]) -> VideoProbe:
    output = run_command([
        "ffprobe", "-v", "error", *extra_args, "-select_streams", "v:0",
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


def probe_video(path: Path, timeout: int) -> VideoProbe:
    return _probe(path, timeout, [])


def probe_playlist(path: Path, timeout: int) -> VideoProbe:
    return _probe(path, timeout, ["-allowed_extensions", "ALL"])


class VideoTranscoder:
    def __init__(self, s3_client, bucket: str, timeout: int, max_video_bytes: int):
        self.s3 = s3_client
        self.bucket = bucket
        self.timeout = timeout
        self.max_video_bytes = max_video_bytes

    def process(self, job: VideoTranscodeMessage) -> list[str]:
        with tracer.start_as_current_span(
            "transcode-video",
            attributes={
                "video.job_id": job.id,
                "video.id": job.video_id,
                "video.source_key": job.source_key,
            },
        ) as span:
            with tracer.start_as_current_span("s3-head-source"):
                source_head = self.s3.head_object(Bucket=self.bucket, Key=job.source_key)
            source_etag = str(source_head.get("ETag", "")).strip('"')
            expected_etag = (job.source_etag or "").strip('"')
            if expected_etag and expected_etag != source_etag:
                span.set_status(Status(StatusCode.ERROR, "Source ETag mismatch"))
                raise UnsupportedVideoError("El objeto fuente ya no coincide con el trabajo SQS")
            if int(source_head.get("ContentLength", 0)) > self.max_video_bytes:
                span.set_status(Status(StatusCode.ERROR, "Video exceeds max size"))
                raise UnsupportedVideoError("El vídeo supera el tamaño máximo configurado")

            span.set_attributes({
                "video.source_etag": source_etag,
                "video.source_size_bytes": source_head.get("ContentLength", 0),
            })

            master = master_key(job.source_key)
            if self._is_complete(master, source_etag):
                span.set_attributes({"video.completed_variants": json.dumps([master])})
                span.set_status(Status(StatusCode.OK))
                logger.info("Trabajo ya completado, se omite key=%s", master)
                return [master]

            uploaded: list[str] = []
            with tempfile.TemporaryDirectory(prefix="clonetube-video-") as temp_dir:
                input_path = Path(temp_dir) / "source"

                with tracer.start_as_current_span("s3-download-source"):
                    self.s3.download_file(self.bucket, job.source_key, str(input_path))

                with tracer.start_as_current_span("ffprobe-source"):
                    source = probe_video(input_path, self.timeout)

                profiles = variants_for(source)
                span.set_attributes({
                    "video.source_width": source.width,
                    "video.source_height": source.height,
                    "video.source_fps": source.fps,
                    "video.source_duration_s": source.duration,
                    "video.source_codec": source.codec,
                    "video.variant_count": len(profiles),
                })
                logger.info(
                    "Fuente validada key=%s resolution=%sx%s fps=%.3f variants=%s",
                    job.source_key, source.width, source.height, source.fps,
                    [profile.name for profile in profiles],
                )

                master_variants: list[MasterVariant] = []
                for profile in profiles:
                    output_dir = Path(temp_dir) / f"hls-{profile.name}"
                    output_dir.mkdir()

                    with tracer.start_as_current_span(
                        "ffmpeg-transcode",
                        attributes={
                            "video.variant_profile": profile.name,
                            "video.variant_height": profile.height,
                        },
                    ):
                        run_command(hls_ffmpeg_command(input_path, output_dir, source, profile), self.timeout)

                    with tracer.start_as_current_span("ffprobe-variant"):
                        output_probe = probe_playlist(output_dir / "playlist.m3u8", self.timeout)
                    if output_probe.codec != "h264" or output_probe.resolution > profile.height + 2:
                        raise RuntimeError(f"La variante {profile.name} no superó la validación de salida")

                    segments = sorted(output_dir.glob("seg_*.ts"))
                    master_variants.append(MasterVariant(
                        name=profile.name,
                        bandwidth=compute_bandwidth(
                            [segment.stat().st_size for segment in segments],
                            output_probe.duration,
                        ),
                        width=output_probe.width,
                        height=output_probe.height,
                    ))

                    with tracer.start_as_current_span(
                        "s3-upload-hls-tree",
                        attributes={"video.variant_profile": profile.name},
                    ):
                        uploaded.extend(
                            self.upload_hls_tree(
                                output_dir,
                                hls_dir_key(job.source_key, profile),
                                source_etag,
                                profile,
                                job,
                            )
                        )
                    logger.info("Rendición HLS subida key=%s", hls_dir_key(job.source_key, profile))

            with tracer.start_as_current_span("s3-upload-master"):
                self.s3.put_object(
                    Bucket=self.bucket,
                    Key=master,
                    Body=build_master_playlist(master_variants).encode("utf-8"),
                    ContentType=HLS_PLAYLIST_CONTENT_TYPE,
                    Metadata={
                        "clonetube-profile": TRANSCODE_VERSION,
                        "source-etag": source_etag,
                        "transcode-job-id": job.id,
                    },
                )
            uploaded.append(master)
            span.set_attributes({"video.completed_variants": json.dumps(uploaded)})
            span.set_status(Status(StatusCode.OK))
            return uploaded

    def upload_hls_tree(
        self,
        local_dir: Path,
        s3_dir_key: str,
        source_etag: str,
        profile: TranscodeProfile,
        job: VideoTranscodeMessage,
    ) -> list[str]:
        uploaded: list[str] = []
        for file in sorted(local_dir.iterdir()):
            if file.suffix == ".m3u8":
                content_type = HLS_PLAYLIST_CONTENT_TYPE
            elif file.suffix == ".ts":
                content_type = HLS_SEGMENT_CONTENT_TYPE
            else:
                content_type = "application/octet-stream"
            key = f"{s3_dir_key}/{file.name}"
            with tracer.start_as_current_span(
                "s3-upload-hls-file",
                attributes={"video.key": key},
            ):
                self.s3.upload_file(
                    str(file), self.bucket, key,
                    ExtraArgs={
                        "ContentType": content_type,
                        "Metadata": {
                            "clonetube-profile": f"{TRANSCODE_VERSION}-{profile.name}",
                            "source-etag": source_etag,
                            "transcode-job-id": job.id,
                        },
                    },
                )
            uploaded.append(key)
        return uploaded

    def _is_complete(self, key: str, source_etag: str) -> bool:
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
            and metadata.get("clonetube-profile") == TRANSCODE_VERSION
        )
