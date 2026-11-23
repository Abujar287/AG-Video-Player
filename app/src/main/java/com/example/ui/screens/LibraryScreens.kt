package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.local.PlaybackHistoryEntity
import com.example.data.model.BrowserDestination
import com.example.data.model.SortDirection
import com.example.data.model.SortField
import com.example.data.model.UpdateUiState
import com.example.data.model.UserPreferences
import com.example.data.model.VideoFolder
import com.example.data.model.VideoItem
import com.example.ui.PendingMoveRequest
import com.example.ui.components.ControlSettingsDialog
import com.example.ui.components.UpdateDialog
import com.example.ui.components.VideoThumbnail
import com.example.ui.theme.BadgeNewRed
import com.example.util.FormatUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryBrowserScreen(
    isLoadingLibrary: Boolean,
    folders: List<VideoFolder>,
    allVideos: List<VideoItem>,
    videosByFolder: Map<String, List<VideoItem>>,
    filteredFolders: List<VideoFolder>,
    currentVideos: List<VideoItem>,
    sortedAllVideos: List<VideoItem>,
    destination: BrowserDestination,
    searchQuery: String,
    sortField: SortField,
    sortDirection: SortDirection,
    selectedVideoUris: Set<String>,
    historyByUri: Map<String, PlaybackHistoryEntity>,
    lastHistoryEntry: PlaybackHistoryEntity?,
    preferences: UserPreferences,
    updateState: UpdateUiState,
    showUpdateDialog: Boolean,
    actionMessage: String?,
    pendingMoveAccessPrompt: PendingMoveRequest?,
    currentAppVersionLabel: String,
    onRefresh: () -> Unit,
    onOpenDocumentPicker: () -> Unit,
    onToggleTheme: () -> Unit,
    onSelectThemeId: (String) -> Unit,
    onToggleGridView: () -> Unit,
    onApplySort: (SortField, SortDirection) -> Unit,
    onOpenUpdateCheck: () -> Unit,
    onDismissUpdateDialog: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onFolderClick: (String) -> Unit,
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
    onRenameVideo: (VideoItem, String) -> Unit,
    onMoveVideos: (List<VideoItem>, String) -> Unit,
    onDeleteVideos: (List<VideoItem>) -> Unit,
    onClearResume: (String) -> Unit,
    onClearActionMessage: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onMoveToScopedMovies: (PendingMoveRequest) -> Unit,
    onDismissMoveAccessPrompt: () -> Unit,
    onSingleClickActionChange: (com.example.data.model.SingleClickAction) -> Unit,
    onDoubleClickActionChange: (com.example.data.model.DoubleClickAction) -> Unit,
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
    onTogglePlayerButtonVisibility: (com.example.data.model.PlayerControlButton, Boolean) -> Unit,
    onShowAllButtons: () -> Unit,
    onProgressBarStyleChange: (com.example.data.model.ProgressBarStyle) -> Unit,
    onStartDownload: (com.example.data.model.GitHubReleaseInfo) -> Unit,
    onInstallUpdate: (String) -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val isSelectionMode = selectedVideoUris.isNotEmpty()
    val isInVideoList = destination != BrowserDestination.Folders
    val isGridLayoutActive = when (preferences.mainViewLayout) {
        "list" -> false
        "grid" -> true
        else -> preferences.isGridView
    }
    val hasAvailableUpdate = updateState is UpdateUiState.Available

    var showSortDialog by remember { mutableStateOf(false) }
    var showControlSettings by remember { mutableStateOf(false) }
    var detailVideo by remember { mutableStateOf<VideoItem?>(null) }
    var renameTargetVideo by remember { mutableStateOf<VideoItem?>(null) }
    var playlistTargetVideo by remember { mutableStateOf<VideoItem?>(null) }
    var moveTargetVideos by remember { mutableStateOf<List<VideoItem>?>(null) }
    var deleteTargetVideos by remember { mutableStateOf<List<VideoItem>?>(null) }

    LaunchedEffect(actionMessage) {
        if (!actionMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(actionMessage)
            onClearActionMessage()
        }
    }

    val displayedVideos = if (destination == BrowserDestination.Folders && preferences.mainDisplayMode == "files") {
        sortedAllVideos
    } else {
        currentVideos
    }

    val selectedFolder = (destination as? BrowserDestination.Folder)?.let { dest ->
        folders.firstOrNull { it.bucketId == dest.bucketId }
    }

    val activePlaylistName = (destination as? BrowserDestination.Playlist)?.name

    val titleText = when (destination) {
        BrowserDestination.Folders -> "AG Video Player"
        is BrowserDestination.Folder -> selectedFolder?.name ?: "Folder"
        BrowserDestination.Favorites -> "Favourites"
        is BrowserDestination.Playlist -> destination.name
    }

    val subtitleText = when {
        selectedFolder != null -> {
            val unit = if (selectedFolder.videoCount == 1) "video" else "videos"
            "${selectedFolder.videoCount} $unit · ${FormatUtils.formatFileSize(selectedFolder.totalSizeBytes)}"
        }
        destination == BrowserDestination.Favorites || destination is BrowserDestination.Playlist -> {
            val unit = if (displayedVideos.size == 1) "video" else "videos"
            "${displayedVideos.size} $unit"
        }
        folders.isNotEmpty() -> "${folders.size} folders · ${allVideos.size} videos"
        else -> ""
    }

    val favoriteVideosCount = remember(allVideos, preferences.favoriteUris) {
        val available = allVideos.map { it.uriString }.toSet()
        preferences.favoriteUris.count { it in available }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    navigationIcon = {
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cancel selection")
                        }
                    },
                    title = {
                        Text(
                            text = "${selectedVideoUris.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    actions = {
                        IconButton(onClick = onSelectAll) {
                            Icon(Icons.Rounded.SelectAll, contentDescription = "Select all")
                        }
                        IconButton(onClick = {
                            val selected = displayedVideos.filter { it.uriString in selectedVideoUris }
                            if (selected.isNotEmpty()) moveTargetVideos = selected
                        }) {
                            Icon(Icons.Rounded.DriveFileMove, contentDescription = "Move selected")
                        }
                        IconButton(onClick = {
                            val selected = displayedVideos.filter { it.uriString in selectedVideoUris }
                            if (selected.isNotEmpty()) deleteTargetVideos = selected
                        }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete selected")
                        }
                    }
                )
            } else {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    navigationIcon = {
                        if (isInVideoList) {
                            IconButton(onClick = onBackToFolders) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                            }
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.ic_launcher_foreground_art),
                                contentDescription = "AG Video Player",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .padding(start = 10.dp, end = 4.dp)
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }
                    },
                    title = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = titleText,
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.5.sp),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (subtitleText.isNotBlank()) {
                                Text(
                                    text = subtitleText,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = onToggleTheme,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (preferences.isDarkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                contentDescription = "Toggle Theme",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = onToggleGridView,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isGridLayoutActive) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                                contentDescription = "Toggle Grid",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = { showSortDialog = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Rounded.SwapVert,
                                contentDescription = "Sort",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Box {
                            IconButton(
                                onClick = { showControlSettings = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.MoreVert,
                                    contentDescription = "Settings",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            if (hasAvailableUpdate) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 7.dp, end = 7.dp)
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondary)
                                )
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode && (lastHistoryEntry != null || allVideos.isNotEmpty())) {
                FloatingActionButton(
                    onClick = onResumeLastPlayed,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Resume Last Video",
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search folders or videos…", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(Modifier.height(10.dp))

            PullToRefreshBox(
                isRefreshing = isLoadingLibrary,
                onRefresh = onRefresh,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
            Column(modifier = Modifier.fillMaxSize()) {
            if (destination == BrowserDestination.Folders && preferences.mainDisplayMode == "folders") {
                if (!searchQuery.isNotBlank() && (favoriteVideosCount > 0 || preferences.playlists.isNotEmpty())) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (favoriteVideosCount > 0) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f))
                                    .clickable(onClick = onOpenFavorites)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Favorite,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Favourites ($favoriteVideosCount)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        preferences.playlists.keys.sorted().forEach { name ->
                            val count = preferences.playlists[name]?.size ?: 0
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { onOpenPlaylist(name) }
                                    .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlaylistPlay,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "$name ($count)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                IconButton(
                                    onClick = { onDeletePlaylist(name) },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Delete playlist",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if (folders.isEmpty() && !isLoadingLibrary) {
                    EmptyLibraryState(onOpenDocumentPicker = onOpenDocumentPicker)
                } else if (isGridLayoutActive) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(155.dp),
                        contentPadding = PaddingValues(bottom = 84.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredFolders, key = { it.bucketId }) { folder ->
                            val unplayed = videosByFolder[folder.bucketId]?.count { it.uriString !in preferences.playedVideoUris } ?: 0
                            FolderGridCard(folder = folder, newCount = unplayed, onClick = { onFolderClick(folder.bucketId) })
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 84.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredFolders, key = { it.bucketId }) { folder ->
                            val unplayed = videosByFolder[folder.bucketId]?.count { it.uriString !in preferences.playedVideoUris } ?: 0
                            FolderListCard(folder = folder, newCount = unplayed, onClick = { onFolderClick(folder.bucketId) })
                        }
                    }
                }
            } else {
                if (displayedVideos.isEmpty()) {
                    EmptyVideosState()
                } else if (isGridLayoutActive) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(160.dp),
                        contentPadding = PaddingValues(bottom = 84.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(displayedVideos, key = { it.uriString }) { video ->
                            VideoCard(
                                video = video,
                                isGrid = true,
                                isSelected = video.uriString in selectedVideoUris,
                                isSelectionMode = isSelectionMode,
                                isFavorite = video.uriString in preferences.favoriteUris,
                                isNew = video.uriString !in preferences.playedVideoUris,
                                history = historyByUri[video.uriString],
                                activePlaylistName = activePlaylistName,
                                onClick = {
                                    if (isSelectionMode) onToggleSelection(video.uriString)
                                    else onVideoClick(video, displayedVideos)
                                },
                                onLongClick = {
                                    if (isSelectionMode) onToggleSelection(video.uriString)
                                    else onStartSelection(video.uriString)
                                },
                                onToggleFavorite = { onToggleFavorite(video) },
                                onAddToPlaylist = { playlistTargetVideo = video },
                                onRemoveFromPlaylist = { name -> onRemoveFromPlaylist(name, video) },
                                onShare = { shareVideoFile(context, video) },
                                onRename = { renameTargetVideo = video },
                                onMove = { moveTargetVideos = listOf(video) },
                                onProperties = { detailVideo = video },
                                onDelete = { deleteTargetVideos = listOf(video) }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 84.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(displayedVideos, key = { it.uriString }) { video ->
                            VideoCard(
                                video = video,
                                isGrid = false,
                                isSelected = video.uriString in selectedVideoUris,
                                isSelectionMode = isSelectionMode,
                                isFavorite = video.uriString in preferences.favoriteUris,
                                isNew = video.uriString !in preferences.playedVideoUris,
                                history = historyByUri[video.uriString],
                                activePlaylistName = activePlaylistName,
                                onClick = {
                                    if (isSelectionMode) onToggleSelection(video.uriString)
                                    else onVideoClick(video, displayedVideos)
                                },
                                onLongClick = {
                                    if (isSelectionMode) onToggleSelection(video.uriString)
                                    else onStartSelection(video.uriString)
                                },
                                onToggleFavorite = { onToggleFavorite(video) },
                                onAddToPlaylist = { playlistTargetVideo = video },
                                onRemoveFromPlaylist = { name -> onRemoveFromPlaylist(name, video) },
                                onShare = { shareVideoFile(context, video) },
                                onRename = { renameTargetVideo = video },
                                onMove = { moveTargetVideos = listOf(video) },
                                onProperties = { detailVideo = video },
                                onDelete = { deleteTargetVideos = listOf(video) }
                            )
                        }
                    }
                }
            }
            }
            }
        }
    }

    // Dialogs
    if (showSortDialog) {
        SortOptionsDialog(
            currentField = sortField,
            currentDirection = sortDirection,
            onDismiss = { showSortDialog = false },
            onApply = { f, d ->
                onApplySort(f, d)
                showSortDialog = false
            }
        )
    }

    if (showControlSettings) {
        ControlSettingsDialog(
            preferences = preferences,
            currentVersionLabel = currentAppVersionLabel,
            hasAvailableUpdate = hasAvailableUpdate,
            onDismiss = { showControlSettings = false },
            onToggleTheme = onToggleTheme,
            onSelectThemeId = onSelectThemeId,
            onOpenUpdateCheck = onOpenUpdateCheck,
            onProgressBarStyleChange = onProgressBarStyleChange,
            onSingleClickActionChange = onSingleClickActionChange,
            onDoubleClickActionChange = onDoubleClickActionChange,
            onAlwaysBackgroundPlayChange = onAlwaysBackgroundPlayChange,
            onAutoPipChange = onAutoPipChange,
            onEnableSwipeGesturesChange = onEnableSwipeGesturesChange,
            onGestureSeekStepChange = onGestureSeekStepChange,
            onDefaultSoftwareDecoderChange = onDefaultSoftwareDecoderChange,
            onShowHiddenFilesChange = onShowHiddenFilesChange,
            onMainDisplayModeChange = onMainDisplayModeChange,
            onMainViewLayoutChange = onMainViewLayoutChange,
            onOneFingerGestureChange = onOneFingerGestureChange,
            onTwoFingerGestureChange = onTwoFingerGestureChange,
            onThreeFingerGestureChange = onThreeFingerGestureChange,
            onTogglePlayerButtonVisibility = onTogglePlayerButtonVisibility,
            onShowAllButtons = onShowAllButtons
        )
    }

    if (showUpdateDialog) {
        UpdateDialog(
            currentVersionLabel = currentAppVersionLabel,
            state = updateState,
            onDismiss = onDismissUpdateDialog,
            onRetryCheck = onOpenUpdateCheck,
            onStartDownload = onStartDownload,
            onInstallUpdate = onInstallUpdate
        )
    }

    detailVideo?.let { vid ->
        VideoPropertiesDialog(
            video = vid,
            history = historyByUri[vid.uriString],
            onClearResume = onClearResume,
            onDismiss = { detailVideo = null }
        )
    }

    renameTargetVideo?.let { vid ->
        RenameVideoDialog(
            video = vid,
            onRename = onRenameVideo,
            onDismiss = { renameTargetVideo = null }
        )
    }

    playlistTargetVideo?.let { vid ->
        AddToPlaylistDialog(
            video = vid,
            existingPlaylists = preferences.playlists.keys.sorted(),
            onAdd = onAddToPlaylist,
            onDismiss = { playlistTargetVideo = null }
        )
    }

    moveTargetVideos?.let { list ->
        MoveVideosDialog(
            videos = list,
            folders = folders,
            onMove = onMoveVideos,
            onDismiss = { moveTargetVideos = null }
        )
    }

    deleteTargetVideos?.let { list ->
        DeleteConfirmDialog(
            videos = list,
            onConfirm = { targets ->
                onDeleteVideos(targets)
                onClearSelection()
                deleteTargetVideos = null
            },
            onDismiss = { deleteTargetVideos = null }
        )
    }

    // Popup asking user cleanly for File Access when needed
    pendingMoveAccessPrompt?.let { pending ->
        val isDelete = pending.targetPath == "delete"
        AlertDialog(
            onDismissRequest = onDismissMoveAccessPrompt,
            icon = {
                Icon(Icons.Rounded.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            title = {
                Text(if (isDelete) "Storage Access Needed" else "Storage Access Needed to Move", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isDelete) {
                            "To delete videos without repeated system popups, please grant File Management access once."
                        } else {
                            "To move files directly to \"${pending.targetPath}\" without Android folder restrictions, grant File Management access in the popup, or move directly into Movies/${pending.targetPath.substringAfterLast('/')}."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(onClick = onRequestAllFilesAccess) {
                    Text("Allow File Access")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = onDismissMoveAccessPrompt) {
                        Text("Cancel")
                    }
                    if (!isDelete) {
                        OutlinedButton(onClick = { onMoveToScopedMovies(pending) }) {
                            Text("Move to Movies")
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun FolderGridCard(folder: VideoFolder, newCount: Int, onClick: () -> Unit) {
    val unit = if (folder.videoCount == 1) "video" else "videos"
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        if (newCount > 0) {
            Text(
                text = newCount.toString(),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(BadgeNewRed)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${folder.videoCount} $unit · ${FormatUtils.formatFileSize(folder.totalSizeBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FolderListCard(folder: VideoFolder, newCount: Int, onClick: () -> Unit) {
    val unit = if (folder.videoCount == 1) "video" else "videos"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "${folder.videoCount} $unit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "· ${FormatUtils.formatFileSize(folder.totalSizeBytes)} · ${folder.pathHint}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (newCount > 0) {
            Text(
                text = newCount.toString(),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(BadgeNewRed)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoCard(
    video: VideoItem,
    isGrid: Boolean,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isFavorite: Boolean,
    isNew: Boolean,
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
    onProperties: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val progressFraction = if (history != null && history.durationMs > 0L && history.positionMs > 0L) {
        (history.positionMs.toFloat() / history.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

    if (isGrid) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface)
                .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                VideoThumbnail(
                    uriString = video.uriString,
                    filePath = video.filePath,
                    modifier = Modifier.fillMaxSize()
                )
                if (isNew) {
                    Text(
                        text = "NEW",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(BadgeNewRed)
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
                if (isSelectionMode) {
                    Icon(
                        imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(20.dp)
                    )
                }
                Text(
                    text = FormatUtils.formatDuration(video.durationMs),
                    color = Color.White,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
                if (progressFraction > 0f) {
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.25f)
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${FormatUtils.formatFileSize(video.sizeBytes)} · ${FormatUtils.formatResolution(video.width, video.height)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!isSelectionMode) {
                    Box {
                        IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More", modifier = Modifier.size(18.dp))
                        }
                        VideoContextMenu(
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
                            onProperties = onProperties,
                            onDelete = onDelete
                        )
                    }
                }
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface)
                .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(108.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                VideoThumbnail(
                    uriString = video.uriString,
                    filePath = video.filePath,
                    modifier = Modifier.fillMaxSize()
                )
                if (isNew) {
                    Text(
                        text = "NEW",
                        color = Color.White,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(BadgeNewRed)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
                Text(
                    text = FormatUtils.formatDuration(video.durationMs),
                    color = Color.White,
                    fontSize = 9.5.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
                if (progressFraction > 0f) {
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(2.5.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.25f)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${FormatUtils.formatFileSize(video.sizeBytes)} · ${FormatUtils.formatResolution(video.width, video.height)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isSelectionMode) {
                Icon(
                    imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 6.dp)
                )
            } else {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
                    }
                    VideoContextMenu(
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
                        onProperties = onProperties,
                        onDelete = onDelete
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoContextMenu(
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
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(if (isFavorite) "Remove from Favourites" else "Add to Favourites") },
            leadingIcon = { Icon(if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null) },
            onClick = { onDismiss(); onToggleFavorite() }
        )
        DropdownMenuItem(
            text = { Text("Add to Playlist") },
            leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, null) },
            onClick = { onDismiss(); onAddToPlaylist() }
        )
        if (activePlaylistName != null) {
            DropdownMenuItem(
                text = { Text("Remove from \"$activePlaylistName\"") },
                leadingIcon = { Icon(Icons.Rounded.Close, null) },
                onClick = { onDismiss(); onRemoveFromPlaylist(activePlaylistName) }
            )
        }
        DropdownMenuItem(
            text = { Text("Rename") },
            leadingIcon = { Icon(Icons.Rounded.Edit, null) },
            onClick = { onDismiss(); onRename() }
        )
        DropdownMenuItem(
            text = { Text("Move") },
            leadingIcon = { Icon(Icons.Rounded.DriveFileMove, null) },
            onClick = { onDismiss(); onMove() }
        )
        DropdownMenuItem(
            text = { Text("Share") },
            leadingIcon = { Icon(Icons.Rounded.Share, null) },
            onClick = { onDismiss(); onShare() }
        )
        DropdownMenuItem(
            text = { Text("Properties") },
            leadingIcon = { Icon(Icons.Rounded.Info, null) },
            onClick = { onDismiss(); onProperties() }
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
            leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
            onClick = { onDismiss(); onDelete() }
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
    var field by remember { mutableStateOf(currentField) }
    var direction by remember { mutableStateOf(currentDirection) }

    fun hint(dir: SortDirection): String = when (field) {
        SortField.NAME, SortField.TYPE -> if (dir == SortDirection.ASC) "A → Z" else "Z → A"
        SortField.SIZE -> if (dir == SortDirection.ASC) "Smallest" else "Largest"
        SortField.DATE, SortField.PLAYS -> if (dir == SortDirection.ASC) "Oldest" else "Newest"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.SwapVert, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Sort By", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SortField.entries.forEach { f ->
                    val selected = field == f
                    val icon = when (f) {
                        SortField.NAME -> Icons.Rounded.SortByAlpha
                        SortField.SIZE -> Icons.Rounded.Storage
                        SortField.DATE -> Icons.Rounded.CalendarToday
                        SortField.TYPE -> Icons.Rounded.Folder
                        SortField.PLAYS -> Icons.Rounded.PlayArrow
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable { field = f }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(f.label, modifier = Modifier.weight(1f), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                        if (selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SortDirection.entries.forEach { dir ->
                        val selected = direction == dir
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { direction = dir }
                                .padding(vertical = 8.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (dir == SortDirection.ASC) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${dir.label} (${hint(dir)})",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(field, direction) }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun VideoPropertiesDialog(
    video: VideoItem,
    history: PlaybackHistoryEntity?,
    onClearResume: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Video Properties", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PropRow("File Name", video.displayName)
                PropRow("Folder", video.relativePath.trimEnd('/').ifBlank { video.folderName })
                PropRow("Duration", FormatUtils.formatDuration(video.durationMs))
                PropRow("Size", FormatUtils.formatFileSize(video.sizeBytes))
                PropRow("Resolution", FormatUtils.formatResolution(video.width, video.height))
                PropRow("Format", video.mimeType)
                if (history != null && history.positionMs > 0L) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { onClearResume(video.uriString); onDismiss() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reset Resume (${FormatUtils.formatDuration(history.positionMs)})")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun PropRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RenameVideoDialog(
    video: VideoItem,
    onRename: (VideoItem, String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember(video.uriString) { mutableStateOf(video.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Video", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("New name") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                enabled = text.isNotBlank(),
                onClick = { onRename(video, text); onDismiss() }
            ) { Text("Rename") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AddToPlaylistDialog(
    video: VideoItem,
    existingPlaylists: List<String>,
    onAdd: (String, VideoItem) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Playlist", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                existingPlaylists.forEach { pl ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onAdd(pl, video); onDismiss() }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.PlaylistPlay, null, tint = MaterialTheme.colorScheme.primary)
                        Text(pl, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("New playlist name") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = newName.isNotBlank(),
                onClick = { onAdd(newName, video); onDismiss() }
            ) {
                Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Create & Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun MoveVideosDialog(
    videos: List<VideoItem>,
    folders: List<VideoFolder>,
    onMove: (List<VideoItem>, String) -> Unit,
    onDismiss: () -> Unit
) {
    var targetFolder by remember { mutableStateOf("Movies/Moved") }
    val suggestions = remember(folders) { folders.map { it.pathHint }.filter { it.isNotBlank() }.distinct() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Move ${videos.size} video(s) to folder", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = targetFolder,
                    onValueChange = { targetFolder = it },
                    singleLine = true,
                    label = { Text("Target folder (e.g. Movies/Clips)") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (suggestions.isNotEmpty()) {
                    Text("Or tap an existing folder:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    suggestions.take(5).forEach { path ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onMove(videos, path); onDismiss() }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text(path, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = targetFolder.isNotBlank(),
                onClick = { onMove(videos, targetFolder); onDismiss() }
            ) { Text("Move") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun DeleteConfirmDialog(
    videos: List<VideoItem>,
    onConfirm: (List<VideoItem>) -> Unit,
    onDismiss: () -> Unit
) {
    val titleText = if (videos.size == 1) "Are you sure to delete file" else "Are you sure to delete ${videos.size} files"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = { Text("This file will be permanently removed from storage.") },
        confirmButton = {
            Button(
                onClick = { onConfirm(videos); onDismiss() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EmptyLibraryState(onOpenDocumentPicker: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Rounded.FolderOpen, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(54.dp))
        Spacer(Modifier.height(10.dp))
        Text("No Local Videos Found", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text("Add videos to storage or pick a file directly.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onOpenDocumentPicker) {
            Text("Open Video File")
        }
    }
}

@Composable
private fun EmptyVideosState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Rounded.FolderOpen, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(8.dp))
        Text("No videos found", style = MaterialTheme.typography.titleSmall)
    }
}

private fun shareVideoFile(context: android.content.Context, video: VideoItem) {
    val rawUri = android.net.Uri.parse(video.uriString)
    val shareUri = if (rawUri.scheme == "file" && !video.filePath.isNullOrBlank()) {
        runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(video.filePath))
        }.getOrDefault(rawUri)
    } else {
        rawUri
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = video.mimeType
        putExtra(Intent.EXTRA_STREAM, shareUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share ${video.title}"))
}
