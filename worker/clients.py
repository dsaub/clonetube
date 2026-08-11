from typing import Any

import boto3
from opentelemetry.trace import SpanKind

from settings import settings
from telemetry import get_tracer

tracer = get_tracer("sqs-client")

_raw_sqs = boto3.client("sqs", region_name=settings.aws_region)


def _sqs_span(
    operation: str,
    queue_url: str,
    **extra_attrs: Any,
) -> Any:
    """Internal helper: create a span for an SQS operation."""
    from opentelemetry import trace as otel_trace

    span = tracer.start_span(
        f"sqs-{operation}",
        kind=SpanKind.CLIENT,
        attributes={
            "messaging.system": "aws-sqs",
            "messaging.operation": operation,
            "messaging.destination": queue_url,
            **extra_attrs,
        },
    )
    return otel_trace.use_span(span)


def sqs_receive_message(queue_url: str | None = None, **kwargs: Any) -> dict[str, Any]:
    """Instrumented SQS receive_message."""
    url = queue_url or kwargs.pop("QueueUrl", settings.queue_url)
    with _sqs_span("receive", url):
        return _raw_sqs.receive_message(QueueUrl=url, **kwargs)


def sqs_delete_message(queue_url: str | None = None, **kwargs: Any) -> dict[str, Any]:
    """Instrumented SQS delete_message."""
    url = queue_url or kwargs.pop("QueueUrl", settings.queue_url)
    with _sqs_span("delete", url):
        return _raw_sqs.delete_message(QueueUrl=url, **kwargs)


def sqs_change_message_visibility(queue_url: str | None = None, **kwargs: Any) -> dict[str, Any]:
    """Instrumented SQS change_message_visibility (heartbeat)."""
    url = queue_url or kwargs.pop("QueueUrl", settings.queue_url)
    with _sqs_span("change-visibility", url):
        return _raw_sqs.change_message_visibility(QueueUrl=url, **kwargs)


# Backwards-compatible raw client for advanced usage
sqs_client = _raw_sqs


def instrumented_sqs(queue_url: str, region_name: str | None = None) -> dict[str, Any]:
    """Return an object whose attribute access delegates to a raw SQS client
    created for *queue_url* and *region_name*, while offering instrumented
    wrappers for the most common operations.

    Usage::

        sqs = instrumented_sqs(settings.video_queue_url)
        sqs.receive_message(...)   # instrumented
        sqs.delete_message(...)    # instrumented
        sqs.change_message_visibility(...)  # instrumented
        sqs.raw.<any_other_method>(...)  # raw, no instrumentation
    """
    region = region_name or settings.aws_region
    raw = boto3.client("sqs", region_name=region)

    class _Instrumented:
        raw = raw

        @staticmethod
        def receive_message(**kw: Any) -> dict[str, Any]:
            with _sqs_span("receive", queue_url):
                return raw.receive_message(QueueUrl=queue_url, **kw)

        @staticmethod
        def delete_message(**kw: Any) -> dict[str, Any]:
            with _sqs_span("delete", queue_url):
                return raw.delete_message(QueueUrl=queue_url, **kw)

        @staticmethod
        def change_message_visibility(**kw: Any) -> dict[str, Any]:
            with _sqs_span("change-visibility", queue_url):
                return raw.change_message_visibility(QueueUrl=queue_url, **kw)

    return _Instrumented()