package me.elordenador.clonetube.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.elordenador.clonetube.BuildConfig
import me.elordenador.clonetube.model.VideoItem
import me.elordenador.clonetube.ui.components.IconCheck
import me.elordenador.clonetube.ui.components.IconChevronDown
import me.elordenador.clonetube.ui.components.IconLink
import me.elordenador.clonetube.ui.components.IconMore
import me.elordenador.clonetube.ui.components.IconTrash
import me.elordenador.clonetube.ui.components.IconUpload
import me.elordenador.clonetube.ui.components.LabeledField
import me.elordenador.clonetube.ui.components.PrimaryButton
import me.elordenador.clonetube.ui.components.VideoCover
import me.elordenador.clonetube.ui.state.AuthMode
import me.elordenador.clonetube.ui.state.ClonetubeAppState
import me.elordenador.clonetube.ui.theme.RADIUS_MD
import me.elordenador.clonetube.ui.theme.currentPalette

@Composable
fun StudioTab(state: ClonetubeAppState) {
    val p = currentPalette()
    if (!state.loggedIn) {
        SignedOutStudio(state)
        return
    }

    var selected by remember { mutableStateOf<VideoItem?>(null) }
    val editing = selected ?: state.studioVideos.firstOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    "CLONETUBE STUDIO",
                    color = p.accentText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp,
                )
                Text(
                    "Contenido del canal",
                    color = p.text,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(p.chipSelected)
                    .padding(horizontal = 9.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${state.studioVideos.size} vídeo${if (state.studioVideos.size == 1) "" else "s"}",
                    color = p.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        PrimaryButton(
            label = "Subir vídeo",
            onClick = state::openUpload,
            modifier = Modifier.fillMaxWidth(),
            height = 48.dp,
            icon = { IconUpload(Color.White, size = 20.dp) },
        )

        if (editing != null) {
            EditorCard(
                video = editing,
                onSave = { title, desc ->
                    state.updateStudioVideo(editing.id, title, desc)
                },
                onDelete = { state.deleteStudioVideo(editing.id) },
            )
        }

        if (state.studioVideos.isEmpty()) {
            Text(
                "Aún no has subido vídeos. Pulsa «Subir vídeo» para empezar.",
                color = p.textMuted,
                fontSize = 13.sp,
            )
        } else {
            state.studioVideos.forEach { video ->
                StudioSummaryRow(
                    video = video,
                    selected = video.id == editing?.id,
                    onClick = { selected = video },
                )
            }
        }
    }
}

@Composable
private fun SignedOutStudio(state: ClonetubeAppState) {
    val p = currentPalette()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(p.surface)
            .border(1.dp, p.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(p.chipSelected),
            contentAlignment = Alignment.Center,
        ) {
            IconUpload(p.accentIcon, size = 24.dp)
        }
        Text("Sube tus propios vídeos", color = p.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(
            "Inicia sesión para acceder a tu estudio y gestionar el contenido.",
            color = p.textMuted,
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        PrimaryButton(
            "Iniciar sesión",
            onClick = { state.openAuth(AuthMode.LOGIN) },
            modifier = Modifier.fillMaxWidth(),
            height = 44.dp,
        )
    }
}

/** The design's video editor card: summary, visibility, title, description, actions. */
@Composable
private fun EditorCard(
    video: VideoItem,
    onSave: (String, String) -> Unit,
    onDelete: () -> Unit,
) {
    val p = currentPalette()
    val context = LocalContext.current
    var title by remember(video.id) { mutableStateOf(video.title) }
    var desc by remember(video.id) { mutableStateOf(video.description) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(p.surface)
            .border(1.dp, p.border, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            VideoCover(
                coverIndex = video.coverIndex,
                cornerRadius = 7.dp,
                modifier = Modifier.width(112.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    video.title.ifBlank { video.key },
                    color = p.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(video.date, color = p.textMuted2, fontSize = 12.sp)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            val clip = ClipData.newPlainText("link", BuildConfig.BASE_URL + "video/" + video.id)
                            context.getSystemService(Context.CLIPBOARD_SERVICE)
                                ?.let { cm -> (cm as ClipboardManager).setPrimaryClip(clip) }
                        }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    IconLink(p.accentText, size = 14.dp)
                    Text("Copiar enlace", color = p.accentText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Visibilidad", color = p.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(p.input)
                    .border(1.dp, p.borderInput, RoundedCornerShape(7.dp))
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(p.success)
                    )
                    Text("Público", color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                IconChevronDown(p.text, size = 19.dp)
            }
            Text(
                "Aparece en el listado de vídeos.",
                color = p.textMuted2,
                fontSize = 11.sp,
            )
        }

        LabeledField(
            label = "Título",
            value = title,
            onValueChange = { if (it.length <= 120) title = it },
        )
        LabeledField(
            label = "Descripción",
            value = desc,
            onValueChange = { desc = it },
            placeholder = "Añade una descripción…",
            singleLine = false,
            minHeight = 62.dp,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(
                "Guardar",
                onClick = { onSave(title, desc) },
                modifier = Modifier.weight(1f),
                height = 48.dp,
                cornerRadius = 9.dp,
                icon = { IconCheck(Color.White, size = 18.dp) },
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .border(1.dp, p.dangerBorder, RoundedCornerShape(9.dp))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    IconTrash(p.danger, size = 17.dp)
                    Text("Eliminar", color = p.danger, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun StudioSummaryRow(
    video: VideoItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val p = currentPalette()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RADIUS_MD.dp))
            .background(if (selected) p.selectedTab else p.surface)
            .border(1.dp, if (selected) p.accentText else p.border, RoundedCornerShape(RADIUS_MD.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VideoCover(
            coverIndex = video.coverIndex,
            cornerRadius = 7.dp,
            modifier = Modifier.width(96.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                video.title.ifBlank { video.key },
                color = p.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(video.date, color = p.textMuted2, fontSize = 12.sp)
        }
        IconMore(p.textMuted, size = 20.dp)
    }
}
