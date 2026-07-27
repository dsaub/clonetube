package me.elordenador.clonetube.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import me.elordenador.clonetube.ui.components.IconPlay
import me.elordenador.clonetube.ui.components.PrimaryButton
import me.elordenador.clonetube.ui.components.SecondaryButton
import me.elordenador.clonetube.ui.components.SolidDivider
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
    val video = state.watchVideo
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

        MockPlayer(video.coverIndex)

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
            Text(
                text = "${video.date} · ${video.duration}",
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
                            text = video.author,
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

            Text(
                text = video.description,
                color = Neutral300,
                fontSize = 13.sp,
                lineHeight = 21.sp,
                modifier = Modifier.padding(top = 14.dp),
            )

            Text(
                text = "MÁS VIDEOS",
                color = Neutral500,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.04.sp,
                modifier = Modifier.padding(top = 20.dp, bottom = 10.dp),
            )
            state.otherVideos.forEach { other ->
                VideoRow(
                    video = other,
                    meta = "${other.author} · ${other.duration}",
                    thumbWidth = 108.dp,
                    onClick = { state.openWatch(other.id) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Static stand-in for the player: cover art, a play badge and a scrub line. */
@Composable
private fun MockPlayer(coverIndex: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(coverBrush(coverIndex)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(CoverScrim.copy(alpha = 0.55f))
                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            IconPlay(size = 22.dp)
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(3.dp)
                .background(Color.White.copy(alpha = 0.18f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.38f)
                    .fillMaxHeight()
                    .background(Accent)
            )
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
