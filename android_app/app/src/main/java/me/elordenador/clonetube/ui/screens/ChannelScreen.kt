package me.elordenador.clonetube.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import me.elordenador.clonetube.ui.components.FadingDivider
import me.elordenador.clonetube.ui.components.IconBack
import me.elordenador.clonetube.ui.components.IconButtonBox
import me.elordenador.clonetube.ui.components.IconMore
import me.elordenador.clonetube.ui.components.VideoRow
import me.elordenador.clonetube.ui.state.ClonetubeAppState
import me.elordenador.clonetube.ui.theme.Accent100
import me.elordenador.clonetube.ui.theme.Accent800
import me.elordenador.clonetube.ui.theme.Bg
import me.elordenador.clonetube.ui.theme.DividerColor
import me.elordenador.clonetube.ui.theme.Neutral500
import me.elordenador.clonetube.ui.theme.RADIUS_MD
import me.elordenador.clonetube.ui.theme.TextColor
import me.elordenador.clonetube.ui.theme.coverBrush

@Composable
fun ChannelScreen(state: ClonetubeAppState) {
    val info = state.channelInfo
    val subscribed = state.isSubscribed(info.handle)

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconButtonBox(onClick = state::closeOverlay, size = 36.dp) { IconBack(TextColor) }
            Text(
                text = info.name,
                color = TextColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            // Full-bleed banner; the block below is pulled up so the avatar overlaps it.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .background(coverBrush(info.coverIndex))
            )

            Column(
                modifier = Modifier
                    .offset(y = (-28).dp)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Accent800)
                        .border(3.dp, Bg, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = info.initial,
                        color = Accent100,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Text(
                    text = info.name,
                    color = TextColor,
                    fontSize = 32.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.48).sp,
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                )
                Text(
                    text = "@${info.handle} · ${info.videoCount} videos · ${info.subCount} seguidores",
                    color = Neutral500,
                    fontSize = 12.sp,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp, bottom = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SubscribeButton(
                        subscribed = subscribed,
                        modifier = Modifier.weight(1f),
                    ) { state.toggleSubscription(info.handle) }
                    Box(
                        modifier = Modifier
                            .size(width = 44.dp, height = 32.dp)
                            .clip(RoundedCornerShape(RADIUS_MD.dp))
                            .border(1.dp, DividerColor, RoundedCornerShape(RADIUS_MD.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        IconMore(TextColor)
                    }
                }

                FadingDivider()
                Text(
                    text = "VIDEOS",
                    color = Neutral500,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.04.sp,
                    modifier = Modifier.padding(top = 14.dp, bottom = 10.dp),
                )
                state.channelVideos.forEach { video ->
                    VideoRow(
                        video = video,
                        meta = "${video.date} · ${video.duration}",
                        thumbWidth = 120.dp,
                        onClick = { state.openWatch(video.id) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
