package me.elordenador.clonetube.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.elordenador.clonetube.ui.components.IconButtonBox
import me.elordenador.clonetube.ui.components.IconClose
import me.elordenador.clonetube.ui.components.LabeledField
import me.elordenador.clonetube.ui.components.PrimaryButton
import me.elordenador.clonetube.ui.state.AuthMode
import me.elordenador.clonetube.ui.state.ClonetubeAppState
import me.elordenador.clonetube.ui.theme.currentPalette

@Composable
fun AuthScreen(state: ClonetubeAppState) {
    val p = currentPalette()
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButtonBox(onClick = state::closeOverlay, size = 32.dp) { IconClose(p.text) }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
        ) {
            Text(
                text = if (state.authMode == AuthMode.LOGIN) "INICIAR SESIÓN" else "CREAR CUENTA",
                color = p.accentText,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.32.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )

            state.verifyNotice?.let { notice ->
                Text(
                    text = notice,
                    color = p.accentText,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                )
            }

            when (state.authMode) {
                AuthMode.LOGIN -> LoginForm(state)
                AuthMode.REGISTER -> RegisterForm(state)
            }
        }
    }
}

@Composable
private fun LoginForm(state: ClonetubeAppState) {
    val p = currentPalette()
    Heading("Bienvenido de nuevo", "Inicia sesión para continuar en Clonetube.")
    LabeledField(
        label = "Usuario",
        value = state.loginUsername,
        onValueChange = { state.loginUsername = it },
        modifier = Modifier.padding(bottom = 12.dp),
    )
    LabeledField(
        label = "Contraseña",
        value = state.loginPassword,
        onValueChange = { state.loginPassword = it },
        isPassword = true,
        keyboardType = KeyboardType.Password,
        modifier = Modifier.padding(bottom = 20.dp),
    )
    PrimaryButton(
        label = if (state.authLoading) "Cargando…" else "Iniciar sesión",
        onClick = state::submitAuth,
        enabled = state.loginEnabled && !state.authLoading,
        modifier = Modifier.fillMaxWidth(),
        height = 46.dp,
    )
    state.authError?.let { error ->
        Text(
            text = error,
            color = p.textMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    SwitchModePrompt(
        question = "¿Todavía no tienes cuenta?",
        action = "Regístrate",
        onClick = { state.switchAuthMode(AuthMode.REGISTER) },
    )
}

@Composable
private fun RegisterForm(state: ClonetubeAppState) {
    val p = currentPalette()
    Heading("Crea tu cuenta", "Completa tus datos para empezar.")
    LabeledField(
        label = "Usuario",
        value = state.registerUsername,
        onValueChange = { state.registerUsername = it },
        modifier = Modifier.padding(bottom = 12.dp),
    )
    LabeledField(
        label = "Nombre completo",
        value = state.registerName,
        onValueChange = { state.registerName = it },
        modifier = Modifier.padding(bottom = 12.dp),
    )
    LabeledField(
        label = "Correo electrónico",
        value = state.registerEmail,
        onValueChange = { state.registerEmail = it },
        keyboardType = KeyboardType.Email,
        modifier = Modifier.padding(bottom = 12.dp),
    )
    LabeledField(
        label = "Contraseña",
        value = state.registerPassword,
        onValueChange = { state.registerPassword = it },
        isPassword = true,
        keyboardType = KeyboardType.Password,
        modifier = Modifier.padding(bottom = 20.dp),
    )
    PrimaryButton(
        label = if (state.authLoading) "Cargando…" else "Registrarse",
        onClick = state::submitAuth,
        enabled = state.registerEnabled && !state.authLoading,
        modifier = Modifier.fillMaxWidth(),
        height = 46.dp,
    )
    state.authError?.let { error ->
        Text(
            text = error,
            color = p.textMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    SwitchModePrompt(
        question = "¿Ya tienes una cuenta?",
        action = "Inicia sesión",
        onClick = { state.switchAuthMode(AuthMode.LOGIN) },
    )
}

@Composable
private fun Heading(title: String, subtitle: String) {
    val p = currentPalette()
    Text(
        text = title,
        color = p.text,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.48).sp,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    Text(
        text = subtitle,
        color = p.textMuted,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        modifier = Modifier.padding(bottom = 20.dp),
    )
}

@Composable
private fun SwitchModePrompt(question: String, action: String, onClick: () -> Unit) {
    val p = currentPalette()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$question ", color = p.textMuted, fontSize = 13.sp)
        Text(
            text = action,
            color = p.accentText,
            fontSize = 13.sp,
            modifier = Modifier.clickable(onClick = onClick),
        )
    }
}

