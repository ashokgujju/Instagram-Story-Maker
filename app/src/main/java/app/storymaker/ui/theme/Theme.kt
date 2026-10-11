package app.storymaker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

// The app is dark-only with a fixed palette (no dynamic color, no light scheme).
private val ColorScheme = darkColorScheme(
    primary = PrimaryButtonContainer,
    onPrimary = PrimaryButtonContent,
    secondary = SurfaceHigh,
    onSecondary = OnBackground,
    tertiary = Accent,
    onTertiary = OnAccent,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnBackground,
    surfaceVariant = SurfaceHigher,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceContainerLowest = Background,
    surfaceContainerLow = Surface,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = SurfaceHigher,
    outline = Muted,
    outlineVariant = SurfaceHighest,
)

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp), // caption label
    small = RoundedCornerShape(12.dp), // inputs
    medium = RoundedCornerShape(16.dp), // sticker tiles, cards
    large = RoundedCornerShape(20.dp), // photo canvas
    extraLarge = RoundedCornerShape(28.dp), // dialogs
)

@Composable
fun StoryMakerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
