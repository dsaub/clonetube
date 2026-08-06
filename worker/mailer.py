import smtplib
import ssl
from email.message import EmailMessage as SMTPMessage

from models import EmailMessage
from settings import settings

def send_email(message: EmailMessage) -> None:
    email = SMTPMessage()
    email["From"] = settings.smtp_from
    email["To"] = str(message.to)
    email["Subject"] = message.subject
    email.set_content(message.body)
    if message.body_html:
        email.add_alternative(message.body_html, subtype="html")
    ssl_context = ssl.create_default_context()
    with smtplib.SMTP_SSL(
        host = settings.smtp_host,
        port = settings.smtp_port,
        context=ssl_context,
        timeout=30
    ) as smtp:
        smtp.login(
            settings.smtp_username,
            settings.smtp_password
        )
        smtp.send_message(email)