package app.storymaker.accessibility

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Guards the checker itself: an unlabeled control must be reported, so the screen checks can't pass vacuously. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class AccessibilitySanityTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun unlabeledControl_isReported() {
        compose.setContent { Box(Modifier.size(20.dp).background(Color.Gray).clickable {}) }
        val error = assertThrows(AssertionError::class.java) { compose.assertControlsAreAccessible() }
        assertTrue(error.message!!.contains("no label"))
    }
}
