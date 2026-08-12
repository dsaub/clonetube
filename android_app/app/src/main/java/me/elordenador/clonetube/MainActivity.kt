package me.elordenador.clonetube

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.tooling.preview.Preview
import me.elordenador.clonetube.ui.ClonetubeApp
import me.elordenador.clonetube.ui.state.ClonetubeAppState
import me.elordenador.clonetube.ui.state.rememberClonetubeAppState
import me.elordenador.clonetube.ui.theme.ClonetubeTheme

class MainActivity : ComponentActivity() {

    /** Verification code captured from an Android App Link, forwarded to the UI. */
    private val pendingVerifyCode = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            // The design ships dark and light variants; the system bars follow the theme.
            val dark = isSystemInDarkTheme()
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle =
                        if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                        else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    navigationBarStyle =
                        if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                        else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
            }
            ClonetubeTheme {
                ClonetubeApp(rememberClonetubeAppState(), pendingVerifyCode.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.host == "clonetube.aws.elordenador.org" &&
            data.path?.startsWith("/api/v1/auth/verify/") == true
        ) {
            pendingVerifyCode.value = data.lastPathSegment
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
fun ClonetubeAppPreview() {
    ClonetubeTheme {
        ClonetubeApp(ClonetubeAppState())
    }
}
