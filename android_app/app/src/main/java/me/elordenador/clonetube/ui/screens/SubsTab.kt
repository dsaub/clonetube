package me.elordenador.clonetube.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.elordenador.clonetube.ui.components.SolidDivider
import me.elordenador.clonetube.ui.components.VideoRow
import me.elordenador.clonetube.ui.state.ClonetubeAppState
import me.elordenador.clonetube.ui.theme.Neutral500

@Composable
fun SubsTab(state: ClonetubeAppState) {
    // Rail of subscribed channels.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 6.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        state.subsChannels.forEach { channel ->
            Column(
                modifier = Modifier
                    .width(56.dp)
                    .clickable { state.openChannel(channel.handle) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ChannelAvatar(channel.initial)
                Text(
                    text = channel.name,
                    color = Neutral500,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    SolidDivider()

    state.subsVideos.forEach { video ->
                VideoRow(
            video = video,
            meta = video.date,
            thumbWidth = 130.dp,
            onClick = { state.openWatch(video.id) },
            verticalPadding = 10.dp,
            modifier = Modifier.fillMaxWidth(),
        )
        SolidDivider()
    }
}
