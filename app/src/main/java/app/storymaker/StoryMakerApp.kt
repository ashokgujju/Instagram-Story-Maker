package app.storymaker

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.storymaker.media.SessionStore
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class StoryMakerApp : Application() {

    @Inject
    lateinit var sessionStore: SessionStore

    override fun onCreate() {
        super.onCreate()
        // Save pending edits as soon as the app leaves the foreground; the process may be killed after that.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = sessionStore.flushAsync()
        })
    }
}
