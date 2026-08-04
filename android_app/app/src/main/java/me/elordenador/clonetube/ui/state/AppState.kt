package me.elordenador.clonetube.ui.state

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.elordenador.clonetube.data.di.AppContainer
import me.elordenador.clonetube.data.remote.dto.PartInfo
import me.elordenador.clonetube.data.repository.AuthRepository
import me.elordenador.clonetube.data.repository.SocialRepository
import me.elordenador.clonetube.data.repository.VideoRepository
import me.elordenador.clonetube.model.Channel
import me.elordenador.clonetube.model.ChannelInfo
import me.elordenador.clonetube.model.VideoItem
import me.elordenador.clonetube.model.VideoVisibility
import me.elordenador.clonetube.model.sampleVideos
import retrofit2.HttpException
import java.text.NumberFormat
import java.util.Locale

enum class Tab { HOME, SUBS, YOU }

enum class Overlay { WATCH, CHANNEL, UPLOAD, AUTH }

enum class UploadStep { IDLE, PICKED, UPLOADING, DONE }

enum class AuthMode { LOGIN, REGISTER }

private val SPANISH: Locale = Locale.forLanguageTag("es-ES")

/**
 * Single source of truth for the UI, now backed by the real backend (when a [Context] is
 * available) or by mock data (Compose previews, where no Context is provided). The screen
 * call surface is kept intentionally close to the original prototype so the Compose UI
 * barely changes.
 */
