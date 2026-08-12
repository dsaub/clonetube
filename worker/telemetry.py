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
from opentelemetry._logs import set_logger_provider
from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
from opentelemetry.exporter.otlp.proto.http._log_exporter import OTLPLogExporter
from opentelemetry.sdk._logs import LoggerProvider, LoggingHandler
from opentelemetry.sdk._logs.export import BatchLogRecordProcessor
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


def _parse_otel_headers(raw: str) -> dict[str, str]:
    """Parse ``OTEL_EXPORTER_OTLP_HEADERS`` into a dict.

    Format: ``key1=value1,key2=value2``
    Example: ``Authorization=Basic dXNlcjpwYXNz``
    """
    headers: dict[str, str] = {}
    if not raw or not raw.strip():
        return headers
    for pair in raw.split(","):
        pair = pair.strip()
        if "=" not in pair:
            continue
        key, _, value = pair.partition("=")
        headers[key.strip()] = value.strip()
    return headers


def init_telemetry(service_name: str) -> None:
    """Bootstrap the OpenTelemetry SDK.

    Reads standard env vars:
    - ``OTEL_SERVICE_NAME`` – overrides *service_name* when set
    - ``OTEL_EXPORTER_OTLP_ENDPOINT`` – base URL for the OTLP collector
      (e.g. ``https://tempo.example.grafana.net:443``)
    - ``OTEL_EXPORTER_OTLP_TRACES_ENDPOINT`` – optional complete traces URL;
      takes precedence over the base endpoint
    - ``OTEL_EXPORTER_OTLP_PROTOCOL`` – ``http/protobuf`` (default) or ``http/json``
    - ``OTEL_EXPORTER_OTLP_HEADERS`` – ``key=value`` pairs separated by commas
      (e.g. ``Authorization=Basic dXNlcjpwYXNz`` para Grafana Cloud)

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
    log_provider = LoggerProvider(resource=resource)

    otlp_endpoint_raw = os.getenv("OTEL_EXPORTER_OTLP_ENDPOINT", "").strip()
    traces_endpoint_raw = os.getenv("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT", "").strip()
    otlp_headers_raw = os.getenv("OTEL_EXPORTER_OTLP_HEADERS", "").strip()

    if traces_endpoint_raw or otlp_endpoint_raw:
        # Accept both the standard complete traces endpoint and a collector base URL.
        endpoint = traces_endpoint_raw or otlp_endpoint_raw.rstrip("/")
        if not endpoint.endswith("/v1/traces"):
            endpoint = f"{endpoint}/v1/traces"

        headers = _parse_otel_headers(otlp_headers_raw)

        exporter_kwargs: dict[str, Any] = {"endpoint": endpoint}
        if headers:
            exporter_kwargs["headers"] = headers

        exporter = OTLPSpanExporter(**exporter_kwargs)
        provider.add_span_processor(BatchSpanProcessor(exporter))

        logs_endpoint_raw = os.getenv("OTEL_EXPORTER_OTLP_LOGS_ENDPOINT", "").strip()
        logs_endpoint = logs_endpoint_raw or otlp_endpoint_raw.rstrip("/")
        if not logs_endpoint and traces_endpoint_raw:
            logs_endpoint = traces_endpoint_raw.rstrip("/")
        if logs_endpoint.endswith("/v1/traces"):
            logs_endpoint = logs_endpoint[:-len("/v1/traces")]
        if not logs_endpoint.endswith("/v1/logs"):
            logs_endpoint = f"{logs_endpoint}/v1/logs"
        log_exporter_kwargs: dict[str, Any] = {"endpoint": logs_endpoint}
        if headers:
            log_exporter_kwargs["headers"] = headers
        log_provider.add_log_record_processor(
            BatchLogRecordProcessor(OTLPLogExporter(**log_exporter_kwargs))
        )
        set_logger_provider(log_provider)
        logging.getLogger().addHandler(
            LoggingHandler(level=logging.NOTSET, logger_provider=log_provider)
        )
        logger.info(
            "OTel configurado: traces=%s logs=%s service=%s headers=%s",
            endpoint,
            logs_endpoint,
            otel_service,
            list(headers.keys()) if headers else "(ninguno)",
        )
    else:
        logger.warning(
            "⚠  OTEL_EXPORTER_OTLP_ENDPOINT NO definido — los spans NO se enviarán a ningún colector. "
            "Define esta variable para apuntar a Grafana (ej. https://otel.elordenador.org). "
            "service=%s",
            otel_service,
        )

    trace.set_tracer_provider(provider)
    propagate.set_global_textmap(TraceContextTextMapPropagator())


def get_tracer(name: str = "worker") -> trace.Tracer:
    """Return a tracer scoped to *name*."""
    return trace.get_tracer(name)


def flush_telemetry(timeout_millis: int = 10_000) -> None:
    """Flush pending traces and logs before process termination."""
    try:
        trace.get_tracer_provider().force_flush(timeout_millis)
    except Exception:
        logger.exception("No se pudieron enviar las trazas pendientes")
    try:
        from opentelemetry import _logs

        _logs.get_logger_provider().force_flush(timeout_millis)
    except Exception:
        logger.exception("No se pudieron enviar los logs pendientes")
