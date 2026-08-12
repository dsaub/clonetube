package me.elordenador.clonetube.ui.screens

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.elordenador.clonetube.ui.components.IconButtonBox
import me.elordenador.clonetube.ui.components.IconBlockButton
import me.elordenador.clonetube.ui.components.IconCamera
import me.elordenador.clonetube.ui.components.IconCheck
import me.elordenador.clonetube.ui.components.IconClose
import me.elordenador.clonetube.ui.components.IconGallery
import me.elordenador.clonetube.ui.components.LabeledField
import me.elordenador.clonetube.ui.components.PrimaryButton
import me.elordenador.clonetube.ui.components.SecondaryButton
import me.elordenador.clonetube.ui.components.SolidDivider
import me.elordenador.clonetube.ui.state.ClonetubeAppState
import me.elordenador.clonetube.ui.state.UploadStep
import me.elordenador.clonetube.ui.theme.currentPalette

@Composable
fun UploadScreen(state: ClonetubeAppState) {
    val context = LocalContext.current
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { state.pickVideo(it, nameFromUri(context, it)) } }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CaptureVideo(),
    ) { ok -> if (ok) cameraUri?.let { state.pickVideo(it, nameFromUri(context, it)) } }

    val p = currentPalette()

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Subir vídeo", color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            IconButtonBox(onClick = state::closeOverlay, size = 32.dp) { IconClose(p.text) }
        }
        SolidDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            when (state.uploadStep) {
                UploadStep.IDLE -> SourcePicker(
                    onGallery = { galleryLauncher.launch(null) },
                    onCamera = {
                        val uri = createVideoUri(context)
                        cameraUri = uri
                        cameraLauncher.launch(uri)
                    },
                )
                UploadStep.PICKED -> DetailsForm(state)
                UploadStep.UPLOADING -> Progress(state)
                UploadStep.DONE -> Done(state)
            }
        }
    }
}

@Composable
private fun SourcePicker(onGallery: () -> Unit, onCamera: () -> Unit) {
    val p = currentPalette()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        IconBlockButton(
            label = "Elegir de la galería",
            onClick = onGallery,
            icon = { IconGallery(p.accentIcon) },
        )
        IconBlockButton(
            label = "Grabar con la cámara",
            onClick = onCamera,
            icon = { IconCamera(p.accentIcon) },
        )
    }
}

@Composable
private fun DetailsForm(state: ClonetubeAppState) {
    val p = currentPalette()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(p.surface)
            .border(1.dp, p.border, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconCamera(p.accent, size = 22.dp)
        Text(state.pickedFileName, color = p.textSecondary, fontSize = 13.sp)
    }
    LabeledField(
        label = "Título",
        value = state.uploadTitle,
        onValueChange = { if (it.length <= 120) state.uploadTitle = it },
        modifier = Modifier.padding(bottom = 12.dp),
    )
    LabeledField(
        label = "Descripción",
        value = state.uploadDesc,
        onValueChange = { state.uploadDesc = it },
        placeholder = "Cuenta de qué trata el video (opcional)",
        singleLine = false,
        minHeight = 90.dp,
        modifier = Modifier.padding(bottom = 16.dp),
    )
    PrimaryButton(
        label = if (state.uploadError != null) "Reintentar" else "Subir vídeo",
        onClick = state::startUpload,
        modifier = Modifier.fillMaxWidth(),
        height = 46.dp,
    )
    state.uploadError?.let { error ->
        Text(
            text = error,
            color = p.textMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun Progress(state: ClonetubeAppState) {
    val p = currentPalette()
    Column(Modifier.padding(top = 24.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape)
                .background(p.surfaceAlt),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(state.uploadProgress / 100f)
                    .fillMaxHeight()
                    .background(p.accent),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Subiendo ${state.pickedFileName}", color = p.textMuted, fontSize = 12.sp)
            Text("${state.uploadProgress}%", color = p.textMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Done(state: ClonetubeAppState) {
    val p = currentPalette()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(p.accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            IconCheck(p.accent, size = 26.dp)
        }
        Text(
            text = "Vídeo subido",
            color = p.text,
            fontSize = 25.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "«${state.uploadTitle}» ya está en tu biblioteca.",
            color = p.textMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        SecondaryButton("Subir otro", onClick = state::resetUpload)
    }
}

private fun nameFromUri(context: android.content.Context, uri: Uri): String {
    val projection = arrayOf(MediaStore.Video.Media.DISPLAY_NAME)
    context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val idx = c.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
            if (idx >= 0) return c.getString(idx)
        }
    }
    return uri.lastPathSegment ?: "video.mp4"
}

private fun createVideoUri(context: android.content.Context): Uri {
    val values = ContentValues().apply {
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
    }
    return context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        ?: Uri.parse("")
}
