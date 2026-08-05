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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.elordenador.clonetube.ui.components.Avatar
import me.elordenador.clonetube.ui.components.IconBack
import me.elordenador.clonetube.ui.components.IconButtonBox
import me.elordenador.clonetube.ui.components.PrimaryButton
import me.elordenador.clonetube.ui.components.SecondaryButton
import me.elordenador.clonetube.ui.components.SolidDivider
import me.elordenador.clonetube.ui.components.VideoPlayer
import me.elordenador.clonetube.ui.components.VideoRow
import me.elordenador.clonetube.ui.state.ClonetubeAppState
import me.elordenador.clonetube.ui.theme.Accent
import me.elordenador.clonetube.ui.theme.Accent300
import me.elordenador.clonetube.ui.theme.CoverScrim
import me.elordenador.clonetube.ui.theme.Neutral300
import me.elordenador.clonetube.ui.theme.Neutral400
import me.elordenador.clonetube.ui.theme.Neutral500
import me.elordenador.clonetube.ui.theme.TextColor
import me.elordenador.clonetube.ui.theme.coverBrush

@Composable
fun WatchScreen(state: ClonetubeAppState) {
    val video = state.watchItem ?: return
    val subscribed = state.isSubscribed(video.handle)

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
                text = "AHORA EN EMISIÓN",
                color = Accent300,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.96.sp,
            )
        }

        val streamUrl = state.watchStreamUrl
        if (streamUrl != null) {
            VideoPlayer(url = streamUrl)
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Accent),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(coverBrush(video.coverIndex))
                    .height(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Cargando vídeo…", color = Neutral300, fontSize = 13.sp)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                text = video.title,
                color = TextColor,
                fontSize = 18.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            val sub = video.duration?.let { " · $it" } ?: ""
            Text(
                text = "${video.date}$sub",
                color = Neutral400,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 14.dp),
            )

            SolidDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable { state.openChannel(video.handle) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Avatar(video.initial, 38.dp, 15.sp)
                    Column {
                        Text(
                            text = video.author.ifBlank { video.handle },
                            color = TextColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("@${video.handle}", color = Neutral500, fontSize = 12.sp)
                    }
                }
                SubscribeButton(subscribed) { state.toggleSubscription(video.handle) }
            }
            SolidDivider()

            if (video.description.isNotBlank()) {
                Text(
                    text = video.description,
                    color = Neutral300,
                    fontSize = 13.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }

            Text(
                text = "MÁS VIDEOS",
                color = Neutral500,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.04.sp,
                modifier = Modifier.padding(top = 20.dp, bottom = 10.dp),
            )
            state.otherVideos.forEach { other ->
                val otherSub = other.duration?.let { " · $it" } ?: ""
                VideoRow(
                    video = other,
                    meta = "${other.author.ifBlank { other.handle }}$otherSub",
                    thumbWidth = 108.dp,
                    onClick = { state.openWatch(other.id) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
internal fun SubscribeButton(
    subscribed: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    if (subscribed) {
        SecondaryButton("Siguiendo", onClick, modifier)
    } else {
        PrimaryButton("Seguir", onClick, modifier)
    }
}
