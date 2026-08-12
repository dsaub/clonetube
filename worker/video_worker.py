import json
import logging
import signal
import threading
import time
from types import FrameType
from typing import Any

import boto3
from botocore.exceptions import BotoCoreError, ClientError
from opentelemetry.trace import SpanKind, Status, StatusCode
from pydantic import ValidationError

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")
logger = logging.getLogger("video-worker")

# Initialize logging before telemetry so exporter diagnostics are visible.
from telemetry import extract_context, get_tracer, init_telemetry

init_telemetry("clonetube-video-worker")
tracer = get_tracer("video-worker")

from clients import instrumented_sqs
from models import VideoTranscodeMessage
from transcoder import VideoTranscoder
from video_settings import settings

running = True

sqs_client = instrumented_sqs(settings.video_queue_url, settings.aws_region)
s3_client = boto3.client(
    "s3",
    region_name=settings.aws_region,
    endpoint_url=settings.s3_endpoint_url,
)
transcoder = VideoTranscoder(
    s3_client,
    settings.aws_bucket_name,
    settings.transcode_timeout_seconds,
    settings.max_video_bytes,
)


def stop_worker(signum: int, frame: FrameType | None) -> None:
    global running
    logger.info("Señal %s recibida; no se aceptarán más trabajos", signum)
    running = False


class VisibilityHeartbeat:
    def __init__(self, receipt_handle: str):
        self.receipt_handle = receipt_handle
        self.stopped = threading.Event()
        self.thread = threading.Thread(target=self._run, daemon=True)

    def __enter__(self):
        self.thread.start()
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        self.stopped.set()
        self.thread.join(timeout=2)

    def _run(self) -> None:
        while not self.stopped.wait(settings.visibility_heartbeat_seconds):
            try:
                sqs_client.change_message_visibility(
                    ReceiptHandle=self.receipt_handle,
                    VisibilityTimeout=settings.visibility_timeout,
                )
            except Exception:
                logger.exception("No se pudo ampliar la visibilidad del trabajo activo")


def parse_message(raw_body: str) -> VideoTranscodeMessage:
    return VideoTranscodeMessage.model_validate(json.loads(raw_body))


def process_sqs_message(sqs_message: dict[str, Any]) -> None:
    receipt_handle = sqs_message["ReceiptHandle"]
    parent_ctx = extract_context(sqs_message)

    with tracer.start_as_current_span(
        "process-video-transcode",
        kind=SpanKind.CONSUMER,
        attributes={
            "messaging.system": "aws-sqs",
            "messaging.message.id": sqs_message.get("MessageId", "unknown"),
        },
        context=parent_ctx,
    ) as span:
        try:
            job = parse_message(sqs_message["Body"])
        except (json.JSONDecodeError, ValidationError) as error:
            logger.error("Trabajo inválido message_id=%s error=%s", sqs_message.get("MessageId"), error)
            span.set_status(Status(StatusCode.ERROR, "Invalid message body"))
            return

        span.set_attributes({
            "video.job_id": job.id,
            "video.id": job.video_id,
            "video.source_key": job.source_key,
            "video.original_filename": job.original_filename,
        })
        logger.info("Procesando transcodificación id=%s video_id=%s key=%s", job.id, job.video_id, job.source_key)
        try:
            with VisibilityHeartbeat(receipt_handle):
                variants = transcoder.process(job)
        except Exception as exc:
            logger.exception("Falló la transcodificación id=%s; SQS reintentará el trabajo", job.id)
            span.set_status(Status(StatusCode.ERROR, "Transcode failed"))
            span.record_exception(exc)
            return

        sqs_client.delete_message(ReceiptHandle=receipt_handle)
        span.set_attributes({"video.variants": json.dumps(variants)})
        span.set_status(Status(StatusCode.OK))
        logger.info("Trabajo completado id=%s variants=%s", job.id, variants)


def poll_messages() -> list[dict[str, Any]]:
    response = sqs_client.receive_message(
        MaxNumberOfMessages=1,
        WaitTimeSeconds=settings.wait_time_seconds,
        VisibilityTimeout=settings.visibility_timeout,
        MessageSystemAttributeNames=["ApproximateReceiveCount", "SentTimestamp"],
    )
    return response.get("Messages", [])


def main() -> None:
    signal.signal(signal.SIGTERM, stop_worker)
    signal.signal(signal.SIGINT, stop_worker)
    logger.info("Worker de vídeo iniciado queue=%s bucket=%s", settings.video_queue_url, settings.aws_bucket_name)
    try:
        while running:
            try:
                for message in poll_messages():
                    process_sqs_message(message)
            except (BotoCoreError, ClientError):
                logger.exception("Error comunicándose con AWS")
                time.sleep(5)
            except Exception:
                logger.exception("Error inesperado en el worker")
                time.sleep(5)
    finally:
        logger.info("Worker de vídeo detenido, enviando telemetría pendiente…")
        try:
            from opentelemetry import trace as otel_trace
            otel_trace.get_tracer_provider().force_flush(10_000)
        except Exception:
            pass


if __name__ == "__main__":
    main()
