package me.elordenador.clonetube.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.elordenador.clonetube.ui.theme.Accent
import me.elordenador.clonetube.ui.theme.Accent100
import me.elordenador.clonetube.ui.theme.Accent800
import me.elordenador.clonetube.ui.theme.CoverScrim
import me.elordenador.clonetube.ui.theme.DividerColor
import me.elordenador.clonetube.ui.theme.Neutral100
import me.elordenador.clonetube.ui.theme.Neutral800
import me.elordenador.clonetube.ui.theme.RADIUS_MD
import me.elordenador.clonetube.ui.theme.SurfaceColor
import me.elordenador.clonetube.ui.theme.TextColor
import me.elordenador.clonetube.ui.theme.coverBrush

// ── buttons (.btn / .btn-primary / .btn-secondary) ─────────────────────────────

@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp? = null,
) = NocturneButton(label, onClick, modifier, enabled, height, Accent, Accent)

@Composable
fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp? = null,
) = NocturneButton(label, onClick, modifier, enabled, height, TextColor, DividerColor)

@Composable
private fun NocturneButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    height: Dp?,
    contentColor: Color,
    borderColor: Color,
) {
    val shape = RoundedCornerShape(RADIUS_MD.dp)
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f)
            .then(if (height != null) Modifier.height(height) else Modifier)
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .defaultMinSize(minHeight = 32.dp)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = contentColor,
            fontSize = 14.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** A full-width button with a leading icon, used by the upload source picker. */
@Composable
fun IconBlockButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(RADIUS_MD.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .border(1.dp, DividerColor, shape)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon()
        Text(label, color = TextColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

// ── forms (.field / .input) ────────────────────────────────────────────────────

@Composable
fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minHeight: Dp = 36.dp,
) {
    val shape = RoundedCornerShape(RADIUS_MD.dp)
    Column(modifier) {
        Text(
            text = label,
            color = TextColor.copy(alpha = 0.7f),
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 5.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = TextStyle(color = TextColor, fontSize = 14.sp),
            cursorBrush = SolidColor(Accent),
            visualTransformation =
                if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = minHeight)
                .clip(shape)
                .background(SurfaceColor)
                .border(1.dp, DividerColor, shape)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, color = TextColor.copy(alpha = 0.4f), fontSize = 14.sp)
                    }
                    inner()
                }
            },
        )
    }
}

// ── avatars, covers and small chrome ───────────────────────────────────────────

@Composable
fun Avatar(
    initial: String,
    size: Dp,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Accent800),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, color = Accent100, fontSize = fontSize, fontWeight = FontWeight.Bold)
    }
}

/** A mock video thumbnail: gradient cover, optional play glyph and duration badge. */
@Composable
fun VideoCover(
    coverIndex: Int,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 4.dp,
    playBadgeSize: Dp? = null,
    duration: String? = null,
) {
    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(cornerRadius))
            .background(coverBrush(coverIndex)),
        contentAlignment = Alignment.Center,
    ) {
        if (playBadgeSize != null) {
            Box(
                modifier = Modifier
                    .size(playBadgeSize)
                    .clip(CircleShape)
                    .background(CoverScrim.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                IconPlay(size = playBadgeSize * 0.4f)
            }
        }
        if (duration != null) {
            Text(
                text = duration,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CoverScrim.copy(alpha = 0.75f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/** The `.hr` rule, which fades out at both ends. */
@Composable
fun FadingDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.18f to DividerColor,
                    0.82f to DividerColor,
                    1f to Color.Transparent,
                )
            )
    )
}

/** A plain 1dp rule, used where the design draws solid box edges. */
@Composable
fun SolidDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(DividerColor)
    )
}

/** `.tag.tag-neutral` */
@Composable
fun NeutralTag(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Neutral800)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text, color = Neutral100, fontSize = 11.sp)
    }
}

/** A borderless circular tap target, used for the header back/close/upload buttons. */
@Composable
fun IconButtonBox(
    onClick: () -> Unit,
    size: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** `.card` + `.elev-sm` */
@Composable
fun NocturneCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(8.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(RADIUS_MD.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(SurfaceColor)
            .border(1.dp, Neutral800, shape)
            .padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}
