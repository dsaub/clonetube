"""
OpenTelemetry initialization and helpers for Clonetube workers.

Sets up OTLP HTTP exporter and W3C trace-context propagation.
Automatically extracts trace context from SQS message attributes so that
processing spans are linked to the publisher's trace.
"""

import logging
import os
from typing import Any

from opentelemetry import context, propagate, trace
from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
from opentelemetry.sdk.resources import SERVICE_NAME, Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from opentelemetry.trace.propagation.tracecontext import TraceContextTextMapPropagator

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# SQS message-attributes ↔ OTel carrier helpers
# ---------------------------------------------------------------------------
# SQS represents message attributes as a dict like:
#   {"traceparent": {"DataType":"String","StringValue":"00-…"}, …}
# OTel propagators expect a flat dict:  {"traceparent": "00-…"}


def _sqs_attrs_to_carrier(sqs_attrs: dict[str, Any]) -> dict[str, str]:
    """Convert SQS MessageAttributes to a flat OTel carrier dict."""
    carrier: dict[str, str] = {}
    for key, attr in (sqs_attrs or {}).items():
        if isinstance(attr, dict) and attr.get("DataType") == "String":
            val = attr.get("StringValue")
            if val:
                carrier[key] = val
    return carrier


def extract_context(sqs_message: dict[str, Any]) -> context.Context | None:
    """Extract a parent OTel Context from an SQS message, if present."""
    attrs = sqs_message.get("MessageAttributes") or {}
    carrier = _sqs_attrs_to_carrier(attrs)
    if not carrier.get("traceparent"):
        return None
    return propagate.extract(carrier)


def inject_context(carrier: dict[str, str]) -> dict[str, Any]:
    """Inject the current OTel context into an SQS MessageAttributes dict."""
    propagate.inject(carrier)
    attrs: dict[str, Any] = {}
    for key, val in carrier.items():
        attrs[key] = {"DataType": "String", "StringValue": val}
    return attrs


# ---------------------------------------------------------------------------
# One-time SDK init (called once per process)
# ---------------------------------------------------------------------------

_initialized = False


def init_telemetry(service_name: str) -> None:
    """Bootstrap the OpenTelemetry SDK.

    Reads standard env vars:
    - ``OTEL_EXPORTER_OTLP_ENDPOINT`` – base URL for the OTLP collector
    - ``OTEL_SERVICE_NAME`` – overrides *service_name* when set

    If no OTLP endpoint is configured the SDK is still installed but spans
    are not exported (zero runtime overhead).
    """
    global _initialized
    if _initialized:
        return
    _initialized = True

    otel_service = os.getenv("OTEL_SERVICE_NAME", service_name)
    resource = Resource(attributes={SERVICE_NAME: otel_service})

    provider = TracerProvider(resource=resource)

    otlp_endpoint = os.getenv("OTEL_EXPORTER_OTLP_ENDPOINT", "").strip()
    if otlp_endpoint:
        if not otlp_endpoint.endswith("/"):
            otlp_endpoint += "/"
        exporter = OTLPSpanExporter(endpoint=f"{otlp_endpoint}v1/traces")
        provider.add_span_processor(BatchSpanProcessor(exporter))
        logger.info(
            "OTel exportando a %s (service=%s)", otlp_endpoint, otel_service
        )
    else:
        logger.info(
            "OTEL_EXPORTER_OTLP_ENDPOINT no definido: spans no exportados (service=%s)",
            otel_service,
        )

    trace.set_tracer_provider(provider)
    propagate.set_global_textmap(TraceContextTextMapPropagator())


def get_tracer(name: str = "worker") -> trace.Tracer:
    """Return a tracer scoped to *name*."""
    return trace.get_tracer(name)
