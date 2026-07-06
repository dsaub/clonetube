import settings
import boto3
from botocore.config import Config as BotoConfig

s3_kwargs = {
    'region_name': settings.REGION_NAME,
    'aws_access_key_id': settings.AWS_ACCESS_KEY_ID,
    'aws_secret_access_key': settings.AWS_SECRET_ACCESS_KEY,
    'config': BotoConfig(signature_version='s3v4'),
}

if settings.S3_ENDPOINT_URL is not None:
    s3_kwargs['endpoint_url'] = settings.S3_ENDPOINT_URL

s3_client = boto3.client('s3', **s3_kwargs)
