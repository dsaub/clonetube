package me.elordenador.clonetube.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import me.elordenador.clonetube.model.Channel
import me.elordenador.clonetube.model.ChannelInfo
import me.elordenador.clonetube.model.Video
import me.elordenador.clonetube.model.sampleVideos
import java.text.NumberFormat
import java.util.Locale

enum class Tab { HOME, SUBS, YOU }

enum class Overlay { WATCH, CHANNEL, UPLOAD, AUTH }

enum class UploadStep { IDLE, PICKED, UPLOADING, DONE }

enum class AuthMode { LOGIN, REGISTER }

private enum class AuthIntent { ACCOUNT, UPLOAD }

private val SPANISH: Locale = Locale.forLanguageTag("es-ES")

/** Percentage added on each tick of the simulated upload (prototype: +14 every 220ms). */
const val UPLOAD_STEP_PERCENT = 14
const val UPLOAD_TICK_MILLIS = 220L

/**
 * Everything the prototype's `Component` class kept in `state`, plus the derived values
 * its `renderVals()` computed. All data is in-memory mock data — there is no backend yet.
 */
@Stable
class ClonetubeAppState(private val videos: List<Video> = sampleVideos) {

    var tab by mutableStateOf(Tab.HOME)
        private set
    var overlay by mutableStateOf<Overlay?>(null)
        private set
    var authMode by mutableStateOf(AuthMode.LOGIN)
        private set
    private var authIntent by mutableStateOf(AuthIntent.ACCOUNT)

    var loggedIn by mutableStateOf(false)
        private set
    var username by mutableStateOf("Tú")
        private set
    var usernameHandle by mutableStateOf("tu_usuario")
        private set

    var searchQuery by mutableStateOf("")

    private var selectedVideoId by mutableIntStateOf(1)
    private var selectedChannelHandle by mutableStateOf<String?>(null)

    private val subscribedHandles = mutableStateMapOf<String, Boolean>().apply {
        videos.filter { it.subscribed }.forEach { put(it.handle, true) }
    }

    var uploadStep by mutableStateOf(UploadStep.IDLE)
        private set
    var pickedFileName by mutableStateOf("")
        private set
    var uploadTitle by mutableStateOf("")
    var uploadDesc by mutableStateOf("")
    var uploadProgress by mutableIntStateOf(0)
        private set

    var loginUsername by mutableStateOf("")
    var loginPassword by mutableStateOf("")
    var registerUsername by mutableStateOf("")
    var registerName by mutableStateOf("")
    var registerEmail by mutableStateOf("")
    var registerPassword by mutableStateOf("")

    // ── derived ────────────────────────────────────────────────────────────────

    /** Home feed, filtered by the search box over title + author. */
    val homeVideos: List<Video>
        get() {
            val query = searchQuery.trim().lowercase(SPANISH)
            if (query.isEmpty()) return videos
            return videos.filter { "${it.title} ${it.author}".lowercase(SPANISH).contains(query) }
        }

    val subsVideos: List<Video> get() = videos.filter { isSubscribed(it.handle) }

    val subsChannels: List<Channel>
        get() = subsVideos.distinctBy { it.handle }
            .map { Channel(handle = it.handle, name = it.author, initial = it.initial) }

    val watchVideo: Video get() = videos.firstOrNull { it.id == selectedVideoId } ?: videos.first()

    val otherVideos: List<Video> get() = videos.filter { it.id != watchVideo.id }.take(3)

    val channelVideos: List<Video> get() = videos.filter { it.handle == selectedChannelHandle }

    val channelInfo: ChannelInfo
        get() {
            val videosOfChannel = channelVideos
            val base = videosOfChannel.firstOrNull() ?: videos.first()
            return ChannelInfo(
                name = base.author,
                handle = base.handle,
                initial = base.initial,
                videoCount = videosOfChannel.size,
                subCount = NumberFormat.getIntegerInstance(SPANISH)
                    .format(videosOfChannel.size * 1240 + 320),
                coverIndex = base.id,
            )
        }

    val accountInitial: String get() = username.take(1).uppercase(SPANISH)

    val loginEnabled: Boolean get() = loginUsername.isNotBlank() && loginPassword.isNotBlank()

    val registerEnabled: Boolean
        get() = registerUsername.isNotBlank() && registerName.isNotBlank() &&
            registerEmail.isNotBlank() && registerPassword.isNotBlank()

    fun isSubscribed(handle: String?): Boolean = handle != null && subscribedHandles[handle] == true

    // ── actions ────────────────────────────────────────────────────────────────

    fun selectTab(next: Tab) {
        tab = next
        overlay = null
    }

    fun openWatch(videoId: Int) {
        selectedVideoId = videoId
        overlay = Overlay.WATCH
    }

    fun openChannel(handle: String) {
        selectedChannelHandle = handle
        overlay = Overlay.CHANNEL
    }

    fun closeOverlay() {
        overlay = null
    }

    fun toggleSubscription(handle: String) {
        subscribedHandles[handle] = !isSubscribed(handle)
    }

    fun openAccount() {
        tab = Tab.YOU
        overlay = null
    }

    fun logout() {
        loggedIn = false
        tab = Tab.HOME
        overlay = null
    }

    /** The + button: signed-in users go straight to upload, everyone else signs in first. */
    fun openUpload() {
        if (loggedIn) {
            resetUpload()
            overlay = Overlay.UPLOAD
        } else {
            authMode = AuthMode.LOGIN
            authIntent = AuthIntent.UPLOAD
            overlay = Overlay.AUTH
        }
    }

    fun pickFromGallery() = pick("video_playa_2026.mp4", "video_playa_2026")

    fun pickFromCamera() = pick("grabacion_camara.mp4", "grabacion_camara")

    private fun pick(fileName: String, title: String) {
        uploadStep = UploadStep.PICKED
        pickedFileName = fileName
        uploadTitle = title
    }

    fun startUpload() {
        uploadStep = UploadStep.UPLOADING
        uploadProgress = 0
    }

    /** One tick of the simulated upload; flips to DONE once it reaches 100%. */
    fun advanceUpload() {
        uploadProgress = (uploadProgress + UPLOAD_STEP_PERCENT).coerceAtMost(100)
        if (uploadProgress >= 100) uploadStep = UploadStep.DONE
    }

    fun resetUpload() {
        uploadStep = UploadStep.IDLE
        pickedFileName = ""
        uploadTitle = ""
        uploadDesc = ""
        uploadProgress = 0
    }

    fun openAuth(mode: AuthMode) {
        authMode = mode
        authIntent = AuthIntent.ACCOUNT
        overlay = Overlay.AUTH
    }

    fun switchAuthMode(mode: AuthMode) {
        authMode = mode
    }

    fun submitAuth() {
        val name = when (authMode) {
            AuthMode.LOGIN -> loginUsername.ifBlank { "Tú" }
            AuthMode.REGISTER -> registerName.ifBlank { "Tú" }
        }
        val handle = when (authMode) {
            AuthMode.LOGIN -> loginUsername.ifBlank { "tu_usuario" }
            AuthMode.REGISTER -> registerUsername.ifBlank { "tu_usuario" }
        }
        loggedIn = true
        username = name
        usernameHandle = handle
        resetUpload()
        if (authIntent == AuthIntent.UPLOAD) {
            overlay = Overlay.UPLOAD
        } else {
            overlay = null
            tab = Tab.YOU
        }
    }
}

@Composable
fun rememberClonetubeAppState(): ClonetubeAppState = remember { ClonetubeAppState() }
