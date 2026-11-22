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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.UpdateUiState
import com.example.updater.GitHubReleaseInfo
import com.example.util.FormatUtils
import java.io.File

/**
 * Minimal, clean Update Dialog: tapping Update immediately checks GitHub and only shows
 * whether a new update is available (plus download/install controls if available).
 */
@Composable
fun UpdateCheckDialog(
    currentVersionLabel: String,
    updateState: UpdateUiState,
    onDismiss: () -> Unit,
    onRetryCheck: () -> Unit,
    onStartInAppDownload: (GitHubReleaseInfo) -> Unit,
    onCancelDownload: () -> Unit,
    onOpenExternalDownload: (String) -> Unit,
    onInstallApk: (File) -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (updateState !is UpdateUiState.Downloading) onDismiss()
        },
        icon = {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = when (updateState) {
                    is UpdateUiState.Available -> "New Update Available"
                    is UpdateUiState.Downloading -> "Downloading Update…"
                    is UpdateUiState.ReadyToInstall -> "Update Ready"
                    is UpdateUiState.UpToDate -> "Already Installed Latest Version"
                    is UpdateUiState.Error -> "Update Check"
                    else -> "Checking for Update…"
                },
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (updateState) {
                    UpdateUiState.Idle, UpdateUiState.Checking -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Checking GitHub for updates ($currentVersionLabel)…",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    is UpdateUiState.UpToDate -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Already installed latest version (v${updateState.versionName}).",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    is UpdateUiState.Error -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = updateState.message,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    is UpdateUiState.Available -> {
                        val release = updateState.release
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "v${release.versionName}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
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
                                    text = release.releaseNotes.ifBlank { "New update ready to install." },
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 120.dp)
                                        .verticalScroll(rememberScrollState())
                                )
                            }
                        }

                        Button(
                            onClick = { onStartInAppDownload(release) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("update_now_button")
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.update_now_cta))
                        }

                        OutlinedButton(
                            onClick = { onOpenExternalDownload(release.apkDownloadUrl) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("download_idm_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.download_idm_browser_cta))
                        }
                    }

                    is UpdateUiState.Downloading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Downloading v${updateState.release.versionName}",
                                style = MaterialTheme.typography.bodyMedium
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
                            val totalText = if (updateState.totalBytes > 0L) {
                                FormatUtils.formatFileSize(updateState.totalBytes)
                            } else "?"
                            Text(
                                text = "${FormatUtils.formatFileSize(updateState.bytesDownloaded)} / $totalText",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${FormatUtils.formatFileSize(updateState.speedBytesPerSec)}/s",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Text(
                            text = "Saving to File Manager (Downloads) & notification bar…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    is UpdateUiState.ReadyToInstall -> {
                        Text(
                            text = "${updateState.release.apkFileName} downloaded & saved to Downloads. Tap Install Update to replace the old version with v${updateState.release.versionName}.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = { onInstallApk(updateState.apkFile) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("install_apk_button")
                        ) {
                            Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.install_apk_cta))
                        }
                    }
                }
            }
        },
        dismissButton = {
            if (updateState is UpdateUiState.Error) {
                TextButton(onClick = onRetryCheck) {
                    Text("Retry")
                }
            } else if (updateState is UpdateUiState.Downloading) {
                TextButton(onClick = onCancelDownload) {
                    Text(stringResource(R.string.cancel_cta))
                }
            }
        },
        confirmButton = {
            if (updateState !is UpdateUiState.Downloading) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close_cta))
                }
            }
        }
    )
}
