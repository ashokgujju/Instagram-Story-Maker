package app.storymaker.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.storymaker.ui.theme.OnBackground
import app.storymaker.ui.theme.PrimaryButtonContent
import app.storymaker.ui.theme.Surface
import app.storymaker.ui.theme.SurfaceHighest

/** Rounded card holding [SettingsSwitchRow]s separated by dividers. */
@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(16.dp)),
        content = content,
    )
}

@Composable
fun SettingsDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(SurfaceHighest),
    )
}

/** A title and subtitle with a [StorySwitch]; the whole row toggles and is announced as a switch. */
@Composable
fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = SubtitleColor)
        }
        StorySwitch(checked = checked)
    }
}

/** The design's 52×32 switch: white track with a dark knob when on, dark track with a white knob when off. */
@Composable
fun StorySwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val track by animateColorAsState(if (checked) OnBackground else OffTrack, label = "track")
    val knob by animateColorAsState(if (checked) PrimaryButtonContent else OnBackground, label = "knob")
    val offset by animateDpAsState(if (checked) 20.dp else 0.dp, label = "offset")
    Box(
        modifier = modifier
            .size(width = 52.dp, height = 32.dp)
            .background(track, CircleShape)
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset { IntOffset(offset.roundToPx(), 0) }
                .size(26.dp)
                .background(knob, CircleShape),
        )
    }
}

private val SubtitleColor = Color(0xFFA3A3A3)
private val OffTrack = Color(0xFF3A3A3A)
