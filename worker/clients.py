import boto3

from settings import settings

sqs_client = boto3.client(
    "sqs",
    region_name=settings.aws_region
)