package me.elordenador.clonetube

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.elordenador.clonetube.data.mapping.toDomain
import me.elordenador.clonetube.data.mapping.visibilityOf
import me.elordenador.clonetube.data.remote.dto.FeedVideoItemDto
import me.elordenador.clonetube.data.remote.dto.PartInfo
import me.elordenador.clonetube.data.remote.dto.UploadChunkResponse
import me.elordenador.clonetube.model.VideoVisibility
import me.elordenador.clonetube.util.formatSpanishDate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class MappingTest {

    @Test
    fun formatsIsoTimestampToSpanishDate() {
        assertEquals("12 jul 2026", formatSpanishDate("2026-07-12T10:00:00+00:00").lowercase(Locale.US))
        assertEquals("3 jul 2026", formatSpanishDate("2026-07-03T22:00:00Z").lowercase(Locale.US))
    }

    @Test
    fun parsesVisibilityLiterals() {
        assertEquals(VideoVisibility.PUBLIC, visibilityOf("public"))
        assertEquals(VideoVisibility.UNLISTED, visibilityOf("unlisted"))
        assertEquals(VideoVisibility.PRIVATE, visibilityOf("private"))
        assertEquals(VideoVisibility.PUBLIC, visibilityOf(null))
    }

    @Test
    fun preservesMultipartFieldNames() {
        val response = Json.decodeFromString<UploadChunkResponse>("""{"PartNumber":2,"ETag":"etag-2"}""")

        assertEquals(2, response.partNumber)
        assertEquals("etag-2", response.eTag)
        assertEquals(
            """{"PartNumber":2,"ETag":"etag-2"}""",
            Json.encodeToString(PartInfo(response.partNumber, response.eTag)),
        )
    }

    @Test
    fun mapsFeedItemToDomain() {
        val dto = FeedVideoItemDto(
            id = 123,
            filename = "videos/abc.mp4",
            title = "Mi video",
            description = "desc",
            authorId = "u1",
            authorUsername = "martarios",
            authorName = "Marta Ríos",
            createdAt = "2026-07-12T10:00:00+00:00",
            likes = 12,
            score = 0.9,
            fromFollowedAuthor = true,
        )
        val item = dto.toDomain()
        assertEquals("Mi video", item.title)
        assertEquals("martarios", item.handle)
        assertEquals("Marta Ríos", item.author)
        assertEquals(12, item.likes)
        assertEquals(true, item.subscribed)
        assertEquals("12 jul 2026", item.date)
        assertEquals(VideoVisibility.PUBLIC, item.visibility)
    }
}
