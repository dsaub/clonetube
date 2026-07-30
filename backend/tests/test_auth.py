import pytest


class TestRegister:
    async def test_register_success(self, client, user_payload):
        resp = await client.post("/api/v1/auth/register", json=user_payload)
        assert resp.status_code == 201
        data = resp.json()
        assert data["status"] == "PENDING_CONFIRMATION"

    async def test_register_duplicate_username(self, client, registered_user, user_payload):
        resp = await client.post("/api/v1/auth/register", json=user_payload)
        assert resp.status_code == 409
        assert "ya están registrados" in resp.json()["detail"]

    async def test_register_duplicate_email(self, client, registered_user, user_payload):
        payload = {
            **user_payload,
            "username": "anotheruser",
        }
        resp = await client.post("/api/v1/auth/register", json=payload)
        assert resp.status_code == 409
        assert "ya están registrados" in resp.json()["detail"]

    async def test_register_missing_fields(self, client):
        resp = await client.post("/api/v1/auth/register", json={})
        assert resp.status_code == 422

    async def test_register_invalid_email(self, client):
        payload = {
            "username": "user",
            "password": "Pass123!",
            "full_name": "User",
            "email": "not-an-email",
        }
        resp = await client.post("/api/v1/auth/register", json=payload)
        assert resp.status_code == 422

    async def test_register_weak_password(self, client):
        payload = {
            "username": "user",
            "password": "123",
            "full_name": "User",
            "email": "user@example.com",
        }
        resp = await client.post("/api/v1/auth/register", json=payload)
        assert resp.status_code == 201


class TestLogin:
    async def test_login_success(self, client, registered_user, user_payload):
        resp = await client.post("/api/v1/auth/login", json={
            "username": user_payload["username"],
            "password": user_payload["password"],
        })
        assert resp.status_code == 200
        data = resp.json()
        assert "access_token" in data
        assert data["token_type"] == "bearer"

    async def test_login_unverified_user(self, client, user_payload):
        resp = await client.post("/api/v1/auth/register", json=user_payload)
        assert resp.status_code == 201
        resp = await client.post("/api/v1/auth/login", json={
            "username": user_payload["username"],
            "password": user_payload["password"],
        })
        assert resp.status_code == 401
        assert "no esta verificado" in resp.json()["detail"]

    async def test_login_wrong_password(self, client, registered_user, user_payload):
        resp = await client.post("/api/v1/auth/login", json={
            "username": user_payload["username"],
            "password": "WrongPassword!",
        })
        assert resp.status_code == 401
        assert "Credenciales inválidas" in resp.json()["detail"]

    async def test_login_nonexistent_user(self, client):
        resp = await client.post("/api/v1/auth/login", json={
            "username": "doesnotexist",
            "password": "SomePass1!",
        })
        assert resp.status_code == 401
        assert "Credenciales inválidas" in resp.json()["detail"]

    async def test_login_empty_body(self, client):
        resp = await client.post("/api/v1/auth/login", json={})
        assert resp.status_code == 422

    async def test_login_case_sensitive_username(self, client, registered_user, user_payload):
        resp = await client.post("/api/v1/auth/login", json={
            "username": user_payload["username"].upper(),
            "password": user_payload["password"],
        })
        assert resp.status_code == 401


class TestMe:
    async def test_me_success(self, client, auth_headers, registered_user):
        resp = await client.get("/api/v1/auth/me", headers=auth_headers)
        assert resp.status_code == 200
        data = resp.json()
        assert data["username"] == "testuser"
        assert data["email"] == "test@example.com"
        assert data["full_name"] == "Test User"
        assert "id" in data

    async def test_me_no_token(self, client):
        resp = await client.get("/api/v1/auth/me")
        assert resp.status_code == 401

    async def test_me_invalid_token(self, client):
        resp = await client.get("/api/v1/auth/me",
                                headers={"Authorization": "Bearer invalidtoken"})
        assert resp.status_code == 401

    async def test_me_expired_token(self, client, registered_user):
        import jwt as pyjwt
        from auth.security import decode_access_token
        payload = decode_access_token(registered_user["access_token"])
        expired_token = pyjwt.encode(
            {**payload, "exp": 0},
            "cambiar-por-clave-segura-de-al-menos-32-byts",
            algorithm="HS256",
        )
        resp = await client.get("/api/v1/auth/me",
                                headers={"Authorization": f"Bearer {expired_token}"})
        assert resp.status_code == 401


