package me.elordenador.clonetube.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import me.elordenador.clonetube.ui.components.NeutralTag
import me.elordenador.clonetube.ui.components.NocturneCard
import me.elordenador.clonetube.ui.components.PrimaryButton
import me.elordenador.clonetube.ui.components.SecondaryButton
import me.elordenador.clonetube.ui.state.AuthMode
import me.elordenador.clonetube.ui.state.ClonetubeAppState
import me.elordenador.clonetube.ui.theme.DividerColor
import me.elordenador.clonetube.ui.theme.Neutral400
import me.elordenador.clonetube.ui.theme.Neutral500
import me.elordenador.clonetube.ui.theme.SurfaceColor
import me.elordenador.clonetube.ui.theme.TextColor

@Composable
fun YouTab(state: ClonetubeAppState) {
    if (state.loggedIn) SignedIn(state) else SignedOut(state)
}

@Composable
private fun SignedOut(state: ClonetubeAppState) {
    NocturneCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        padding = PaddingValues(horizontal = 18.dp, vertical = 28.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(SurfaceColor)
                .border(1.dp, DividerColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            IconPerson(Neutral400, size = 22.dp, strokeWidth = 1.6f)
        }
        Text("Inicia sesión", color = TextColor, fontSize = 17.sp, lineHeight = 20.sp)
        Text(
            text = "Guarda tus suscripciones y sube tus propios videos.",
            color = TextColor.copy(alpha = 0.8f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Iniciar sesión", onClick = { state.openAuth(AuthMode.LOGIN) })
            SecondaryButton("Registrarse", onClick = { state.openAuth(AuthMode.REGISTER) })
        }
    }
}

@Composable
private fun SignedIn(state: ClonetubeAppState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Avatar(state.accountInitial, 68.dp, 26.sp, Modifier.padding(bottom = 10.dp))
        Text(state.username, color = TextColor, fontSize = 25.sp, lineHeight = 28.sp)
        Text(
            text = "@${state.usernameHandle}",
            color = Neutral500,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
    FadingDivider(Modifier.padding(vertical = 11.dp))
    NeutralTag(
        text = "La gestión de canal vive en la app Studio",
        modifier = Modifier.padding(bottom = 14.dp),
    )
    SecondaryButton(
        label = "Cerrar sesión",
        onClick = state::logout,
        modifier = Modifier.fillMaxWidth(),
    )
}
