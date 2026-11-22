package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "veloplay_prefs")

enum class SingleClickAction(val label: String, val subtitle: String) {
    TOGGLE_CONTROLS("Show / Hide Controls", "Tap once to toggle player overlay"),
    PLAY_PAUSE("Play / Pause", "Tap once to toggle playback"),
    TOGGLE_MUTE("Mute / Unmute", "Tap once to mute or unmute audio"),
    NONE("Do Nothing", "Ignore single taps while playing");

    companion object {
        fun fromStored(name: String?): SingleClickAction {
            return entries.firstOrNull { it.name == name } ?: TOGGLE_CONTROLS
        }
    }
}

enum class DoubleClickAction(val label: String, val subtitle: String) {
    PLAY_PAUSE("Play / Pause", "Double tap screen to play or pause"),
    SEEK_10S("Seek ±10s", "Double tap left/right side to skip 10s"),
    SEEK_20S("Seek ±20s", "Double tap left/right side to skip 20s"),
    NONE("Do Nothing", "Disable double tap action");

    companion object {
        fun fromStored(name: String?): DoubleClickAction {
            return entries.firstOrNull { it.name == name } ?: PLAY_PAUSE
        }
    }
}

enum class PlayerControlButton(val label: String) {
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

data class PlayerControlsVisibility(
    val showFitButton: Boolean = true,
    val showRotateButton: Boolean = true,
    val showSpeedButton: Boolean = true,
    val showLoopButton: Boolean = true,
    val showMuteButton: Boolean = true,
    val showSleepButton: Boolean = true,
    val showBgPlayButton: Boolean = true,
    val showTracksButton: Boolean = true,
    val showDecoderButton: Boolean = true,
    val showLockButton: Boolean = true,
    val showPrevNextButtons: Boolean = true,
    val showPipButton: Boolean = true
) {
    fun isVisible(button: PlayerControlButton): Boolean = when (button) {
        PlayerControlButton.FIT -> showFitButton
        PlayerControlButton.ROTATE -> showRotateButton
        PlayerControlButton.SPEED -> showSpeedButton
        PlayerControlButton.LOOP -> showLoopButton
        PlayerControlButton.MUTE -> showMuteButton
        PlayerControlButton.SLEEP -> showSleepButton
        PlayerControlButton.BG_PLAY -> showBgPlayButton
        PlayerControlButton.TRACKS -> showTracksButton
        PlayerControlButton.DECODER -> showDecoderButton
        PlayerControlButton.LOCK -> showLockButton
        PlayerControlButton.PREV_NEXT -> showPrevNextButtons
        PlayerControlButton.PIP -> showPipButton
    }

    fun hiddenSet(): Set<String> = buildSet {
        PlayerControlButton.entries.forEach { btn ->
            if (!isVisible(btn)) add(btn.name)
        }
    }

    companion object {
        fun fromHiddenSet(hidden: Set<String>): PlayerControlsVisibility {
            return PlayerControlsVisibility(
                showFitButton = !hidden.contains(PlayerControlButton.FIT.name),
                showRotateButton = !hidden.contains(PlayerControlButton.ROTATE.name),
                showSpeedButton = !hidden.contains(PlayerControlButton.SPEED.name),
                showLoopButton = !hidden.contains(PlayerControlButton.LOOP.name),
                showMuteButton = !hidden.contains(PlayerControlButton.MUTE.name),
                showSleepButton = !hidden.contains(PlayerControlButton.SLEEP.name),
                showBgPlayButton = !hidden.contains(PlayerControlButton.BG_PLAY.name),
                showTracksButton = !hidden.contains(PlayerControlButton.TRACKS.name),
                showDecoderButton = !hidden.contains(PlayerControlButton.DECODER.name),
                showLockButton = !hidden.contains(PlayerControlButton.LOCK.name),
                showPrevNextButtons = !hidden.contains(PlayerControlButton.PREV_NEXT.name),
                showPipButton = !hidden.contains(PlayerControlButton.PIP.name)
            )
        }
    }
}

data class UserPreferences(
    val isDarkMode: Boolean = true,
    val isGridView: Boolean = false,
    val autoPipOnHome: Boolean = true,
    val defaultSoftwareDecoder: Boolean = false,
    val githubRepoSlug: String = DEFAULT_GITHUB_REPO,
    val favoriteUris: Set<String> = emptySet(),
    val playlists: Map<String, List<String>> = emptyMap(),
    val playedVideoUris: Set<String> = emptySet(),
    val singleClickAction: SingleClickAction = SingleClickAction.TOGGLE_CONTROLS,
    val doubleClickAction: DoubleClickAction = DoubleClickAction.PLAY_PAUSE,
    val alwaysBackgroundPlay: Boolean = false,
    val enableSwipeGestures: Boolean = true,
    val gestureSeekStepSeconds: Int = 20,
    val controlsVisibility: PlayerControlsVisibility = PlayerControlsVisibility()
) {
    companion object {
        val DEFAULT_GITHUB_REPO: String
            get() = BuildConfig.GITHUB_REPO_SLUG
    }
}

class UserPreferencesRepository(private val context: Context) {
    private object Keys {
        val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        val IS_GRID_VIEW = booleanPreferencesKey("is_grid_view")
        val AUTO_PIP_ON_HOME = booleanPreferencesKey("auto_pip_on_home")
        val DEFAULT_SW_DECODER = booleanPreferencesKey("default_sw_decoder")
        val GITHUB_REPO_SLUG = stringPreferencesKey("github_repo_slug")
        val FAVORITE_URIS = stringSetPreferencesKey("favorite_uris")
        val PLAYLISTS_JSON = stringPreferencesKey("playlists_json")
        val PLAYED_VIDEO_URIS = stringSetPreferencesKey("played_video_uris")
        val SINGLE_CLICK_ACTION = stringPreferencesKey("single_click_action")
        val DOUBLE_CLICK_ACTION = stringPreferencesKey("double_click_action")
        val ALWAYS_BACKGROUND_PLAY = booleanPreferencesKey("always_background_play")
        val ENABLE_SWIPE_GESTURES = booleanPreferencesKey("enable_swipe_gestures")
        val GESTURE_SEEK_STEP_SECONDS = intPreferencesKey("gesture_seek_step_seconds")
        val HIDDEN_PLAYER_BUTTONS = stringSetPreferencesKey("hidden_player_buttons")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val savedSlug = prefs[Keys.GITHUB_REPO_SLUG]
            ?.takeIf { it.isNotBlank() && it != "owner/ag-video-player" }
        UserPreferences(
            isDarkMode = prefs[Keys.IS_DARK_MODE] ?: true,
            isGridView = prefs[Keys.IS_GRID_VIEW] ?: false,
            autoPipOnHome = prefs[Keys.AUTO_PIP_ON_HOME] ?: true,
            defaultSoftwareDecoder = prefs[Keys.DEFAULT_SW_DECODER] ?: false,
            githubRepoSlug = savedSlug ?: UserPreferences.DEFAULT_GITHUB_REPO,
            favoriteUris = prefs[Keys.FAVORITE_URIS] ?: emptySet(),
            playlists = parsePlaylists(prefs[Keys.PLAYLISTS_JSON]),
            playedVideoUris = prefs[Keys.PLAYED_VIDEO_URIS] ?: emptySet(),
            singleClickAction = SingleClickAction.fromStored(prefs[Keys.SINGLE_CLICK_ACTION]),
            doubleClickAction = DoubleClickAction.fromStored(prefs[Keys.DOUBLE_CLICK_ACTION]),
            alwaysBackgroundPlay = prefs[Keys.ALWAYS_BACKGROUND_PLAY] ?: false,
            enableSwipeGestures = prefs[Keys.ENABLE_SWIPE_GESTURES] ?: true,
            gestureSeekStepSeconds = (prefs[Keys.GESTURE_SEEK_STEP_SECONDS] ?: 20).let {
                if (it == 10 || it == 20) it else 20
            },
            controlsVisibility = PlayerControlsVisibility.fromHiddenSet(
                prefs[Keys.HIDDEN_PLAYER_BUTTONS] ?: emptySet()
            )
        )
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.IS_DARK_MODE] = enabled }
    }

    suspend fun setGridView(enabled: Boolean) {
        context.dataStore.edit { it[Keys.IS_GRID_VIEW] = enabled }
    }

    suspend fun setAutoPipOnHome(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_PIP_ON_HOME] = enabled }
    }

    suspend fun setDefaultSoftwareDecoder(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DEFAULT_SW_DECODER] = enabled }
    }

    suspend fun setSingleClickAction(action: SingleClickAction) {
        context.dataStore.edit { it[Keys.SINGLE_CLICK_ACTION] = action.name }
    }

    suspend fun setDoubleClickAction(action: DoubleClickAction) {
        context.dataStore.edit { it[Keys.DOUBLE_CLICK_ACTION] = action.name }
    }

    suspend fun setAlwaysBackgroundPlay(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ALWAYS_BACKGROUND_PLAY] = enabled }
    }

    suspend fun setEnableSwipeGestures(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ENABLE_SWIPE_GESTURES] = enabled }
    }

    suspend fun setGestureSeekStepSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.GESTURE_SEEK_STEP_SECONDS] = seconds.coerceIn(5, 60) }
    }

    suspend fun setPlayerButtonVisible(button: PlayerControlButton, visible: Boolean) {
        context.dataStore.edit { prefs ->
            val hidden = prefs[Keys.HIDDEN_PLAYER_BUTTONS]?.toMutableSet() ?: mutableSetOf()
            if (visible) {
                hidden.remove(button.name)
            } else {
                hidden.add(button.name)
            }
            prefs[Keys.HIDDEN_PLAYER_BUTTONS] = hidden
        }
    }

    suspend fun showAllPlayerButtons() {
        context.dataStore.edit { it[Keys.HIDDEN_PLAYER_BUTTONS] = emptySet() }
    }

    suspend fun setGithubRepoSlug(slug: String) {
        context.dataStore.edit { it[Keys.GITHUB_REPO_SLUG] = slug.trim() }
    }

    suspend fun toggleFavorite(videoUri: String): Boolean {
        var added = false
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.FAVORITE_URIS]?.toMutableSet() ?: mutableSetOf()
            added = if (current.contains(videoUri)) {
                current.remove(videoUri)
                false
            } else {
                current.add(videoUri)
                true
            }
            prefs[Keys.FAVORITE_URIS] = current
        }
        return added
    }

    suspend fun removeFavoriteIfPresent(videoUri: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.FAVORITE_URIS]?.toMutableSet() ?: return@edit
            if (current.remove(videoUri)) {
                prefs[Keys.FAVORITE_URIS] = current
            }
        }
    }

    suspend fun markVideoPlayed(videoUri: String) {
        if (videoUri.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.PLAYED_VIDEO_URIS]?.toMutableSet() ?: mutableSetOf()
            if (current.add(videoUri)) {
                prefs[Keys.PLAYED_VIDEO_URIS] = current
            }
        }
    }

    suspend fun addVideoToPlaylist(playlistName: String, videoUri: String) {
        val trimmed = playlistName.trim()
        if (trimmed.isBlank()) return
        context.dataStore.edit { prefs ->
            val map = parsePlaylists(prefs[Keys.PLAYLISTS_JSON]).toMutableMap()
            val existing = map[trimmed].orEmpty().toMutableList()
            if (!existing.contains(videoUri)) {
                existing.add(videoUri)
            }
            map[trimmed] = existing
            prefs[Keys.PLAYLISTS_JSON] = serializePlaylists(map)
        }
    }

    suspend fun removeVideoFromPlaylist(playlistName: String, videoUri: String) {
        context.dataStore.edit { prefs ->
            val map = parsePlaylists(prefs[Keys.PLAYLISTS_JSON]).toMutableMap()
            val list = map[playlistName]?.filterNot { it == videoUri } ?: return@edit
            if (list.isEmpty()) {
                map.remove(playlistName)
            } else {
                map[playlistName] = list
            }
            prefs[Keys.PLAYLISTS_JSON] = serializePlaylists(map)
        }
    }

    suspend fun deletePlaylist(playlistName: String) {
        context.dataStore.edit { prefs ->
            val map = parsePlaylists(prefs[Keys.PLAYLISTS_JSON]).toMutableMap()
            map.remove(playlistName)
            prefs[Keys.PLAYLISTS_JSON] = serializePlaylists(map)
        }
    }

    private fun parsePlaylists(json: String?): Map<String, List<String>> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(json)
            val result = linkedMapOf<String, List<String>>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val name = keys.next()
                val arr = root.optJSONArray(name) ?: continue
                val uris = buildList {
                    for (i in 0 until arr.length()) {
                        val uri = arr.optString(i)
                        if (uri.isNotBlank()) add(uri)
                    }
                }
                result[name] = uris
            }
            result
        }.getOrDefault(emptyMap())
    }

    private fun serializePlaylists(map: Map<String, List<String>>): String {
        val root = JSONObject()
        map.forEach { (name, uris) ->
            val arr = JSONArray()
            uris.forEach { arr.put(it) }
            root.put(name, arr)
        }
        return root.toString()
    }
}