class TestChangePassword:
    async def test_change_password_success(self, client, registered_user, auth_headers):
        resp = await client.post("/api/v1/auth/change-password", json={
            "old_password": registered_user["password"],
            "new_password": "NewStr0ng!Pass",
        }, headers=auth_headers)
        assert resp.status_code == 200
        data = resp.json()
        assert "access_token" in data

        old_token = registered_user["access_token"]
        resp_old = await client.get("/api/v1/auth/me",
                                    headers={"Authorization": f"Bearer {old_token}"})
        assert resp_old.status_code == 401

    async def test_change_password_wrong_old(self, client, auth_headers):
        resp = await client.post("/api/v1/auth/change-password", json={
            "old_password": "WrongOldPass!",
            "new_password": "NewStr0ng!Pass",
        }, headers=auth_headers)
        assert resp.status_code == 401
        assert "Contraseña actual incorrecta" in resp.json()["detail"]

    async def test_change_password_no_auth(self, client):
        resp = await client.post("/api/v1/auth/change-password", json={
            "old_password": "old",
            "new_password": "new",
        })
        assert resp.status_code == 401


class TestForgotPassword:
    async def test_forgot_password_existing_email(self, client, registered_user, user_payload):
        resp = await client.post("/api/v1/auth/forgot-password", json={
            "email": user_payload["email"],
        })
        assert resp.status_code == 200
        data = resp.json()
        assert "reset_token" in data
        assert data["reset_token"] is not None
        assert len(data["reset_token"]) > 10

    async def test_forgot_password_nonexistent_email(self, client):
        resp = await client.post("/api/v1/auth/forgot-password", json={
            "email": "nonexistent@example.com",
        })
        assert resp.status_code == 200
        data = resp.json()
        assert data["reset_token"] is None
        assert "Si el email existe" in data["status"]

    async def test_forgot_password_invalid_email(self, client):
        resp = await client.post("/api/v1/auth/forgot-password", json={
            "email": "notanemail",
        })
        assert resp.status_code == 422


class TestResetPassword:
    async def test_reset_password_success(self, client, registered_user, user_payload):
        forgot_resp = await client.post("/api/v1/auth/forgot-password", json={
            "email": user_payload["email"],
        })
        reset_token = forgot_resp.json()["reset_token"]

        resp = await client.post("/api/v1/auth/reset-password", json={
            "token": reset_token,
            "new_password": "ResetP@ss123",
        })
        assert resp.status_code == 200
        assert resp.json()["status"] == "Contraseña restablecida exitosamente"

        login_resp = await client.post("/api/v1/auth/login", json={
            "username": user_payload["username"],
            "password": "ResetP@ss123",
        })
        assert login_resp.status_code == 200
        assert "access_token" in login_resp.json()

    async def test_reset_password_invalid_token(self, client):
        resp = await client.post("/api/v1/auth/reset-password", json={
            "token": "invalid-token-123",
            "new_password": "NewP@ss123",
        })
        assert resp.status_code == 400
        assert "inválido o expirado" in resp.json()["detail"]

    async def test_reset_password_tampered_token(self, client, registered_user, user_payload):
        forgot_resp = await client.post("/api/v1/auth/forgot-password", json={
            "email": user_payload["email"],
        })
        reset_token = forgot_resp.json()["reset_token"]

        resp = await client.post("/api/v1/auth/reset-password", json={
            "token": reset_token + "tampered",
            "new_password": "NewP@ss123",
        })
        assert resp.status_code == 400
