package app.storymaker

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.storymaker.media.SessionStore
import app.storymaker.media.StartupCleanup
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class StoryMakerApp : Application() {

    @Inject
    lateinit var sessionStore: SessionStore

    @Inject
    lateinit var startupCleanup: StartupCleanup

    override fun onCreate() {
        super.onCreate()
        startupCleanup.run()
        // Save pending edits as soon as the app leaves the foreground; the process may be killed after that.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = sessionStore.flushAsync()
        })
    }
}
