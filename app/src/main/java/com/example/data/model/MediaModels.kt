package com.example.data.model

data class VideoFolder(
    val bucketId: String,
    val name: stringAlias = "",
    val pathHint: String,
    val videoCount: Int,
    val totalSizeBytes: Long,
    val latestModifiedSec: Long,
    val previewVideoId: Long,
    val previewUri: String
)

typealias stringAlias = String

data class VideoItem(
    val id: Long,
    val uriString: String,
    val title: String,
    val displayName: String,
    val bucketId: String,
    val folderName: String,
    val relativePath: String,
    val filePath: String? = null,
    val durationMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val dateModifiedSec: Long,
    val mimeType: String
)

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
    data class Folder(val bucketId: String) : BrowserDestination
    data object Favorites : BrowserDestination
    data class Playlist(val name: String) : BrowserDestination
}

enum class SingleClickAction(val label: String) {
    TOGGLE_CONTROLS("Show / Hide Controls"),
    PLAY_PAUSE("Play / Pause"),
    TOGGLE_MUTE("Mute / Unmute"),
    NONE("Do Nothing")
}

enum class DoubleClickAction(val label: String) {
    PLAY_PAUSE("Play / Pause"),
    SEEK_10S("Seek ±10s"),
    SEEK_20S("Seek ±20s"),
    NONE("Do Nothing")
}

enum class PlayerControlButton(val label: String) {
    ZOOM("Quick Zoom"),
    FIT("Fit / Full"),
    ROTATE("Rotate"),
    SPEED("Speed"),
    LOOP("Loop"),
    MUTE("Mute"),
    SLEEP("Sleep Timer"),
    BG_PLAY("BG Play"),
    TRACKS("Tracks"),
    DECODER("HW / SW"),
    LOCK("Lock"),
    PREV_NEXT("Prev / Next"),
    PIP("PiP")
}

enum class ProgressBarStyle(val label: String) {
    MATERIAL("Material"),
    FLAT("Flat")
}

enum class PlayerAspectMode(val label: String) {
    FIT("Fit"),
    FILL("Stretch"),
    ZOOM("Crop")
}

enum class AppThemePreset(
    val id: String,
    val label: String,
    val darkPrimaryHex: Long,
    val darkContainerHex: Long,
    val lightPrimaryHex: Long,
    val lightContainerHex: Long
) {
    TEAL_MINT("TEAL_MINT", "Teal Mint", 0xFF2DD4BFL, 0xFF0F3D39L, 0xFF0D9488L, 0xFFCCFBF1L),
    EMERALD("EMERALD", "Emerald", 0xFF10B981L, 0xFF064E3BL, 0xFF059669L, 0xFFD1FAE5L),
    AMBER("AMBER", "Amber", 0xFFF59E0BL, 0xFF452A04L, 0xFFD97706L, 0xFFFEF3C7L),
    PURPLE("PURPLE", "Purple", 0xFFA855F7L, 0xFF3B0764L, 0xFF9333EAL, 0xFFF3E8FFL),
    NEON_PINK("NEON_PINK", "Neon Pink", 0xFFEC4899L, 0xFF500724L, 0xFFDB2777L, 0xFFFCE7F3L),
    OCEAN_BLUE("OCEAN_BLUE", "Ocean Blue", 0xFF3B82F6L, 0xFF1E3A8AL, 0xFF2563EBL, 0xFFDBEAFEL);

    companion object {
        fun fromId(id: String): AppThemePreset? = entries.firstOrNull { it.id == id }
    }
}

data class PlaylistEntry(
    val uriString: String,
    val title: String
)

data class MediaTrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val language: String?,
    val isSelected: Boolean
)

data class UserPreferences(
    val isDarkMode: Boolean = true,
    val themeId: String = AppThemePreset.TEAL_MINT.id,
    val isGridView: Boolean = false,
    val showHiddenFiles: Boolean = false,
    val mainDisplayMode: String = "folders",
    val mainViewLayout: String = "list",
    val progressBarStyle: ProgressBarStyle = ProgressBarStyle.MATERIAL,
    val oneFingerGesture: String = "Brightness / Volume Swipe",
    val twoFingerGesture: String = "Zoom / Scale",
    val threeFingerGesture: String = "Play / Pause",
    val autoPipOnHome: Boolean = false,
    val defaultSoftwareDecoder: Boolean = false,
    val githubRepoSlug: String = "",
    val favoriteUris: Set<String> = emptySet(),
    val playlists: Map<String, List<String>> = emptyMap(),
    val playedVideoUris: Set<String> = emptySet(),
    val singleClickAction: SingleClickAction = SingleClickAction.TOGGLE_CONTROLS,
    val doubleClickAction: DoubleClickAction = DoubleClickAction.SEEK_10S,
    val alwaysBackgroundPlay: Boolean = false,
    val enableSwipeGestures: Boolean = true,
    val gestureSeekStepSeconds: Int = 0,
    val hiddenButtons: Set<PlayerControlButton> = emptySet()
) {
    fun isButtonVisible(button: PlayerControlButton): Boolean = button !in hiddenButtons
}

data class GitHubReleaseInfo(
    val versionName: String,
    val versionCode: Int?,
    val releaseTitle: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val apkSizeBytes: Long,
    val htmlUrl: String
)

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Available(val release: GitHubReleaseInfo) : UpdateUiState
    data class Downloading(val downloadId: Long, val percent: Int, val bytesDownloaded: Long, val bytesTotal: Long) : UpdateUiState
    data class ReadyToInstall(val filePath: String, val contentUri: String? = null) : UpdateUiState
    data class UpToDate(val versionName: String) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}
