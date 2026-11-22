package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.PlaybackHistoryEntity
import com.example.data.model.VideoFolder
import com.example.data.model.VideoItem
import com.example.ui.BrowserDestination
import com.example.ui.MainUiState
import com.example.ui.SortDirection
import com.example.ui.SortField
import com.example.ui.UpdateUiState
import com.example.ui.components.VideoThumbnail
import com.example.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VeloMainScreen(
    uiState: MainUiState,
    hasStoragePermission: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSingleFile: () -> Unit,
    onRefresh: () -> Unit,
    onToggleTheme: () -> Unit,
    onToggleGridView: () -> Unit,
    onApplySort: (SortField, SortDirection) -> Unit,
    onOpenUpdateCheck: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onFolderClick: (VideoFolder) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onBackToFolders: () -> Unit,
    onVideoClick: (VideoItem, List<VideoItem>) -> Unit,
    onResumeLastPlayed: () -> Unit,
    onStartSelection: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onToggleFavorite: (VideoItem) -> Unit,
    onAddToPlaylist: (String, VideoItem) -> Unit,
    onRemoveFromPlaylist: (String, VideoItem) -> Unit,
    onShareVideo: (VideoItem) -> Unit,
    onRenameVideo: (VideoItem, String) -> Unit,
    onMoveVideos: (List<VideoItem>, String) -> Unit,
    onDeleteVideos: (List<VideoItem>) -> Unit,
    onClearResume: (String) -> Unit,
    onClearActionMessage: () -> Unit,
    loadThumbnail: suspend (Long, String) -> Bitmap?
) {
    val activeFolder = uiState.selectedFolder
    val isInVideoList = uiState.destination != BrowserDestination.Folders

    if (uiState.isSelectionMode) {
        BackHandler(onBack = onClearSelection)
    } else if (isInVideoList) {
        BackHandler(onBack = onBackToFolders)
    }

    var detailVideo by remember { mutableStateOf<VideoItem?>(null) }
    var renameTargetVideo by remember { mutableStateOf<VideoItem?>(null) }
    var playlistTargetVideo by remember { mutableStateOf<VideoItem?>(null) }
    var moveTargetVideos by remember { mutableStateOf<List<VideoItem>?>(null) }
    var deleteTargetVideos by remember { mutableStateOf<List<VideoItem>?>(null) }
    var showSortDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.actionMessage) {
        val msg = uiState.actionMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        onClearActionMessage()
    }

    val hasAvailableUpdate = uiState.updateState is UpdateUiState.Available ||
        uiState.updateState is UpdateUiState.ReadyToInstall

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!uiState.isSelectionMode &&
                hasStoragePermission &&
                (uiState.lastHistoryEntry != null || uiState.allVideos.isNotEmpty())
            ) {
                FloatingActionButton(
                    onClick = onResumeLastPlayed,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .testTag("bottom_resume_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = if (uiState.lastHistoryEntry != null) {
                            "Resume Last Video"
                        } else {
                            "Play Video"
                        },
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        },
        topBar = {
            if (uiState.isSelectionMode) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    navigationIcon = {
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                        }
                    },
                    title = {
                        Text(
                            text = "${uiState.selectedVideoUris.size} selected",
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    actions = {
                        IconButton(onClick = onSelectAll) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select all")
                        }
                        IconButton(
                            onClick = {
                                val selected = uiState.currentVideos.filter {
                                    uiState.selectedVideoUris.contains(it.uriString)
                                }
                                if (selected.isNotEmpty()) {
                                    moveTargetVideos = selected
                                }
                            }
                        ) {
                            Icon(Icons.Default.DriveFileMove, contentDescription = "Move selected")
                        }
                        IconButton(
                            onClick = {
                                val selected = uiState.currentVideos.filter {
                                    uiState.selectedVideoUris.contains(it.uriString)
                                }
                                if (selected.isNotEmpty()) {
                                    deleteTargetVideos = selected
                                }
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove selected")
                        }
                    }
                )
            } else {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    navigationIcon = {
                        if (isInVideoList) {
                            IconButton(
                                onClick = onBackToFolders,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("back_to_folders_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.back_desc)
                                )
                            }
                        }
                    },
                    title = {
                        val titleText = when (val dest = uiState.destination) {
                            BrowserDestination.Folders -> stringResource(R.string.app_name)
                            is BrowserDestination.Folder -> activeFolder?.name ?: "Folder"
                            BrowserDestination.Favorites -> "Favourites"
                            is BrowserDestination.Playlist -> dest.name
                        }
                        Column {
                            Text(
                                text = titleText,
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (activeFolder != null) {
                                val unit = if (activeFolder.videoCount == 1) {
                                    stringResource(R.string.video_singular)
                                } else {
                                    stringResource(R.string.video_plural)
                                }
                                Text(
                                    text = "${activeFolder.videoCount} $unit · ${FormatUtils.formatFileSize(activeFolder.totalSizeBytes)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else if (uiState.destination is BrowserDestination.Favorites ||
                                uiState.destination is BrowserDestination.Playlist
                            ) {
                                val vids = uiState.currentVideos
                                val unit = if (vids.size == 1) "video" else "videos"
                                Text(
                                    text = "${vids.size} $unit",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else if (hasStoragePermission && uiState.folders.isNotEmpty()) {
                                Text(
                                    text = "${uiState.folders.size} folders · ${uiState.allVideos.size} videos",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        // Show Grid / List toggle button right on main interface
                        if (hasStoragePermission) {
                            IconButton(
                                onClick = onToggleGridView,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("grid_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (uiState.preferences.isGridView) {
                                        Icons.AutoMirrored.Filled.ViewList
                                    } else {
                                        Icons.Default.GridView
                                    },
                                    contentDescription = if (uiState.preferences.isGridView) {
                                        "Switch to List View"
                                    } else {
                                        "Show Grid"
                                    },
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = onToggleTheme,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("theme_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (uiState.preferences.isDarkMode) {
                                    Icons.Default.LightMode
                                } else {
                                    Icons.Default.DarkMode
                                },
                                contentDescription = stringResource(R.string.toggle_theme_desc),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = onOpenUpdateCheck,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("update_menu_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (hasAvailableUpdate) {
                                        Badge(containerColor = MaterialTheme.colorScheme.secondary)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = stringResource(R.string.check_updates_desc),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        if (hasStoragePermission) {
                            IconButton(
                                onClick = onRefresh,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("refresh_library_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.refresh_library_desc),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
        ) {
            if (!hasStoragePermission) {
                PermissionPromptView(
                    onGrantPermission = onRequestPermission,
                    onOpenSingleFile = onOpenSingleFile
                )
            } else if (uiState.isLoadingLibrary && uiState.folders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                AnimatedContent(
                    targetState = uiState.destination,
                    label = "folder_video_transition"
                ) { dest ->
                    if (dest == BrowserDestination.Folders) {
                        FolderBrowserContent(
                            uiState = uiState,
                            onSearchQueryChange = onSearchQueryChange,
                            onOpenSortPopup = { showSortDialog = true },
                            onToggleGridView = onToggleGridView,
                            onFolderClick = onFolderClick,
                            onOpenFavorites = onOpenFavorites,
                            onOpenPlaylist = onOpenPlaylist,
                            onDeletePlaylist = onDeletePlaylist,
                            onResumeLastVideo = { video ->
                                val folderVideos = uiState.videosByFolder[video.bucketId].orEmpty()
                                onVideoClick(video, folderVideos.ifEmpty { listOf(video) })
                            },
                            onOpenSingleFile = onOpenSingleFile
                        )
                    } else {
                        val videos = uiState.currentVideos
                        val activePlaylistName = (dest as? BrowserDestination.Playlist)?.name
                        VideoListContent(
                            videos = videos,
                            isGridView = uiState.preferences.isGridView,
                            isSelectionMode = uiState.isSelectionMode,
                            selectedUris = uiState.selectedVideoUris,
                            favoriteUris = uiState.preferences.favoriteUris,
                            historyByUri = uiState.historyByUri,
                            searchQuery = uiState.searchQuery,
                            sortField = uiState.sortField,
                            sortDirection = uiState.sortDirection,
                            activePlaylistName = activePlaylistName,
                            onSearchQueryChange = onSearchQueryChange,
                            onOpenSortPopup = { showSortDialog = true },
                            onToggleGridView = onToggleGridView,
                            onVideoClick = { video ->
                                if (uiState.isSelectionMode) {
                                    onToggleSelection(video.uriString)
                                } else {
                                    onVideoClick(video, videos)
                                }
                            },
                            onVideoLongClick = { video ->
                                if (uiState.isSelectionMode) {
                                    onToggleSelection(video.uriString)
                                } else {
                                    onStartSelection(video.uriString)
                                }
                            },
                            onToggleFavorite = onToggleFavorite,
                            onAddToPlaylist = { playlistTargetVideo = it },
                            onRemoveFromPlaylist = { name, video ->
                                onRemoveFromPlaylist(name, video)
                            },
                            onShareVideo = onShareVideo,
                            onRenameVideo = { renameTargetVideo = it },
                            onMoveVideo = { moveTargetVideos = listOf(it) },
                            onShowVideoDetails = { detailVideo = it },
                            onDeleteVideo = { deleteTargetVideos = listOf(it) },
                            loadThumbnail = loadThumbnail
                        )
                    }
                }
            }
        }
    }

    // Sort By Popup Dialog (Requirement 2)
    if (showSortDialog) {
        SortOptionsDialog(
            currentField = uiState.sortField,
            currentDirection = uiState.sortDirection,
            onDismiss = { showSortDialog = false },
            onApply = { field, dir ->
                onApplySort(field, dir)
                showSortDialog = false
            }
        )
    }

    // Properties Dialog
    detailVideo?.let { video ->
        val history = uiState.historyByUri[video.uriString]
        AlertDialog(
            onDismissRequest = { detailVideo = null },
            title = {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("File: ${video.displayName}", style = MaterialTheme.typography.bodyMedium)
                    Text("Folder: ${video.folderName}", style = MaterialTheme.typography.bodyMedium)
                    if (video.relativePath.isNotBlank()) {
                        Text("Path: ${video.relativePath}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("Resolution: ${FormatUtils.formatResolution(video.width, video.height)}", style = MaterialTheme.typography.bodyMedium)
                    Text("Duration: ${FormatUtils.formatDuration(video.durationMs)}", style = MaterialTheme.typography.bodyMedium)
                    Text("Size: ${FormatUtils.formatFileSize(video.sizeBytes)}", style = MaterialTheme.typography.bodyMedium)
                    Text("Format: ${video.mimeType}", style = MaterialTheme.typography.bodyMedium)
                    if (history != null && history.positionMs > 1_500L) {
                        Text(
                            text = "Saved resume: ${FormatUtils.formatDuration(history.positionMs)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            },
            dismissButton = {
                if (history != null && history.positionMs > 1_500L) {
                    TextButton(
                        onClick = {
                            onClearResume(video.uriString)
                            detailVideo = null
                        }
                    ) {
                        Text("Reset Resume")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { detailVideo = null }) {
                    Text(stringResource(R.string.close_cta))
                }
            }
        )
    }

    // Rename Dialog
    renameTargetVideo?.let { video ->
        var text by remember(video.uriString) { mutableStateOf(video.title) }
        AlertDialog(
            onDismissRequest = { renameTargetVideo = null },
            title = { Text("Rename Video", style = MaterialTheme.typography.titleMedium) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Video name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            dismissButton = {
                TextButton(onClick = { renameTargetVideo = null }) {
                    Text(stringResource(R.string.cancel_cta))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameVideo(video, text)
                        renameTargetVideo = null
                    },
                    enabled = text.isNotBlank()
                ) {
                    Text("Rename")
                }
            }
        )
    }

    // Add to Playlist Dialog
    playlistTargetVideo?.let { video ->
        var newPlaylistName by remember { mutableStateOf("") }
        val existingPlaylists = uiState.preferences.playlists.keys.sorted()
        AlertDialog(
            onDismissRequest = { playlistTargetVideo = null },
            title = { Text("Add to Playlist", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (existingPlaylists.isNotEmpty()) {
                        existingPlaylists.forEach { name ->
                            Surface(
                                onClick = {
                                    onAddToPlaylist(name, video)
                                    playlistTargetVideo = null
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlaylistPlay,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${uiState.preferences.playlists[name]?.size ?: 0}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("New playlist name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistTargetVideo = null }) {
                    Text(stringResource(R.string.cancel_cta))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddToPlaylist(newPlaylistName, video)
                        playlistTargetVideo = null
                    },
                    enabled = newPlaylistName.isNotBlank()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Create & Add")
                }
            }
        )
    }

    // Move to Folder Dialog
    moveTargetVideos?.let { videosToMove ->
        var customFolder by remember { mutableStateOf("Movies/Moved") }
        val folderSuggestions = remember(uiState.folders) {
            uiState.folders.map { it.pathHint }.distinct().filter { it.isNotBlank() }
        }
        AlertDialog(
            onDismissRequest = { moveTargetVideos = null },
            title = {
                Text(
                    text = "Move ${videosToMove.size} video(s) to folder",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customFolder,
                        onValueChange = { customFolder = it },
                        label = { Text("Target folder (e.g. Movies/Clips)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (folderSuggestions.isNotEmpty()) {
                        Text(
                            text = "Or pick an existing folder:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyColumn(
                            modifier = Modifier.height(160.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(folderSuggestions) { path ->
                                Surface(
                                    onClick = {
                                        onMoveVideos(videosToMove, path)
                                        moveTargetVideos = null
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = path,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { moveTargetVideos = null }) {
                    Text(stringResource(R.string.cancel_cta))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onMoveVideos(videosToMove, customFolder)
                        moveTargetVideos = null
                    },
                    enabled = customFolder.isNotBlank()
                ) {
                    Text("Move")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    deleteTargetVideos?.let { videosToDelete ->
        AlertDialog(
            onDismissRequest = { deleteTargetVideos = null },
            title = { Text("Delete Video(s)", style = MaterialTheme.typography.titleMedium) },
            text = {
                val label = if (videosToDelete.size == 1) {
                    "Remove \"${videosToDelete.first().title}\" from device storage?"
                } else {
                    "Remove ${videosToDelete.size} selected videos from device storage?"
                }
                Text(label, style = MaterialTheme.typography.bodyMedium)
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetVideos = null }) {
                    Text(stringResource(R.string.cancel_cta))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteVideos(videosToDelete)
                        deleteTargetVideos = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete")
                }
            }
        )
    }
}

@Composable
private fun SortOptionsDialog(
    currentField: SortField,
    currentDirection: SortDirection,
    onDismiss: () -> Unit,
    onApply: (SortField, SortDirection) -> Unit
) {
    var selectedField by remember(currentField) { mutableStateOf(currentField) }
    var selectedDirection by remember(currentDirection) { mutableStateOf(currentDirection) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sort By", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SortField.entries.forEach { field ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selectedField = field }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedField == field,
                            onClick = { selectedField = field }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(field.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "Order",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SortDirection.entries.forEach { dir ->
                        val active = selectedDirection == dir
                        Surface(
                            onClick = { selectedDirection = dir },
                            shape = RoundedCornerShape(10.dp),
                            color = if (active) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (dir == SortDirection.ASC) "Ascending (ASC)" else "Descending (DESC)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                color = if (active) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp)
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_cta))
            }
        },
        confirmButton = {
            Button(onClick = { onApply(selectedField, selectedDirection) }) {
                Text("Apply")
            }
        }
    )
}

@Composable
private fun PermissionPromptView(
    onGrantPermission: () -> Unit,
    onOpenSingleFile: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = stringResource(R.string.permission_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.permission_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onGrantPermission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("grant_permission_button")
                ) {
                    Text(stringResource(R.string.permission_grant_cta))
                }

                OutlinedButton(
                    onClick = onOpenSingleFile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("fallback_open_file_button")
                ) {
                    Text(stringResource(R.string.permission_open_file_cta))
                }
            }
        }
    }
}

@Composable
private fun FolderBrowserContent(
    uiState: MainUiState,
    onSearchQueryChange: (String) -> Unit,
    onOpenSortPopup: () -> Unit,
    onToggleGridView: () -> Unit,
    onFolderClick: (VideoFolder) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onResumeLastVideo: (VideoItem) -> Unit,
    onOpenSingleFile: () -> Unit
) {
    if (uiState.folders.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(52.dp)
                )
                Text(
                    text = stringResource(R.string.empty_folders_title),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(R.string.empty_folders_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = onOpenSingleFile) {
                    Text(stringResource(R.string.open_file_desc))
                }
            }
        }
        return
    }

    val isGrid = uiState.preferences.isGridView

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns = if (isGrid) {
            when {
                maxWidth >= 840.dp -> 4
                maxWidth >= 560.dp -> 3
                else -> 2
            }
        } else {
            when {
                maxWidth >= 840.dp -> 3
                maxWidth >= 560.dp -> 2
                else -> 1
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(columns) }) {
                SearchAndSortRow(
                    query = uiState.searchQuery,
                    sortField = uiState.sortField,
                    sortDirection = uiState.sortDirection,
                    isGridView = isGrid,
                    onQueryChange = onSearchQueryChange,
                    onOpenSortPopup = onOpenSortPopup,
                    onToggleGridView = onToggleGridView
                )
            }

            // Favourites & Playlists quick chips row
            if (uiState.searchQuery.isBlank() &&
                (uiState.favoriteVideosCount > 0 || uiState.preferences.playlists.isNotEmpty())
            ) {
                item(span = { GridItemSpan(columns) }) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.favoriteVideosCount > 0) {
                            item {
                                Surface(
                                    onClick = onOpenFavorites,
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Favorite,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Favourites (${uiState.favoriteVideosCount})",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }

                        items(uiState.preferences.playlists.keys.sorted()) { name ->
                            val count = uiState.preferences.playlists[name]?.size ?: 0
                            Surface(
                                onClick = { onOpenPlaylist(name) },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlaylistPlay,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "$name ($count)",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    IconButton(
                                        onClick = { onDeletePlaylist(name) },
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Delete playlist",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val lastPlayed = uiState.recentVideos.firstOrNull()
            if (lastPlayed != null && uiState.searchQuery.isBlank()) {
                item(span = { GridItemSpan(columns) }) {
                    val (video, history) = lastPlayed
                    Surface(
                        onClick = { onResumeLastVideo(video) },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quick_resume_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Resume: ${video.title}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = FormatUtils.formatDuration(history.positionMs),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            items(uiState.filteredFolders, key = { it.bucketId }) { folder ->
                if (isGrid) {
                    GridFolderCard(
                        folder = folder,
                        onClick = { onFolderClick(folder) }
                    )
                } else {
                    CompactFolderRow(
                        folder = folder,
                        onClick = { onFolderClick(folder) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoListContent(
    videos: List<VideoItem>,
    isGridView: Boolean,
    isSelectionMode: Boolean,
    selectedUris: Set<String>,
    favoriteUris: Set<String>,
    historyByUri: Map<String, PlaybackHistoryEntity>,
    searchQuery: String,
    sortField: SortField,
    sortDirection: SortDirection,
    activePlaylistName: String?,
    onSearchQueryChange: (String) -> Unit,
    onOpenSortPopup: () -> Unit,
    onToggleGridView: () -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    onVideoLongClick: (VideoItem) -> Unit,
    onToggleFavorite: (VideoItem) -> Unit,
    onAddToPlaylist: (VideoItem) -> Unit,
    onRemoveFromPlaylist: (String, VideoItem) -> Unit,
    onShareVideo: (VideoItem) -> Unit,
    onRenameVideo: (VideoItem) -> Unit,
    onMoveVideo: (VideoItem) -> Unit,
    onShowVideoDetails: (VideoItem) -> Unit,
    onDeleteVideo: (VideoItem) -> Unit,
    loadThumbnail: suspend (Long, String) -> Bitmap?
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val gridColumns = when {
            maxWidth >= 840.dp -> 4
            maxWidth >= 560.dp -> 3
            else -> 2
        }

        if (isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(span = { GridItemSpan(gridColumns) }) {
                    SearchAndSortRow(
                        query = searchQuery,
                        sortField = sortField,
                        sortDirection = sortDirection,
                        isGridView = isGridView,
                        onQueryChange = onSearchQueryChange,
                        onOpenSortPopup = onOpenSortPopup,
                        onToggleGridView = onToggleGridView
                    )
                }

                items(videos, key = { it.id }) { video ->
                    GridVideoCard(
                        video = video,
                        isSelected = selectedUris.contains(video.uriString),
                        isSelectionMode = isSelectionMode,
                        isFavorite = favoriteUris.contains(video.uriString),
                        history = historyByUri[video.uriString],
                        activePlaylistName = activePlaylistName,
                        onClick = { onVideoClick(video) },
                        onLongClick = { onVideoLongClick(video) },
                        onToggleFavorite = { onToggleFavorite(video) },
                        onAddToPlaylist = { onAddToPlaylist(video) },
                        onRemoveFromPlaylist = { name -> onRemoveFromPlaylist(name, video) },
                        onShare = { onShareVideo(video) },
                        onRename = { onRenameVideo(video) },
                        onMove = { onMoveVideo(video) },
                        onDetailsClick = { onShowVideoDetails(video) },
                        onDelete = { onDeleteVideo(video) },
                        loadThumbnail = loadThumbnail
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    SearchAndSortRow(
                        query = searchQuery,
                        sortField = sortField,
                        sortDirection = sortDirection,
                        isGridView = isGridView,
                        onQueryChange = onSearchQueryChange,
                        onOpenSortPopup = onOpenSortPopup,
                        onToggleGridView = onToggleGridView
                    )
                }

                items(videos, key = { it.id }) { video ->
                    CompactVideoRow(
                        video = video,
                        isSelected = selectedUris.contains(video.uriString),
                        isSelectionMode = isSelectionMode,
                        isFavorite = favoriteUris.contains(video.uriString),
                        history = historyByUri[video.uriString],
                        activePlaylistName = activePlaylistName,
                        onClick = { onVideoClick(video) },
                        onLongClick = { onVideoLongClick(video) },
                        onToggleFavorite = { onToggleFavorite(video) },
                        onAddToPlaylist = { onAddToPlaylist(video) },
                        onRemoveFromPlaylist = { name -> onRemoveFromPlaylist(name, video) },
                        onShare = { onShareVideo(video) },
                        onRename = { onRenameVideo(video) },
                        onMove = { onMoveVideo(video) },
                        onDetailsClick = { onShowVideoDetails(video) },
                        onDelete = { onDeleteVideo(video) },
                        loadThumbnail = loadThumbnail
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchAndSortRow(
    query: String,
    sortField: SortField,
    sortDirection: SortDirection,
    isGridView: Boolean,
    onQueryChange: (String) -> Unit,
    onOpenSortPopup: () -> Unit,
    onToggleGridView: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                Text(
                    text = stringResource(R.string.search_videos_hint),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp)
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .weight(1f)
                .testTag("library_search_input")
        )

        Surface(
            onClick = onOpenSortPopup,
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("sort_chip_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = "Sort",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "${sortField.label} ${sortDirection.label}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Surface(
            onClick = onToggleGridView,
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("toolbar_grid_button")
        ) {
            Box(
                modifier = Modifier.padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGridView) {
                        Icons.AutoMirrored.Filled.ViewList
                    } else {
                        Icons.Default.GridView
                    },
                    contentDescription = "Show Grid",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun CompactFolderRow(
    folder: VideoFolder,
    onClick: () -> Unit
) {
    val unit = if (folder.videoCount == 1) {
        stringResource(R.string.video_singular)
    } else {
        stringResource(R.string.video_plural)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("folder_card_${folder.bucketId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 13.5.sp,
                        lineHeight = 17.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(1.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${folder.videoCount} $unit",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "· ${FormatUtils.formatFileSize(folder.totalSizeBytes)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "· ${folder.pathHint}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun GridFolderCard(
    folder: VideoFolder,
    onClick: () -> Unit
) {
    val unit = if (folder.videoCount == 1) {
        stringResource(R.string.video_singular)
    } else {
        stringResource(R.string.video_plural)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("folder_grid_card_${folder.bucketId}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = "${folder.videoCount} $unit · ${FormatUtils.formatFileSize(folder.totalSizeBytes)}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactVideoRow(
    video: VideoItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isFavorite: Boolean,
    history: PlaybackHistoryEntity?,
    activePlaylistName: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onRemoveFromPlaylist: (String) -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDetailsClick: () -> Unit,
    onDelete: () -> Unit,
    loadThumbnail: suspend (Long, String) -> Bitmap?
) {
    val resumePos = history?.positionMs ?: 0L
    val progress = if (history != null && history.durationMs > 0L) {
        (history.positionMs.toFloat() / history.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val ext = video.displayName.substringAfterLast('.', "").uppercase().ifBlank { "VID" }
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("video_card_${video.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Icon(
                    imageVector = if (isSelected) {
                        Icons.Default.CheckCircle
                    } else {
                        Icons.Default.RadioButtonUnchecked
                    },
                    contentDescription = if (isSelected) "Selected" else "Not selected",
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(22.dp)
                )
            }

            Box(
                modifier = Modifier
                    .width(92.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                VideoThumbnail(
                    videoId = video.id,
                    uriString = video.uriString,
                    contentDescription = video.title,
                    loadThumbnail = loadThumbnail,
                    modifier = Modifier.fillMaxSize()
                )

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(3.dp)
                ) {
                    Text(
                        text = FormatUtils.formatDuration(video.durationMs),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }

                if (resumePos > 1_500L) {
                    LinearProgressIndicator(
                        progress = { progress },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.25f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .align(Alignment.BottomCenter)
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 12.5.sp,
                        lineHeight = 16.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val resTag = FormatUtils.formatResolution(video.width, video.height)
                    .substringBefore(" ·")
                Text(
                    text = "$resTag · ${FormatUtils.formatFileSize(video.sizeBytes)} · $ext",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (resumePos > 1_500L) {
                    Text(
                        text = stringResource(
                            R.string.resume_badge_format,
                            FormatUtils.formatDuration(resumePos)
                        ),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            if (!isSelectionMode) {
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "File Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    VideoFileDropdownMenu(
                        expanded = menuExpanded,
                        isFavorite = isFavorite,
                        activePlaylistName = activePlaylistName,
                        onDismiss = { menuExpanded = false },
                        onToggleFavorite = onToggleFavorite,
                        onAddToPlaylist = onAddToPlaylist,
                        onRemoveFromPlaylist = onRemoveFromPlaylist,
                        onShare = onShare,
                        onRename = onRename,
                        onMove = onMove,
                        onProperties = onDetailsClick,
                        onDelete = onDelete
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridVideoCard(
    video: VideoItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isFavorite: Boolean,
    history: PlaybackHistoryEntity?,
    activePlaylistName: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onRemoveFromPlaylist: (String) -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDetailsClick: () -> Unit,
    onDelete: () -> Unit,
    loadThumbnail: suspend (Long, String) -> Bitmap?
) {
    val resumePos = history?.positionMs ?: 0L
    val progress = if (history != null && history.durationMs > 0L) {
        (history.positionMs.toFloat() / history.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                VideoThumbnail(
                    videoId = video.id,
                    uriString = video.uriString,
                    contentDescription = video.title,
                    loadThumbnail = loadThumbnail,
                    modifier = Modifier.fillMaxSize()
                )

                if (isSelectionMode) {
                    Icon(
                        imageVector = if (isSelected) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.RadioButtonUnchecked
                        },
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .size(22.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                ) {
                    Text(
                        text = FormatUtils.formatDuration(video.durationMs),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }

                if (resumePos > 1_500L) {
                    LinearProgressIndicator(
                        progress = { progress },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.25f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .align(Alignment.BottomCenter)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = FormatUtils.formatFileSize(video.sizeBytes),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!isSelectionMode) {
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "File Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        VideoFileDropdownMenu(
                            expanded = menuExpanded,
                            isFavorite = isFavorite,
                            activePlaylistName = activePlaylistName,
                            onDismiss = { menuExpanded = false },
                            onToggleFavorite = onToggleFavorite,
                            onAddToPlaylist = onAddToPlaylist,
                            onRemoveFromPlaylist = onRemoveFromPlaylist,
                            onShare = onShare,
                            onRename = onRename,
                            onMove = onMove,
                            onProperties = onDetailsClick,
                            onDelete = onDelete
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoFileDropdownMenu(
    expanded: Boolean,
    isFavorite: Boolean,
    activePlaylistName: String?,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onRemoveFromPlaylist: (String) -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onProperties: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text(if (isFavorite) "Remove from Favourite" else "Add to Favourite") },
            leadingIcon = {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null
                )
            },
            onClick = {
                onDismiss()
                onToggleFavorite()
            }
        )
        DropdownMenuItem(
            text = { Text("Add to Playlist") },
            leadingIcon = {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
            },
            onClick = {
                onDismiss()
                onAddToPlaylist()
            }
        )
        if (activePlaylistName != null) {
            DropdownMenuItem(
                text = { Text("Remove from Playlist") },
                leadingIcon = {
                    Icon(Icons.Default.Close, contentDescription = null)
                },
                onClick = {
                    onDismiss()
                    onRemoveFromPlaylist(activePlaylistName)
                }
            )
        }
        DropdownMenuItem(
            text = { Text("Share") },
            leadingIcon = {
                Icon(Icons.Default.Share, contentDescription = null)
            },
            onClick = {
                onDismiss()
                onShare()
            }
        )
        DropdownMenuItem(
            text = { Text("Rename") },
            leadingIcon = {
                Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null)
            },
            onClick = {
                onDismiss()
                onRename()
            }
        )
        DropdownMenuItem(
            text = { Text("Move") },
            leadingIcon = {
                Icon(Icons.Default.DriveFileMove, contentDescription = null)
            },
            onClick = {
                onDismiss()
                onMove()
            }
        )
        DropdownMenuItem(
            text = { Text("Properties") },
            leadingIcon = {
                Icon(Icons.Default.Info, contentDescription = null)
            },
            onClick = {
                onDismiss()
                onProperties()
            }
        )
        DropdownMenuItem(
            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            onClick = {
                onDismiss()
                onDelete()
            }
        )
    }
}