@Stable
class ClonetubeAppState(context: Context? = null) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container = context?.let { AppContainer(it) }
    private val authRepo: AuthRepository? = container?.authRepository
    private val videoRepo: VideoRepository? = container?.videoRepository
    private val socialRepo: SocialRepository? = container?.socialRepository

    // ── navigation / session ─────────────────────────────────────────────────
    var tab by mutableStateOf(Tab.HOME); private set
    var overlay by mutableStateOf<Overlay?>(null); private set
    var authMode by mutableStateOf(AuthMode.LOGIN); private set
    var loggedIn by mutableStateOf(false); private set
    var username by mutableStateOf("Tú"); private set
    var usernameHandle by mutableStateOf("tu_usuario"); private set

    var searchQuery by mutableStateOf("")

    var authLoading by mutableStateOf(false); private set
    var authError by mutableStateOf<String?>(null); private set
    var pendingVerification by mutableStateOf(false); private set
    var verifyNotice by mutableStateOf<String?>(null); private set

    // ── home / feed ──────────────────────────────────────────────────────────
    var homeVideos by mutableStateOf<List<VideoItem>>(emptyList()); private set
    var homeError by mutableStateOf<String?>(null); private set

    // ── subscriptions ────────────────────────────────────────────────────────
    var subsChannels by mutableStateOf<List<Channel>>(emptyList()); private set
    var subsVideos by mutableStateOf<List<VideoItem>>(emptyList()); private set

    // ── watch ────────────────────────────────────────────────────────────────
    var watchItem by mutableStateOf<VideoItem?>(null); private set
    var watchStreamUrl by mutableStateOf<String?>(null); private set
    var watchError by mutableStateOf<String?>(null); private set
    var otherVideos by mutableStateOf<List<VideoItem>>(emptyList()); private set

    // ── channel ──────────────────────────────────────────────────────────────
    var channelInfo by mutableStateOf<ChannelInfo?>(null); private set
    var channelVideos by mutableStateOf<List<VideoItem>>(emptyList()); private set
    var channelError by mutableStateOf<String?>(null); private set

    // ── studio ───────────────────────────────────────────────────────────────
    var studioVideos by mutableStateOf<List<VideoItem>>(emptyList()); private set

    // ── upload ───────────────────────────────────────────────────────────────
    var uploadStep by mutableStateOf(UploadStep.IDLE); private set
    var pickedUri by mutableStateOf<Uri?>(null); private set
    var pickedFileName by mutableStateOf(""); private set
    var uploadTitle by mutableStateOf("")
    var uploadDesc by mutableStateOf("")
    var uploadProgress by mutableIntStateOf(0); private set
    var uploadError by mutableStateOf<String?>(null); private set

    // ── auth form fields ─────────────────────────────────────────────────────
    var loginUsername by mutableStateOf("")
    var loginPassword by mutableStateOf("")
    var registerUsername by mutableStateOf("")
    var registerName by mutableStateOf("")
    var registerEmail by mutableStateOf("")
    var registerPassword by mutableStateOf("")

    private val subscribedHandles = mutableStateListOf<String>()
    private val allVideos = mutableMapOf<String, VideoItem>()

    init {
        if (container != null) {
            if (authRepo?.isLoggedIn == true) scope.launch { loadMe() }
            scope.launch { loadHome() }
        } else {
            homeVideos = sampleVideos
        }
    }

    // ── derived values used by screens ───────────────────────────────────────
    val homeFeed: List<VideoItem>
        get() {
            val query = searchQuery.trim().lowercase(SPANISH)
            return if (query.isEmpty()) {
                homeVideos
            } else {
                homeVideos.filter { "${it.title} ${it.author}".lowercase(SPANISH).contains(query) }
            }
        }

    val accountInitial: String get() = username.take(1).uppercase(SPANISH)
    val loginEnabled: Boolean get() = loginUsername.isNotBlank() && loginPassword.isNotBlank()
    val registerEnabled: Boolean
        get() = registerUsername.isNotBlank() && registerName.isNotBlank() &&
            registerEmail.isNotBlank() && registerPassword.isNotBlank()

    fun isSubscribed(handle: String?): Boolean = handle != null && handle in subscribedHandles

    // ── navigation actions ───────────────────────────────────────────────────
    fun selectTab(next: Tab) {
        tab = next
        overlay = null
        if (next == Tab.SUBS) scope.launch { loadSubs() }
    }

    fun openWatch(id: String) {
        allVideos[id]?.let { selectWatch(it) }
    }

    fun openWatch(item: VideoItem) = selectWatch(item)

    private fun selectWatch(item: VideoItem) {
        watchItem = item
        watchStreamUrl = null
        watchError = null
        overlay = Overlay.WATCH
        scope.launch { loadWatch(item) }
    }

    fun openChannel(handle: String) {
        channelInfo = null
        channelVideos = emptyList()
        channelError = null
        overlay = Overlay.CHANNEL
        scope.launch { loadChannel(handle) }
    }

    fun closeOverlay() {
        overlay = null
    }

    fun toggleSubscription(handle: String) {
        if (!loggedIn) {
            openAuth(AuthMode.LOGIN)
            return
        }
        scope.launch {
            try {
                val response = if (isSubscribed(handle)) {
                    socialRepo?.unfollow(handle)
                } else {
                    socialRepo?.follow(handle)
                }
                val nowFollowing = response?.following ?: !isSubscribed(handle)
                if (nowFollowing) subscribedHandles.add(handle) else subscribedHandles.remove(handle)
                channelInfo = channelInfo?.copy(
                    following = nowFollowing,
                    subCount = response?.followers ?: channelInfo?.subCount ?: 0,
                )
            } catch (e: Exception) {
                Log.w("Clonetube", "follow failed", e)
            }
        }
    }

    fun openAccount() {
        tab = Tab.YOU
        overlay = null
    }

    fun logout() {
        authRepo?.logout()
        loggedIn = false
        username = "Tú"
        usernameHandle = "tu_usuario"
        subscribedHandles.clear()
        subsChannels = emptyList()
        subsVideos = emptyList()
        studioVideos = emptyList()
        tab = Tab.HOME
        overlay = null
    }

    fun openUpload() {
        if (loggedIn) {
            resetUpload()
            overlay = Overlay.UPLOAD
        } else {
            authMode = AuthMode.LOGIN
            overlay = Overlay.AUTH
        }
    }

    fun pickVideo(uri: Uri, fileName: String) {
        pickedUri = uri
        pickedFileName = fileName
        uploadTitle = fileName.substringBeforeLast('.')
        uploadStep = UploadStep.PICKED
        uploadError = null
    }

    fun resetUpload() {
        uploadStep = UploadStep.IDLE
        pickedUri = null
        pickedFileName = ""
        uploadTitle = ""
        uploadDesc = ""
        uploadProgress = 0
        uploadError = null
    }

    fun openAuth(mode: AuthMode) {
        authMode = mode
        overlay = Overlay.AUTH
    }

    fun switchAuthMode(mode: AuthMode) {
        authMode = mode
    }

    fun submitAuth() {
        authError = null
        verifyNotice = null
        if (authMode == AuthMode.LOGIN) {
            if (!loginEnabled) return
            authLoading = true
            scope.launch {
                try {
                    val user = authRepo!!.login(loginUsername, loginPassword)
                    loggedIn = true
                    username = user.fullName
                    usernameHandle = user.username
                    loginPassword = ""
                    overlay = null
                    tab = Tab.YOU
                    loadSubs()
                    loadStudio()
                } catch (e: Exception) {
                    authError = friendly(e)
                } finally {
                    authLoading = false
                }
            }
        } else {
            if (!registerEnabled) return
            authLoading = true
            scope.launch {
                try {
                    authRepo!!.register(registerUsername, registerName, registerEmail, registerPassword)
                    pendingVerification = true
                    verifyNotice = "Te enviamos un correo a $registerEmail. Ábrelo para verificar la cuenta."
                    registerPassword = ""
                } catch (e: Exception) {
                    authError = friendly(e)
                } finally {
                    authLoading = false
                }
            }
        }
    }

    fun startUpload() {
        val uri = pickedUri ?: return
        val ctx = container?.context ?: return
        val repo = videoRepo ?: return
        uploadStep = UploadStep.UPLOADING
        uploadProgress = 0
        uploadError = null
        scope.launch {
            try {
                val name = pickedFileName.ifBlank { "video.mp4" }
                val started = repo.startMultipart(name)
                val parts: List<PartInfo> = repo.uploadFile(ctx, uri, started.key, started.uploadId) {
                    uploadProgress = it
                }
                repo.complete(started.key, started.uploadId, parts)
                val detail = repo.detail(started.key)
                repo.updateVideo(detail.id, uploadTitle, uploadDesc, VideoVisibility.PUBLIC)
                uploadStep = UploadStep.DONE
                loadStudio()
            } catch (e: Exception) {
                Log.w("Clonetube", "upload failed", e)
                uploadError = friendly(e)
                uploadStep = UploadStep.PICKED
            }
        }
    }

    // ── email verification (App Link) ────────────────────────────────────────
    fun startVerification(code: String) {
        if (container == null) return
        scope.launch {
            try {
                authRepo?.verify(code)
                verifyNotice = "Cuenta verificada. Ya puedes iniciar sesión."
                authMode = AuthMode.LOGIN
                overlay = Overlay.AUTH
            } catch (e: Exception) {
                verifyNotice = "No se pudo verificar la cuenta (enlace inválido o caducado)."
            }
        }
    }

    fun clearVerifyNotice() {
        verifyNotice = null
    }

    // ── data loads ───────────────────────────────────────────────────────────
    private suspend fun loadHome() {
        try {
            homeVideos = videoRepo?.feed() ?: emptyList()
            indexVideos(homeVideos)
            homeError = null
        } catch (e: Exception) {
            homeError = friendly(e)
        }
    }

    private suspend fun loadMe() {
        try {
            authRepo?.me()?.let {
                loggedIn = true
                username = it.fullName
                usernameHandle = it.username
            }
        } catch (e: Exception) {
            authRepo?.logout()
            loggedIn = false
        }
    }

    private suspend fun loadSubs() {
        if (!loggedIn) return
        try {
            val following = socialRepo?.following() ?: emptyList()
            subsChannels = following
            subscribedHandles.clear()
            subscribedHandles.addAll(following.map { it.handle })
            val videos = mutableListOf<VideoItem>()
            following.forEach { ch ->
                val v = runCatching { socialRepo?.channelVideos(ch.handle, 1) ?: emptyList() }
                    .getOrDefault(emptyList())
                videos.addAll(v)
            }
            subsVideos = videos
            indexVideos(videos)
        } catch (e: Exception) {
            Log.w("Clonetube", "subs load failed", e)
        }
    }

    private suspend fun loadWatch(item: VideoItem) {
        try {
            val detail = runCatching { videoRepo?.detail(item.key) }.getOrNull() ?: item
            val url = runCatching { videoRepo?.streamUrl(item.key) }.getOrNull().orEmpty()
            watchItem = detail
            watchStreamUrl = url.ifBlank { null }
            otherVideos = (homeVideos + subsVideos)
                .filter { it.id != item.id && it.visibility == VideoVisibility.PUBLIC }
                .distinctBy { it.id }
                .take(5)
        } catch (e: Exception) {
            watchError = friendly(e)
        }
    }

    private suspend fun loadChannel(handle: String) {
        try {
            val c = socialRepo?.channel(handle)
            channelInfo = c
            if (c != null) {
                if (c.following) subscribedHandles.add(handle) else subscribedHandles.remove(handle)
            }
            channelVideos = socialRepo?.channelVideos(handle, 1) ?: emptyList()
            indexVideos(channelVideos)
        } catch (e: Exception) {
            channelError = friendly(e)
        }
    }

    private suspend fun loadStudio() {
        if (!loggedIn) return
        try {
            studioVideos = videoRepo?.studio() ?: emptyList()
            indexVideos(studioVideos)
        } catch (e: Exception) {
            Log.w("Clonetube", "studio load failed", e)
        }
    }

    fun deleteStudioVideo(id: String) {
        scope.launch {
            try {
                videoRepo?.deleteVideo(id)
                loadStudio()
            } catch (e: Exception) {
                Log.w("Clonetube", "delete failed", e)
            }
        }
    }

    fun updateStudioVideo(id: String, title: String, description: String) {
        scope.launch {
            try {
                videoRepo?.updateVideo(id, title, description, VideoVisibility.PUBLIC)
                loadStudio()
            } catch (e: Exception) {
                Log.w("Clonetube", "update failed", e)
            }
        }
    }

    private fun indexVideos(list: List<VideoItem>) {
        list.forEach { allVideos[it.id] = it }
    }

    private fun friendly(e: Exception): String = when (e) {
        is HttpException -> when (e.code()) {
            401 -> "Sesión caducada. Inicia sesión de nuevo."
            404 -> "No se encontró el contenido."
            409 -> "El usuario o el email ya están registrados."
            413 -> "El vídeo supera el tamaño permitido."
            else -> "Error del servidor (${e.code()}). Inténtalo más tarde."
        }
        else -> "No se pudo conectar con Clonetube. Revisa tu conexión."
    }
}

@Composable
fun rememberClonetubeAppState(): ClonetubeAppState {
    val context = LocalContext.current
    return remember(context) { ClonetubeAppState(context.applicationContext) }
}

fun formatFollowers(count: Int): String =
    NumberFormat.getIntegerInstance(SPANISH).format(count)
