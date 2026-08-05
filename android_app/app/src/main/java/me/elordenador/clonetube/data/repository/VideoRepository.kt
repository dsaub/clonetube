package me.elordenador.clonetube.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import me.elordenador.clonetube.BuildConfig
import me.elordenador.clonetube.data.mapping.toDomain
import me.elordenador.clonetube.data.remote.VideoService
import me.elordenador.clonetube.data.remote.dto.CompleteMultipartBody
import me.elordenador.clonetube.data.remote.dto.PartInfo
import me.elordenador.clonetube.data.remote.dto.VideoUpdateRequest
import me.elordenador.clonetube.model.VideoItem
import me.elordenador.clonetube.model.VideoVisibility
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream

/** 5 MB parts, matching the frontend and comfortably above S3's minimum part size. */
private const val CHUNK_BYTES = 5 * 1024 * 1024

class VideoRepository(private val service: VideoService) {

    suspend fun feed(onlyFollowing: Boolean = false): List<VideoItem> =
        service.feed(onlyFollowing).videos.map { it.toDomain() }

    suspend fun catalog(): List<VideoItem> = service.catalog().videos.map { it.toDomain() }

    suspend fun detail(key: String): VideoItem = service.detail(key).toDomain()

    /** Resolves the relative stream URL from the backend into an absolute one. */
    suspend fun streamUrl(key: String): String {
        val relative = service.streamUrl(key).url
        return if (relative.startsWith("http")) {
            relative
        } else {
            BuildConfig.BASE_URL + relative.removePrefix("/")
        }
    }

    suspend fun studio(): List<VideoItem> = service.studio().videos.map { it.toDomain() }

    suspend fun updateVideo(
        id: String,
        title: String,
        description: String,
        visibility: VideoVisibility,
    ): VideoItem = service.updateVideo(
        id,
        VideoUpdateRequest(title, description, visibility.name.lowercase()),
    ).toDomain()

    suspend fun deleteVideo(id: String) {
        service.deleteVideo(id)
    }

    // ── multipart upload ──────────────────────────────────────────────────────

    suspend fun startMultipart(name: String) = service.startMultipart(name)

    suspend fun uploadChunk(
        filename: String,
        uploadId: String,
        chunkNumber: Int,
        bytes: ByteArray,
    ) = service.uploadChunk(filename, uploadId, chunkNumber, bytes.toRequestBody("application/octet-stream".toMediaType()))

    suspend fun complete(filename: String, uploadId: String, parts: List<PartInfo>) =
        service.completeMultipart(CompleteMultipartBody(filename, uploadId, parts))

    suspend fun cancel(filename: String, uploadId: String) =
        service.cancelMultipart(filename, uploadId)

    /**
     * Streams the picked video into 5 MB parts, uploading each one via the proxied
     * `upload-chunk` endpoint and reporting progress (0-100). Returns the ETag list
     * needed to complete the upload.
     */
    suspend fun uploadFile(
        context: Context,
        uri: Uri,
        key: String,
        uploadId: String,
        onProgress: (Int) -> Unit,
    ): List<PartInfo> {
        val resolver = context.contentResolver
        val totalSize = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getLong(0).let { if (it < 0) null else it } else null
        }

        val stream: InputStream = resolver.openInputStream(uri)
            ?: throw IllegalStateException("No se pudo leer el vídeo seleccionado")

        stream.use { input ->
            val buffer = ByteArray(CHUNK_BYTES)
            val parts = mutableListOf<PartInfo>()
            var partNumber = 1
            var bytesReadTotal = 0L
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                val chunk = if (n == buffer.size) buffer else buffer.copyOf(n)
                val response = uploadChunk(key, uploadId, partNumber, chunk)
                parts.add(PartInfo(response.PartNumber, response.ETag))
                bytesReadTotal += n
                if (totalSize != null && totalSize > 0) {
                    onProgress(((bytesReadTotal * 100) / totalSize).toInt().coerceIn(0, 100))
                }
                partNumber++
            }
            if (parts.isEmpty()) throw IllegalStateException("El vídeo está vacío")
            return parts
        }
    }
}
