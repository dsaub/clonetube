package me.elordenador.clonetube.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.elordenador.clonetube.model.VideoItem
import me.elordenador.clonetube.ui.components.Avatar
import me.elordenador.clonetube.ui.components.IconButtonBox
import me.elordenador.clonetube.ui.components.IconCheck
import me.elordenador.clonetube.ui.components.IconMore
import me.elordenador.clonetube.ui.components.IconRefresh
import me.elordenador.clonetube.ui.components.VideoCover
import me.elordenador.clonetube.ui.state.ClonetubeAppState
import me.elordenador.clonetube.ui.state.FeedFilter
import me.elordenador.clonetube.ui.theme.currentPalette

@Composable
fun HomeTab(state: ClonetubeAppState) {
    val p = currentPalette()
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (state.searchOpen) SearchRow(state)

        FeedFilters(state)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    "EMISIONES RECIENTES",
                    color = p.accentText,
                    fontSize = 10.5f.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.8.sp,
                )
                Text(
                    if (state.feedFilter == FeedFilter.FOLLOWING) "De tus canales" else "Videos disponibles",
                    color = p.text,
                    fontSize = 26.sp,
                    lineHeight = 31.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.7).sp,
                )
            }
            IconButtonBox(onClick = state::refreshHome, size = 40.dp) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(p.surfaceAlt)
                        .border(1.dp, p.borderAlt, RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    IconRefresh(p.accentIcon, size = 18.dp)
                }
            }
        }

        val videos = state.homeFeed
        if (videos.isEmpty()) {
            FeedStatusCard(
                message = when {
                    state.feedFilter == FeedFilter.FOLLOWING -> "Todavía no sigues a nadie."
                    state.searchQuery.isNotBlank() -> "No hay coincidencias para «${state.searchQuery}»."
                    else -> "No hay vídeos disponibles ahora mismo."
                },
            )
        } else {
            videos.forEach { video ->
                FeedCard(video, onClick = { state.openWatch(video.id) })
            }
        }
    }
}

@Composable
private fun FeedFilters(state: ClonetubeAppState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            label = "Para ti",
            selected = state.feedFilter == FeedFilter.ALL,
            onClick = { state.feedFilter = FeedFilter.ALL },
        )
        FilterChip(
            label = "Siguiendo",
            selected = state.feedFilter == FeedFilter.FOLLOWING,
            onClick = { state.feedFilter = FeedFilter.FOLLOWING },
        )
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val p = currentPalette()
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) p.chipSelected else p.surfaceAlt)
            .border(
                1.dp,
                if (selected) p.accentText else p.borderAlt,
                RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) p.accentLabel else p.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun FeedCard(video: VideoItem, onClick: () -> Unit) {
    val p = currentPalette()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VideoCover(
            coverIndex = video.coverIndex,
            cornerRadius = 14.dp,
            playBadgeSize = 64.dp,
            duration = video.duration,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(video.initial, 42.dp, 15.sp)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = video.title,
                    color = p.text,
                    fontSize = 16.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${video.author.ifBlank { video.handle }} · @${video.handle}",
                    color = p.textSecondary,
                    fontSize = 12.5f.sp,
                )
                Text(
                    text = video.date,
                    color = p.textMuted2,
                    fontSize = 12.sp,
                )
            }
            IconMore(p.textMuted, size = 19.dp)
        }
    }
}

/** The "Estás al día" card shown when the feed has no content. */
@Composable
private fun FeedStatusCard(message: String) {
    val p = currentPalette()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(p.surface)
            .border(1.dp, p.border, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(p.chipSelected),
            contentAlignment = Alignment.Center,
        ) {
            IconCheck(p.accentText, size = 18.dp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "Estás al día",
                color = p.accentLabel,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                message,
                color = p.textMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Start,
            )
        }
    }
}
