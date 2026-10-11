package app.storymaker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import app.storymaker.ui.theme.OnBackground
import app.storymaker.ui.theme.OnSurfaceVariant
import app.storymaker.ui.theme.SurfaceHigh
import app.storymaker.ui.theme.TextButtonContent

/**
 * The app's dialog: dark surface, title, 16sp body lines, and text buttons.
 *
 * @param body one or more paragraphs
 * @param dismissText omit for a single-button dialog
 */
@Composable
fun StoryDialog(
    title: String,
    body: List<String>,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    dismissText: String? = null,
    onDismiss: () -> Unit = onDismissRequest,
) {
    val buttonColors = ButtonDefaults.textButtonColors(contentColor = TextButtonContent)
    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = SurfaceHigh,
        titleContentColor = OnBackground,
        textContentColor = OnSurfaceVariant,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                body.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, colors = buttonColors) {
                Text(confirmText, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = dismissText?.let {
            {
                TextButton(onClick = onDismiss, colors = buttonColors) {
                    Text(it, style = MaterialTheme.typography.labelMedium)
                }
            }
        },
    )
}
