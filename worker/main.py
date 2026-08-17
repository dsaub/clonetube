import json, logging, signal, sys
from types import FrameType
from typing import Any

from botocore.exceptions import BotoCoreError, ClientError
from opentelemetry.trace import SpanKind, Status, StatusCode
from pydantic import ValidationError

from constants import OTEL_AWS_SQS_SYSTEM, OTEL_MESSAGING_SYSTEM

# ── OTel: inicializar ANTES de importar clients ────────────────
from telemetry import extract_context, flush_telemetry, get_tracer, init_telemetry

# Configure logging before telemetry so exporter diagnostics are visible.
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
init_telemetry("clonetube-email-worker")
tracer = get_tracer("email-worker")

# Ahora los imports de clients ya encuentran el TracerProvider real
from clients import sqs_delete_message, sqs_receive_message
from mailer import send_email
from models import EmailMessage
from settings import settings

logger = logging.getLogger("email-worker")

running = True

def stop_worker(
        signum: int,
        frame: FrameType | None,
) -> None:
    global running

    logger.info("Señal %s recibida. Deteniendo worker...", signum)
    running = False

def parse_message(raw_body: str) -> EmailMessage:
    payload = json.loads(raw_body)
    return EmailMessage.model_validate(payload)

def delete_message(receipt_handle: str) -> None:
    sqs_delete_message(
        QueueUrl=settings.queue_url,
        ReceiptHandle=receipt_handle
    )

def process_sqs_message(sqs_message: dict[str, Any]) -> None:
    sqs_message_id = sqs_message.get("MessageId", "unknown")
    receipt_handle = sqs_message["ReceiptHandle"]
    raw_body = sqs_message["Body"]

    # Extraer contexto de traza del mensaje SQS (inyectado por el publicador)
    parent_ctx = extract_context(sqs_message)

    with tracer.start_as_current_span(
        "process-email-message",
        kind=SpanKind.CONSUMER,
        attributes={
            OTEL_MESSAGING_SYSTEM: OTEL_AWS_SQS_SYSTEM,
            "messaging.message.id": sqs_message_id,
        },
        context=parent_ctx,
    ) as span:
        try:
            message = parse_message(raw_body)
        except (json.JSONDecodeError, ValidationError) as exc:
            logger.warning(
                "Mensaje invalido. sqs_message_id=%s error_type=%s",
                sqs_message_id,
                type(exc).__name__,
            )
            span.set_status(Status(StatusCode.ERROR, "Invalid message body"))
            delete_message(receipt_handle)
            return

        span.set_attributes({
            "email.id": message.id,
            "email.to": str(message.to),
            "email.subject": message.subject,
        })
        logger.info(
            "Procesando correo id=%s destinatario=%s",
            message.id,
            message.to,
        )
        try:
            with tracer.start_as_current_span("send-email"):
                send_email(message)
        except Exception as exc:
            logger.exception(
                "No se pudo enviar el correo id=%s ."
                "El mensaje no se borrará y SQS lo reintentara.",
                message.id
            )
            span.set_status(Status(StatusCode.ERROR, "SMTP send failed"))
            span.record_exception(exc)
            return

        delete_message(receipt_handle)
        span.set_status(Status(StatusCode.OK))

        logger.info(
            "Correo enviado y mensaje eliminado id=%s",
            message.id
        )

def poll_messages() -> list[dict[str, Any]]:
    response = sqs_receive_message(
        QueueUrl=settings.queue_url,
        MaxNumberOfMessages=10,
        WaitTimeSeconds=settings.wait_time_seconds,
        VisibilityTimeout=settings.visibility_timeout,
        MessageSystemAttributeNames=[
            "ApproximateReceiveCount",
            "SentTimestamp",
        ],
    )
    return response.get("Messages", [])
def run_worker() -> None:
    # Validar que las credenciales SMTP están configuradas
    if not all([settings.smtp_host, settings.smtp_username, settings.smtp_password, settings.smtp_from]):
        logger.critical(
            "Faltan credenciales SMTP. Configura SMTP_HOST, SMTP_USERNAME, SMTP_PASSWORD y SMTP_FROM."
        )
        sys.exit(1)

    logger.info("Worker iniciado")
    logger.info("Queue URL: %s", settings.queue_url)

    while running:
        try:
            messages = poll_messages()

            for message in messages:
                if not running:
                    break

                receive_count = message.get(
                    "Attributes",
                    {},
                ).get("ApproximateReceiveCount", "unknown")

                logger.info(
                    "Mensaje recibido message_id=%s intento=%s",
                    message.get("MessageId"),
                    receive_count,
                )

                process_sqs_message(message)

        except (BotoCoreError, ClientError):
            logger.exception("Error comunicándose con Amazon SQS")

        except Exception:
            logger.exception("Error inesperado en el worker")

    logger.info("Worker detenido correctamente")


def _shutdown_telemetry() -> None:
    """Flush pending spans before exit."""
    flush_telemetry(5_000)


def main() -> None:
    signal.signal(signal.SIGTERM, stop_worker)
    signal.signal(signal.SIGINT, stop_worker)

    try:
        run_worker()
    except KeyboardInterrupt:
        logger.info("Worker interrumpido")
    finally:
        _shutdown_telemetry()
        sys.exit(0)


if __name__ == "__main__":
    main()
