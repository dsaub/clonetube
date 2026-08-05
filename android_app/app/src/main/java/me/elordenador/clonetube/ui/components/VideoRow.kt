package me.elordenador.clonetube.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Text
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.elordenador.clonetube.model.VideoItem
import me.elordenador.clonetube.ui.theme.Neutral500
import me.elordenador.clonetube.ui.theme.RADIUS_SM
import me.elordenador.clonetube.ui.theme.TextColor

/**
 * The compact thumbnail-left list row shared by the subscriptions tab, the
 * "Más videos" list and the channel screen. Only the thumbnail width and the
 * metadata line differ between them.
 */
@Composable
fun VideoRow(
    video: VideoItem,
    meta: String,
    thumbWidth: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    verticalPadding: Dp = 8.dp,
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = verticalPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        VideoCover(
            coverIndex = video.coverIndex,
            cornerRadius = RADIUS_SM.dp,
            modifier = Modifier.width(thumbWidth),
        )
        Column {
            Text(
                text = video.title,
                color = TextColor,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = meta,
                color = Neutral500,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
