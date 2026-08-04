package me.elordenador.clonetube.data.remote

import me.elordenador.clonetube.data.remote.dto.ChangePasswordRequest
import me.elordenador.clonetube.data.remote.dto.ForgotPasswordRequest
import me.elordenador.clonetube.data.remote.dto.LoginRequest
import me.elordenador.clonetube.data.remote.dto.MessageResponse
import me.elordenador.clonetube.data.remote.dto.RegisterRequest
import me.elordenador.clonetube.data.remote.dto.RegisterResponse
import me.elordenador.clonetube.data.remote.dto.ResetPasswordRequest
import me.elordenador.clonetube.data.remote.dto.TokenResponse
import me.elordenador.clonetube.data.remote.dto.UserResponse
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface AuthService {

    @POST("api/v1/auth/login")
    suspend fun login(@Body body: LoginRequest): TokenResponse

    @POST("api/v1/auth/register")
    suspend fun register(@Body body: RegisterRequest): RegisterResponse

    @GET("api/v1/auth/me")
    suspend fun me(): UserResponse

    @POST("api/v1/auth/change-password")
    suspend fun changePassword(@Body body: ChangePasswordRequest): TokenResponse

    @POST("api/v1/auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): MessageResponse

    @POST("api/v1/auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequest): MessageResponse

    /** The backend clears the verification code and then 307-redirects to "/". */
    @GET("api/v1/auth/verify/{code}")
    suspend fun verify(@Path("code") code: String): ResponseBody
}
