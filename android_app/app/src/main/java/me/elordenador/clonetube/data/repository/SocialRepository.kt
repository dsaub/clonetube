package me.elordenador.clonetube.data.repository

import me.elordenador.clonetube.data.mapping.toDomain
import me.elordenador.clonetube.data.remote.SocialService
import me.elordenador.clonetube.model.Channel
import me.elordenador.clonetube.model.ChannelInfo
import me.elordenador.clonetube.model.VideoItem

class SocialRepository(private val service: SocialService) {

    suspend fun following(): List<Channel> = service.following().toDomain()

    suspend fun channel(username: String): ChannelInfo = service.channel(username).toDomain()

    suspend fun channelVideos(username: String, page: Int = 1): List<VideoItem> =
        service.channelVideos(username, page).videos.map { it.toDomain() }

    suspend fun followState(username: String) = service.followState(username)

    suspend fun follow(username: String) = service.follow(username)

    suspend fun unfollow(username: String) = service.unfollow(username)
}
