package me.elordenador.clonetube.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
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
import me.elordenador.clonetube.ui.state.UPLOAD_TICK_MILLIS
import me.elordenador.clonetube.ui.state.UploadStep
import me.elordenador.clonetube.ui.theme.Accent
import me.elordenador.clonetube.ui.theme.Accent100
import me.elordenador.clonetube.ui.theme.Accent800
import me.elordenador.clonetube.ui.theme.Neutral300
import me.elordenador.clonetube.ui.theme.Neutral400
import me.elordenador.clonetube.ui.theme.Neutral500
import me.elordenador.clonetube.ui.theme.RADIUS_MD
import me.elordenador.clonetube.ui.theme.SurfaceColor
import me.elordenador.clonetube.ui.theme.TextColor

@Composable
fun UploadScreen(state: ClonetubeAppState) {
    // Drives the simulated upload, replacing the prototype's setInterval.
    LaunchedEffect(state.uploadStep) {
        if (state.uploadStep != UploadStep.UPLOADING) return@LaunchedEffect
        while (state.uploadStep == UploadStep.UPLOADING) {
            delay(UPLOAD_TICK_MILLIS)
            state.advanceUpload()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Subir video", color = TextColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            IconButtonBox(onClick = state::closeOverlay, size = 32.dp) { IconClose(TextColor) }
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
                UploadStep.IDLE -> SourcePicker(state)
                UploadStep.PICKED -> DetailsForm(state)
                UploadStep.UPLOADING -> Progress(state)
                UploadStep.DONE -> Done(state)
            }
        }
    }
}

@Composable
private fun SourcePicker(state: ClonetubeAppState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        IconBlockButton(
            label = "Elegir de la galería",
            onClick = state::pickFromGallery,
            icon = { IconGallery(TextColor) },
        )
        IconBlockButton(
            label = "Grabar con la cámara",
            onClick = state::pickFromCamera,
            icon = { IconCamera(TextColor) },
        )
    }
}

@Composable
private fun DetailsForm(state: ClonetubeAppState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .clip(RoundedCornerShape(RADIUS_MD.dp))
            .background(SurfaceColor)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconCamera(Accent, size = 22.dp)
        Text(state.pickedFileName, color = Neutral300, fontSize = 13.sp)
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
        label = "Subir video",
        onClick = state::startUpload,
        modifier = Modifier.fillMaxWidth(),
        height = 46.dp,
    )
}

@Composable
private fun Progress(state: ClonetubeAppState) {
    Column(Modifier.padding(top = 24.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape)
                .background(SurfaceColor),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(state.uploadProgress / 100f)
                    .fillMaxHeight()
                    .background(Accent)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Subiendo ${state.pickedFileName}", color = Neutral500, fontSize = 12.sp)
            Text("${state.uploadProgress}%", color = Neutral500, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Done(state: ClonetubeAppState) {
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
                .background(Accent800),
            contentAlignment = Alignment.Center,
        ) {
            IconCheck(Accent100)
        }
        Text(
            text = "Video subido",
            color = TextColor,
            fontSize = 25.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "«${state.uploadTitle}» ya está en tu biblioteca.",
            color = Neutral400,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        SecondaryButton("Subir otro", onClick = state::resetUpload)
    }
}
