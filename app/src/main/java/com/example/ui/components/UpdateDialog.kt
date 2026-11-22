package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.repository.UserPreferences
import com.example.ui.UpdateUiState
import com.example.updater.GitHubReleaseInfo
import com.example.util.FormatUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateAndSettingsSheet(
    currentVersionLabel: String,
    preferences: UserPreferences,
    updateState: UpdateUiState,
    onDismiss: () -> Unit,
    onCheckUpdates: (String) -> Unit,
    onStartInAppDownload: (GitHubReleaseInfo) -> Unit,
    onCancelDownload: () -> Unit,
    onOpenExternalDownload: (String) -> Unit,
    onInstallApk: (File) -> Unit,
    onToggleAutoPip: (Boolean) -> Unit,
    onToggleDefaultSwDecoder: (Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var repoSlugInput by remember(preferences.githubRepoSlug) {
        mutableStateOf(preferences.githubRepoSlug)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "GitHub In-App Updater & Settings",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Installed: $currentVersionLabel",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Repo configuration
            OutlinedTextField(
                value = repoSlugInput,
                onValueChange = { repoSlugInput = it },
                label = { Text(stringResource(R.string.github_repo_label)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("github_repo_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = { onCheckUpdates(repoSlugInput) },
                    enabled = updateState !is UpdateUiState.Checking && updateState !is UpdateUiState.Downloading,
                    modifier = Modifier.testTag("check_updates_button")
                ) {
                    Text(stringResource(R.string.check_now_cta))
                }
            }

            // Update Status Card
            when (updateState) {
                UpdateUiState.Idle -> Unit

                UpdateUiState.Checking -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Checking GitHub Releases…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                is UpdateUiState.UpToDate -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "${stringResource(R.string.update_up_to_date_title)} (v${updateState.versionName})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                is UpdateUiState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = updateState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                is UpdateUiState.Available -> {
                    val release = updateState.release
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${stringResource(R.string.update_available_title)}: v${release.versionName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (release.apkSizeBytes > 0L) {
                                    Text(
                                        text = FormatUtils.formatFileSize(release.apkSizeBytes),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }

                            Text(
                                text = release.releaseNotes.ifBlank { "Bug fixes and performance improvements." },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 140.dp)
                                    .verticalScroll(rememberScrollState())
                            )

                            Button(
                                onClick = { onStartInAppDownload(release) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("update_now_button")
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.update_now_cta))
                            }

                            OutlinedButton(
                                onClick = { onOpenExternalDownload(release.apkDownloadUrl) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("download_idm_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.download_idm_browser_cta))
                            }
                        }
                    }
                }

                is UpdateUiState.Downloading -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Downloading v${updateState.release.versionName}…",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "${updateState.progressPercent}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            LinearProgressIndicator(
                                progress = { updateState.progressPercent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val totalLabel = if (updateState.totalBytes > 0L) {
                                    FormatUtils.formatFileSize(updateState.totalBytes)
                                } else {
                                    "?"
                                }
                                Text(
                                    text = "${FormatUtils.formatFileSize(updateState.bytesDownloaded)} / $totalLabel",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${FormatUtils.formatFileSize(updateState.speedBytesPerSec)}/s",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                TextButton(onClick = onCancelDownload) {
                                    Text(stringResource(R.string.cancel_cta))
                                }
                                TextButton(onClick = { onOpenExternalDownload(updateState.release.apkDownloadUrl) }) {
                                    Text(stringResource(R.string.download_idm_browser_cta))
                                }
                            }
                        }
                    }
                }

                is UpdateUiState.ReadyToInstall -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "APK Ready: ${updateState.release.apkFileName}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Installing preserves all resume timestamps and app preferences.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { onInstallApk(updateState.apkFile) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("install_apk_button")
                            ) {
                                Icon(Icons.Default.InstallMobile, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.install_apk_cta))
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            Text(
                text = "Playback Preferences",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Auto Picture-in-Picture on Home", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Automatically shrinks video into PiP when leaving the player",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = preferences.autoPipOnHome,
                    onCheckedChange = onToggleAutoPip,
                    modifier = Modifier.testTag("auto_pip_switch")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Prefer Software (SW) Decoder", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Off uses Hardware (HW) decoding by default for best battery life",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = preferences.defaultSoftwareDecoder,
                    onCheckedChange = onToggleDefaultSwDecoder,
                    modifier = Modifier.testTag("default_sw_switch")
                )
            }
        }
    }
}
