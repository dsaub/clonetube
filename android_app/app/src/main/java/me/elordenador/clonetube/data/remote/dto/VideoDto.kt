package me.elordenador.clonetube.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StartMultipartResponse(
    val uploadId: String,
    val key: String,
    @SerialName("original_filename") val originalFilename: String,
)

@Serializable
data class UploadChunkResponse(
    @SerialName("PartNumber") val partNumber: Int,
    @SerialName("ETag") val eTag: String,
)

@Serializable
data class PartInfo(
    @SerialName("PartNumber") val partNumber: Int,
    @SerialName("ETag") val eTag: String,
)

@Serializable
data class CompleteMultipartBody(
    val filename: String,
    val uploadId: String,
    val parts: List<PartInfo>,
)

@Serializable
data class CompleteMultipartResponse(
    val status: String,
    val location: String?,
    val key: String,
    @SerialName("original_filename") val originalFilename: String,
)

@Serializable
data class VideoCatalogItemDto(
    val id: Int,
    val filename: String,
    val title: String,
    val description: String,
    @SerialName("author_id") val authorId: String,
    @SerialName("author_username") val authorUsername: String,
    @SerialName("author_name") val authorName: String,
)

@Serializable
data class VideoCatalogResponse(val videos: List<VideoCatalogItemDto>)

@Serializable
data class FeedVideoItemDto(
    val id: Int,
    val filename: String,
    val title: String,
    val description: String,
    @SerialName("author_id") val authorId: String,
    @SerialName("author_username") val authorUsername: String,
    @SerialName("author_name") val authorName: String,
    @SerialName("created_at") val createdAt: String,
    val likes: Int,
    val score: Double,
    @SerialName("from_followed_author") val fromFollowedAuthor: Boolean,
)

@Serializable
data class FeedResponse(
    val videos: List<FeedVideoItemDto>,
    @SerialName("following_count") val followingCount: Int,
    val personalized: Boolean,
)

@Serializable
data class VideoDetailDto(
    val id: Int,
    val key: String,
    val title: String,
    val description: String,
    val visibility: String,
    @SerialName("author_id") val authorId: String,
    @SerialName("author_username") val authorUsername: String,
    @SerialName("author_name") val authorName: String,
)

@Serializable
data class StreamUrlResponse(val url: String, val key: String)

@Serializable
data class StudioVideoItemDto(
    val id: Int,
    val key: String,
    val size: Long,
    @SerialName("last_modified") val lastModified: String,
    @SerialName("original_filename") val originalFilename: String,
    val title: String,
    val description: String,
    val visibility: String,
    @SerialName("allowed_users") val allowedUsers: List<String>,
)

@Serializable
data class StudioVideoResponse(val videos: List<StudioVideoItemDto>)

@Serializable
data class VideoUpdateRequest(
    val title: String,
    val description: String,
    val visibility: String,
    @SerialName("allowed_users") val allowedUsers: List<String> = emptyList(),
)
