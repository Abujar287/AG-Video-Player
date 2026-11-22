package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.local.PlaybackHistoryEntity
import com.example.data.local.VeloDatabase
import com.example.data.model.VideoFolder
import com.example.data.model.VideoItem
import com.example.data.repository.PlaybackRepository
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

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

data class MainUiState(
    val isLoadingLibrary: Boolean = false,
    val folders: List<VideoFolder> = emptyList(),
    val videosByFolder: Map<Long, List<VideoItem>> = emptyMap(),
    val allVideos: List<VideoItem> = emptyList(),
    val selectedFolderId: Long? = null,
    val searchQuery: String = "",
    val historyByUri: Map<String, PlaybackHistoryEntity> = emptyMap(),
    val recentVideos: List<Pair<VideoItem, PlaybackHistoryEntity>> = emptyList(),
    val preferences: UserPreferences = UserPreferences(),
    val updateState: UpdateUiState = UpdateUiState.Idle,
    val showUpdateSheet: Boolean = false,
    val pendingInstallApk: File? = null
) {
    val selectedFolder: VideoFolder?
        get() = folders.firstOrNull { it.bucketId == selectedFolderId }

    val filteredFolders: List<VideoFolder>
        get() = if (searchQuery.isBlank()) {
            folders
        } else {
            val q = searchQuery.trim().lowercase()
            folders.filter { folder ->
                folder.name.lowercase().contains(q) ||
                    videosByFolder[folder.bucketId].orEmpty().any {
                        it.title.lowercase().contains(q) || it.displayName.lowercase().contains(q)
                    }
            }
        }

    val currentFolderVideos: List<VideoItem>
        get() {
            val raw = selectedFolderId?.let { videosByFolder[it].orEmpty() } ?: emptyList()
            return if (searchQuery.isBlank()) {
                raw
            } else {
                val q = searchQuery.trim().lowercase()
                raw.filter {
                    it.title.lowercase().contains(q) || it.displayName.lowercase().contains(q)
                }
            }
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
    private val _selectedFolderId = MutableStateFlow<Long?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    private val _showUpdateSheet = MutableStateFlow(false)
    private val _pendingInstallApk = MutableStateFlow<File?>(null)

    private var downloadJob: Job? = null

    val uiState: StateFlow<MainUiState> = combine(
        _librarySnapshot,
        _isLoadingLibrary,
        _selectedFolderId,
        _searchQuery,
        playbackRepo.historyByUri,
        prefsRepo.preferences,
        _updateState,
        _showUpdateSheet,
        _pendingInstallApk
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val snapshot = values[0] as VideoLibrarySnapshot
        val loading = values[1] as Boolean
        val selectedFolderId = values[2] as Long?
        val query = values[3] as String
        @Suppress("UNCHECKED_CAST")
        val history = values[4] as Map<String, PlaybackHistoryEntity>
        val prefs = values[5] as UserPreferences
        val updateState = values[6] as UpdateUiState
        val showSheet = values[7] as Boolean
        val pendingApk = values[8] as File?

        val videoByUri = snapshot.allVideos.associateBy { it.uriString }
        val recent = history.values
            .filter { it.positionMs > 1_500L }
            .sortedByDescending { it.lastPlayedAt }
            .mapNotNull { entry -> videoByUri[entry.videoUri]?.let { it to entry } }
            .take(8)

        MainUiState(
            isLoadingLibrary = loading,
            folders = snapshot.folders,
            videosByFolder = snapshot.videosByFolder,
            allVideos = snapshot.allVideos,
            selectedFolderId = selectedFolderId,
            searchQuery = query,
            historyByUri = history,
            recentVideos = recent,
            preferences = prefs,
            updateState = updateState,
            showUpdateSheet = showSheet,
            pendingInstallApk = pendingApk
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
        _selectedFolderId.value = bucketId
        _searchQuery.value = ""
    }

    fun navigateBackToFolders() {
        _selectedFolderId.value = null
        _searchQuery.value = ""
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleDarkMode() {
        viewModelScope.launch {
            val current = prefsRepo.preferences.first().isDarkMode
            prefsRepo.setDarkMode(!current)
        }
    }

    fun setAutoPipEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setAutoPipOnHome(enabled) }
    }

    fun setDefaultSoftwareDecoder(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setDefaultSoftwareDecoder(enabled) }
    }

    fun openUpdateSheet() {
        _showUpdateSheet.value = true
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
