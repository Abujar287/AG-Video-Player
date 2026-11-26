package com.example.ui

import android.app.Application
import android.app.DownloadManager
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.example.BuildConfig
import com.example.data.local.PlaybackHistoryEntity
import com.example.data.model.BrowserDestination
import com.example.data.model.DoubleClickAction
import com.example.data.model.GitHubReleaseInfo
import com.example.data.model.PlayerControlButton
import com.example.data.model.PlaylistEntry
import com.example.data.model.ProgressBarStyle
import com.example.data.model.SingleClickAction
import com.example.data.model.SortDirection
import com.example.data.model.SortField
import com.example.data.model.UpdateUiState
import com.example.data.model.UserPreferences
import com.example.data.model.VideoFolder
import com.example.data.model.VideoItem
import com.example.data.repository.LibrarySnapshot
import com.example.data.repository.MediaMutationOutcome
import com.example.data.repository.PlaybackRepository
import com.example.data.repository.UserPreferencesRepository
import com.example.data.repository.VideoLibraryRepository
import com.example.updater.ApkInstallResult
import com.example.updater.DownloadedApkTarget
import com.example.updater.GitHubUpdateManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.File

data class PendingMoveRequest(
    val videos: List<VideoItem>,
    val targetPath: String
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefsRepo = UserPreferencesRepository(application)
    private val playbackRepo = PlaybackRepository(application)
    private val libraryRepo = VideoLibraryRepository(application)

    private val _isLoadingLibrary = MutableStateFlow(true)
    val isLoadingLibrary: StateFlow<Boolean> = _isLoadingLibrary.asStateFlow()

    private val _snapshot = MutableStateFlow(LibrarySnapshot(emptyList(), emptyMap(), emptyList()))
    val snapshot: StateFlow<LibrarySnapshot> = _snapshot.asStateFlow()

    private val _destination = MutableStateFlow<BrowserDestination>(BrowserDestination.Folders)
    val destination: StateFlow<BrowserDestination> = _destination.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortField = MutableStateFlow(SortField.DATE)
    val sortField: StateFlow<SortField> = _sortField.asStateFlow()

    private val _sortDirection = MutableStateFlow(SortDirection.DESC)
    val sortDirection: StateFlow<SortDirection> = _sortDirection.asStateFlow()

    private val _selectedVideoUris = MutableStateFlow<Set<String>>(emptySet())
    val selectedVideoUris: StateFlow<Set<String>> = _selectedVideoUris.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _pendingIntentSender = MutableStateFlow<IntentSender?>(null)
    val pendingIntentSender: StateFlow<IntentSender?> = _pendingIntentSender.asStateFlow()

    private val _pendingMoveAccessPrompt = MutableStateFlow<PendingMoveRequest?>(null)
    val pendingMoveAccessPrompt: StateFlow<PendingMoveRequest?> = _pendingMoveAccessPrompt.asStateFlow()

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    private var downloadPollJob: Job? = null
    private var activeDownloadManagerId: Long = -1L
    private var lastCheckedRelease: GitHubReleaseInfo? = null
    private var lastDownloadedTarget: DownloadedApkTarget? = null
    private var pendingInstallTarget: DownloadedApkTarget? = null

    val preferences: StateFlow<UserPreferences> = prefsRepo.preferencesFlow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        UserPreferences()
    )

    val historyByUri: StateFlow<Map<String, PlaybackHistoryEntity>> = playbackRepo.observeHistory()
        .map { list -> list.associateBy { it.videoUri } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val lastHistoryEntry: StateFlow<PlaybackHistoryEntity?> = combine(
        playbackRepo.observeHistory(),
        _snapshot
    ) { entries, snap ->
        val available = snap.allVideos.map { it.uriString }.toSet()
        entries.firstOrNull { it.videoUri in available || it.positionMs > 0L }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val filteredFolders: StateFlow<List<VideoFolder>> = combine(
        _snapshot,
        _searchQuery,
        _sortField,
        _sortDirection,
        preferences
    ) { snap, query, field, dir, prefs ->
        val q = query.trim().lowercase()
        val matched = if (q.isBlank()) {
            snap.folders
        } else {
            snap.folders.filter { folder ->
                folder.name.lowercase().contains(q) ||
                    (snap.videosByFolder[folder.bucketId]?.any {
                        it.title.lowercase().contains(q) || it.displayName.lowercase().contains(q)
                    } == true)
            }
        }
        val unplayedCounts = snap.videosByFolder.mapValues { (_, list) ->
            list.count { it.uriString !in prefs.playedVideoUris }
        }
        val cmp = when (field) {
            SortField.NAME -> compareBy<VideoFolder> { it.name.lowercase() }
            SortField.SIZE -> compareBy<VideoFolder> { it.totalSizeBytes }
            SortField.DATE -> compareBy<VideoFolder> { it.latestModifiedSec }
            SortField.TYPE -> compareBy<VideoFolder> { it.pathHint.lowercase() }
            SortField.PLAYS -> compareBy<VideoFolder> { unplayedCounts[it.bucketId] ?: 0 }
        }
        val sorted = matched.sortedWith(cmp)
        if (dir == SortDirection.DESC) sorted.reversed() else sorted
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currentVideos: StateFlow<List<VideoItem>> = combine(
        _snapshot,
        _destination,
        _searchQuery,
        _sortField,
        _sortDirection,
        preferences
    ) { arr ->
        val snap = arr[0] as LibrarySnapshot
        val dest = arr[1] as BrowserDestination
        val query = arr[2] as String
        val field = arr[3] as SortField
        val dir = arr[4] as SortDirection
        val prefs = arr[5] as UserPreferences

        val videoByUri = snap.allVideos.associateBy { it.uriString }
        val raw = when (dest) {
            BrowserDestination.Folders -> emptyList()
            is BrowserDestination.Folder -> snap.videosByFolder[dest.bucketId].orEmpty()
            BrowserDestination.Favorites -> snap.allVideos.filter { it.uriString in prefs.favoriteUris }
            is BrowserDestination.Playlist -> prefs.playlists[dest.name].orEmpty().mapNotNull { videoByUri[it] }
        }
        filterAndSortVideos(raw, query, field, dir, prefs)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val sortedAllVideos: StateFlow<List<VideoItem>> = combine(
        _snapshot,
        _searchQuery,
        _sortField,
        _sortDirection,
        preferences
    ) { snap, query, field, dir, prefs ->
        filterAndSortVideos(snap.allVideos, query, field, dir, prefs)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            preferences.map { it.showHiddenFiles }.distinctUntilChanged().collect {
                refreshLibrary()
            }
        }
        viewModelScope.launch {
            val app = getApplication<Application>()
            val slug = preferences.value.githubRepoSlug.ifBlank { BuildConfig.GITHUB_REPO_SLUG }
            val result = GitHubUpdateManager.checkForUpdate(
                repoSlug = slug,
                currentVersionName = GitHubUpdateManager.getInstalledVersionName(app),
                currentVersionCode = GitHubUpdateManager.getInstalledVersionCode(app)
            )
            if (result is UpdateUiState.Available) {
                lastCheckedRelease = result.release
                _updateState.value = result
            }
        }
    }

    fun currentAppVersionLabel(): String {
        val versionName = GitHubUpdateManager.getInstalledVersionName(getApplication())
        return "v$versionName"
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            _isLoadingLibrary.value = true
            _snapshot.value = libraryRepo.loadLibrary(preferences.value.showHiddenFiles)
            _isLoadingLibrary.value = false
        }
    }

    fun openFolder(bucketId: String) {
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

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun setSortOption(field: SortField, dir: SortDirection) {
        _sortField.value = field
        _sortDirection.value = dir
    }

    fun startSelection(uri: String) {
        _selectedVideoUris.value = _selectedVideoUris.value + uri
    }

    fun toggleSelection(uri: String) {
        val current = _selectedVideoUris.value
        _selectedVideoUris.value = if (uri in current) current - uri else current + uri
    }

    fun selectAllVisible() {
        val list = if (_destination.value == BrowserDestination.Folders && (preferences.value.mainDisplayMode == "files" || _searchQuery.value.isNotBlank())) {
            sortedAllVideos.value
        } else {
            currentVideos.value
        }
        _selectedVideoUris.value = list.map { it.uriString }.toSet()
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

    fun addToPlaylist(name: String, video: VideoItem) {
        viewModelScope.launch {
            prefsRepo.addToPlaylist(name, video.uriString)
            _actionMessage.value = "Added to playlist \"${name.trim()}\""
        }
    }

    fun removeFromPlaylist(name: String, video: VideoItem) {
        viewModelScope.launch {
            prefsRepo.removeFromPlaylist(name, video.uriString)
            _actionMessage.value = "Removed from playlist \"$name\""
        }
    }

    fun deletePlaylist(name: String) {
        viewModelScope.launch {
            prefsRepo.deletePlaylist(name)
            if ((_destination.value as? BrowserDestination.Playlist)?.name == name) {
                _destination.value = BrowserDestination.Folders
            }
            _actionMessage.value = "Deleted playlist \"$name\""
        }
    }

    fun renameVideo(video: VideoItem, newTitle: String) {
        viewModelScope.launch {
            handleMutationOutcome(libraryRepo.renameVideo(video, newTitle))
        }
    }

    private var pendingMoveForRetry: PendingMoveRequest? = null
    private var pendingDeleteForRetry: List<VideoItem>? = null

    fun moveVideos(videos: List<VideoItem>, targetPath: String) {
        viewModelScope.launch {
            pendingMoveForRetry = PendingMoveRequest(videos, targetPath)
            val outcome = libraryRepo.moveVideos(videos, targetPath)
            if (outcome is MediaMutationOutcome.NeedsAllFilesAccess) {
                _pendingMoveAccessPrompt.value = PendingMoveRequest(videos, outcome.targetFolder)
            } else {
                handleMutationOutcome(outcome)
            }
        }
    }

    fun dismissMoveAccessPrompt() {
        _pendingMoveAccessPrompt.value = null
        pendingMoveForRetry = null
        pendingDeleteForRetry = null
    }

    fun onAllFilesAccessResult() {
        val pendingDelete = pendingDeleteForRetry
        if (pendingDelete != null) {
            pendingDeleteForRetry = null
            _pendingMoveAccessPrompt.value = null
            deleteVideos(pendingDelete)
            return
        }
        val pending = _pendingMoveAccessPrompt.value ?: pendingMoveForRetry
        if (pending != null) {
            _pendingMoveAccessPrompt.value = null
            pendingMoveForRetry = null
            val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
            if (granted) {
                moveVideos(pending.videos, pending.targetPath)
            } else {
                _actionMessage.value = "File access not granted. Try moving under Movies/ or grant All Files Access."
            }
            return
        }
        refreshLibrary()
    }

    fun retryMoveAfterAllFilesAccessResult() {
        onAllFilesAccessResult()
    }

    fun moveToScopedMoviesSubfolder(pending: PendingMoveRequest) {
        _pendingMoveAccessPrompt.value = null
        val folderLeaf = pending.targetPath.trim('/').substringAfterLast('/').ifBlank { "Moved" }
        moveVideos(pending.videos, "Movies/$folderLeaf")
    }

    fun deleteVideos(videos: List<VideoItem>) {
        _selectedVideoUris.value = emptySet()
        pendingDeleteForRetry = videos
        viewModelScope.launch {
            val outcome = libraryRepo.deleteVideos(videos)
            if (outcome is MediaMutationOutcome.Success) {
                pendingDeleteForRetry = null
                for (v in videos) {
                    playbackRepo.clearPosition(v.uriString)
                    prefsRepo.removeUriFromCollections(v.uriString)
                }
            }
            _selectedVideoUris.value = emptySet()
            handleMutationOutcome(outcome)
        }
    }

    fun clearResumePosition(videoUri: String) {
        viewModelScope.launch {
            playbackRepo.clearPosition(videoUri)
            _actionMessage.value = "Resume position cleared"
        }
    }

    fun onPermissionSenderHandled(granted: Boolean) {
        _pendingIntentSender.value = null
        if (granted) {
            val pending = pendingMoveForRetry
            if (pending != null) {
                pendingMoveForRetry = null
                moveVideos(pending.videos, pending.targetPath)
                return
            }
            refreshLibrary()
            _actionMessage.value = "Permission granted"
        } else {
            pendingMoveForRetry = null
            _actionMessage.value = "Storage permission denied"
        }
    }

    fun consumeIntentSender() {
        _pendingIntentSender.value = null
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun markVideoPlayed(uri: String) {
        viewModelScope.launch { prefsRepo.markVideoPlayed(uri) }
    }

    fun buildPlaylistFor(video: VideoItem, list: List<VideoItem>): Pair<List<PlaylistEntry>, Int> {
        val entries = list.map { PlaylistEntry(it.uriString, it.title) }
        val idx = list.indexOfFirst { it.uriString == video.uriString }.coerceAtLeast(0)
        return entries to idx
    }

    fun getLastPlayedVideoAndPlaylist(): Pair<VideoItem, List<VideoItem>>? {
        val snap = _snapshot.value
        val last = lastHistoryEntry.value
        val target = if (last != null) {
            snap.allVideos.firstOrNull { it.uriString == last.videoUri } ?: snap.allVideos.firstOrNull()
        } else {
            snap.allVideos.firstOrNull()
        } ?: return null
        val folderList = snap.videosByFolder[target.bucketId].orEmpty().ifEmpty { listOf(target) }
        return target to folderList
    }

    // Settings updates
    fun toggleDarkMode() = viewModelScope.launch { prefsRepo.setDarkMode(!preferences.value.isDarkMode) }
    fun toggleGridView() = viewModelScope.launch { prefsRepo.setGridView(!preferences.value.isGridView) }
    fun setShowHiddenFiles(v: Boolean) = viewModelScope.launch { prefsRepo.setShowHiddenFiles(v) }
    fun setMainDisplayMode(m: String) = viewModelScope.launch { prefsRepo.setMainDisplayMode(m) }
    fun setThemeId(themeId: String) = viewModelScope.launch { prefsRepo.setThemeId(themeId) }
    fun setMainViewLayout(l: String) = viewModelScope.launch { prefsRepo.setMainViewLayout(l) }
    fun setOneFingerGesture(g: String) = viewModelScope.launch { prefsRepo.setOneFingerGesture(g) }
    fun setTwoFingerGesture(g: String) = viewModelScope.launch { prefsRepo.setTwoFingerGesture(g) }
    fun setThreeFingerGesture(g: String) = viewModelScope.launch { prefsRepo.setThreeFingerGesture(g) }
    fun setAutoPipOnHome(v: Boolean) = viewModelScope.launch { prefsRepo.setAutoPipOnHome(v) }
    fun setDefaultSoftwareDecoder(v: Boolean) = viewModelScope.launch { prefsRepo.setDefaultSoftwareDecoder(v) }
    fun setSingleClickAction(a: SingleClickAction) = viewModelScope.launch { prefsRepo.setSingleClickAction(a) }
    fun setDoubleClickAction(a: DoubleClickAction) = viewModelScope.launch { prefsRepo.setDoubleClickAction(a) }
    fun setAlwaysBackgroundPlay(v: Boolean) = viewModelScope.launch { prefsRepo.setAlwaysBackgroundPlay(v) }
    fun setEnableSwipeGestures(v: Boolean) = viewModelScope.launch { prefsRepo.setEnableSwipeGestures(v) }
    fun setGestureSeekStepSeconds(s: Int) = viewModelScope.launch { prefsRepo.setGestureSeekStepSeconds(s) }
    fun setPlayerButtonVisible(b: PlayerControlButton, v: Boolean) = viewModelScope.launch {
        prefsRepo.setPlayerButtonVisible(b, v)
    }
    fun showAllPlayerButtons() = viewModelScope.launch { prefsRepo.showAllPlayerButtons() }
    fun setProgressBarStyle(style: ProgressBarStyle) = viewModelScope.launch { prefsRepo.setProgressBarStyle(style) }

    // GitHub Updates
    fun openUpdateCheck() {
        _showUpdateDialog.value = true
        if (_updateState.value is UpdateUiState.Downloading) {
            return
        }
        viewModelScope.launch {
            val app = getApplication<Application>()
            _updateState.value = UpdateUiState.Checking
            val slug = preferences.value.githubRepoSlug.ifBlank { BuildConfig.GITHUB_REPO_SLUG }
            val result = GitHubUpdateManager.checkForUpdate(
                repoSlug = slug,
                currentVersionName = GitHubUpdateManager.getInstalledVersionName(app),
                currentVersionCode = GitHubUpdateManager.getInstalledVersionCode(app)
            )
            if (result is UpdateUiState.Available) {
                lastCheckedRelease = result.release
            }
            _updateState.value = result
        }
    }

    fun startUpdateDownload(release: GitHubReleaseInfo) {
        val context = getApplication<Application>()
        lastCheckedRelease = release
        lastDownloadedTarget = null
        pendingInstallTarget = null
        downloadPollJob?.cancel()
        cancelActiveSystemDownload(context)

        val initialName = GitHubUpdateManager.buildUpdateFileName(release)
        _updateState.value = UpdateUiState.Downloading(
            downloadId = 0L,
            percent = 0,
            bytesDownloaded = 0L,
            bytesTotal = release.apkSizeBytes,
            fileName = initialName,
            usingDownloadManager = true
        )

        downloadPollJob = viewModelScope.launch {
            val (downloadId, publicFileName) = GitHubUpdateManager.startApkDownloadAsync(context, release)
            activeDownloadManagerId = downloadId

            if (downloadId <= 0L) {
                runDirectDownloadFallback(context, release)
                return@launch
            }

            _updateState.value = UpdateUiState.Downloading(
                downloadId = downloadId,
                percent = 0,
                bytesDownloaded = 0L,
                bytesTotal = release.apkSizeBytes,
                fileName = publicFileName,
                usingDownloadManager = true
            )

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (dm == null) {
                runDirectDownloadFallback(context, release)
                return@launch
            }

            var emptyPolls = 0
            var lastProgressBytes = 0L
            var lastActivityMs = System.currentTimeMillis()
            while (true) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = runCatching { dm.query(query) }.getOrNull()
                if (cursor != null && cursor.moveToFirst()) {
                    emptyPolls = 0
                    val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val downloadedCol = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val localUriCol = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)

                    val status = if (statusCol >= 0) cursor.getInt(statusCol) else DownloadManager.STATUS_FAILED
                    val bytesDownloaded = if (downloadedCol >= 0) cursor.getLong(downloadedCol).coerceAtLeast(0L) else 0L
                    val bytesTotal = if (totalCol >= 0) cursor.getLong(totalCol).coerceAtLeast(0L) else 0L
                    val localUri = if (localUriCol >= 0) cursor.getString(localUriCol) else null

                    cursor.close()

                    if (bytesDownloaded > lastProgressBytes || status == DownloadManager.STATUS_RUNNING) {
                        lastProgressBytes = bytesDownloaded
                        lastActivityMs = System.currentTimeMillis()
                    }

                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            activeDownloadManagerId = -1L
                            val resolvedTarget = resolveDownloadedApk(context, dm, downloadId, localUri, publicFileName, release)
                            if (resolvedTarget != null && GitHubUpdateManager.isValidApkTarget(context, resolvedTarget)) {
                                GitHubUpdateManager.scanDownloadedFile(context, resolvedTarget.file)
                                lastDownloadedTarget = resolvedTarget
                                val savedName = resolvedTarget.file?.name ?: publicFileName
                                _actionMessage.value = "Downloaded to Download/$savedName"
                                _updateState.value = UpdateUiState.ReadyToInstall(
                                    filePath = resolvedTarget.file?.absolutePath.orEmpty(),
                                    contentUri = resolvedTarget.contentUri?.toString()
                                )
                                installUpdateTarget(resolvedTarget)
                            } else {
                                // Retry once via direct streaming downloader to 0/Download
                                runDirectDownloadFallback(context, release)
                            }
                            break
                        }
                        DownloadManager.STATUS_FAILED -> {
                            cancelActiveSystemDownload(context)
                            runDirectDownloadFallback(context, release)
                            break
                        }
                        DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING, DownloadManager.STATUS_PAUSED -> {
                            val effectiveTotal = if (bytesTotal > 0L) bytesTotal else release.apkSizeBytes
                            val percent = if (effectiveTotal > 0L) {
                                ((bytesDownloaded * 100L) / effectiveTotal).toInt().coerceIn(0, 100)
                            } else 0
                            _updateState.value = UpdateUiState.Downloading(
                                downloadId = downloadId,
                                percent = percent,
                                bytesDownloaded = bytesDownloaded,
                                bytesTotal = effectiveTotal,
                                fileName = publicFileName,
                                usingDownloadManager = true
                            )

                            // Only fall back if system DownloadManager stays stuck at 0 bytes PENDING for >45s
                            if (status == DownloadManager.STATUS_PENDING &&
                                bytesDownloaded == 0L &&
                                System.currentTimeMillis() - lastActivityMs > 45_000L
                            ) {
                                cancelActiveSystemDownload(context)
                                runDirectDownloadFallback(context, release)
                                break
                            }
                        }
                    }
                } else {
                    cursor?.close()
                    emptyPolls++
                    if (emptyPolls >= 8) {
                        cancelActiveSystemDownload(context)
                        runDirectDownloadFallback(context, release)
                        break
                    }
                }
                delay(300L)
            }
        }
    }

    private suspend fun runDirectDownloadFallback(context: Context, release: GitHubReleaseInfo) {
        activeDownloadManagerId = -1L
        val fileName = GitHubUpdateManager.buildUpdateFileName(release)
        val target = GitHubUpdateManager.downloadApkDirect(context, release) { downloaded, total, percent ->
            val effectiveTotal = if (total > 0L) total else release.apkSizeBytes
            _updateState.value = UpdateUiState.Downloading(
                downloadId = 0L,
                percent = percent,
                bytesDownloaded = downloaded,
                bytesTotal = effectiveTotal,
                fileName = fileName,
                usingDownloadManager = false
            )
        }
        if (target != null && GitHubUpdateManager.isValidApkTarget(context, target)) {
            GitHubUpdateManager.scanDownloadedFile(context, target.file)
            lastDownloadedTarget = target
            val savedName = target.file?.name ?: fileName
            _actionMessage.value = "Downloaded to Download/$savedName"
            _updateState.value = UpdateUiState.ReadyToInstall(
                filePath = target.file?.absolutePath.orEmpty(),
                contentUri = target.contentUri?.toString()
            )
            installUpdateTarget(target)
        } else {
            _updateState.value = UpdateUiState.Error("Download failed. Please check connection and try again.")
        }
    }

    private fun resolveDownloadedApk(
        context: Context,
        dm: DownloadManager,
        downloadId: Long,
        localUri: String?,
        publicFileName: String,
        release: GitHubReleaseInfo
    ): DownloadedApkTarget? {
        val dmUri = runCatching { dm.getUriForDownloadedFile(downloadId) }.getOrNull()
        val parsedLocal = localUri?.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val mediaStoreMatch = GitHubUpdateManager.queryPublicDownloadByName(context, publicFileName)

        val localFile = when {
            parsedLocal != null && (parsedLocal.scheme == "file" || parsedLocal.scheme.isNullOrBlank()) ->
                parsedLocal.path?.let(::File)?.takeIf { it.exists() }
            publicFileName.isNotBlank() ->
                File(GitHubUpdateManager.getPublicDownloadsDir(), publicFileName).takeIf { it.exists() }
            else -> null
        } ?: mediaStoreMatch?.file ?: GitHubUpdateManager.getExpectedApkFile(release).takeIf { it.exists() }

        val contentUri = when {
            dmUri != null && dmUri.scheme == "content" -> dmUri
            parsedLocal != null && parsedLocal.scheme == "content" -> parsedLocal
            mediaStoreMatch?.contentUri != null -> mediaStoreMatch.contentUri
            else -> null
        }

        if (localFile == null && contentUri == null) return null
        return DownloadedApkTarget(file = localFile, contentUri = contentUri)
    }

    private fun cancelActiveSystemDownload(context: Context) {
        val id = activeDownloadManagerId
        activeDownloadManagerId = -1L
        if (id > 0L) {
            runCatching {
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                dm?.remove(id)
            }
        }
    }

    fun cancelUpdateDownload() {
        val context = getApplication<Application>()
        downloadPollJob?.cancel()
        downloadPollJob = null
        cancelActiveSystemDownload(context)
        lastDownloadedTarget = null
        pendingInstallTarget = null
        _showUpdateDialog.value = false
        _updateState.value = lastCheckedRelease?.let { UpdateUiState.Available(it) } ?: UpdateUiState.Idle
    }

    fun openExternalDownloader(release: GitHubReleaseInfo) {
        val context = getApplication<Application>()
        if (!GitHubUpdateManager.openInExternalDownloader(context, release)) {
            _actionMessage.value = "External downloader not found."
        }
    }

    fun installUpdate(targetIdentifier: String) {
        val context = getApplication<Application>()
        val cached = lastDownloadedTarget
        val candidate = when {
            cached != null && GitHubUpdateManager.isValidApkTarget(context, cached) -> cached
            targetIdentifier.startsWith("content://") ->
                DownloadedApkTarget(file = null, contentUri = Uri.parse(targetIdentifier))
            targetIdentifier.isNotBlank() ->
                DownloadedApkTarget(file = File(targetIdentifier), contentUri = null)
            else -> lastCheckedRelease?.let {
                DownloadedApkTarget(file = GitHubUpdateManager.getExpectedApkFile(it), contentUri = null)
            }
        }

        if (candidate == null || !GitHubUpdateManager.isValidApkTarget(context, candidate)) {
            _updateState.value = UpdateUiState.Error("Downloaded APK file is invalid or missing. Please download again.")
            return
        }
        installUpdateTarget(candidate)
    }

    private fun installUpdateTarget(target: DownloadedApkTarget) {
        val context = getApplication<Application>()
        when (GitHubUpdateManager.installDownloadedApk(context, target)) {
            ApkInstallResult.LAUNCHED -> {
                pendingInstallTarget = null
            }
            ApkInstallResult.PERMISSION_NEEDED -> {
                pendingInstallTarget = target
                _actionMessage.value = "Allow 'Install unknown apps' to continue update."
            }
            ApkInstallResult.FAILED -> {
                pendingInstallTarget = null
                _updateState.value = UpdateUiState.Error("Could not launch installer for the downloaded APK.")
            }
        }
    }

    fun retryPendingInstallIfAllowed() {
        val target = pendingInstallTarget ?: return
        val context = getApplication<Application>()
        if (GitHubUpdateManager.canInstallPackages(context)) {
            pendingInstallTarget = null
            installUpdateTarget(target)
        }
    }

    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
    }

    private fun handleMutationOutcome(outcome: MediaMutationOutcome) {
        when (outcome) {
            is MediaMutationOutcome.Success -> {
                pendingMoveForRetry = null
                _selectedVideoUris.value = emptySet()
                _actionMessage.value = outcome.message
                refreshLibrary()
            }
            is MediaMutationOutcome.NeedsPermission -> {
                _pendingIntentSender.value = outcome.intentSender
            }
            is MediaMutationOutcome.NeedsAllFilesAccess -> {
                val pending = pendingMoveForRetry
                if (pending != null) {
                    _pendingMoveAccessPrompt.value = pending
                } else if (pendingDeleteForRetry != null) {
                    _pendingMoveAccessPrompt.value = PendingMoveRequest(pendingDeleteForRetry.orEmpty(), "delete")
                } else {
                    _actionMessage.value = "Storage permission required."
                }
            }
            is MediaMutationOutcome.Failure -> {
                pendingMoveForRetry = null
                _actionMessage.value = outcome.message
            }
        }
    }

    private fun filterAndSortVideos(
        raw: List<VideoItem>,
        query: String,
        field: SortField,
        dir: SortDirection,
        prefs: UserPreferences
    ): List<VideoItem> {
        val q = query.trim().lowercase()
        val matched = if (q.isBlank()) {
            raw
        } else {
            raw.filter { it.title.lowercase().contains(q) || it.displayName.lowercase().contains(q) }
        }
        val cmp = when (field) {
            SortField.NAME -> compareBy<VideoItem> { it.title.lowercase() }
            SortField.SIZE -> compareBy<VideoItem> { it.sizeBytes }
            SortField.DATE -> compareBy<VideoItem> { it.dateModifiedSec }
            SortField.TYPE -> compareBy<VideoItem> { it.displayName.substringAfterLast('.', "").lowercase() }
            SortField.PLAYS -> compareBy<VideoItem> { if (it.uriString in prefs.playedVideoUris) 1 else 0 }
        }
        val sorted = matched.sortedWith(cmp)
        return if (dir == SortDirection.DESC) sorted.reversed() else sorted
    }
}
