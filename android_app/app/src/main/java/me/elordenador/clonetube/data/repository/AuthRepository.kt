package me.elordenador.clonetube.data.repository

import me.elordenador.clonetube.BuildConfig
import me.elordenador.clonetube.data.remote.AuthService
import me.elordenador.clonetube.data.remote.dto.ChangePasswordRequest
import me.elordenador.clonetube.data.remote.dto.ForgotPasswordRequest
import me.elordenador.clonetube.data.remote.dto.LoginRequest
import me.elordenador.clonetube.data.remote.dto.RegisterRequest
import me.elordenador.clonetube.data.remote.dto.ResetPasswordRequest
import me.elordenador.clonetube.data.remote.dto.UserResponse
import me.elordenador.clonetube.data.session.SessionStore

class AuthRepository(
    private val service: AuthService,
    private val session: SessionStore,
) {

    val isLoggedIn: Boolean get() = session.token() != null

    suspend fun login(username: String, password: String): UserResponse {
        val token = service.login(LoginRequest(username, password)).accessToken
        session.setToken(token)
        return service.me()
    }

    suspend fun register(username: String, fullName: String, email: String, password: String) {
        service.register(RegisterRequest(username, password, fullName, email))
    }

    /** Returns the current user if a (still valid) token is stored, else null. */
    suspend fun me(): UserResponse? = runCatching { service.me() }.getOrNull()

    suspend fun changePassword(oldPassword: String, newPassword: String): UserResponse {
        val token = service.changePassword(ChangePasswordRequest(oldPassword, newPassword)).accessToken
        session.setToken(token)
        return service.me()
    }

    suspend fun forgotPassword(email: String) {
        service.forgotPassword(ForgotPasswordRequest(email))
    }

    suspend fun resetPassword(token: String, newPassword: String) {
        service.resetPassword(ResetPasswordRequest(token, newPassword))
    }

    /** Hits the verification endpoint; clears the code server-side on success. */
    suspend fun verify(code: String) {
        service.verify(code)
    }

    fun logout() {
        session.setToken(null)
    }
}
