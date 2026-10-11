package app.storymaker.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.storymaker.R
import app.storymaker.ui.theme.Accent
import app.storymaker.ui.theme.DisabledIcon
import app.storymaker.ui.theme.OnAccent
import app.storymaker.ui.theme.OnBackground
import app.storymaker.ui.theme.PrimaryButtonContainer
import app.storymaker.ui.theme.PrimaryButtonContent
import app.storymaker.ui.theme.StoryMakerTheme
import app.storymaker.ui.theme.SurfaceHigh
import app.storymaker.ui.theme.TextButtonContent

/**
 * White pill: Continue, Next, Open app settings.
 *
 * @param compact smaller variant for top bars (the design's 44px Next, rounded up to the 48dp touch minimum)
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
    compact: Boolean = false,
) = PillButton(
    text = text,
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    icon = icon,
    containerColor = PrimaryButtonContainer,
    contentColor = PrimaryButtonContent,
    height = if (compact) 48.dp else 56.dp,
    textStyle = if (compact) MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp) else MaterialTheme.typography.labelLarge,
    horizontalPadding = if (compact) 20.dp else 24.dp,
)

/** Accent pill, reserved for "Share to Instagram Stories". */
@Composable
fun AccentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) = PillButton(
    text = text,
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    icon = icon,
    containerColor = Accent,
    contentColor = OnAccent,
    height = 56.dp,
    textStyle = MaterialTheme.typography.labelLarge,
)

/** Dark pill: Save. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) = PillButton(
    text = text,
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    icon = icon,
    containerColor = SurfaceHigh,
    contentColor = OnBackground,
    height = 48.dp,
    textStyle = MaterialTheme.typography.labelMedium,
)

/** Borderless button: Not now. */
@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = TextButtonContent),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium)
    }
}

/** Round icon button (back, close, undo…). The design shows 44px; Android needs a 48dp target. */
@Composable
fun RoundIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = SurfaceHigh,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        enabled = enabled,
        shape = CircleShape,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = containerColor,
            contentColor = OnBackground,
            disabledContainerColor = containerColor,
            disabledContentColor = DisabledIcon,
        ),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    @DrawableRes icon: Int?,
    containerColor: Color,
    contentColor: Color,
    height: Dp,
    textStyle: TextStyle,
    horizontalPadding: Dp = 24.dp,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(height),
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.38f),
            disabledContentColor = contentColor.copy(alpha = 0.6f),
        ),
        contentPadding = PaddingValues(horizontal = horizontalPadding),
    ) {
        if (icon != null) {
            Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text = text, style = textStyle)
    }
}

@Preview(backgroundColor = 0xFF0E0E0E, showBackground = true)
@Composable
private fun ButtonsPreview() {
    StoryMakerTheme {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton("Continue", onClick = {}, modifier = Modifier.fillMaxWidth())
            AccentButton("Share to Instagram Stories", onClick = {}, modifier = Modifier.fillMaxWidth())
            SecondaryButton("Save", onClick = {}, modifier = Modifier.fillMaxWidth())
            QuietButton("Not now", onClick = {})
            RoundIconButton(R.drawable.ic_arrow_back, contentDescription = "Back", onClick = {})
        }
    }
}
