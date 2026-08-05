package me.elordenador.clonetube.data.remote

import me.elordenador.clonetube.data.remote.dto.CompleteMultipartBody
import me.elordenador.clonetube.data.remote.dto.CompleteMultipartResponse
import me.elordenador.clonetube.data.remote.dto.FeedResponse
import me.elordenador.clonetube.data.remote.dto.StartMultipartResponse
import me.elordenador.clonetube.data.remote.dto.StudioVideoItemDto
import me.elordenador.clonetube.data.remote.dto.StudioVideoResponse
import me.elordenador.clonetube.data.remote.dto.StreamUrlResponse
import me.elordenador.clonetube.data.remote.dto.VideoCatalogResponse
import me.elordenador.clonetube.data.remote.dto.VideoDetailDto
import me.elordenador.clonetube.data.remote.dto.VideoUpdateRequest
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface VideoService {

    @POST("api/v1/video/start-multipart")
    suspend fun startMultipart(
        @Query("original_filename") name: String,
    ): StartMultipartResponse

    @PUT("api/v1/video/upload-chunk")
    suspend fun uploadChunk(
        @Query("filename") filename: String,
        @Query("upload_id") uploadId: String,
        @Query("chunk_number") chunkNumber: Int,
        @Body body: RequestBody,
    ): me.elordenador.clonetube.data.remote.dto.UploadChunkResponse

    @POST("api/v1/video/complete-multipart")
    suspend fun completeMultipart(@Body body: CompleteMultipartBody): CompleteMultipartResponse

    @HTTP(method = "DELETE", path = "api/v1/video/cancel-multipart", hasBody = false)
    suspend fun cancelMultipart(
        @Query("filename") filename: String,
        @Query("upload_id") uploadId: String,
    ): me.elordenador.clonetube.data.remote.dto.MessageResponse

    @GET("api/v1/video/feed")
    suspend fun feed(
        @Query("only_following") onlyFollowing: Boolean = false,
        @Query("limit") limit: Int = 200,
    ): FeedResponse

    @GET("api/v1/video/catalog")
    suspend fun catalog(): VideoCatalogResponse

    @GET("api/v1/video/detail")
    suspend fun detail(@Query("key") key: String): VideoDetailDto

    @GET("api/v1/video/stream-url")
    suspend fun streamUrl(@Query("key") key: String): StreamUrlResponse

    @GET("api/v1/video/studio")
    suspend fun studio(): StudioVideoResponse

    @PATCH("api/v1/video/{id}")
    suspend fun updateVideo(
        @Path("id") id: String,
        @Body body: VideoUpdateRequest,
    ): StudioVideoItemDto

    @DELETE("api/v1/video/{id}")
    suspend fun deleteVideo(@Path("id") id: String)
}
