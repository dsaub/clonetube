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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import me.elordenador.clonetube.ui.theme.RADIUS_MD
import me.elordenador.clonetube.ui.theme.currentPalette
import me.elordenador.clonetube.ui.theme.coverBrush

// ── buttons (.btn / .btn-primary / .btn-secondary) ─────────────────────────────

@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp? = null,
    cornerRadius: Dp = 10.dp,
    icon: (@Composable () -> Unit)? = null,
) {
    val p = currentPalette()
    val shape = RoundedCornerShape(cornerRadius)
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f)
            .then(if (height != null) Modifier.height(height) else Modifier)
            .clip(shape)
            .background(p.accent)
            .clickable(enabled = enabled, onClick = onClick)
            .defaultMinSize(minHeight = 32.dp)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp? = null,
    cornerRadius: Dp = 10.dp,
) {
    val p = currentPalette()
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f)
            .then(if (height != null) Modifier.height(height) else Modifier)
            .clip(shape)
            .border(1.dp, p.borderAlt, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .defaultMinSize(minHeight = 32.dp)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = p.textSecondary,
            fontSize = 14.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold,
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
    val p = currentPalette()
    val shape = RoundedCornerShape(RADIUS_MD.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(p.surfaceAlt)
            .border(1.dp, p.border, shape)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon()
        Text(label, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
    minHeight: Dp = 42.dp,
) {
    val p = currentPalette()
    val shape = RoundedCornerShape(7.dp)
    Column(modifier) {
        Text(
            text = label,
            color = p.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = TextStyle(color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            cursorBrush = SolidColor(p.accent),
            visualTransformation =
                if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = minHeight)
                .clip(shape)
                .background(p.input)
                .border(1.dp, p.borderInput, shape)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            placeholder,
                            color = p.textMuted2,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                        )
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
    ring: Color? = null,
) {
    val p = currentPalette()
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(if (ring != null) Modifier.border(2.dp, ring, CircleShape) else Modifier)
            .background(p.avatar),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initial,
            color = p.accentIcon,
            fontSize = fontSize,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

/** A mock video thumbnail: gradient cover, optional play glyph and duration badge. */
@Composable
fun VideoCover(
    coverIndex: Int,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = RADIUS_MD.dp,
    playBadgeSize: Dp? = null,
    duration: String? = null,
) {
    val p = currentPalette()
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
                    .background(p.playButton),
                contentAlignment = Alignment.Center,
            ) {
                IconPlay(size = playBadgeSize * 0.42f)
            }
        }
        if (duration != null) {
            Text(
                text = duration,
                color = p.badgeText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(p.badge)
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
    }
}

/** The header rule under the app / screen headers. */
@Composable
fun HeaderDivider(modifier: Modifier = Modifier) {
    val p = currentPalette()
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(p.divider)
    )
}

/** The `.hr` rule, which fades out at both ends. */
@Composable
fun FadingDivider(modifier: Modifier = Modifier) {
    val p = currentPalette()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.18f to p.divider,
                    0.82f to p.divider,
                    1f to Color.Transparent,
                )
            )
    )
}

/** A plain 1dp rule, used where the design draws solid box edges. */
@Composable
fun SolidDivider(modifier: Modifier = Modifier) {
    val p = currentPalette()
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(p.divider)
    )
}

/** `.tag.tag-neutral` */
@Composable
fun NeutralTag(text: String, modifier: Modifier = Modifier) {
    val p = currentPalette()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(p.chipSelected)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(text, color = p.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** A circular tap target, optionally tinted like the design's header icon buttons. */
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

/** `.card` */
@Composable
fun NocturneCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(8.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = currentPalette()
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(p.surface)
            .border(1.dp, p.border, shape)
            .padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}
