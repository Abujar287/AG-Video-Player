package com.example

import android.Manifest
import android.app.Activity
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
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.VideoItem
import com.example.player.PlayerActivity
import com.example.player.PlaylistEntry
import com.example.ui.MainViewModel
import com.example.ui.components.UpdateCheckDialog
import com.example.ui.screens.VeloMainScreen
import com.example.ui.theme.VeloPlayTheme
import com.example.util.PreviewEnvironment
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

            val isPreviewMode = remember { PreviewEnvironment.isEmulator() }

            var hasStoragePermission by remember {
                mutableStateOf(
                    ContextCompat.checkSelfPermission(this, storagePermission) ==
                        PackageManager.PERMISSION_GRANTED
                )
            }
            val canBrowseLibrary = hasStoragePermission || isPreviewMode

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

            val mediaWritePermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartIntentSenderForResult()
            ) { result ->
                viewModel.onPermissionRequestResult(result.resultCode == Activity.RESULT_OK)
            }

            val pendingMediaRequest = uiState.pendingPermissionRequest
            LaunchedEffect(pendingMediaRequest) {
                if (pendingMediaRequest != null) {
                    mediaWritePermissionLauncher.launch(
                        IntentSenderRequest.Builder(pendingMediaRequest.intentSender).build()
                    )
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

            LaunchedEffect(canBrowseLibrary, hasStoragePermission, isPreviewMode) {
                if (canBrowseLibrary) {
                    viewModel.refreshLibrary()
                    if (!isPreviewMode &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                    }
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

            fun shareVideo(video: VideoItem) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = video.mimeType.ifBlank { "video/*" }
                    putExtra(Intent.EXTRA_STREAM, Uri.parse(video.uriString))
                    putExtra(Intent.EXTRA_SUBJECT, video.title)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Share ${video.title}"))
            }

            VeloPlayTheme(darkTheme = uiState.preferences.isDarkMode) {
                VeloMainScreen(
                    uiState = uiState,
                    hasStoragePermission = canBrowseLibrary,
                    onRequestPermission = { requestStorageAndNotifyPermissions() },
                    onOpenSingleFile = { singleVideoLauncher.launch(arrayOf("video/*")) },
                    onRefresh = { viewModel.refreshLibrary() },
                    onToggleTheme = { viewModel.toggleDarkMode() },
                    onToggleGridView = { viewModel.toggleGridView() },
                    onApplySort = { field, dir -> viewModel.setSortOption(field, dir) },
                    onOpenUpdateCheck = { viewModel.openUpdateSheet() },
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    onFolderClick = { folder -> viewModel.openFolder(folder.bucketId) },
                    onOpenFavorites = { viewModel.openFavorites() },
                    onOpenPlaylist = { name -> viewModel.openPlaylist(name) },
                    onDeletePlaylist = { name -> viewModel.deletePlaylist(name) },
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
                    onResumeLastPlayed = {
                        val lastEntry = uiState.lastHistoryEntry
                        if (lastEntry != null) {
                            val matchingVideo = uiState.allVideos.firstOrNull {
                                it.uriString == lastEntry.videoUri
                            }
                            if (matchingVideo != null) {
                                val folderList = uiState.videosByFolder[matchingVideo.bucketId].orEmpty()
                                val playlist = folderList.ifEmpty { listOf(matchingVideo) }
                                val entries = playlist.map { PlaylistEntry(it.uriString, it.title) }
                                val idx = playlist.indexOfFirst {
                                    it.uriString == matchingVideo.uriString
                                }.coerceAtLeast(0)
                                startActivity(
                                    PlayerActivity.createPlaylistIntent(
                                        context = this,
                                        playlist = entries,
                                        startIndex = idx
                                    )
                                )
                            } else {
                                startActivity(
                                    PlayerActivity.createIntent(
                                        context = this,
                                        videoUri = lastEntry.videoUri,
                                        title = lastEntry.title.ifBlank { "Video" }
                                    )
                                )
                            }
                        } else {
                            val firstVideo = uiState.allVideos.firstOrNull()
                            if (firstVideo != null) {
                                val folderList = uiState.videosByFolder[firstVideo.bucketId].orEmpty()
                                val playlist = folderList.ifEmpty { listOf(firstVideo) }
                                val entries = playlist.map { PlaylistEntry(it.uriString, it.title) }
                                val idx = playlist.indexOfFirst {
                                    it.uriString == firstVideo.uriString
                                }.coerceAtLeast(0)
                                startActivity(
                                    PlayerActivity.createPlaylistIntent(
                                        context = this,
                                        playlist = entries,
                                        startIndex = idx
                                    )
                                )
                            }
                        }
                    },
                    onStartSelection = { uri -> viewModel.startVideoSelection(uri) },
                    onToggleSelection = { uri -> viewModel.toggleVideoSelection(uri) },
                    onSelectAll = { viewModel.selectAllCurrentVideos() },
                    onClearSelection = { viewModel.clearSelection() },
                    onToggleFavorite = { video -> viewModel.toggleFavorite(video) },
                    onAddToPlaylist = { playlistName, video ->
                        viewModel.addVideoToPlaylist(playlistName, video)
                    },
                    onRemoveFromPlaylist = { playlistName, video ->
                        viewModel.removeVideoFromPlaylist(playlistName, video)
                    },
                    onShareVideo = { video -> shareVideo(video) },
                    onRenameVideo = { video, newName -> viewModel.renameVideo(video, newName) },
                    onMoveVideos = { videos, targetPath -> viewModel.moveVideos(videos, targetPath) },
                    onDeleteVideos = { videos -> viewModel.deleteVideos(videos) },
                    onClearResume = { uri -> viewModel.clearResumePosition(uri) },
                    onClearActionMessage = { viewModel.clearActionMessage() },
                    onSingleClickActionChange = { viewModel.setSingleClickAction(it) },
                    onDoubleClickActionChange = { viewModel.setDoubleClickAction(it) },
                    onAlwaysBackgroundPlayChange = { viewModel.setAlwaysBackgroundPlay(it) },
                    onAutoPipChange = { viewModel.setAutoPipEnabled(it) },
                    onEnableSwipeGesturesChange = { viewModel.setEnableSwipeGestures(it) },
                    onGestureSeekStepChange = { viewModel.setGestureSeekStepSeconds(it) },
                    onDefaultSoftwareDecoderChange = { viewModel.setDefaultSoftwareDecoder(it) },
                    onTogglePlayerButtonVisibility = { btn, visible ->
                        viewModel.setPlayerButtonVisible(btn, visible)
                    },
                    onShowAllButtons = { viewModel.showAllPlayerButtons() },
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
