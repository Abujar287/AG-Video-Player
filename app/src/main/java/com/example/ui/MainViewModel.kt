package com.example.ui

import android.app.Application
import android.content.IntentSender
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.local.PlaybackHistoryEntity
import com.example.data.local.VeloDatabase
import com.example.data.model.VideoFolder
import com.example.data.model.VideoItem
import com.example.data.repository.DoubleClickAction
import com.example.data.repository.MediaFileOpResult
import com.example.data.repository.PendingMediaOp
import com.example.data.repository.PlaybackRepository
import com.example.data.repository.PlayerControlButton
import com.example.data.repository.SingleClickAction
import com.example.data.repository.UserPreferences
import com.example.data.repository.UserPreferencesRepository
import com.example.data.repository.VideoLibraryRepository
import com.example.data.repository.VideoLibrarySnapshot
import com.example.updater.ApkDownloadState
import com.example.updater.GitHubReleaseInfo
import com.example.updater.GitHubUpdateManager
import com.example.updater.UpdateCheckResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class SortField(val label: String) {
    NAME("Name"),
    SIZE("Size"),
    DATE("Date"),
    TYPE("Type"),
    PLAYS("Plays")
}

enum class SortDirection(val label: String) {
    ASC("ASC"),
    DESC("DESC")
}

sealed interface BrowserDestination {
    data object Folders : BrowserDestination
    data class Folder(val bucketId: Long) : BrowserDestination
    data object Favorites : BrowserDestination
    data class Playlist(val name: String) : BrowserDestination
}

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Available(val release: GitHubReleaseInfo) : UpdateUiState
    data class UpToDate(val versionName: String) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
    data class Downloading(
        val release: GitHubReleaseInfo,
        val progressPercent: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long
    ) : UpdateUiState
    data class ReadyToInstall(val release: GitHubReleaseInfo, val apkFile: File) : UpdateUiState
}

data class PendingPermissionRequest(
    val intentSender: IntentSender,
    val pendingOp: PendingMediaOp
)

