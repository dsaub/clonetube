package me.elordenador.clonetube.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.elordenador.clonetube.model.ChannelInfo
import me.elordenador.clonetube.model.VideoItem
import me.elordenador.clonetube.ui.components.Avatar
import me.elordenador.clonetube.ui.components.IconButtonBox
import me.elordenador.clonetube.ui.components.IconMore
import me.elordenador.clonetube.ui.components.IconPlay
import me.elordenador.clonetube.ui.components.IconPlus
import me.elordenador.clonetube.ui.components.IconSearch
import me.elordenador.clonetube.ui.components.PrimaryButton
import me.elordenador.clonetube.ui.components.SecondaryButton
import me.elordenador.clonetube.ui.components.VideoCover
import me.elordenador.clonetube.ui.components.VideoRow
import me.elordenador.clonetube.ui.state.ClonetubeAppState
import me.elordenador.clonetube.ui.state.formatFollowers
import me.elordenador.clonetube.ui.theme.currentPalette
import me.elordenador.clonetube.ui.theme.coverGlow

@Composable
fun ChannelScreen(state: ClonetubeAppState) {
    val info = state.channelInfo ?: return
    val subscribed = state.isSubscribed(info.handle)
    val p = currentPalette()

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(p.accent),
                contentAlignment = Alignment.Center,
            ) {
                IconPlay(size = 13.dp)
            }
            Text(
                "Clonetube",
                color = p.textBright,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButtonBox(onClick = {}, size = 42.dp) {
                IconSearch(p.textSecondary, size = 22.dp)
            }
            IconButtonBox(onClick = {}, size = 42.dp) {
                IconMore(p.textSecondary, size = 22.dp)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ChannelHeader(info, subscribed) { state.toggleSubscription(info.handle) }

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    "EMISIONES DEL CANAL",
                    color = p.accentText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Vídeos",
                        color = p.text,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${info.videoCount} vídeo${if (info.videoCount == 1) "" else "s"}",
                        color = p.textMuted,
                        fontSize = 13.sp,
                    )
                }
            }

            state.channelVideos.forEachIndexed { index, video ->
                if (index == 0) {
                    ChannelVideoCard(video) { state.openWatch(video.id) }
                } else {
                    VideoRow(
                        video = video,
                        meta = "${video.date}${video.duration?.let { " · $it" } ?: ""}",
                        thumbWidth = 120.dp,
                        onClick = { state.openWatch(video.id) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelHeader(
    info: ChannelInfo,
    subscribed: Boolean,
    onFollowToggle: () -> Unit,
) {
    val p = currentPalette()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(p.surface)
            .border(1.dp, p.border, RoundedCornerShape(12.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Avatar(info.initial, 68.dp, 28.sp, ring = p.accent)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    info.name,
                    color = p.textBright,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "@${info.handle} · ${info.videoCount} vídeo${if (info.videoCount == 1) "" else "s"}",
                    color = p.textMuted,
                    fontSize = 13.sp,
                )
                Text(
                    "${formatFollowers(info.subCount)} seguidores",
                    color = p.textMuted2,
                    fontSize = 12.sp,
                )
            }
        }
        if (subscribed) {
            SecondaryButton(
                "Siguiendo",
                onClick = onFollowToggle,
                modifier = Modifier.fillMaxWidth(),
                height = 44.dp,
            )
        } else {
            PrimaryButton(
                "Seguir",
                onClick = onFollowToggle,
                modifier = Modifier.fillMaxWidth(),
                height = 44.dp,
                icon = { IconPlus(androidx.compose.ui.graphics.Color.White, size = 18.dp) },
            )
        }
    }
}

@Composable
private fun ChannelVideoCard(video: VideoItem, onClick: () -> Unit) {
    val p = currentPalette()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box {
            VideoCover(
                coverIndex = video.coverIndex,
                cornerRadius = 12.dp,
                modifier = Modifier.fillMaxWidth(),
            )
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .align(Alignment.TopCenter)
                    .background(coverGlow(video.coverIndex)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(p.playButton),
                contentAlignment = Alignment.Center,
            ) {
                IconPlay(size = 28.dp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(video.initial, 38.dp, 15.sp)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    video.title,
                    color = p.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${video.author.ifBlank { video.handle }} · ${video.date}",
                    color = p.textMuted2,
                    fontSize = 12.sp,
                )
            }
            IconMore(p.textMuted, size = 22.dp)
        }
    }
}
