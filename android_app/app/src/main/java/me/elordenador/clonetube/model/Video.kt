package me.elordenador.clonetube.model

import kotlin.math.absoluteValue

private const val SAMPLE_AUTHOR = "Marta Ríos"

/** Visibility of a video, mirroring the backend's literal values. */
enum class VideoVisibility { PUBLIC, UNLISTED, PRIVATE }

/** A video as consumed by the UI. Backed by the backend catalogue / feed / detail. */
data class VideoItem(
    val id: String,
    val key: String,
    val title: String,
    val description: String,
    val author: String,
    val handle: String,
    val authorId: String,
    val date: String,
    val duration: String?,
    val subscribed: Boolean,
    val likes: Int,
    val visibility: VideoVisibility,
    val coverIndex: Int,
) {
    val initial: String get() = author.take(1)
}

/** A channel as shown in the subscriptions rail. */
data class Channel(
    val handle: String,
    val name: String,
    val initial: String,
)

/** Aggregated channel profile shown by the channel overlay / tab. */
data class ChannelInfo(
    val id: String,
    val name: String,
    val handle: String,
    val initial: String,
    val videoCount: Int,
    val subCount: Int,
    val following: Boolean,
    val coverIndex: Int,
)

/** Deterministic cover gradient index derived from a stable seed (no thumbnails yet). */
fun coverIndexFor(seed: String): Int = (seed.hashCode().absoluteValue) % 3

/**
 * Mock catalogue used only when the app is rendered without a Context (Compose preview),
 * so the UI can be previewed without touching the network.
 */
val sampleVideos: List<VideoItem> = listOf(
    VideoItem("s1", "videos/s1.mp4", "Amanecer en el desierto de Tabernas", "", SAMPLE_AUTHOR, "martarios", "u1", "12 jul 2026", null, true, 0, VideoVisibility.PUBLIC, 0),
    VideoItem("s2", "videos/s2.mp4", "Cómo grabar audio limpio en exteriores", "", "Iker Zabala", "ikerz", "u2", "9 jul 2026", null, false, 0, VideoVisibility.PUBLIC, 1),
    VideoItem("s3", "videos/s3.mp4", "Recorrido nocturno por el barrio del Carmen", "", SAMPLE_AUTHOR, "martarios", "u1", "3 jul 2026", null, true, 0, VideoVisibility.PUBLIC, 2),
    VideoItem("s4", "videos/s4.mp4", "Probando el nuevo micro de solapa", "", "Nuria Puig", "nuriapuig", "u3", "29 jun 2026", null, false, 0, VideoVisibility.PUBLIC, 0),
    VideoItem("s5", "videos/s5.mp4", "Sesión de skate en la Marina", "", "Dídac Ferrer", "didacf", "u4", "21 jun 2026", null, true, 0, VideoVisibility.PUBLIC, 1),
    VideoItem("s6", "videos/s6.mp4", "Charla: cómo empecé a grabar viajes", "", SAMPLE_AUTHOR, "martarios", "u1", "14 jun 2026", null, true, 0, VideoVisibility.PUBLIC, 2),
)
