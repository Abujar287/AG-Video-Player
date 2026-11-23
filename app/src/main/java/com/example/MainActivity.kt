package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.VideoItem
import com.example.player.PlayerActivity
import com.example.player.PlayerSessionManager
import com.example.ui.MainViewModel
import com.example.ui.screens.LibraryBrowserScreen
import com.example.ui.theme.VeloPlayTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val vm: MainViewModel = viewModel()
            val isLoading by vm.isLoadingLibrary.collectAsState()
            val snapshot by vm.snapshot.collectAsState()
            val filteredFolders by vm.filteredFolders.collectAsState()
            val currentVideos by vm.currentVideos.collectAsState()
            val sortedAllVideos by vm.sortedAllVideos.collectAsState()
            val destination by vm.destination.collectAsState()
            val searchQuery by vm.searchQuery.collectAsState()
            val sortField by vm.sortField.collectAsState()
            val sortDirection by vm.sortDirection.collectAsState()
            val selectedUris by vm.selectedVideoUris.collectAsState()
            val historyByUri by vm.historyByUri.collectAsState()
            val lastHistory by vm.lastHistoryEntry.collectAsState()
            val prefs by vm.preferences.collectAsState()
            val updateState by vm.updateState.collectAsState()
            val showUpdateDialog by vm.showUpdateDialog.collectAsState()
            val actionMessage by vm.actionMessage.collectAsState()
            val pendingIntentSender by vm.pendingIntentSender.collectAsState()
            val pendingMoveAccessPrompt by vm.pendingMoveAccessPrompt.collectAsState()

            // Runtime media permission launcher
            val mediaPermLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) {
                vm.refreshLibrary()
            }

            // MediaStore IntentSender write/delete consent launcher
            val intentSenderLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartIntentSenderForResult()
            ) { res ->
                vm.onPermissionSenderHandled(res.resultCode == Activity.RESULT_OK)
            }

            // All Files Access Settings launcher for direct file deletion / move without repeated OS dialogs
            val allFilesAccessLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) {
                vm.onAllFilesAccessResult()
            }

            // Open Document Video Picker (used only from Empty Library CTA)
            val openDocLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                if (uri != null) {
                    runCatching {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val intent = Intent(this@MainActivity, PlayerActivity::class.java).apply {
                        data = uri
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(intent)
                }
            }

            LaunchedEffect(Unit) {
                val needed = mutableListOf<String>()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                        needed += Manifest.permission.READ_MEDIA_VIDEO
                    }
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        needed += Manifest.permission.POST_NOTIFICATIONS
                    }
                } else if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    needed += Manifest.permission.READ_EXTERNAL_STORAGE
                }
                if (needed.isNotEmpty()) {
                    mediaPermLauncher.launch(needed.toTypedArray())
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !android.os.Environment.isExternalStorageManager()) {
                    runCatching {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        allFilesAccessLauncher.launch(intent)
                    }
                }
            }

            LaunchedEffect(pendingIntentSender) {
                val sender = pendingIntentSender ?: return@LaunchedEffect
                vm.consumeIntentSender()
                intentSenderLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }

            fun openPlayer(video: VideoItem, list: List<VideoItem>) {
                val (entries, idx) = vm.buildPlaylistFor(video, list)
                PlayerSessionManager.setPlaylist(entries, idx)
                startActivity(Intent(this@MainActivity, PlayerActivity::class.java))
            }

            var showExitDialog by remember { mutableStateOf(false) }

            androidx.activity.compose.BackHandler(enabled = true) {
                if (selectedUris.isNotEmpty()) {
                    vm.clearSelection()
                } else if (destination != com.example.data.model.BrowserDestination.Folders) {
                    vm.navigateBackToFolders()
                } else {
                    showExitDialog = true
                }
            }

            VeloPlayTheme(darkTheme = prefs.isDarkMode) {
                LibraryBrowserScreen(
                    isLoadingLibrary = isLoading,
                    folders = snapshot.folders,
                    allVideos = snapshot.allVideos,
                    videosByFolder = snapshot.videosByFolder,
                    filteredFolders = filteredFolders,
                    currentVideos = currentVideos,
                    sortedAllVideos = sortedAllVideos,
                    destination = destination,
                    searchQuery = searchQuery,
                    sortField = sortField,
                    sortDirection = sortDirection,
                    selectedVideoUris = selectedUris,
                    historyByUri = historyByUri,
                    lastHistoryEntry = lastHistory,
                    preferences = prefs,
                    updateState = updateState,
                    showUpdateDialog = showUpdateDialog,
                    actionMessage = actionMessage,
                    pendingMoveAccessPrompt = pendingMoveAccessPrompt,
                    currentAppVersionLabel = vm.currentAppVersionLabel(),
                    onRefresh = vm::refreshLibrary,
                    onOpenDocumentPicker = { openDocLauncher.launch(arrayOf("video/*")) },
                    onToggleTheme = vm::toggleDarkMode,
                    onToggleGridView = vm::toggleGridView,
                    onApplySort = vm::setSortOption,
                    onOpenUpdateCheck = vm::openUpdateCheck,
                    onDismissUpdateDialog = vm::dismissUpdateDialog,
                    onSearchQueryChange = vm::setSearchQuery,
                    onFolderClick = vm::openFolder,
                    onOpenFavorites = vm::openFavorites,
                    onOpenPlaylist = vm::openPlaylist,
                    onDeletePlaylist = vm::deletePlaylist,
                    onBackToFolders = vm::navigateBackToFolders,
                    onVideoClick = { video, list -> openPlayer(video, list) },
                    onResumeLastPlayed = {
                        vm.getLastPlayedVideoAndPlaylist()?.let { (vid, list) ->
                            openPlayer(vid, list)
                        }
                    },
                    onStartSelection = vm::startSelection,
                    onToggleSelection = vm::toggleSelection,
                    onSelectAll = vm::selectAllVisible,
                    onClearSelection = vm::clearSelection,
                    onToggleFavorite = vm::toggleFavorite,
                    onAddToPlaylist = vm::addToPlaylist,
                    onRemoveFromPlaylist = vm::removeFromPlaylist,
                    onRenameVideo = vm::renameVideo,
                    onMoveVideos = vm::moveVideos,
                    onDeleteVideos = vm::deleteVideos,
                    onClearResume = vm::clearResumePosition,
                    onClearActionMessage = vm::clearActionMessage,
                    onRequestAllFilesAccess = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                            allFilesAccessLauncher.launch(intent)
                        } else {
                            vm.retryMoveAfterAllFilesAccessResult()
                        }
                    },
                    onMoveToScopedMovies = vm::moveToScopedMoviesSubfolder,
                    onDismissMoveAccessPrompt = vm::dismissMoveAccessPrompt,
                    onSingleClickActionChange = vm::setSingleClickAction,
                    onDoubleClickActionChange = vm::setDoubleClickAction,
                    onAlwaysBackgroundPlayChange = vm::setAlwaysBackgroundPlay,
                    onAutoPipChange = vm::setAutoPipOnHome,
                    onEnableSwipeGesturesChange = vm::setEnableSwipeGestures,
                    onGestureSeekStepChange = vm::setGestureSeekStepSeconds,
                    onDefaultSoftwareDecoderChange = vm::setDefaultSoftwareDecoder,
                    onShowHiddenFilesChange = vm::setShowHiddenFiles,
                    onMainDisplayModeChange = vm::setMainDisplayMode,
                    onMainViewLayoutChange = vm::setMainViewLayout,
                    onOneFingerGestureChange = vm::setOneFingerGesture,
                    onTwoFingerGestureChange = vm::setTwoFingerGesture,
                    onThreeFingerGestureChange = vm::setThreeFingerGesture,
                    onTogglePlayerButtonVisibility = vm::setPlayerButtonVisible,
                    onShowAllButtons = vm::showAllPlayerButtons,
                    onProgressBarStyleChange = vm::setProgressBarStyle,
                    onStartDownload = vm::startUpdateDownload,
                    onInstallUpdate = vm::installUpdate
                )

                if (showExitDialog) {
                    AlertDialog(
                        onDismissRequest = { showExitDialog = false },
                        title = {
                            Text(
                                text = "Exit AG Video Player",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Text(
                                text = "Are you sure you want to exit?",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showExitDialog = false
                                    finish()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Exit")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showExitDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }
        }
    }
}
