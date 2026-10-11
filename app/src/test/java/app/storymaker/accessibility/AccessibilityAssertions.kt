package app.storymaker.accessibility

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import org.junit.Assert.fail

/** Minimum touch target, as in the Android accessibility guidelines and docs/ARCHITECTURE.md section 17. */
private const val MIN_TOUCH_TARGET_DP = 48f

private val isControl = SemanticsMatcher("is a control") { node ->
    SemanticsActions.OnClick in node.config ||
        SemanticsProperties.ToggleableState in node.config ||
        SemanticsProperties.Selected in node.config
}

/** Fails with a list of every control that has no label or a touch area smaller than 48×48dp. */
fun ComposeContentTestRule.assertControlsAreAccessible() {
    val controls = onAllNodes(isControl).fetchSemanticsNodes()
    val density = density.density
    val problems = controls.mapNotNull { node ->
        val label = node.label()
        val touch = node.touchBoundsInRoot
        val widthDp = touch.width / density
        val heightDp = touch.height / density
        val issues = buildList {
            if (label.isBlank()) add("no label")
            if (widthDp < MIN_TOUCH_TARGET_DP - 0.5f || heightDp < MIN_TOUCH_TARGET_DP - 0.5f) {
                add("touch area %.1f×%.1fdp".format(widthDp, heightDp))
            }
        }
        if (issues.isEmpty()) null else "\"$label\" (${node.config.getOrNull(SemanticsProperties.Role) ?: "control"}): ${issues.joinToString()}"
    }
    if (controls.isEmpty()) fail("No controls found on screen")
    if (problems.isNotEmpty()) fail("Accessibility problems:\n" + problems.joinToString("\n"))
}

/** What a screen reader announces: the node's own or merged descendants' text and content description. */
private fun SemanticsNode.label(): String {
    val own = (config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
        config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
        listOfNotNull(config.getOrNull(SemanticsProperties.EditableText)?.text)).joinToString(" ")
    return own.ifBlank { children.joinToString(" ") { it.label() } }
}
