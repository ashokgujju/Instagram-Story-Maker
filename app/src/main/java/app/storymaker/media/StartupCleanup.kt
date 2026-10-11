package app.storymaker.media

import app.storymaker.ApplicationScope
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Housekeeping run once at app start (docs/ARCHITECTURE.md section 13), in the background. */
class StartupCleanup @Inject constructor(
    private val sessionStore: SessionStore,
    private val shareFiles: ShareFiles,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun run() {
        scope.launch {
            shareFiles.cleanUp()
            sessionStore.cleanUpOrphans()
        }
    }
}
