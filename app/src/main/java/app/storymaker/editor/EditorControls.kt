package app.storymaker.editor

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.storymaker.R
import app.storymaker.media.EditorTab
import app.storymaker.ui.theme.OnBackground
import app.storymaker.ui.theme.PrimaryButtonContainer
import app.storymaker.ui.theme.PrimaryButtonContent
import app.storymaker.ui.theme.Surface
import app.storymaker.ui.theme.TextButtonContent

/** Draw / Stickers segmented control. */
@Composable
fun EditorTabBar(selected: EditorTab, onSelect: (EditorTab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(26.dp))
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EditorTabButton(
            label = stringResource(R.string.editor_tab_draw),
            icon = R.drawable.ic_edit,
            selected = selected == EditorTab.Draw,
            onClick = { onSelect(EditorTab.Draw) },
            modifier = Modifier.weight(1f),
        )
        EditorTabButton(
            label = stringResource(R.string.editor_tab_stickers),
            icon = R.drawable.ic_sticky_note_2,
            selected = selected == EditorTab.Stickers,
            onClick = { onSelect(EditorTab.Stickers) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun EditorTabButton(
    label: String,
    @DrawableRes icon: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (selected) PrimaryButtonContent else TextButtonContent
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(if (selected) PrimaryButtonContainer else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            ),
        )
    }
}

/** The 7 brush colors; the selected one has a white ring. */
@Composable
fun BrushSwatches(selectedColor: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BrushPalette.forEach { brush ->
            val argb = brush.color.toArgb()
            val selected = argb == selectedColor
            val name = stringResource(brush.name)
            // Seven swatches share the row equally (48dp each where the screen allows).
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(CircleShape)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(argb) })
                    .semantics { contentDescription = name },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Box(Modifier.size(40.dp).border(2.dp, OnBackground, CircleShape))
                }
                Box(
                    Modifier
                        .size(28.dp)
                        .background(brush.color, CircleShape)
                        .then(if (brush.color == Color(0xFF111111)) Modifier.border(1.dp, BlackSwatchBorder, CircleShape) else Modifier),
                )
            }
        }
    }
}

/** Brush size slider between a small and a large dot. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrushSizeSlider(sizeDp: Float, onSizeChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.editor_brush_size)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(6.dp).background(OnBackground, CircleShape))
        val colors = SliderDefaults.colors(
            thumbColor = OnBackground,
            activeTrackColor = OnBackground,
            inactiveTrackColor = InactiveTrack,
        )
        // Thin track with a round thumb, as in the design (not the default expressive style).
        Slider(
            value = sizeDp,
            onValueChange = onSizeChange,
            valueRange = MIN_BRUSH_SIZE_DP..MAX_BRUSH_SIZE_DP,
            colors = colors,
            thumb = { Box(Modifier.size(20.dp).background(OnBackground, CircleShape)) },
            track = { state ->
                SliderDefaults.Track(
                    sliderState = state,
                    modifier = Modifier.height(4.dp),
                    colors = colors,
                    drawStopIndicator = null,
                    thumbTrackGapSize = 0.dp,
                )
            },
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = label },
        )
        Box(Modifier.size(18.dp).background(OnBackground, CircleShape))
    }
}

private val BlackSwatchBorder = Color(0xFF4A4A4A)
private val InactiveTrack = Color(0xFF3A3A3A)
