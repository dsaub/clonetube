import smtplib
import ssl
from email.message import EmailMessage as SMTPMessage

from opentelemetry.trace import Status, StatusCode

from constants import OTEL_MESSAGING_SYSTEM
from models import EmailMessage
from settings import settings
from telemetry import get_tracer

tracer = get_tracer("mailer")


def send_email(message: EmailMessage) -> None:
    email = SMTPMessage()
    email["From"] = settings.smtp_from
    email["To"] = str(message.to)
    email["Subject"] = message.subject
    email.set_content(message.body)
    if message.body_html:
        email.add_alternative(message.body_html, subtype="html")

    ssl_context = ssl.create_default_context()
    ssl_context.minimum_version = ssl.TLSVersion.TLSv1_2
    with tracer.start_as_current_span(
        "smtp-send",
        attributes={
            "email.id": message.id,
            "email.to": str(message.to),
            OTEL_MESSAGING_SYSTEM: "smtp",
        },
    ) as span:
        try:
            with smtplib.SMTP_SSL(
                host=settings.smtp_host,
                port=settings.smtp_port,
                context=ssl_context,
                timeout=30,
            ) as smtp:
                smtp.login(settings.smtp_username, settings.smtp_password)
                smtp.send_message(email)
        except Exception as exc:
            span.set_status(Status(StatusCode.ERROR, "SMTP send failed"))
            span.record_exception(exc)
            raise
        span.set_status(Status(StatusCode.OK))
