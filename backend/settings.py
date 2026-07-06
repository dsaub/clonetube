import os

BUCKET_NAME = os.getenv("AWS_BUCKET_NAME", None)
REGION_NAME = os.getenv("AWS_REGION", "us-east-1")
AWS_ACCESS_KEY_ID = os.getenv("AWS_ACCESS_KEY_ID", None)
AWS_SECRET_ACCESS_KEY = os.getenv("AWS_SECRET_ACCESS_KEY", None)
S3_ENDPOINT_URL = os.getenv("S3_ENDPOINT_URL", None)
