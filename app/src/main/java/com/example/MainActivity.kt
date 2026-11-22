package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.player.PlayerActivity
import com.example.player.PlaylistEntry
import com.example.ui.MainViewModel
import com.example.ui.components.UpdateCheckDialog
import com.example.ui.screens.VeloMainScreen
import com.example.ui.theme.VeloPlayTheme
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: MainViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            val storagePermission = remember {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_VIDEO
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }
            }

            var hasStoragePermission by remember {
                mutableStateOf(
                    ContextCompat.checkSelfPermission(this, storagePermission) ==
                        PackageManager.PERMISSION_GRANTED
                )
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { results ->
                val granted = results[storagePermission] == true ||
                    ContextCompat.checkSelfPermission(this, storagePermission) ==
                    PackageManager.PERMISSION_GRANTED
                hasStoragePermission = granted
                if (granted) {
                    viewModel.refreshLibrary()
                }
            }

            val singleVideoLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                if (uri != null) {
                    runCatching {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                    val title = resolveDisplayName(uri) ?: "Video"
                    startActivity(PlayerActivity.createIntent(this, uri.toString(), title))
                }
            }

            val unknownSourcesLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) {
                val pendingApk = uiState.pendingInstallApk
                if (pendingApk != null && viewModel.updateManager.canInstallPackages()) {
                    viewModel.updateManager.launchApkInstaller(pendingApk)
                    viewModel.clearPendingInstallApk()
                }
            }

            LaunchedEffect(hasStoragePermission) {
                if (hasStoragePermission) {
                    viewModel.refreshLibrary()
                } else {
                    val perms = buildList {
                        add(storagePermission)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }.toTypedArray()
                    permissionLauncher.launch(perms)
                }
            }

            fun requestStorageAndNotifyPermissions() {
                val perms = buildList {
                    add(storagePermission)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }.toTypedArray()
                permissionLauncher.launch(perms)
            }

            fun triggerApkInstall(apkFile: File) {
                if (viewModel.updateManager.canInstallPackages()) {
                    viewModel.updateManager.launchApkInstaller(apkFile)
                    viewModel.clearPendingInstallApk()
                } else {
                    unknownSourcesLauncher.launch(viewModel.updateManager.createUnknownSourcesIntent())
                }
            }

            VeloPlayTheme(darkTheme = uiState.preferences.isDarkMode) {
                VeloMainScreen(
                    uiState = uiState,
                    hasStoragePermission = hasStoragePermission,
                    onRequestPermission = { requestStorageAndNotifyPermissions() },
                    onOpenSingleFile = { singleVideoLauncher.launch(arrayOf("video/*")) },
                    onRefresh = { viewModel.refreshLibrary() },
                    onToggleTheme = { viewModel.toggleDarkMode() },
                    onCycleSort = { viewModel.cycleSortOption() },
                    onOpenUpdateCheck = { viewModel.openUpdateSheet() },
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    onFolderClick = { folder -> viewModel.openFolder(folder.bucketId) },
                    onBackToFolders = { viewModel.navigateBackToFolders() },
                    onVideoClick = { video, playlist ->
                        val entries = playlist.map { PlaylistEntry(it.uriString, it.title) }
                        val idx = playlist.indexOfFirst { it.uriString == video.uriString }.coerceAtLeast(0)
                        startActivity(
                            PlayerActivity.createPlaylistIntent(
                                context = this,
                                playlist = entries,
                                startIndex = idx
                            )
                        )
                    },
                    onClearResume = { uri -> viewModel.clearResumePosition(uri) },
                    loadThumbnail = { id, uri -> viewModel.loadThumbnail(id, uri) }
                )

                val updateStateNow = uiState.updateState
                LaunchedEffect(updateStateNow) {
                    if (updateStateNow is com.example.ui.UpdateUiState.ReadyToInstall) {
                        triggerApkInstall(updateStateNow.apkFile)
                    }
                }

                if (uiState.showUpdateSheet) {
                    UpdateCheckDialog(
                        currentVersionLabel = viewModel.currentAppVersionLabel(),
                        updateState = uiState.updateState,
                        onDismiss = { viewModel.dismissUpdateSheet() },
                        onRetryCheck = { viewModel.checkForUpdates(manual = true) },
                        onStartInAppDownload = { release -> viewModel.startInAppDownload(release) },
                        onCancelDownload = { viewModel.cancelInAppDownload() },
                        onOpenExternalDownload = { url ->
                            viewModel.updateManager.openExternalDownloadUrl(url)
                        },
                        onInstallApk = { file -> triggerApkInstall(file) }
                    )
                }
            }
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx)?.substringBeforeLast('.') else null
                    } else null
                }
        }.getOrNull()
    }
}
