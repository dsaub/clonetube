package me.elordenador.clonetube.data.di

import android.content.Context
import me.elordenador.clonetube.data.remote.AuthService
import me.elordenador.clonetube.data.remote.NetworkConfig
import me.elordenador.clonetube.data.remote.SocialService
import me.elordenador.clonetube.data.remote.VideoService
import me.elordenador.clonetube.data.repository.AuthRepository
import me.elordenador.clonetube.data.repository.SocialRepository
import me.elordenador.clonetube.data.repository.VideoRepository
import me.elordenador.clonetube.data.session.SessionStore

/**
 * Manual dependency container (no DI framework). Built once per application context and
 * handed to [me.elordenador.clonetube.ui.state.ClonetubeAppState].
 */
class AppContainer(context: Context) {

    val context: Context = context.applicationContext

    private val session = SessionStore(context)

    private val client = NetworkConfig.buildClient(
        getToken = { session.token() },
        onAuthFailure = { session.setToken(null) },
    )

    private val retrofit = NetworkConfig.buildRetrofit(client)

    val authRepository = AuthRepository(retrofit.create(AuthService::class.java), session)
    val videoRepository = VideoRepository(retrofit.create(VideoService::class.java))
    val socialRepository = SocialRepository(retrofit.create(SocialService::class.java))
}
