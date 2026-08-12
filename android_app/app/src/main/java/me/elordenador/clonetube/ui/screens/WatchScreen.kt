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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import me.elordenador.clonetube.ui.components.IconBookmark
import me.elordenador.clonetube.ui.components.IconButtonBox
import me.elordenador.clonetube.ui.components.IconLike
import me.elordenador.clonetube.ui.components.IconMore
import me.elordenador.clonetube.ui.components.IconPlay
import me.elordenador.clonetube.ui.components.IconShare
import me.elordenador.clonetube.ui.components.PrimaryButton
import me.elordenador.clonetube.ui.components.SecondaryButton
import me.elordenador.clonetube.ui.components.SolidDivider
import me.elordenador.clonetube.ui.components.VideoPlayer
import me.elordenador.clonetube.ui.components.VideoRow
import me.elordenador.clonetube.ui.state.ClonetubeAppState
import me.elordenador.clonetube.ui.theme.currentPalette
import me.elordenador.clonetube.ui.theme.coverBrush

@Composable
fun WatchScreen(state: ClonetubeAppState) {
    val video = state.watchItem ?: return
    val subscribed = state.isSubscribed(video.handle)
    val p = currentPalette()

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconButtonBox(onClick = state::closeOverlay, size = 24.dp) {
                IconBack(p.accentText, size = 22.dp)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    "CLONETUBE",
                    color = p.accentText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.4.sp,
                )
                Text(
                    video.title,
                    color = p.textSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconMore(p.textMuted, size = 22.dp)
        }
        SolidDivider()

        val streamUrl = state.watchStreamUrl
        if (streamUrl != null) {
            VideoPlayer(url = streamUrl)
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(coverBrush(video.coverIndex))
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(p.playButton),
                        contentAlignment = Alignment.Center,
                    ) {
                        IconPlay(size = 26.dp)
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Live status + title.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(p.accentText)
                    )
                    Text(
                        "AHORA EN EMISIÓN",
                        color = p.accentText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.45.sp,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Text(
                    text = video.title,
                    color = p.textBright,
                    fontSize = 22.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            // Creator row with follow button.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { state.openChannel(video.handle) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Avatar(video.initial, 38.dp, 15.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = video.author.ifBlank { video.handle },
                            color = p.text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "@${video.handle} · ${video.duration ?: ""}",
                            color = p.textMuted,
                            fontSize = 11.sp,
                        )
                    }
                }
                SubscribePill(subscribed) { state.toggleSubscription(video.handle) }
            }

            // Actions: like / share / save.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                var liked by remember { mutableStateOf(false) }
                var saved by remember { mutableStateOf(false) }
                WatchAction(
                    label = "Me gusta",
                    selected = liked,
                    onClick = { liked = !liked },
                    modifier = Modifier.weight(1f),
                ) { IconLike(it, size = 17.dp) }
                WatchAction(
                    label = "Compartir",
                    onClick = {},
                    modifier = Modifier.weight(1f),
                ) { IconShare(it, size = 17.dp) }
                WatchAction(
                    label = "Guardar",
                    selected = saved,
                    onClick = { saved = !saved },
                    modifier = Modifier.weight(1f),
                ) { IconBookmark(it, size = 17.dp) }
            }

            if (video.description.isNotBlank()) {
                var expanded by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(p.surfaceAlt)
                        .border(1.dp, p.border, RoundedCornerShape(9.dp))
                        .clickable { expanded = !expanded }
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Descripción",
                            color = p.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (expanded) "Ocultar" else "Expandir",
                            color = p.textMuted,
                            fontSize = 11.sp,
                        )
                    }
                    Text(
                        video.description,
                        color = p.textMuted,
                        fontSize = 10.5f.sp,
                        lineHeight = 15.sp,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // "A continuación" suggested list.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "A continuación",
                        color = p.textBright,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        "Reproducción automática",
                        color = p.textMuted,
                        fontSize = 10.5f.sp,
                    )
                }
                state.otherVideos.forEach { other ->
                    val otherSub = other.duration?.let { " · $it" } ?: ""
                    VideoRow(
                        video = other,
                        meta = "${other.author.ifBlank { other.handle }}$otherSub",
                        thumbWidth = 126.dp,
                        onClick = { state.openWatch(other.id) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SubscribePill(
    subscribed: Boolean,
    onClick: () -> Unit,
) {
    if (subscribed) {
        SecondaryButton("Siguiendo", onClick, cornerRadius = 18.dp)
    } else {
        PrimaryButton("Seguir", onClick, cornerRadius = 18.dp)
    }
}

@Composable
private fun WatchAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    icon: @Composable (Color) -> Unit,
) {
    val p = currentPalette()
    val tint = if (selected) p.accentText else p.accentIcon
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(if (selected) p.selectedTab else p.surfaceAlt)
            .border(1.dp, if (selected) p.accentText else p.borderAlt, RoundedCornerShape(19.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        icon(tint)
        Text(
            label,
            color = p.textSecondary,
            fontSize = 10.5f.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
