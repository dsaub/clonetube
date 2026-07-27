package me.elordenador.clonetube.model

/** A video in the mock catalogue. Mirrors the prototype's VIDEOS entries. */
data class Video(
    val id: Int,
    val title: String,
    val author: String,
    val handle: String,
    val date: String,
    val duration: String,
    val subscribed: Boolean,
    val description: String,
    /** Index into the three cover gradients, cycled by position as in the prototype. */
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

/** Aggregated channel profile shown by the channel overlay. */
data class ChannelInfo(
    val name: String,
    val handle: String,
    val initial: String,
    val videoCount: Int,
    val subCount: String,
    val coverIndex: Int,
)

val sampleVideos: List<Video> = listOf(
    Video(
        id = 1,
        title = "Amanecer en el desierto de Tabernas",
        author = "Marta Ríos",
        handle = "martarios",
        date = "12 jul 2026",
        duration = "14:22",
        subscribed = true,
        description = "Un timelapse grabado durante tres madrugadas seguidas en Tabernas, " +
            "con el equipo de siempre y un dron nuevo que casi no sobrevive al viento.",
        coverIndex = 0,
    ),
    Video(
        id = 2,
        title = "Cómo grabar audio limpio en exteriores",
        author = "Iker Zabala",
        handle = "ikerz",
        date = "9 jul 2026",
        duration = "08:47",
        subscribed = false,
        description = "Trucos rápidos de campo para quitarte el viento y el eco de encima " +
            "sin gastarte el sueldo en un estudio móvil.",
        coverIndex = 1,
    ),
    Video(
        id = 3,
        title = "Recorrido nocturno por el barrio del Carmen",
        author = "Marta Ríos",
        handle = "martarios",
        date = "3 jul 2026",
        duration = "21:10",
        subscribed = true,
        description = "Una caminata sin cortes por las calles del Carmen a las dos de la " +
            "madrugada, solo con el sonido ambiente.",
        coverIndex = 2,
    ),
    Video(
        id = 4,
        title = "Probando el nuevo micro de solapa",
        author = "Nuria Puig",
        handle = "nuriapuig",
        date = "29 jun 2026",
        duration = "05:33",
        subscribed = false,
        description = "Comparativa rápida contra el micro que llevaba usando dos años. " +
            "Spoiler: no todo es mejor por ser más caro.",
        coverIndex = 0,
    ),
    Video(
        id = 5,
        title = "Sesión de skate en la Marina",
        author = "Dídac Ferrer",
        handle = "didacf",
        date = "21 jun 2026",
        duration = "11:58",
        subscribed = true,
        description = "Una tarde entera grabando trucos con la peña en el skatepark de la " +
            "Marina, hasta que se fue la luz.",
        coverIndex = 1,
    ),
    Video(
        id = 6,
        title = "Charla: cómo empecé a grabar viajes",
        author = "Marta Ríos",
        handle = "martarios",
        date = "14 jun 2026",
        duration = "33:02",
        subscribed = true,
        description = "Cuento cómo empezó todo esto, los primeros vídeos horribles y lo que " +
            "cambiaría si volviera a empezar.",
        coverIndex = 2,
    ),
)
