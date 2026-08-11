from typing import Any

import boto3
from opentelemetry.trace import SpanKind

from settings import settings
from telemetry import get_tracer

tracer = get_tracer("sqs-client")

_raw_sqs = boto3.client("sqs", region_name=settings.aws_region)


def sqs_receive_message(queue_url: str | None = None, **kwargs: Any) -> dict[str, Any]:
    """Instrumented SQS receive_message."""
    url = queue_url or kwargs.pop("QueueUrl", settings.queue_url)
    with tracer.start_as_current_span(
        "sqs-receive",
        kind=SpanKind.CLIENT,
        attributes={
            "messaging.system": "aws-sqs",
            "messaging.operation": "receive",
            "messaging.destination": url,
        },
    ):
        return _raw_sqs.receive_message(QueueUrl=url, **kwargs)


def sqs_delete_message(queue_url: str | None = None, **kwargs: Any) -> dict[str, Any]:
    """Instrumented SQS delete_message."""
    url = queue_url or kwargs.pop("QueueUrl", settings.queue_url)
    with tracer.start_as_current_span(
        "sqs-delete",
        kind=SpanKind.CLIENT,
        attributes={
            "messaging.system": "aws-sqs",
            "messaging.operation": "delete",
            "messaging.destination": url,
        },
    ):
        return _raw_sqs.delete_message(QueueUrl=url, **kwargs)


def sqs_change_message_visibility(queue_url: str | None = None, **kwargs: Any) -> dict[str, Any]:
    """Instrumented SQS change_message_visibility (heartbeat)."""
    url = queue_url or kwargs.pop("QueueUrl", settings.queue_url)
    with tracer.start_as_current_span(
        "sqs-change-visibility",
        kind=SpanKind.CLIENT,
        attributes={
            "messaging.system": "aws-sqs",
            "messaging.operation": "change-visibility",
            "messaging.destination": url,
        },
    ):
        return _raw_sqs.change_message_visibility(QueueUrl=url, **kwargs)


# Backwards-compatible raw client for advanced usage
sqs_client = _raw_sqs


def instrumented_sqs(queue_url: str, region_name: str | None = None) -> Any:
    """Return an object offering instrumented wrappers for common SQS operations.

    Usage::

        sqs = instrumented_sqs(settings.video_queue_url)
        sqs.receive_message(...)              # instrumented
        sqs.delete_message(...)               # instrumented
        sqs.change_message_visibility(...)    # instrumented
        sqs.raw.<any_other_method>(...)       # raw, no instrumentation
    """
    region = region_name or settings.aws_region
    raw = boto3.client("sqs", region_name=region)

    class _Instrumented:
        raw = raw

        @staticmethod
        def receive_message(**kw: Any) -> dict[str, Any]:
            with tracer.start_as_current_span(
                "sqs-receive",
                kind=SpanKind.CLIENT,
                attributes={
                    "messaging.system": "aws-sqs",
                    "messaging.operation": "receive",
                    "messaging.destination": queue_url,
                },
            ):
                return raw.receive_message(QueueUrl=queue_url, **kw)

        @staticmethod
        def delete_message(**kw: Any) -> dict[str, Any]:
            with tracer.start_as_current_span(
                "sqs-delete",
                kind=SpanKind.CLIENT,
                attributes={
                    "messaging.system": "aws-sqs",
                    "messaging.operation": "delete",
                    "messaging.destination": queue_url,
                },
            ):
                return raw.delete_message(QueueUrl=queue_url, **kw)

        @staticmethod
        def change_message_visibility(**kw: Any) -> dict[str, Any]:
            with tracer.start_as_current_span(
                "sqs-change-visibility",
                kind=SpanKind.CLIENT,
                attributes={
                    "messaging.system": "aws-sqs",
                    "messaging.operation": "change-visibility",
                    "messaging.destination": queue_url,
                },
            ):
                return raw.change_message_visibility(QueueUrl=queue_url, **kw)

    return _Instrumented()