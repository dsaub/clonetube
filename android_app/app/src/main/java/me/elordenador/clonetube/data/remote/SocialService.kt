package me.elordenador.clonetube.data.remote

import me.elordenador.clonetube.data.remote.dto.ChannelResponseDto
import me.elordenador.clonetube.data.remote.dto.ChannelVideosResponse
import me.elordenador.clonetube.data.remote.dto.FollowStateResponse
import me.elordenador.clonetube.data.remote.dto.FollowingListResponse
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface SocialService {

    @GET("api/v1/users/me/following")
    suspend fun following(): FollowingListResponse

    @GET("api/v1/users/{username}/channel")
    suspend fun channel(@Path("username") username: String): ChannelResponseDto

    @GET("api/v1/users/{username}/videos")
    suspend fun channelVideos(
        @Path("username") username: String,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20,
    ): ChannelVideosResponse

    @GET("api/v1/users/{username}/follow")
    suspend fun followState(@Path("username") username: String): FollowStateResponse

    @POST("api/v1/users/{username}/follow")
    suspend fun follow(@Path("username") username: String): FollowStateResponse

    @DELETE("api/v1/users/{username}/follow")
    suspend fun unfollow(@Path("username") username: String): FollowStateResponse
}
