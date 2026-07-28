VERSION="0.1"
import json

from clients import sqs_client
from settings import settings
import uuid
Q_URL = settings.QUEUE_URL

def enviar_message(to: str, subject: str, body: str) -> dict:
    try:
        msg_id = str(uuid.uuid4())
        sqs_client.send_message(
            QueueUrl=Q_URL,
            MessageBody=json.dumps({
                "id": msg_id, "to": to, "subject": subject, "body": body
            })
        )
        return {
            "status": "queued",
            "id": msg_id
        }
    except Exception as e:
        print(str(e))
        return {
            "status": "error"
        }
