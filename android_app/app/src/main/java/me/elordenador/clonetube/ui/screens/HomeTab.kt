package me.elordenador.clonetube.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.elordenador.clonetube.model.VideoItem
import me.elordenador.clonetube.ui.components.Avatar
import me.elordenador.clonetube.ui.components.VideoCover
import me.elordenador.clonetube.ui.state.ClonetubeAppState
import me.elordenador.clonetube.ui.theme.Neutral500
import me.elordenador.clonetube.ui.theme.RADIUS_MD
import me.elordenador.clonetube.ui.theme.TextColor

@Composable
fun HomeTab(state: ClonetubeAppState) {
    val videos = state.homeFeed
    if (videos.isEmpty()) {
        Text(
            text = "No hay coincidencias para «${state.searchQuery}».",
            color = Neutral500,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 60.dp),
        )
        return
    }
    videos.forEach { video ->
        FeedCard(video, onClick = { state.openWatch(video.id) })
    }
}

@Composable
private fun FeedCard(video: VideoItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(bottom = 18.dp),
    ) {
        VideoCover(
            coverIndex = video.coverIndex,
            cornerRadius = RADIUS_MD.dp,
            playBadgeSize = 40.dp,
            duration = video.duration,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Avatar(video.initial, 30.dp, 12.sp)
            Column {
                Text(
                    text = video.title,
                    color = TextColor,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${video.author} · ${video.date}",
                    color = Neutral500,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}
