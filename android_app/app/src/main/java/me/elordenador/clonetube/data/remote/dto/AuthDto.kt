package me.elordenador.clonetube.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class RegisterRequest(
    val username: String,
    val password: String,
    @SerialName("full_name") val fullName: String,
    val email: String,
)

@Serializable
data class ChangePasswordRequest(
    @SerialName("old_password") val oldPassword: String,
    @SerialName("new_password") val newPassword: String,
)

@Serializable
data class ForgotPasswordRequest(val email: String)

@Serializable
data class ResetPasswordRequest(val token: String, @SerialName("new_password") val newPassword: String)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
)

@Serializable
data class UserResponse(
    val id: Int,
    val username: String,
    @SerialName("full_name") val fullName: String,
    val email: String,
)

@Serializable
data class RegisterResponse(val status: String)

@Serializable
data class MessageResponse(val status: String)
