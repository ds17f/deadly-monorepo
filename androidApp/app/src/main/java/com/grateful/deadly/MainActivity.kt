package com.grateful.deadly

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.grateful.deadly.core.connect.ConnectService
import com.grateful.deadly.core.database.AnalyticsService
import com.grateful.deadly.theme.DeadlyMaterialTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var analyticsService: AnalyticsService
    @Inject lateinit var connectService: ConnectService

    private var deepLinkUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        analyticsService.track("app_open")

        deepLinkUri = intent?.data

        setContent {
            DeadlyMaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(
                        deepLinkUri = deepLinkUri,
                        onDeepLinkHandled = { deepLinkUri = null }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        connectService.startIfAuthenticated()
    }

    override fun onStop() {
        super.onStop()
        analyticsService.flush()
        connectService.stop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkUri = intent.data
    }

    // Intercept hardware volume keys. When playback is on a remote Connect
    // device, step the *remote* volume (Spotify Connect behavior) and consume
    // the event so the phone's own stream volume doesn't change. Otherwise,
    // fall through to normal system handling.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return super.dispatchKeyEvent(event)
        }
        val delta = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) VOLUME_STEP else -VOLUME_STEP
        // Only act on key-down; key-up just needs to be consumed if we consumed
        // the down so the OS doesn't half-process the press.
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (connectService.handleHardwareVolumeKey(delta)) return true
        } else if (event.action == KeyEvent.ACTION_UP) {
            // Probe with delta=0 — returns true iff a remote session is active.
            if (connectService.handleHardwareVolumeKey(0)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        private const val VOLUME_STEP = 2
    }
}
