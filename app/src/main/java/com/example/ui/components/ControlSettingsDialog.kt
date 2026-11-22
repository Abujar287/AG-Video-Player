package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.repository.DoubleClickAction
import com.example.data.repository.PlayerControlButton
import com.example.data.repository.SingleClickAction
import com.example.data.repository.UserPreferences

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlSettingsDialog(
    preferences: UserPreferences,
    currentVersionLabel: String,
    hasAvailableUpdate: Boolean,
    onDismiss: () -> Unit,
    onOpenUpdateCheck: () -> Unit,
    onSingleClickActionChange: (SingleClickAction) -> Unit,
    onDoubleClickActionChange: (DoubleClickAction) -> Unit,
    onAlwaysBackgroundPlayChange: (Boolean) -> Unit,
    onAutoPipChange: (Boolean) -> Unit,
    onEnableSwipeGesturesChange: (Boolean) -> Unit,
    onGestureSeekStepChange: (Int) -> Unit,
    onDefaultSoftwareDecoderChange: (Boolean) -> Unit,
    onTogglePlayerButtonVisibility: (PlayerControlButton, Boolean) -> Unit,
    onShowAllButtons: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp)
                .testTag("control_settings_dialog")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(9.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Player & Controls",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Taps, background lock play & screen buttons",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Background & Screen Lock Playback
                    SettingsSectionCard(
                        icon = Icons.Default.Headphones,
                        title = "Background & Screen Lock"
                    ) {
                        SettingsSwitchRow(
                            title = "Always Play in Background",
                            subtitle = "Keep video playing even when the phone screen is locked or you switch apps",
                            checked = preferences.alwaysBackgroundPlay,
                            onCheckedChange = onAlwaysBackgroundPlayChange
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                        SettingsSwitchRow(
                            title = "Auto PiP on Home",
                            subtitle = "Enter Picture-in-Picture automatically when leaving player (when background play is off)",
                            checked = preferences.autoPipOnHome,
                            onCheckedChange = onAutoPipChange
                        )
                    }

                    // 2. Single Click & Double Click Actions
                    SettingsSectionCard(
                        icon = Icons.Default.TouchApp,
                        title = "Tap & Click Controls"
                    ) {
                        Text(
                            text = "Single Click Action",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SingleClickAction.entries.forEach { action ->
                                val selected = preferences.singleClickAction == action
                                SelectableOptionPill(
                                    label = action.label,
                                    selected = selected,
                                    onClick = { onSingleClickActionChange(action) }
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Double Click Action",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DoubleClickAction.entries.forEach { action ->
                                val selected = preferences.doubleClickAction == action
                                SelectableOptionPill(
                                    label = action.label,
                                    selected = selected,
                                    onClick = { onDoubleClickActionChange(action) }
                                )
                            }
                        }
                    }

                    // 3. Visible Buttons During Playback
                    SettingsSectionCard(
                        icon = Icons.Default.Visibility,
                        title = "Player Screen Buttons",
                        trailingAction = {
                            TextButton(onClick = onShowAllButtons) {
                                Text("Show All", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    ) {
                        Text(
                            text = "Choose which buttons appear on screen while video plays",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PlayerControlButton.entries.forEach { btn ->
                                val visible = preferences.controlsVisibility.isVisible(btn)
                                FilterChip(
                                    selected = visible,
                                    onClick = { onTogglePlayerButtonVisibility(btn, !visible) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = buttonIconFor(btn),
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = btn.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                    }

                    // 4. Swipe Gestures & Decoder Preferences
                    SettingsSectionCard(
                        icon = Icons.Default.Swipe,
                        title = "Gestures & Decoder"
                    ) {
                        SettingsSwitchRow(
                            title = "Swipe Gestures",
                            subtitle = "Drag left/right to seek and up/down for brightness & sound",
                            checked = preferences.enableSwipeGestures,
                            onCheckedChange = onEnableSwipeGesturesChange
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Horizontal Swipe Step",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Seconds jumped per swipe step",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(10, 20).forEach { sec ->
                                    SelectableOptionPill(
                                        label = "${sec}s",
                                        selected = preferences.gestureSeekStepSeconds == sec,
                                        onClick = { onGestureSeekStepChange(sec) }
                                    )
                                }
                            }
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        SettingsSwitchRow(
                            title = "Default SW Decoder",
                            subtitle = "Prefer Software decoding instead of Hardware by default",
                            checked = preferences.defaultSoftwareDecoder,
                            onCheckedChange = onDefaultSoftwareDecoderChange
                        )
                    }

                    // 5. App Update
                    SettingsSectionCard(
                        icon = Icons.Default.SystemUpdate,
                        title = "App Update"
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Version $currentVersionLabel",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (hasAvailableUpdate) {
                                        "New update available to download"
                                    } else {
                                        "Check GitHub for the latest APK"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (hasAvailableUpdate) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            if (hasAvailableUpdate) {
                                Button(
                                    onClick = {
                                        onDismiss()
                                        onOpenUpdateCheck()
                                    },
                                    modifier = Modifier.testTag("settings_update_button")
                                ) {
                                    Text("Update")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        onDismiss()
                                        onOpenUpdateCheck()
                                    },
                                    modifier = Modifier.testTag("settings_update_button")
                                ) {
                                    Text("Update")
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = onDismiss) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    icon: ImageVector,
    title: String,
    trailingAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                trailingAction?.invoke()
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SelectableOptionPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

private fun buttonIconFor(button: PlayerControlButton): ImageVector = when (button) {
    PlayerControlButton.FIT -> Icons.Default.AspectRatio
    PlayerControlButton.ROTATE -> Icons.Default.ScreenRotation
    PlayerControlButton.SPEED -> Icons.Default.Speed
    PlayerControlButton.LOOP -> Icons.Default.Repeat
    PlayerControlButton.MUTE -> Icons.AutoMirrored.Filled.VolumeUp
    PlayerControlButton.SLEEP -> Icons.Default.Bedtime
    PlayerControlButton.BG_PLAY -> Icons.Default.Headphones
    PlayerControlButton.TRACKS -> Icons.Default.Subtitles
    PlayerControlButton.DECODER -> Icons.Default.FastForward
    PlayerControlButton.LOCK -> Icons.Default.Lock
    PlayerControlButton.PREV_NEXT -> Icons.Default.SkipNext
    PlayerControlButton.PIP -> Icons.Default.PictureInPictureAlt
}
