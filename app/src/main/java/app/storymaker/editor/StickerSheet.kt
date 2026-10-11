package app.storymaker.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.storymaker.R
import app.storymaker.stickers.Sticker
import app.storymaker.stickers.StickerCategory
import app.storymaker.stickers.filterStickers
import app.storymaker.ui.theme.Muted
import app.storymaker.ui.theme.PrimaryButtonContainer
import app.storymaker.ui.theme.PrimaryButtonContent
import app.storymaker.ui.theme.Surface
import app.storymaker.ui.theme.SurfaceHigher
import app.storymaker.ui.theme.SurfaceHighest

/**
 * Sticker library (design: `Stickers`): drag handle, search, category chips, and a 4-column grid.
 *
 * @param onDrag vertical drag of the handle area in pixels (positive = down)
 * @param onDragStopped drag released with the given vertical velocity
 * @param onSearchFocused the search field gained focus (the sheet should expand)
 */
@Composable
fun StickerSheet(
    stickers: List<Sticker>,
    onPick: (Sticker) -> Unit,
    onDrag: (Float) -> Unit,
    onDragStopped: (Float) -> Unit,
    onSearchFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<StickerCategory?>(null) }
    val focusManager = LocalFocusManager.current
    val names = stickers.associate { it.id to stringResource(it.name) }
    val visible = filterStickers(stickers, query, category) { names.getValue(it.id) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Surface, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(horizontal = 16.dp),
    ) {
        // Drag area: the handle plus some margin, so the sheet is easy to grab.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState(onDrag),
                    onDragStopped = { velocity -> onDragStopped(velocity) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(width = 36.dp, height = 4.dp).background(HandleColor, CircleShape))
        }
        SearchField(
            query = query,
            onQueryChange = { query = it },
            onFocused = onSearchFocused,
            onDone = { focusManager.clearFocus() },
        )
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CategoryChip(R.string.sticker_category_all, selected = category == null) { category = null }
            StickerCategory.entries.forEach { entry ->
                CategoryChip(entry.label, selected = category == entry) { category = entry }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (visible.isEmpty()) {
            Text(
                text = stringResource(R.string.sticker_none_found),
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(
                    bottom = 28.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
            ) {
                items(visible, key = { it.id }) { sticker ->
                    StickerTile(
                        sticker = sticker,
                        name = names.getValue(sticker.id),
                        onClick = {
                            focusManager.clearFocus()
                            onPick(sticker)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onFocused: () -> Unit,
    onDone: () -> Unit,
) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White, fontSize = 15.sp),
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onDone() }),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) onFocused() },
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(SurfaceHighest, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = Muted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.sticker_search_hint),
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                            color = Muted,
                        )
                    }
                    innerTextField()
                }
            }
        },
    )
}

@Composable
private fun CategoryChip(@StringRes label: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(CircleShape)
            .background(if (selected) PrimaryButtonContainer else SurfaceHighest)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(label),
            color = if (selected) PrimaryButtonContent else ChipText,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            ),
        )
    }
}

@Composable
private fun StickerTile(sticker: Sticker, name: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceHigher)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(sticker.drawable),
            contentDescription = name,
            modifier = Modifier.padding(6.dp),
        )
    }
}

private val HandleColor = Color(0xFF4A4A4A)
private val ChipText = Color(0xFFE5E5E5)
