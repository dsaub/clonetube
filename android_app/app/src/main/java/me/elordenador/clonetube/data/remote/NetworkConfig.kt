package me.elordenador.clonetube.data.remote

import android.util.Log
import kotlinx.serialization.json.Json
import me.elordenador.clonetube.BuildConfig
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds the shared OkHttp client, Retrofit instance and the [Json] configuration used
 * across every API service. The client injects the stored JWT as a Bearer token on every
 * request and treats a 401 as an expired session (it drops the token so the next call
 * naturally forces a re-login).
 */
object NetworkConfig {

    private const val BASE_URL = BuildConfig.BASE_URL

    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = false
    }

    fun buildClient(
        getToken: () -> String?,
        onAuthFailure: () -> Unit,
    ): OkHttpClient {
        val auth = Interceptor { chain ->
            val token = getToken()
            val request = if (token != null) {
                chain.request().newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            val response = chain.proceed(request)
            if (response.code == 401) onAuthFailure()
            response
        }

        val logging = HttpLoggingInterceptor { Log.d("ClonetubeApi", it) }.apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor(auth)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun buildRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}
