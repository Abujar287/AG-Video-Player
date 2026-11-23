package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.GitHubReleaseInfo
import com.example.data.model.UpdateUiState
import com.example.updater.GitHubUpdateManager
import com.example.util.FormatUtils

@Composable
fun UpdateDialog(
    currentVersionLabel: String,
    state: UpdateUiState,
    onDismiss: () -> Unit,
    onRetryCheck: () -> Unit,
    onStartDownload: (GitHubReleaseInfo) -> Unit,
    onCancelDownload: () -> Unit,
    onInstallUpdate: (String) -> Unit,
    onOpenExternalDownloader: (GitHubReleaseInfo) -> Unit
) {
    val context = LocalContext.current
    val installedDownloader = remember(context) {
        GitHubUpdateManager.findInstalledExternalDownloader(context)
    }

    val title = when (state) {
        is UpdateUiState.Available -> "Update Available Now"
        is UpdateUiState.Downloading -> "Downloading Update"
        is UpdateUiState.ReadyToInstall -> "Ready to Install"
        is UpdateUiState.UpToDate -> "Up to Date"
        is UpdateUiState.Error -> "Update Check"
        else -> "Checking for Update"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            when (state) {
                UpdateUiState.Idle, UpdateUiState.Checking -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Text(
                            text = "Checking for latest version...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                is UpdateUiState.UpToDate -> {
                    Text(
                        text = "You are using the latest version (v${state.versionName.removePrefix("v")}).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                is UpdateUiState.Error -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                is UpdateUiState.Available -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Current Version: ${currentVersionLabel.split(' ').firstOrNull() ?: currentVersionLabel}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "New Version: v${state.release.versionName.removePrefix("v")}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                is UpdateUiState.Downloading -> {
                    val totalText = if (state.bytesTotal > 0L) {
                        FormatUtils.formatFileSize(state.bytesTotal)
                    } else {
                        "..."
                    }
                    val progress = if (state.bytesTotal > 0L) {
                        (state.bytesDownloaded.toFloat() / state.bytesTotal.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (state.bytesTotal > 0L) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${FormatUtils.formatFileSize(state.bytesDownloaded)} / $totalText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${state.percent}%",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                is UpdateUiState.ReadyToInstall -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Download complete",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "100%",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateUiState.Available -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (installedDownloader != null) {
                            TextButton(onClick = { onOpenExternalDownloader(state.release) }) {
                                Text(installedDownloader.second)
                            }
                        }
                        Button(onClick = { onStartDownload(state.release) }) {
                            Text("Download")
                        }
                    }
                }
                is UpdateUiState.ReadyToInstall -> {
                    Button(onClick = { onInstallUpdate(state.filePath.ifBlank { state.contentUri.orEmpty() }) }) {
                        Text("Install")
                    }
                }
                is UpdateUiState.Error -> {
                    TextButton(onClick = onRetryCheck) {
                        Text("Retry")
                    }
                }
                is UpdateUiState.UpToDate -> {
                    TextButton(onClick = onDismiss) {
                        Text("OK")
                    }
                }
                else -> Unit
            }
        },
        dismissButton = {
            when (state) {
                is UpdateUiState.Downloading -> {
                    TextButton(onClick = onCancelDownload) {
                        Text("Cancel")
                    }
                }
                is UpdateUiState.UpToDate -> Unit
                else -> {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            }
        }
    )
}
