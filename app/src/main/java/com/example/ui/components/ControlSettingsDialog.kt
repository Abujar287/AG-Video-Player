package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.DoubleClickAction
import com.example.data.model.PlayerControlButton
import com.example.data.model.SingleClickAction
import com.example.data.model.UserPreferences

private enum class SettingsTab(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    MENU("Menu", "Default entry mode, layout & hidden files", Icons.Rounded.Menu),
    UPDATE("Check Update", "GitHub version & APK download", Icons.Rounded.Refresh),
    PLAYBACK("Playback & Background", "Background play, PiP & SW decoder", Icons.Rounded.Headphones),
    GESTURES("Tap & Swipe Control", "Finger gestures, single/double click & swipe", Icons.Rounded.TouchApp),
    BUTTONS("Play Screen Buttons", "Show or hide video overlay buttons", Icons.Rounded.Visibility)
}

@Composable
fun ControlSettingsDialog(
    preferences: UserPreferences,
    currentVersionLabel: String,
    hasAvailableUpdate: Boolean,
    onDismiss: () -> Unit,
    onRefreshLibrary: () -> Unit,
    onOpenUpdateCheck: () -> Unit,
    onSingleClickActionChange: (SingleClickAction) -> Unit,
    onDoubleClickActionChange: (DoubleClickAction) -> Unit,
    onAlwaysBackgroundPlayChange: (Boolean) -> Unit,
    onAutoPipChange: (Boolean) -> Unit,
    onEnableSwipeGesturesChange: (Boolean) -> Unit,
    onGestureSeekStepChange: (Int) -> Unit,
    onDefaultSoftwareDecoderChange: (Boolean) -> Unit,
    onShowHiddenFilesChange: (Boolean) -> Unit,
    onMainDisplayModeChange: (String) -> Unit,
    onMainViewLayoutChange: (String) -> Unit,
    onOneFingerGestureChange: (String) -> Unit,
    onTwoFingerGestureChange: (String) -> Unit,
    onThreeFingerGestureChange: (String) -> Unit,
    onTogglePlayerButtonVisibility: (PlayerControlButton, Boolean) -> Unit,
    onShowAllButtons: () -> Unit
) {
    var activeTab by remember { mutableStateOf<SettingsTab?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 620.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = activeTab?.icon ?: Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeTab?.title ?: "Settings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = activeTab?.subtitle ?: "Tap a tab below to configure · $currentVersionLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { if (activeTab != null) activeTab = null else onDismiss() }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                // Body (clean normal PlusJakartaSans font throughout)
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (activeTab) {
                        null -> {
                            SettingsTab.entries.forEach { tab ->
                                val showNew = tab == SettingsTab.UPDATE && hasAvailableUpdate
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                        .clickable {
                                            if (tab == SettingsTab.UPDATE) {
                                                onDismiss()
                                                onOpenUpdateCheck()
                                            } else {
                                                activeTab = tab
                                            }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = null,
                                        tint = if (showNew) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = tab.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (showNew) {
                                                Text(
                                                    text = "NEW",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSecondary,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(MaterialTheme.colorScheme.secondary)
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = tab.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        SettingsTab.MENU -> {
                            SettingsGroup(title = "Main Interface & Layout") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onRefreshLibrary()
                                            onDismiss()
                                        }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Refresh Video Library", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                        Text("Scan storage folders for new videos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                SettingsDivider()
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "Default Entry Mode",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    TwoColSelector(
                                        options = listOf("folders" to "All Folders", "files" to "Files"),
                                        selectedValue = preferences.mainDisplayMode,
                                        onSelect = onMainDisplayModeChange
                                    )
                                    Text(
                                        text = "View Layout",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    TwoColSelector(
                                        options = listOf("grid" to "Grid", "list" to "List"),
                                        selectedValue = preferences.mainViewLayout,
                                        onSelect = onMainViewLayoutChange
                                    )
                                }
                                SettingsDivider()
                                SettingsSwitchRow(
                                    title = "Show Hidden Files",
                                    subtitle = "Display hidden files and folders starting with dot (.)",
                                    checked = preferences.showHiddenFiles,
                                    onCheckedChange = onShowHiddenFilesChange
                                )
                            }
                        }

                        SettingsTab.PLAYBACK -> {
                            SettingsGroup(title = "Playback & Background") {
                                SettingsSwitchRow(
                                    title = "Always Background Play",
                                    subtitle = "Keep playing audio when navigating back or minimizing player",
                                    checked = preferences.alwaysBackgroundPlay,
                                    onCheckedChange = onAlwaysBackgroundPlayChange
                                )
                                SettingsDivider()
                                SettingsSwitchRow(
                                    title = "Auto PiP on Home",
                                    subtitle = "Open mini player window when pressing Home",
                                    checked = preferences.autoPipOnHome,
                                    onCheckedChange = onAutoPipChange
                                )
                                SettingsDivider()
                                SettingsSwitchRow(
                                    title = "Default SW Decoder",
                                    subtitle = "Prefer Software decoder instead of Hardware",
                                    checked = preferences.defaultSoftwareDecoder,
                                    onCheckedChange = onDefaultSoftwareDecoderChange
                                )
                            }
                        }

                        SettingsTab.GESTURES -> {
                            SettingsGroup(title = "Tap & Swipe Controls") {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text("1 Finger Gesture", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    TwoColSelector(
                                        options = listOf(
                                            "Brightness / Volume Swipe" to "Brightness / Volume Swipe",
                                            "Seek & Controls" to "Seek & Controls",
                                            "None" to "None"
                                        ),
                                        selectedValue = preferences.oneFingerGesture,
                                        onSelect = onOneFingerGestureChange
                                    )

                                    Text("2 Finger Gesture", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    TwoColSelector(
                                        options = listOf(
                                            "Zoom / Scale" to "Zoom / Scale",
                                            "Seek ±10s" to "Seek ±10s",
                                            "Brightness / Vol" to "Brightness / Vol",
                                            "None" to "None"
                                        ),
                                        selectedValue = preferences.twoFingerGesture,
                                        onSelect = onTwoFingerGestureChange
                                    )

                                    Text("3 Finger Gesture", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    TwoColSelector(
                                        options = listOf(
                                            "Play / Pause" to "Play / Pause",
                                            "Lock / Unlock" to "Lock / Unlock",
                                            "Mute Audio" to "Mute Audio",
                                            "None" to "None"
                                        ),
                                        selectedValue = preferences.threeFingerGesture,
                                        onSelect = onThreeFingerGestureChange
                                    )

                                    Text("Single Click", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    TwoColSelector(
                                        options = SingleClickAction.entries.map { it to it.label },
                                        selectedValue = preferences.singleClickAction,
                                        onSelect = onSingleClickActionChange
                                    )

                                    Text("Double Click", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    TwoColSelector(
                                        options = DoubleClickAction.entries.map { it to it.label },
                                        selectedValue = preferences.doubleClickAction,
                                        onSelect = onDoubleClickActionChange
                                    )
                                }
                                SettingsDivider()
                                SettingsSwitchRow(
                                    title = "Swipe Gestures",
                                    subtitle = "Horizontal seek, left brightness, right volume",
                                    checked = preferences.enableSwipeGestures,
                                    onCheckedChange = onEnableSwipeGesturesChange
                                )
                                SettingsDivider()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Swipe Seek Step", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                        Text("Auto smooth scrub or fixed 10s / 20s step", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        listOf(0 to "Auto", 10 to "10s", 20 to "20s").forEach { (sec, label) ->
                                            val selected = preferences.gestureSeekStepSeconds == sec
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(
                                                        if (selected) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.surface
                                                    )
                                                    .clickable { onGestureSeekStepChange(sec) }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        SettingsTab.BUTTONS -> {
                            SettingsGroup(
                                title = "Player Screen Buttons",
                                action = {
                                    TextButton(onClick = onShowAllButtons) {
                                        Text("Show All", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val buttons = PlayerControlButton.entries
                                    buttons.chunked(2).forEach { pair ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            pair.forEach { btn ->
                                                val enabled = preferences.isButtonVisible(btn)
                                                Row(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(
                                                            if (enabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                                                        )
                                                        .clickable { onTogglePlayerButtonVisibility(btn, !enabled) }
                                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = iconForPlayerButton(btn),
                                                        contentDescription = null,
                                                        tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(
                                                        text = btn.label,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = if (enabled) FontWeight.SemiBold else FontWeight.Normal,
                                                        color = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Icon(
                                                        imageVector = if (enabled) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                                        contentDescription = null,
                                                        tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }

                        SettingsTab.UPDATE -> Unit
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                // Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (activeTab != null) {
                        TextButton(onClick = { activeTab = null }) {
                            Text("Back to Tabs", style = MaterialTheme.typography.labelLarge)
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Done",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

private fun iconForPlayerButton(btn: PlayerControlButton): ImageVector = when (btn) {
    PlayerControlButton.FIT -> Icons.Rounded.OpenInFull
    PlayerControlButton.ROTATE -> Icons.Rounded.ScreenRotation
    PlayerControlButton.SPEED -> Icons.Rounded.Speed
    PlayerControlButton.LOOP -> Icons.Rounded.Repeat
    PlayerControlButton.MUTE -> Icons.Rounded.VolumeUp
    PlayerControlButton.SLEEP -> Icons.Rounded.NightsStay
    PlayerControlButton.BG_PLAY -> Icons.Rounded.Headphones
    PlayerControlButton.TRACKS -> Icons.Rounded.Subtitles
    PlayerControlButton.DECODER -> Icons.Rounded.Memory
    PlayerControlButton.LOCK -> Icons.Rounded.Lock
    PlayerControlButton.PREV_NEXT -> Icons.Rounded.SkipNext
    PlayerControlButton.PIP -> Icons.Rounded.PictureInPictureAlt
}

@Composable
private fun SettingsGroup(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            action?.invoke()
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            content = content
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 14.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun <T> TwoColSelector(
    options: List<Pair<T, String>>,
    selectedValue: T,
    onSelect: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowItems.forEach { (value, label) ->
                    val selected = selectedValue == value
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surface
                            )
                            .clickable { onSelect(value) }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
