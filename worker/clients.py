from typing import Any

import boto3
from opentelemetry.trace import SpanKind

from constants import (
    OTEL_AWS_SQS_SYSTEM,
    OTEL_MESSAGING_DESTINATION,
    OTEL_MESSAGING_OPERATION,
    OTEL_MESSAGING_SYSTEM,
)
from telemetry import get_tracer

tracer = get_tracer("sqs-client")

# ── Lazy init: solo se instancia si las funciones standalone lo necesitan ──
_raw_sqs: Any = None
_settings: Any = None


def _get_raw_sqs():
    global _raw_sqs
    if _raw_sqs is None:
        from settings import settings as _s

        _raw_sqs = boto3.client("sqs", region_name=_s.aws_region)
    return _raw_sqs


def _get_email_settings():
    global _settings
    if _settings is None:
        from settings import settings as _s

        _settings = _s
    return _settings


# ── Funciones standalone (usadas solo por el worker de correo) ──


def sqs_receive_message(queue_url: str | None = None, **kwargs: Any) -> dict[str, Any]:
    """Instrumented SQS receive_message."""
    url = queue_url or kwargs.pop("QueueUrl", _get_email_settings().queue_url)
    with tracer.start_as_current_span(
        "sqs-receive",
        kind=SpanKind.CLIENT,
        attributes={
            OTEL_MESSAGING_SYSTEM: OTEL_AWS_SQS_SYSTEM,
            OTEL_MESSAGING_OPERATION: "receive",
            OTEL_MESSAGING_DESTINATION: url,
        },
    ):
        return _get_raw_sqs().receive_message(QueueUrl=url, **kwargs)


def sqs_delete_message(queue_url: str | None = None, **kwargs: Any) -> dict[str, Any]:
    """Instrumented SQS delete_message."""
    url = queue_url or kwargs.pop("QueueUrl", _get_email_settings().queue_url)
    with tracer.start_as_current_span(
        "sqs-delete",
        kind=SpanKind.CLIENT,
        attributes={
            OTEL_MESSAGING_SYSTEM: OTEL_AWS_SQS_SYSTEM,
            OTEL_MESSAGING_OPERATION: "delete",
            OTEL_MESSAGING_DESTINATION: url,
        },
    ):
        return _get_raw_sqs().delete_message(QueueUrl=url, **kwargs)


def sqs_change_message_visibility(queue_url: str | None = None, **kwargs: Any) -> dict[str, Any]:
    """Instrumented SQS change_message_visibility (heartbeat)."""
    url = queue_url or kwargs.pop("QueueUrl", _get_email_settings().queue_url)
    with tracer.start_as_current_span(
        "sqs-change-visibility",
        kind=SpanKind.CLIENT,
        attributes={
            OTEL_MESSAGING_SYSTEM: OTEL_AWS_SQS_SYSTEM,
            OTEL_MESSAGING_OPERATION: "change-visibility",
            OTEL_MESSAGING_DESTINATION: url,
        },
    ):
        return _get_raw_sqs().change_message_visibility(QueueUrl=url, **kwargs)


def instrumented_sqs(queue_url: str, region_name: str | None = None) -> Any:
    """Return an object offering instrumented wrappers for common SQS operations.

    Usage::

        sqs = instrumented_sqs(settings.video_queue_url)
        sqs.receive_message(...)              # instrumented
        sqs.delete_message(...)               # instrumented
        sqs.change_message_visibility(...)    # instrumented
        sqs.raw.<any_other_method>(...)       # raw, no instrumentation
    """
    region = region_name or __import__("os").getenv("AWS_REGION", "eu-west-3")
    _raw = boto3.client("sqs", region_name=region)

    class _Instrumented:
        raw = _raw

        @staticmethod
        def receive_message(**kw: Any) -> dict[str, Any]:
            with tracer.start_as_current_span(
                "sqs-receive",
                kind=SpanKind.CLIENT,
                attributes={
                    OTEL_MESSAGING_SYSTEM: OTEL_AWS_SQS_SYSTEM,
                    OTEL_MESSAGING_OPERATION: "receive",
                    OTEL_MESSAGING_DESTINATION: queue_url,
                },
            ):
                return _raw.receive_message(QueueUrl=queue_url, **kw)

        @staticmethod
        def delete_message(**kw: Any) -> dict[str, Any]:
            with tracer.start_as_current_span(
                "sqs-delete",
                kind=SpanKind.CLIENT,
                attributes={
                    OTEL_MESSAGING_SYSTEM: OTEL_AWS_SQS_SYSTEM,
                    OTEL_MESSAGING_OPERATION: "delete",
                    OTEL_MESSAGING_DESTINATION: queue_url,
                },
            ):
                return _raw.delete_message(QueueUrl=queue_url, **kw)

        @staticmethod
        def change_message_visibility(**kw: Any) -> dict[str, Any]:
            with tracer.start_as_current_span(
                "sqs-change-visibility",
                kind=SpanKind.CLIENT,
                attributes={
                    OTEL_MESSAGING_SYSTEM: OTEL_AWS_SQS_SYSTEM,
                    OTEL_MESSAGING_OPERATION: "change-visibility",
                    OTEL_MESSAGING_DESTINATION: queue_url,
                },
            ):
                return _raw.change_message_visibility(QueueUrl=queue_url, **kw)

    return _Instrumented()
