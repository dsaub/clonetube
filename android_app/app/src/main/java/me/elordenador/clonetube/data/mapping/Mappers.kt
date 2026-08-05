package me.elordenador.clonetube.data.mapping

import me.elordenador.clonetube.data.remote.dto.ChannelResponseDto
import me.elordenador.clonetube.data.remote.dto.ChannelVideoItemDto
import me.elordenador.clonetube.data.remote.dto.FeedVideoItemDto
import me.elordenador.clonetube.data.remote.dto.FollowingListResponse
import me.elordenador.clonetube.data.remote.dto.PublicUserDto
import me.elordenador.clonetube.data.remote.dto.StudioVideoItemDto
import me.elordenador.clonetube.data.remote.dto.VideoCatalogItemDto
import me.elordenador.clonetube.data.remote.dto.VideoDetailDto
import me.elordenador.clonetube.model.Channel
import me.elordenador.clonetube.model.ChannelInfo
import me.elordenador.clonetube.model.VideoItem
import me.elordenador.clonetube.model.VideoVisibility
import me.elordenador.clonetube.model.coverIndexFor
import me.elordenador.clonetube.util.formatSpanishDate

fun visibilityOf(value: String?): VideoVisibility = when (value?.lowercase()) {
    "unlisted" -> VideoVisibility.UNLISTED
    "private" -> VideoVisibility.PRIVATE
    else -> VideoVisibility.PUBLIC
}

fun VideoCatalogItemDto.toDomain(): VideoItem = VideoItem(
    id = id,
    key = filename,
    title = title,
    description = description,
    author = authorName,
    handle = authorUsername,
    authorId = authorId,
    date = "",
    duration = null,
    subscribed = false,
    likes = 0,
    visibility = VideoVisibility.PUBLIC,
    coverIndex = coverIndexFor(id),
)

fun FeedVideoItemDto.toDomain(): VideoItem = VideoItem(
    id = id,
    key = filename,
    title = title,
    description = description,
    author = authorName,
    handle = authorUsername,
    authorId = authorId,
    date = formatSpanishDate(createdAt),
    duration = null,
    subscribed = fromFollowedAuthor,
    likes = likes,
    visibility = VideoVisibility.PUBLIC,
    coverIndex = coverIndexFor(id),
)

fun VideoDetailDto.toDomain(): VideoItem = VideoItem(
    id = id,
    key = key,
    title = title,
    description = description,
    author = authorName,
    handle = authorUsername,
    authorId = authorId,
    date = "",
    duration = null,
    subscribed = false,
    likes = 0,
    visibility = visibilityOf(visibility),
    coverIndex = coverIndexFor(id),
)

fun StudioVideoItemDto.toDomain(): VideoItem = VideoItem(
    id = id,
    key = key,
    title = title,
    description = description,
    author = "",
    handle = "",
    authorId = "",
    date = formatSpanishDate(lastModified),
    duration = null,
    subscribed = false,
    likes = 0,
    visibility = visibilityOf(visibility),
    coverIndex = coverIndexFor(id),
)

fun FollowingListResponse.toDomain(): List<Channel> = users.map { it.toDomain() }

fun PublicUserDto.toDomain(): Channel = Channel(
    handle = username,
    name = fullName,
    initial = fullName.take(1).uppercase(),
)

fun ChannelResponseDto.toDomain(): ChannelInfo = ChannelInfo(
    id = id,
    name = fullName,
    handle = username,
    initial = fullName.take(1).uppercase(),
    videoCount = videoCount,
    subCount = followers,
    following = following,
    coverIndex = coverIndexFor(id),
)

fun ChannelVideoItemDto.toDomain(): VideoItem = VideoItem(
    id = id,
    key = key,
    title = title,
    description = description,
    author = "",
    handle = "",
    authorId = "",
    date = formatSpanishDate(createdAt),
    duration = null,
    subscribed = false,
    likes = likes,
    visibility = visibilityOf(visibility),
    coverIndex = coverIndexFor(id),
)
