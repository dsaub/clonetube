package me.elordenador.clonetube.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// The design's icons are inline SVGs on a 24x24 viewport. Rather than pull in an icon
// artifact, each one is drawn here from the same path data in that same 24-unit space.

private fun svg(data: String): Path = PathParser().parsePathString(data).toPath()

private val BackPath = svg("M15 5l-7 7 7 7")
private val PlayPath = svg("M8 5v14l11-7z")
private val ClosePath = svg("M6 6l12 12M18 6L6 18")
private val PlusPath = svg("M12 5v14M5 12h14")
private val PersonBodyPath = svg("M4 20c0-4 4-6 8-6s8 2 8 6")
private val SearchHandlePath = svg("M21 21l-4.3-4.3")
private val GalleryMountainPath = svg("M21 16l-5-5-5 5-3-3-4 4")
private val CameraBodyPath = svg("M4 8h3l2-2h6l2 2h3v11H4z")
private val CheckPath = svg("M4 12l5 5 11-11")
private val SubsArcPath = svg("M4 10a8 8 0 0116 0")
private val VideosTrianglePath = svg("M10 10l5 2-5 2z")
private val HomePath = svg("M3 10.5L12 3l9 7.5")
private val HomeRoofPath = svg("M5 9.5V21h14V9.5")
private val RefreshArcPath = svg("M21 12a9 9 0 1 1-2.64-6.36")
private val RefreshHeadPath = svg("M21 3v6h-6")
private val LinkPath = svg("M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.7 1.7")
private val LinkHandlePath = svg("M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.7-1.7")
private val ChevronDownPath = svg("M6 9l6 6 6-6")
private val HeartPath = svg("M12 20s-7-4.5-9.5-9a5.2 5.2 0 0 1 9.5-2.6A5.2 5.2 0 0 1 21.5 11c-2.5 4.5-9.5 9-9.5 9z")
private val SharePath = svg("M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8")
private val ShareHeadPath = svg("M16 6l-4-4-4 4M12 2v13")
private val BookmarkPath = svg("M6 3h12v18l-6-4-6 4z")
private val TrashLidPath = svg("M3 6h18")
private val TrashBodyPath = svg("M8 6V4h8v2M19 6l-1 14H6L5 6")
private val UploadArrowPath = svg("M12 15V3M7 8l5-5 5 5")
private val UploadTrayPath = svg("M5 21h14")
private val VolumeBodyPath = svg("M11 5 6 9H3v6h3l5 4z")
private val VolumeArcPath = svg("M15.5 8.5a5 5 0 0 1 0 7")
private val FullscreenPath = svg("M8 3H5a2 2 0 0 0-2 2v3M21 8V5a2 2 0 0 0-2-2h-3M3 16v3a2 2 0 0 0 2 2h3M16 21h3a2 2 0 0 0 2-2v-3")

@Composable
private fun IconCanvas(size: Dp, modifier: Modifier, draw: DrawScope.() -> Unit) {
    Canvas(modifier.size(size)) {
        val factor = this.size.minDimension / 24f
        scale(factor, factor, pivot = Offset.Zero) { draw() }
    }
}

private fun stroke(width: Float) =
    Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

@Composable
fun IconBack(tint: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) =
    IconCanvas(size, modifier) { drawPath(BackPath, tint, style = stroke(1.8f)) }

@Composable
fun IconPlay(tint: Color = Color.White, modifier: Modifier = Modifier, size: Dp = 16.dp) =
    IconCanvas(size, modifier) { drawPath(PlayPath, tint) }

@Composable
fun IconClose(tint: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) =
    IconCanvas(size, modifier) { drawPath(ClosePath, tint, style = stroke(1.8f)) }

@Composable
fun IconPlus(tint: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) =
    IconCanvas(size, modifier) { drawPath(PlusPath, tint, style = stroke(1.8f)) }

@Composable
fun IconPerson(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 17.dp,
    strokeWidth: Float = 1.8f,
) = IconCanvas(size, modifier) {
    drawCircle(tint, radius = 4f, center = Offset(12f, 8f), style = stroke(strokeWidth))
    drawPath(PersonBodyPath, tint, style = stroke(strokeWidth))
}

@Composable
fun IconSearch(tint: Color, modifier: Modifier = Modifier, size: Dp = 16.dp) =
    IconCanvas(size, modifier) {
        drawCircle(tint, radius = 7f, center = Offset(11f, 11f), style = stroke(2f))
        drawPath(SearchHandlePath, tint, style = stroke(2f))
    }

@Composable
fun IconGallery(tint: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) =
    IconCanvas(size, modifier) {
        drawRoundRect(
            tint,
            topLeft = Offset(3f, 4f),
            size = Size(18f, 16f),
            cornerRadius = CornerRadius(2f),
            style = stroke(1.6f),
        )
        drawCircle(tint, radius = 1.5f, center = Offset(8.5f, 10f), style = stroke(1.6f))
        drawPath(GalleryMountainPath, tint, style = stroke(1.6f))
    }

