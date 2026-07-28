import boto3
from botocore.config import Config as BotoConfig

from settings import settings

_s3_config = BotoConfig(signature_version='s3v4')


def _build_s3_kwargs(endpoint_url: str | None) -> dict:
    kwargs = {
        'region_name': settings.AWS_REGION,
        'aws_access_key_id': settings.AWS_ACCESS_KEY_ID,
        'aws_secret_access_key': settings.AWS_SECRET_ACCESS_KEY,
        'config': _s3_config,
    }
    if endpoint_url is not None:
        kwargs['endpoint_url'] = endpoint_url
    return kwargs


s3_client = boto3.client('s3', **_build_s3_kwargs(settings.S3_ENDPOINT_URL))
sqs_client = boto3.client('sqs', region_name='eu-west-3')

_public_endpoint = settings.S3_PUBLIC_ENDPOINT_URL or settings.S3_ENDPOINT_URL
s3_client_public = boto3.client('s3', **_build_s3_kwargs(_public_endpoint))
