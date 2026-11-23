package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AppThemePreset
import com.example.data.model.DoubleClickAction
import com.example.data.model.PlayerControlButton
import com.example.data.model.ProgressBarStyle
import com.example.data.model.SingleClickAction
import com.example.data.model.UserPreferences
import com.example.ui.theme.resolveThemePrimaryColor

private enum class SettingsTab(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    MENU("Menu", "Theme, entry mode, layout & hidden files", Icons.Rounded.Menu),
    CONTROLS("Controls", "Progress bar style, screen buttons, shortcuts & gestures", Icons.Rounded.TouchApp),
    PLAYBACK("Playback & Background", "Background audio play, PiP & SW decoder", Icons.Rounded.Headphones),
    UPDATE("Check Update", "GitHub version & APK download", Icons.Rounded.Refresh)
}

private val QUICK_SHORTCUT_BUTTONS = listOf(
    PlayerControlButton.ZOOM,
    PlayerControlButton.MUTE,
    PlayerControlButton.ROTATE,
    PlayerControlButton.SPEED,
    PlayerControlButton.LOOP,
    PlayerControlButton.SLEEP,
    PlayerControlButton.BG_PLAY,
    PlayerControlButton.TRACKS
)

private val SCREEN_OVERLAY_BUTTONS = listOf(
    PlayerControlButton.FIT,
    PlayerControlButton.PIP,
    PlayerControlButton.LOCK,
    PlayerControlButton.PREV_NEXT,
    PlayerControlButton.DECODER
)

@Composable
fun FitScreenIcon(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val strokeWidth = w * 0.09f
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // Outer screen frame
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.08f, h * 0.17f),
            size = Size(w * 0.84f, h * 0.66f),
            cornerRadius = CornerRadius(w * 0.12f, w * 0.12f),
            style = stroke
        )

        // 4 crisp corner fit brackets inside the frame
        val path = Path().apply {
            // Top-left
            moveTo(w * 0.25f, h * 0.43f)
            lineTo(w * 0.25f, h * 0.33f)
            lineTo(w * 0.35f, h * 0.33f)
            // Top-right
            moveTo(w * 0.65f, h * 0.33f)
            lineTo(w * 0.75f, h * 0.33f)
            lineTo(w * 0.75f, h * 0.43f)
            // Bottom-left
            moveTo(w * 0.25f, h * 0.57f)
            lineTo(w * 0.25f, h * 0.67f)
            lineTo(w * 0.35f, h * 0.67f)
            // Bottom-right
            moveTo(w * 0.65f, h * 0.67f)
            lineTo(w * 0.75f, h * 0.67f)
            lineTo(w * 0.75f, h * 0.57f)
        }
        drawPath(path = path, color = tint, style = stroke)
    }
}