data class MainUiState(
    val isLoadingLibrary: Boolean = false,
    val folders: List<VideoFolder> = emptyList(),
    val videosByFolder: Map<Long, List<VideoItem>> = emptyMap(),
    val allVideos: List<VideoItem> = emptyList(),
    val destination: BrowserDestination = BrowserDestination.Folders,
    val searchQuery: String = "",
    val sortField: SortField = SortField.NAME,
    val sortDirection: SortDirection = SortDirection.ASC,
    val selectedVideoUris: Set<String> = emptySet(),
    val historyByUri: Map<String, PlaybackHistoryEntity> = emptyMap(),
    val recentVideos: List<Pair<VideoItem, PlaybackHistoryEntity>> = emptyList(),
    val lastHistoryEntry: PlaybackHistoryEntity? = null,
    val preferences: UserPreferences = UserPreferences(),
    val updateState: UpdateUiState = UpdateUiState.Idle,
    val showUpdateSheet: Boolean = false,
    val pendingInstallApk: File? = null,
    val pendingPermissionRequest: PendingPermissionRequest? = null,
    val actionMessage: String? = null
) {
    val isSelectionMode: Boolean
        get() = selectedVideoUris.isNotEmpty()

    val selectedFolder: VideoFolder?
        get() = (destination as? BrowserDestination.Folder)?.let { dest ->
            folders.firstOrNull { it.bucketId == dest.bucketId }
        }

    val filteredFolders: List<VideoFolder>
        get() {
            val baseFolders = if (preferences.showHiddenFiles) {
                folders
            } else {
                folders.filter { !it.name.startsWith(".") }
            }
            val base = if (searchQuery.isBlank()) {
                baseFolders
            } else {
                val q = searchQuery.trim().lowercase()
                baseFolders.filter { folder ->
                    folder.name.lowercase().contains(q) ||
                        videosByFolder[folder.bucketId].orEmpty().any {
                            it.title.lowercase().contains(q) || it.displayName.lowercase().contains(q)
                        }
                }
            }
            val comparator = when (sortField) {
                SortField.NAME -> compareBy<VideoFolder> { it.name.lowercase() }
                SortField.SIZE -> compareBy<VideoFolder> { it.totalSizeBytes }
                SortField.DATE -> compareBy<VideoFolder> { it.latestModifiedSec }
                SortField.TYPE -> compareBy<VideoFolder> { it.videoCount }.thenBy { it.name.lowercase() }
                SortField.PLAYS -> compareBy<VideoFolder> { folder ->
                    videosByFolder[folder.bucketId].orEmpty().maxOfOrNull { historyByUri[it.uriString]?.lastPlayedAt ?: 0L } ?: 0L
                }
            }
            return if (sortDirection == SortDirection.ASC) {
                base.sortedWith(comparator)
            } else {
                base.sortedWith(comparator.reversed())
            }
        }

    val currentVideos: List<VideoItem>
        get() {
            val videoByUri = allVideos.associateBy { it.uriString }
            val rawDest = when (val dest = destination) {
                BrowserDestination.Folders -> emptyList()
                is BrowserDestination.Folder -> videosByFolder[dest.bucketId].orEmpty()
                BrowserDestination.Favorites -> {
                    allVideos.filter { preferences.favoriteUris.contains(it.uriString) }
                }
                is BrowserDestination.Playlist -> {
                    preferences.playlists[dest.name].orEmpty().mapNotNull { videoByUri[it] }
                }
            }
            val raw = if (preferences.showHiddenFiles) {
                rawDest
            } else {
                rawDest.filter { !it.displayName.startsWith(".") && !it.folderName.startsWith(".") }
            }

            val filtered = if (searchQuery.isBlank()) {
                raw
            } else {
                val q = searchQuery.trim().lowercase()
                raw.filter {
                    it.title.lowercase().contains(q) || it.displayName.lowercase().contains(q)
                }
            }

            val comparator = when (sortField) {
                SortField.NAME -> compareBy<VideoItem> { it.title.lowercase() }
                SortField.SIZE -> compareBy<VideoItem> { it.sizeBytes }
                SortField.DATE -> compareBy<VideoItem> { it.dateModifiedSec }
                SortField.TYPE -> compareBy<VideoItem> {
                    it.displayName.substringAfterLast('.', "").lowercase()
                }.thenBy { it.title.lowercase() }
                SortField.PLAYS -> compareBy<VideoItem> { historyByUri[it.uriString]?.lastPlayedAt ?: 0L }
            }
            return if (sortDirection == SortDirection.ASC) {
                filtered.sortedWith(comparator)
            } else {
                filtered.sortedWith(comparator.reversed())
            }
        }

    val favoriteVideosCount: Int
        get() {
            val availableUris = allVideos.map { it.uriString }.toHashSet()
            return preferences.favoriteUris.count { availableUris.contains(it) }
        }

    fun isVideoNew(video: VideoItem): Boolean {
        return !historyByUri.containsKey(video.uriString) &&
            !preferences.playedVideoUris.contains(video.uriString)
    }

    fun unplayedCountForFolder(bucketId: Long): Int {
        return videosByFolder[bucketId].orEmpty().count { isVideoNew(it) }
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val libraryRepo = VideoLibraryRepository(application)
    private val playbackRepo = PlaybackRepository(VeloDatabase.getInstance(application).playbackHistoryDao())
    private val prefsRepo = UserPreferencesRepository(application)
    val updateManager = GitHubUpdateManager(application)

    private val _librarySnapshot = MutableStateFlow(
        VideoLibrarySnapshot(emptyList(), emptyMap(), emptyList())
    )
    private val _isLoadingLibrary = MutableStateFlow(false)
    private val _destination = MutableStateFlow<BrowserDestination>(BrowserDestination.Folders)
    private val _searchQuery = MutableStateFlow("")
    private val _sortField = MutableStateFlow(SortField.NAME)
    private val _sortDirection = MutableStateFlow(SortDirection.ASC)
    private val _selectedVideoUris = MutableStateFlow<Set<String>>(emptySet())
    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    private val _showUpdateSheet = MutableStateFlow(false)
    private val _pendingInstallApk = MutableStateFlow<File?>(null)
    private val _pendingPermissionRequest = MutableStateFlow<PendingPermissionRequest?>(null)
    private val _actionMessage = MutableStateFlow<String?>(null)

    private var downloadJob: Job? = null

    val uiState: StateFlow<MainUiState> = combine(
        _librarySnapshot,
        _isLoadingLibrary,
        _destination,
        _searchQuery,
        _sortField,
        _sortDirection,
        _selectedVideoUris,
        playbackRepo.historyByUri,
        prefsRepo.preferences,
        _updateState,
        _showUpdateSheet,
        _pendingInstallApk,
        _pendingPermissionRequest,
        _actionMessage
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val snapshot = values[0] as VideoLibrarySnapshot
        val loading = values[1] as Boolean
        val destination = values[2] as BrowserDestination
        val query = values[3] as String
        val sortField = values[4] as SortField
        val sortDirection = values[5] as SortDirection
        @Suppress("UNCHECKED_CAST")
        val selectedUris = values[6] as Set<String>
        @Suppress("UNCHECKED_CAST")
        val history = values[7] as Map<String, PlaybackHistoryEntity>
        val prefs = values[8] as UserPreferences
        val updateState = values[9] as UpdateUiState
        val showSheet = values[10] as Boolean
        val pendingApk = values[11] as File?
        val pendingPermission = values[12] as PendingPermissionRequest?
        val actionMsg = values[13] as String?

        val videoByUri = snapshot.allVideos.associateBy { it.uriString }
        val sortedHistory = history.values.sortedByDescending { it.lastPlayedAt }
        val recent = sortedHistory
            .filter { it.positionMs > 1_500L }
            .mapNotNull { entry -> videoByUri[entry.videoUri]?.let { it to entry } }
            .take(8)
        val lastHistory = sortedHistory.firstOrNull { entry ->
            entry.videoUri.isNotBlank() && (videoByUri.containsKey(entry.videoUri) || entry.positionMs > 0L)
        }

        MainUiState(
            isLoadingLibrary = loading,
            folders = snapshot.folders,
            videosByFolder = snapshot.videosByFolder,
            allVideos = snapshot.allVideos,
            destination = destination,
            searchQuery = query,
            sortField = sortField,
            sortDirection = sortDirection,
            selectedVideoUris = selectedUris,
            historyByUri = history,
            recentVideos = recent,
            lastHistoryEntry = lastHistory,
            preferences = prefs,
            updateState = updateState,
            showUpdateSheet = showSheet,
            pendingInstallApk = pendingApk,
            pendingPermissionRequest = pendingPermission,
            actionMessage = actionMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    init {
        viewModelScope.launch {
            val prefs = prefsRepo.preferences.first()
            if (prefs.githubRepoSlug != UserPreferences.DEFAULT_GITHUB_REPO) {
                checkForUpdates(manual = false)
            }
        }
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            _isLoadingLibrary.value = true
            try {
                _librarySnapshot.value = libraryRepo.scanVideos()
            } finally {
                _isLoadingLibrary.value = false
            }
        }
    }

    suspend fun loadThumbnail(videoId: Long, uriString: String): Bitmap? {
        return libraryRepo.loadThumbnail(videoId, uriString)
    }

    fun openFolder(bucketId: Long) {
        _destination.value = BrowserDestination.Folder(bucketId)
        _searchQuery.value = ""
        _selectedVideoUris.value = emptySet()
    }

    fun openFavorites() {
        _destination.value = BrowserDestination.Favorites
        _searchQuery.value = ""
        _selectedVideoUris.value = emptySet()
    }

    fun openPlaylist(name: String) {
        _destination.value = BrowserDestination.Playlist(name)
        _searchQuery.value = ""
        _selectedVideoUris.value = emptySet()
    }

    fun navigateBackToFolders() {
        _destination.value = BrowserDestination.Folders
        _searchQuery.value = ""
        _selectedVideoUris.value = emptySet()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOption(field: SortField, direction: SortDirection) {
        _sortField.value = field
        _sortDirection.value = direction
    }

    fun toggleGridView() {
        viewModelScope.launch {
            val current = prefsRepo.preferences.first().isGridView
            prefsRepo.setGridView(!current)
        }
    }

    fun startVideoSelection(uri: String) {
        _selectedVideoUris.value = _selectedVideoUris.value + uri
    }

    fun toggleVideoSelection(uri: String) {
        val current = _selectedVideoUris.value
        _selectedVideoUris.value = if (current.contains(uri)) {
            current - uri
        } else {
            current + uri
        }
    }

    fun selectAllCurrentVideos() {
        val allUris = uiState.value.currentVideos.map { it.uriString }.toSet()
        _selectedVideoUris.value = allUris
    }

    fun clearSelection() {
        _selectedVideoUris.value = emptySet()
    }

    fun toggleFavorite(video: VideoItem) {
        viewModelScope.launch {
            val added = prefsRepo.toggleFavorite(video.uriString)
            _actionMessage.value = if (added) {
                "Added \"${video.title}\" to Favourites"
            } else {
                "Removed \"${video.title}\" from Favourites"
            }
        }
    }

    fun addVideoToPlaylist(playlistName: String, video: VideoItem) {
        viewModelScope.launch {
            prefsRepo.addVideoToPlaylist(playlistName, video.uriString)
            _actionMessage.value = "Added to playlist \"$playlistName\""
        }
    }

    fun removeVideoFromPlaylist(playlistName: String, video: VideoItem) {
        viewModelScope.launch {
            prefsRepo.removeVideoFromPlaylist(playlistName, video.uriString)
            _actionMessage.value = "Removed from playlist \"$playlistName\""
        }
    }

    fun deletePlaylist(playlistName: String) {
        viewModelScope.launch {
            prefsRepo.deletePlaylist(playlistName)
            if (_destination.value == BrowserDestination.Playlist(playlistName)) {
                _destination.value = BrowserDestination.Folders
            }
            _actionMessage.value = "Deleted playlist \"$playlistName\""
        }
    }

    fun deleteVideos(videos: List<VideoItem>) {
        viewModelScope.launch {
            handleMediaFileOpResult(libraryRepo.deleteVideos(videos))
        }
    }

    fun renameVideo(video: VideoItem, newTitle: String) {
        viewModelScope.launch {
            handleMediaFileOpResult(libraryRepo.renameVideo(video, newTitle))
        }
    }

    fun moveVideos(videos: List<VideoItem>, targetRelativePath: String) {
        viewModelScope.launch {
            handleMediaFileOpResult(libraryRepo.moveVideos(videos, targetRelativePath))
        }
    }

    fun onPermissionRequestResult(granted: Boolean) {
        val pending = _pendingPermissionRequest.value ?: return
        _pendingPermissionRequest.value = null
        if (!granted) {
            _actionMessage.value = "Permission denied"
            return
        }
        viewModelScope.launch {
            val result = when (val op = pending.pendingOp) {
                is PendingMediaOp.DeleteVideos -> {
                    // On Android 11+, createDeleteRequest already deletes upon user approval!
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        MediaFileOpResult.Success(
                            "Deleted ${op.videos.size} video(s)",
                            op.videos.map { it.uriString }
                        )
                    } else {
                        libraryRepo.deleteVideos(op.videos)
                    }
                }
                is PendingMediaOp.RenameVideo -> libraryRepo.renameVideo(op.video, op.newTitle)
                is PendingMediaOp.MoveVideos -> libraryRepo.moveVideos(op.videos, op.targetRelativePath)
            }
            handleMediaFileOpResult(result)
        }
    }

    private suspend fun handleMediaFileOpResult(result: MediaFileOpResult) {
        when (result) {
            is MediaFileOpResult.Success -> {
                _selectedVideoUris.value = emptySet()
                result.affectedUris.forEach { uri ->
                    playbackRepo.clearHistory(uri)
                    prefsRepo.removeFavoriteIfPresent(uri)
                }
                _actionMessage.value = result.message
                _librarySnapshot.value = libraryRepo.scanVideos()
            }
            is MediaFileOpResult.NeedsPermission -> {
                _pendingPermissionRequest.value = PendingPermissionRequest(
                    intentSender = result.intentSender,
                    pendingOp = result.pendingOp
                )
            }
            is MediaFileOpResult.Error -> {
                _actionMessage.value = result.message
            }
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun toggleDarkMode() {
        viewModelScope.launch {
            val current = prefsRepo.preferences.first().isDarkMode
            prefsRepo.setDarkMode(!current)
        }
    }

    fun setShowHiddenFiles(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setShowHiddenFiles(enabled) }
    }

    fun setMainDisplayMode(mode: String) {
        viewModelScope.launch { prefsRepo.setMainDisplayMode(mode) }
    }

    fun setMainViewLayout(layout: String) {
        viewModelScope.launch { prefsRepo.setMainViewLayout(layout) }
    }

    fun setAutoPipEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setAutoPipOnHome(enabled) }
    }

    fun setDefaultSoftwareDecoder(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setDefaultSoftwareDecoder(enabled) }
    }

    fun setSingleClickAction(action: SingleClickAction) {
        viewModelScope.launch { prefsRepo.setSingleClickAction(action) }
    }

    fun setDoubleClickAction(action: DoubleClickAction) {
        viewModelScope.launch { prefsRepo.setDoubleClickAction(action) }
    }

    fun setAlwaysBackgroundPlay(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setAlwaysBackgroundPlay(enabled)
            com.example.player.PlayerSessionManager.setBackgroundPlayEnabled(enabled)
        }
    }

    fun setEnableSwipeGestures(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setEnableSwipeGestures(enabled) }
    }

    fun setGestureSeekStepSeconds(seconds: Int) {
        viewModelScope.launch { prefsRepo.setGestureSeekStepSeconds(seconds) }
    }

    fun setPlayerButtonVisible(button: PlayerControlButton, visible: Boolean) {
        viewModelScope.launch { prefsRepo.setPlayerButtonVisible(button, visible) }
    }

    fun showAllPlayerButtons() {
        viewModelScope.launch { prefsRepo.showAllPlayerButtons() }
    }

    fun openUpdateSheet() {
        checkForUpdates(manual = true)
    }

    fun dismissUpdateSheet() {
        _showUpdateSheet.value = false
    }

    fun saveGithubRepoAndCheck(repoSlug: String) {
        viewModelScope.launch {
            prefsRepo.setGithubRepoSlug(repoSlug)
            checkForUpdates(manual = true, overrideSlug = repoSlug)
        }
    }

    fun checkForUpdates(manual: Boolean, overrideSlug: String? = null) {
        viewModelScope.launch {
            val slug = overrideSlug ?: prefsRepo.preferences.first().githubRepoSlug
            if (manual) {
                _showUpdateSheet.value = true
            }
            _updateState.value = UpdateUiState.Checking
            when (val result = updateManager.checkForUpdate(slug)) {
                is UpdateCheckResult.UpdateAvailable -> {
                    _updateState.value = UpdateUiState.Available(result.release)
                    _showUpdateSheet.value = true
                }
                is UpdateCheckResult.UpToDate -> {
                    _updateState.value = if (manual) {
                        UpdateUiState.UpToDate(result.currentVersion)
                    } else {
                        UpdateUiState.Idle
                    }
                }
                is UpdateCheckResult.Error -> {
                    _updateState.value = if (manual) {
                        UpdateUiState.Error(result.message)
                    } else {
                        UpdateUiState.Idle
                    }
                }
            }
        }
    }

    fun startInAppDownload(release: GitHubReleaseInfo) {
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            updateManager.downloadApk(release).collect { state ->
                when (state) {
                    ApkDownloadState.Idle -> Unit
                    is ApkDownloadState.Downloading -> {
                        _updateState.value = UpdateUiState.Downloading(
                            release = release,
                            progressPercent = state.progressPercent,
                            bytesDownloaded = state.bytesDownloaded,
                            totalBytes = state.totalBytes,
                            speedBytesPerSec = state.speedBytesPerSec
                        )
                    }
                    is ApkDownloadState.Completed -> {
                        _pendingInstallApk.value = state.apkFile
                        _updateState.value = UpdateUiState.ReadyToInstall(release, state.apkFile)
                    }
                    is ApkDownloadState.Failed -> {
                        _updateState.value = UpdateUiState.Error(state.error)
                    }
                }
            }
        }
    }

    fun cancelInAppDownload() {
        downloadJob?.cancel()
        downloadJob = null
        val current = _updateState.value
        if (current is UpdateUiState.Downloading) {
            _updateState.value = UpdateUiState.Available(current.release)
        }
    }

    fun clearPendingInstallApk() {
        _pendingInstallApk.value = null
    }

    fun clearResumePosition(videoUri: String) {
        viewModelScope.launch {
            playbackRepo.clearHistory(videoUri)
        }
    }

    fun currentAppVersionLabel(): String {
        return "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    }
}
