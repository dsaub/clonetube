package me.elordenador.clonetube.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import me.elordenador.clonetube.ui.screens.AuthScreen
import me.elordenador.clonetube.ui.screens.ChannelScreen
import me.elordenador.clonetube.ui.screens.MainScaffold
import me.elordenador.clonetube.ui.screens.UploadScreen
import me.elordenador.clonetube.ui.screens.WatchScreen
import me.elordenador.clonetube.ui.state.ClonetubeAppState
import me.elordenador.clonetube.ui.state.Overlay
import me.elordenador.clonetube.ui.state.rememberClonetubeAppState
import me.elordenador.clonetube.ui.theme.currentPalette

/**
 * Root of the app. Like the prototype, a single state object decides whether the
 * tabbed main screen or one of the full-screen overlays is showing.
 */
@Composable
fun ClonetubeApp(
    state: ClonetubeAppState = rememberClonetubeAppState(),
    verifyCode: String? = null,
) {
    val p = currentPalette()
    LaunchedEffect(verifyCode) {
        if (verifyCode != null) state.startVerification(verifyCode)
    }

    // System back closes an overlay, mirroring its close button. The null check keeps
    // @Preview working, since previews provide no OnBackPressedDispatcherOwner.
    if (LocalOnBackPressedDispatcherOwner.current != null) {
        BackHandler(enabled = state.overlay != null) { state.closeOverlay() }
    }

    CompositionLocalProvider(LocalContentColor provides p.text) {
        Box(
            Modifier
                .fillMaxSize()
                .background(p.bg)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            when (state.overlay) {
                Overlay.WATCH -> WatchScreen(state)
                Overlay.CHANNEL -> ChannelScreen(state)
                Overlay.UPLOAD -> UploadScreen(state)
                Overlay.AUTH -> AuthScreen(state)
                null -> MainScaffold(state)
            }
        }
    }
}
