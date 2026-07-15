import hashlib
import uuid
from datetime import UTC, datetime, timedelta
from unittest.mock import patch

import jwt
import pytest

from auth.security import (
    create_access_token,
    create_reset_token,
    decode_access_token,
    hash_password,
    hash_reset_token,
    verify_password,
)


class TestHashPassword:
    def test_hash_and_verify(self):
        password = "MySecureP@ss1"
        hashed, version = hash_password(password)
        assert isinstance(hashed, str) and len(hashed) > 20
        assert version == 0
        assert verify_password(password, hashed) is True

    def test_verify_wrong_password(self):
        hashed, _ = hash_password("correct")
        assert verify_password("wrong", hashed) is False

    def test_different_hashes_for_same_password(self):
        h1, _ = hash_password("same")
        h2, _ = hash_password("same")
        assert h1 != h2


class TestAccessToken:
    def test_create_and_decode(self):
        uid = uuid.uuid4()
        token = create_access_token(uid, 0)
        payload = decode_access_token(token)
        assert payload["sub"] == str(uid)
        assert payload["version"] == 0
        assert "exp" in payload

    def test_decode_with_version(self):
        uid = uuid.uuid4()
        token = create_access_token(uid, 5)
        payload = decode_access_token(token)
        assert payload["version"] == 5

    def test_invalid_token_raises(self):
        with pytest.raises(jwt.PyJWTError):
            decode_access_token("invalid.token.here")

    def test_expired_token_raises(self):
        uid = uuid.uuid4()
        with patch("auth.security.settings.JWT_EXPIRE_MINUTES", -1):
            token = create_access_token(uid, 0)
        with pytest.raises(jwt.ExpiredSignatureError):
            decode_access_token(token)

    def test_tampered_token_raises(self):
        uid = uuid.uuid4()
        token = create_access_token(uid, 0)
        parts = token.split(".")
        tampered = f"{parts[0]}.{parts[1]}.invalidsig"
        with pytest.raises(jwt.PyJWTError):
            decode_access_token(tampered)


class TestResetToken:
    def test_create_reset_token_structure(self):
        token, token_hash, expires_at = create_reset_token()
        assert isinstance(token, str) and len(token) > 20
        assert token_hash == hashlib.sha256(token.encode("utf-8")).hexdigest()
        assert expires_at > datetime.now(UTC)
        assert expires_at < datetime.now(UTC) + timedelta(minutes=16)

    def test_hash_reset_token_consistency(self):
        token = "my-test-reset-token-12345"
        h1 = hash_reset_token(token)
        h2 = hash_reset_token(token)
        assert h1 == h2
        assert h1 == hashlib.sha256(token.encode("utf-8")).hexdigest()

    def test_different_tokens_different_hashes(self):
        h1 = hash_reset_token("token-a")
        h2 = hash_reset_token("token-b")
        assert h1 != h2

    def test_create_reset_token_is_unique(self):
        t1, _, _ = create_reset_token()
        t2, _, _ = create_reset_token()
        assert t1 != t2