@Composable
fun IconCamera(tint: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) =
    IconCanvas(size, modifier) {
        drawPath(CameraBodyPath, tint, style = stroke(1.6f))
        drawCircle(tint, radius = 3.2f, center = Offset(12f, 13.5f), style = stroke(1.6f))
    }

@Composable
fun IconCheck(tint: Color, modifier: Modifier = Modifier, size: Dp = 26.dp) =
    IconCanvas(size, modifier) { drawPath(CheckPath, tint, style = stroke(2f)) }

@Composable
fun IconMore(tint: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) =
    IconCanvas(size, modifier) {
        drawCircle(tint, radius = 9f, center = Offset(12f, 12f), style = stroke(1.8f))
        listOf(8f, 12f, 16f).forEach { x ->
            drawCircle(tint, radius = 0.9f, center = Offset(x, 12f))
        }
    }

@Composable
fun IconVideos(tint: Color, modifier: Modifier = Modifier, size: Dp = 21.dp) =
    IconCanvas(size, modifier) {
        drawRoundRect(
            tint,
            topLeft = Offset(3f, 6f),
            size = Size(18f, 12f),
            cornerRadius = CornerRadius(2f),
            style = stroke(1.8f),
        )
        drawPath(VideosTrianglePath, tint)
    }

@Composable
fun IconSubscriptions(tint: Color, modifier: Modifier = Modifier, size: Dp = 21.dp) =
    IconCanvas(size, modifier) {
        drawPath(SubsArcPath, tint, style = stroke(1.8f))
        drawRoundRect(
            tint,
            topLeft = Offset(5f, 10f),
            size = Size(14f, 8f),
            cornerRadius = CornerRadius(2f),
            style = stroke(1.8f),
        )
    }

@Composable
fun IconHome(tint: Color, modifier: Modifier = Modifier, size: Dp = 21.dp) =
    IconCanvas(size, modifier) {
        drawPath(HomeRoofPath, tint, style = stroke(1.8f))
        drawPath(HomePath, tint, style = stroke(1.8f))
    }

@Composable
fun IconRefresh(tint: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) =
    IconCanvas(size, modifier) {
        drawPath(RefreshHeadPath, tint, style = stroke(1.8f))
        drawPath(RefreshArcPath, tint, style = stroke(1.8f))
    }

@Composable
fun IconLink(tint: Color, modifier: Modifier = Modifier, size: Dp = 14.dp) =
    IconCanvas(size, modifier) {
        drawPath(LinkHandlePath, tint, style = stroke(1.7f))
        drawPath(LinkPath, tint, style = stroke(1.7f))
    }

@Composable
fun IconChevronDown(tint: Color, modifier: Modifier = Modifier, size: Dp = 19.dp) =
    IconCanvas(size, modifier) { drawPath(ChevronDownPath, tint, style = stroke(1.8f)) }

@Composable
fun IconLike(tint: Color, modifier: Modifier = Modifier, size: Dp = 17.dp) =
    IconCanvas(size, modifier) { drawPath(HeartPath, tint, style = stroke(1.6f)) }

@Composable
fun IconShare(tint: Color, modifier: Modifier = Modifier, size: Dp = 17.dp) =
    IconCanvas(size, modifier) {
        drawPath(ShareHeadPath, tint, style = stroke(1.7f))
        drawPath(SharePath, tint, style = stroke(1.7f))
    }

@Composable
fun IconBookmark(tint: Color, modifier: Modifier = Modifier, size: Dp = 17.dp) =
    IconCanvas(size, modifier) { drawPath(BookmarkPath, tint, style = stroke(1.7f)) }

@Composable
fun IconTrash(tint: Color, modifier: Modifier = Modifier, size: Dp = 17.dp) =
    IconCanvas(size, modifier) {
        drawPath(TrashLidPath, tint, style = stroke(1.7f))
        drawPath(TrashBodyPath, tint, style = stroke(1.7f))
    }

@Composable
fun IconUpload(tint: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) =
    IconCanvas(size, modifier) {
        drawPath(UploadTrayPath, tint, style = stroke(1.8f))
        drawPath(UploadArrowPath, tint, style = stroke(1.8f))
    }

@Composable
fun IconVolume(tint: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) =
    IconCanvas(size, modifier) {
        drawPath(VolumeBodyPath, tint, style = stroke(1.7f))
        drawPath(VolumeArcPath, tint, style = stroke(1.7f))
    }

@Composable
fun IconFullscreen(tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) =
    IconCanvas(size, modifier) { drawPath(FullscreenPath, tint, style = stroke(1.8f)) }
