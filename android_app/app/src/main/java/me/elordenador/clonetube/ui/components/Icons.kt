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