@Composable
fun ControlSettingsDialog(
    preferences: UserPreferences,
    currentVersionLabel: String,
    hasAvailableUpdate: Boolean,
    onDismiss: () -> Unit,
    onToggleTheme: () -> Unit,
    onSelectThemeId: (String) -> Unit,
    onOpenUpdateCheck: () -> Unit,
    onProgressBarStyleChange: (ProgressBarStyle) -> Unit,
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
    var styleDropdownExpanded by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 640.dp),
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
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(0.dp)
                        )
                        Text("✕", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                // Body
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            SettingsGroup(title = "Appearance") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onToggleTheme() }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = if (preferences.isDarkMode) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Dark / Light Mode",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = if (preferences.isDarkMode) "Cinema Dark theme active" else "Crisp Light theme active",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = preferences.isDarkMode,
                                        onCheckedChange = { onToggleTheme() }
                                    )
                                }
                                SettingsDivider()
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    val isRandomActive = preferences.themeId.startsWith("RANDOM:")
                                    val activePreset = AppThemePreset.fromId(preferences.themeId)
                                    val activeLabel = when {
                                        isRandomActive -> "Random"
                                        activePreset != null -> activePreset.label
                                        else -> AppThemePreset.TEAL_MINT.label
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Theme",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Active: $activeLabel",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (isRandomActive) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.primaryContainer
                                                )
                                                .border(
                                                    width = if (isRandomActive) 1.5.dp else 0.dp,
                                                    color = if (isRandomActive) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.35f) else Color.Transparent,
                                                    shape = RoundedCornerShape(10.dp)
                                                )
                                                .clickable {
                                                    val hue = (0..359).random()
                                                    onSelectThemeId("RANDOM:$hue")
                                                }
                                                .padding(horizontal = 12.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            if (isRandomActive) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                            Text(
                                                text = "Random Theme",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isRandomActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }

                                    AppThemePreset.entries.chunked(2).forEach { pair ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            pair.forEach { preset ->
                                                val selected = preferences.themeId == preset.id
                                                val swatch = resolveThemePrimaryColor(preset.id, preferences.isDarkMode)
                                                Row(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(
                                                            if (selected) MaterialTheme.colorScheme.primaryContainer
                                                            else MaterialTheme.colorScheme.surface
                                                        )
                                                        .border(
                                                            width = if (selected) 2.dp else 1.dp,
                                                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                                            shape = RoundedCornerShape(12.dp)
                                                        )
                                                        .clickable { onSelectThemeId(preset.id) }
                                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(14.dp)
                                                            .clip(CircleShape)
                                                            .background(swatch)
                                                            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                                                    )
                                                    Text(
                                                        text = preset.label,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    if (selected) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Check,
                                                            contentDescription = "Selected",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(15.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            SettingsGroup(title = "Main Interface & Layout") {
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

                        SettingsTab.CONTROLS -> {
                            // 1. Progress Bar Style Dropdown (Material / Flat)
                            SettingsGroup(title = "Style & Progress Bar") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Style Progress Bar",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "Choose Material slider or Flat bar in player",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Box {
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .border(
                                                    width = 1.dp,
                                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                                    shape = RoundedCornerShape(10.dp)
                                                )
                                                .background(MaterialTheme.colorScheme.surface)
                                                .clickable { styleDropdownExpanded = true }
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = preferences.progressBarStyle.label,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Icon(
                                                imageVector = Icons.Rounded.ArrowDropDown,
                                                contentDescription = "Select progress bar style",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = styleDropdownExpanded,
                                            onDismissRequest = { styleDropdownExpanded = false }
                                        ) {
                                            ProgressBarStyle.entries.forEach { style ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            Text(style.label)
                                                            if (preferences.progressBarStyle == style) {
                                                                Icon(
                                                                    imageVector = Icons.Rounded.Check,
                                                                    contentDescription = null,
                                                                    tint = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                        }
                                                    },
                                                    onClick = {
                                                        onProgressBarStyleChange(style)
                                                        styleDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 2. Quick Shortcut Icons (paired above Tap & Swipe Gestures)
                            SettingsGroup(
                                title = "Quick Shortcut Icons",
                                action = {
                                    TextButton(onClick = onShowAllButtons) {
                                        Text("Show All", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            ) {
                                TickButtonGrid(
                                    buttons = QUICK_SHORTCUT_BUTTONS,
                                    preferences = preferences,
                                    onToggle = onTogglePlayerButtonVisibility
                                )
                            }

                            // 3. Tap & Swipe Gestures
                            SettingsGroup(title = "Tap & Swipe Gestures") {
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

                            // 4. On-Screen Buttons & Dedicated Toggles
                            SettingsGroup(title = "On-Screen Buttons & Options") {
                                TickButtonGrid(
                                    buttons = SCREEN_OVERLAY_BUTTONS,
                                    preferences = preferences,
                                    onToggle = onTogglePlayerButtonVisibility
                                )
                                SettingsDivider()
                                SettingsSwitchRow(
                                    title = "Quick Zoom",
                                    subtitle = "Show Quick Zoom button (1x → 1.25x → 1.5x → 2x)",
                                    checked = preferences.isButtonVisible(PlayerControlButton.ZOOM),
                                    onCheckedChange = { onTogglePlayerButtonVisibility(PlayerControlButton.ZOOM, it) }
                                )
                                SettingsDivider()
                                SettingsSwitchRow(
                                    title = "Mute Button",
                                    subtitle = "Keep Mute / Unmute icon on the player screen",
                                    checked = preferences.isButtonVisible(PlayerControlButton.MUTE),
                                    onCheckedChange = { onTogglePlayerButtonVisibility(PlayerControlButton.MUTE, it) }
                                )
                                SettingsDivider()
                                SettingsSwitchRow(
                                    title = "Picture-in-Picture Mode",
                                    subtitle = "Show PiP button & auto-enter mini window on Home",
                                    checked = preferences.isButtonVisible(PlayerControlButton.PIP) && preferences.autoPipOnHome,
                                    onCheckedChange = { enabled ->
                                        onTogglePlayerButtonVisibility(PlayerControlButton.PIP, enabled)
                                        onAutoPipChange(enabled)
                                    }
                                )
                                SettingsDivider()
                                SettingsSwitchRow(
                                    title = "Background Play Audio",
                                    subtitle = "Keep audio playing when leaving video screen",
                                    checked = preferences.alwaysBackgroundPlay,
                                    onCheckedChange = { enabled ->
                                        onAlwaysBackgroundPlayChange(enabled)
                                        onTogglePlayerButtonVisibility(PlayerControlButton.BG_PLAY, enabled)
                                    }
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

@Composable
private fun TickButtonGrid(
    buttons: List<PlayerControlButton>,
    preferences: UserPreferences,
    onToggle: (PlayerControlButton, Boolean) -> Unit
) {
    Column(
        modifier = Modifier.padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        buttons.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                pair.forEach { btn ->
                    val enabled = preferences.isButtonVisible(btn)
                    val iconTint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (enabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                            )
                            .clickable { onToggle(btn, !enabled) }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (btn == PlayerControlButton.FIT) {
                            FitScreenIcon(tint = iconTint, size = 16.dp)
                        } else {
                            Icon(
                                imageVector = iconForPlayerButton(btn),
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(16.dp)
                            )
                        }
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
                            imageVector = if (enabled) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
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

private fun iconForPlayerButton(btn: PlayerControlButton): ImageVector = when (btn) {
    PlayerControlButton.ZOOM -> Icons.Rounded.ZoomIn
    PlayerControlButton.FIT -> Icons.Rounded.ZoomIn
    PlayerControlButton.ROTATE -> Icons.Rounded.ScreenRotation
    PlayerControlButton.SPEED -> Icons.Rounded.Speed
    PlayerControlButton.LOOP -> Icons.Rounded.Repeat
    PlayerControlButton.MUTE -> Icons.AutoMirrored.Rounded.VolumeUp
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
