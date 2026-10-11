package app.storymaker

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.lifecycleScope
import app.storymaker.camera.hasCameraPermission
import app.storymaker.media.SessionStore
import app.storymaker.navigation.StoryMakerNavHost
import app.storymaker.navigation.startRoute
import app.storymaker.ui.theme.StoryMakerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionStore: SessionStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No saved state means a fresh launch (not a restore after process death):
        // the previous session is abandoned (docs/ARCHITECTURE.md section 9).
        if (savedInstanceState == null) {
            lifecycleScope.launch { sessionStore.discardActive() }
        }
        // The app is always dark, so system bar icons stay light regardless of the system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            // Saved so a restored back stack keeps the start destination it was built with.
            val startWithCapture = rememberSaveable { hasCameraPermission(this) }
            StoryMakerTheme {
                StoryMakerNavHost(
                    startRoute = startRoute(startWithCapture),
                    sessionErrors = sessionStore.errors,
                    onExit = ::finish,
                )
            }
        }
    }
}
