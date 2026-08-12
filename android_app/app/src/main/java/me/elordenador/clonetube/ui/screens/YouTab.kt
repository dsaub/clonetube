package me.elordenador.clonetube.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.elordenador.clonetube.ui.components.Avatar
import me.elordenador.clonetube.ui.components.FadingDivider
import me.elordenador.clonetube.ui.components.IconPerson
import me.elordenador.clonetube.ui.components.PrimaryButton
import me.elordenador.clonetube.ui.components.SecondaryButton
import me.elordenador.clonetube.ui.state.AuthMode
import me.elordenador.clonetube.ui.state.ClonetubeAppState
import me.elordenador.clonetube.ui.theme.currentPalette

@Composable
fun YouTab(state: ClonetubeAppState) {
    if (state.loggedIn) SignedIn(state) else SignedOut(state)
}

@Composable
private fun SignedOut(state: ClonetubeAppState) {
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
            IconPerson(p.accentIcon, size = 22.dp, strokeWidth = 1.6f)
        }
        Text("Inicia sesión", color = p.text, fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text(
            text = "Guarda tus suscripciones y sube tus propios videos.",
            color = p.textMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Iniciar sesión", onClick = { state.openAuth(AuthMode.LOGIN) }, height = 42.dp)
            SecondaryButton("Registrarse", onClick = { state.openAuth(AuthMode.REGISTER) }, height = 42.dp)
        }
    }
}

@Composable
private fun SignedIn(state: ClonetubeAppState) {
    val p = currentPalette()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(p.surface)
            .border(1.dp, p.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Avatar(state.accountInitial, 68.dp, 26.sp, ring = p.accent)
        Text(
            state.username,
            color = p.textBright,
            fontSize = 25.sp,
            lineHeight = 28.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text("@${state.usernameHandle}", color = p.textMuted, fontSize = 12.sp)
        FadingDivider(Modifier.padding(top = 14.dp, bottom = 14.dp))
        SecondaryButton(
            label = "Cerrar sesión",
            onClick = state::logout,
            modifier = Modifier.fillMaxWidth(),
            height = 44.dp,
        )
    }
}
