package me.elordenador.clonetube.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PublicUserDto(
    val id: String,
    val username: String,
    @SerialName("full_name") val fullName: String,
)

@Serializable
data class FollowingListResponse(val users: List<PublicUserDto>)

@Serializable
data class ChannelResponseDto(
    val id: String,
    val username: String,
    @SerialName("full_name") val fullName: String,
    val following: Boolean,
    val followers: Int,
    @SerialName("following_count") val followingCount: Int,
    @SerialName("video_count") val videoCount: Int,
)

@Serializable
data class ChannelVideoItemDto(
    val id: String,
    val key: String,
    val title: String,
    val description: String,
    val visibility: String,
    @SerialName("created_at") val createdAt: String,
    val likes: Int,
)

@Serializable
data class ChannelVideosResponse(
    val videos: List<ChannelVideoItemDto>,
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    val total: Int,
    val pages: Int,
)

@Serializable
data class FollowStateResponse(
    val username: String,
    val following: Boolean,
    val followers: Int,
)
