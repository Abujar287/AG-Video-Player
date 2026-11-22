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
import com.example.data.model.DoubleClickAction
import com.example.data.model.PlayerControlButton
import com.example.data.model.SingleClickAction
import com.example.data.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ag_player_prefs")

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        val IS_GRID_VIEW = booleanPreferencesKey("is_grid_view")
        val SHOW_HIDDEN_FILES = booleanPreferencesKey("show_hidden_files")
        val MAIN_DISPLAY_MODE = stringPreferencesKey("main_display_mode")
        val MAIN_VIEW_LAYOUT = stringPreferencesKey("main_view_layout")
        val ONE_FINGER_GESTURE = stringPreferencesKey("one_finger_gesture")
        val TWO_FINGER_GESTURE = stringPreferencesKey("two_finger_gesture")
        val THREE_FINGER_GESTURE = stringPreferencesKey("three_finger_gesture")
        val AUTO_PIP_ON_HOME = booleanPreferencesKey("auto_pip_on_home")
        val DEFAULT_SW_DECODER = booleanPreferencesKey("default_sw_decoder")
        val GITHUB_REPO_SLUG = stringPreferencesKey("github_repo_slug")
        val FAVORITE_URIS = stringSetPreferencesKey("favorite_uris")
        val PLAYLISTS_JSON = stringPreferencesKey("playlists_json")
        val PLAYED_VIDEO_URIS = stringSetPreferencesKey("played_video_uris")
        val SINGLE_CLICK_ACTION = stringPreferencesKey("single_click_action")
        val DOUBLE_CLICK_ACTION = stringPreferencesKey("double_click_action")
        val ALWAYS_BG_PLAY = booleanPreferencesKey("always_bg_play")
        val ENABLE_SWIPE_GESTURES = booleanPreferencesKey("enable_swipe_gestures")
        val GESTURE_SEEK_STEP = intPreferencesKey("gesture_seek_step")
        val HIDDEN_BUTTONS = stringSetPreferencesKey("hidden_buttons")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val playlists = parsePlaylistsJson(prefs[Keys.PLAYLISTS_JSON] ?: "{}")
        val hiddenBtns = (prefs[Keys.HIDDEN_BUTTONS] ?: emptySet())
            .mapNotNull { runCatching { PlayerControlButton.valueOf(it) }.getOrNull() }
            .toSet()
        val isGrid = prefs[Keys.IS_GRID_VIEW] ?: false
        val layout = prefs[Keys.MAIN_VIEW_LAYOUT] ?: if (isGrid) "grid" else "list"

        UserPreferences(
            isDarkMode = prefs[Keys.IS_DARK_MODE] ?: true,
            isGridView = isGrid,
            showHiddenFiles = prefs[Keys.SHOW_HIDDEN_FILES] ?: false,
            mainDisplayMode = prefs[Keys.MAIN_DISPLAY_MODE] ?: "folders",
            mainViewLayout = layout,
            oneFingerGesture = prefs[Keys.ONE_FINGER_GESTURE] ?: "Brightness / Volume Swipe",
            twoFingerGesture = prefs[Keys.TWO_FINGER_GESTURE] ?: "Zoom / Scale",
            threeFingerGesture = prefs[Keys.THREE_FINGER_GESTURE] ?: "Play / Pause",
            autoPipOnHome = prefs[Keys.AUTO_PIP_ON_HOME] ?: false,
            defaultSoftwareDecoder = prefs[Keys.DEFAULT_SW_DECODER] ?: false,
            githubRepoSlug = prefs[Keys.GITHUB_REPO_SLUG]?.takeIf { it.isNotBlank() } ?: BuildConfig.GITHUB_REPO_SLUG,
            favoriteUris = prefs[Keys.FAVORITE_URIS] ?: emptySet(),
            playlists = playlists,
            playedVideoUris = prefs[Keys.PLAYED_VIDEO_URIS] ?: emptySet(),
            singleClickAction = runCatching {
                SingleClickAction.valueOf(prefs[Keys.SINGLE_CLICK_ACTION] ?: SingleClickAction.TOGGLE_CONTROLS.name)
            }.getOrDefault(SingleClickAction.TOGGLE_CONTROLS),
            doubleClickAction = runCatching {
                DoubleClickAction.valueOf(prefs[Keys.DOUBLE_CLICK_ACTION] ?: DoubleClickAction.SEEK_10S.name)
            }.getOrDefault(DoubleClickAction.SEEK_10S),
            alwaysBackgroundPlay = prefs[Keys.ALWAYS_BG_PLAY] ?: false,
            enableSwipeGestures = prefs[Keys.ENABLE_SWIPE_GESTURES] ?: true,
            gestureSeekStepSeconds = prefs[Keys.GESTURE_SEEK_STEP] ?: 0,
            hiddenButtons = hiddenBtns
        )
    }

    suspend fun setDarkMode(enabled: Boolean) = update { it[Keys.IS_DARK_MODE] = enabled }
    suspend fun setGridView(enabled: Boolean) = update {
        it[Keys.IS_GRID_VIEW] = enabled
        it[Keys.MAIN_VIEW_LAYOUT] = if (enabled) "grid" else "list"
    }
    suspend fun setShowHiddenFiles(enabled: Boolean) = update { it[Keys.SHOW_HIDDEN_FILES] = enabled }
    suspend fun setMainDisplayMode(mode: String) = update { it[Keys.MAIN_DISPLAY_MODE] = mode }
    suspend fun setMainViewLayout(layout: String) = update {
        it[Keys.MAIN_VIEW_LAYOUT] = layout
        it[Keys.IS_GRID_VIEW] = layout == "grid"
    }
    suspend fun setOneFingerGesture(value: String) = update { it[Keys.ONE_FINGER_GESTURE] = value }
    suspend fun setTwoFingerGesture(value: String) = update { it[Keys.TWO_FINGER_GESTURE] = value }
    suspend fun setThreeFingerGesture(value: String) = update { it[Keys.THREE_FINGER_GESTURE] = value }
    suspend fun setAutoPipOnHome(enabled: Boolean) = update { it[Keys.AUTO_PIP_ON_HOME] = enabled }
    suspend fun setDefaultSoftwareDecoder(enabled: Boolean) = update { it[Keys.DEFAULT_SW_DECODER] = enabled }
    suspend fun setSingleClickAction(action: SingleClickAction) = update { it[Keys.SINGLE_CLICK_ACTION] = action.name }
    suspend fun setDoubleClickAction(action: DoubleClickAction) = update { it[Keys.DOUBLE_CLICK_ACTION] = action.name }
    suspend fun setAlwaysBackgroundPlay(enabled: Boolean) = update { it[Keys.ALWAYS_BG_PLAY] = enabled }
    suspend fun setEnableSwipeGestures(enabled: Boolean) = update { it[Keys.ENABLE_SWIPE_GESTURES] = enabled }
    suspend fun setGestureSeekStepSeconds(seconds: Int) = update { it[Keys.GESTURE_SEEK_STEP] = seconds }

    suspend fun setPlayerButtonVisible(button: PlayerControlButton, visible: Boolean) = update { prefs ->
        val current = (prefs[Keys.HIDDEN_BUTTONS] ?: emptySet()).toMutableSet()
        if (visible) current.remove(button.name) else current.add(button.name)
        prefs[Keys.HIDDEN_BUTTONS] = current
    }

    suspend fun showAllPlayerButtons() = update { it[Keys.HIDDEN_BUTTONS] = emptySet() }

    suspend fun markVideoPlayed(uri: String) = update { prefs ->
        if (uri.isNotBlank()) {
            val current = (prefs[Keys.PLAYED_VIDEO_URIS] ?: emptySet()).toMutableSet()
            current.add(uri)
            prefs[Keys.PLAYED_VIDEO_URIS] = current
        }
    }

    suspend fun toggleFavorite(uri: String): Boolean {
        var added = false
        update { prefs ->
            val current = (prefs[Keys.FAVORITE_URIS] ?: emptySet()).toMutableSet()
            if (current.contains(uri)) {
                current.remove(uri)
                added = false
            } else {
                current.add(uri)
                added = true
            }
            prefs[Keys.FAVORITE_URIS] = current
        }
        return added
    }

    suspend fun addToPlaylist(playlistName: String, uri: String) {
        val clean = playlistName.trim()
        if (clean.isBlank() || uri.isBlank()) return
        update { prefs ->
            val map = parsePlaylistsJson(prefs[Keys.PLAYLISTS_JSON] ?: "{}").toMutableMap()
            val list = (map[clean] ?: emptyList()).toMutableList()
            if (!list.contains(uri)) list.add(uri)
            map[clean] = list
            prefs[Keys.PLAYLISTS_JSON] = encodePlaylistsJson(map)
        }
    }

    suspend fun removeFromPlaylist(playlistName: String, uri: String) {
        update { prefs ->
            val map = parsePlaylistsJson(prefs[Keys.PLAYLISTS_JSON] ?: "{}").toMutableMap()
            val list = (map[playlistName] ?: emptyList()).filter { it != uri }
            if (list.isEmpty()) map.remove(playlistName) else map[playlistName] = list
            prefs[Keys.PLAYLISTS_JSON] = encodePlaylistsJson(map)
        }
    }

    suspend fun deletePlaylist(playlistName: String) {
        update { prefs ->
            val map = parsePlaylistsJson(prefs[Keys.PLAYLISTS_JSON] ?: "{}").toMutableMap()
            map.remove(playlistName)
            prefs[Keys.PLAYLISTS_JSON] = encodePlaylistsJson(map)
        }
    }

    suspend fun removeUriFromCollections(uri: String) {
        update { prefs ->
            val favs = (prefs[Keys.FAVORITE_URIS] ?: emptySet()).toMutableSet()
            favs.remove(uri)
            prefs[Keys.FAVORITE_URIS] = favs

            val map = parsePlaylistsJson(prefs[Keys.PLAYLISTS_JSON] ?: "{}").toMutableMap()
            val updated = mutableMapOf<String, List<String>>()
            for ((k, v) in map) {
                val filtered = v.filter { it != uri }
                if (filtered.isNotEmpty()) updated[k] = filtered
            }
            prefs[Keys.PLAYLISTS_JSON] = encodePlaylistsJson(updated)
        }
    }

    private suspend fun update(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    private fun parsePlaylistsJson(raw: String): Map<String, List<String>> {
        val result = mutableMapOf<String, List<String>>()
        runCatching {
            val obj = JSONObject(raw)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val arr = obj.optJSONArray(key) ?: continue
                val items = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val str = arr.optString(i)
                    if (str.isNotBlank()) items.add(str)
                }
                if (items.isNotEmpty()) result[key] = items
            }
        }
        return result
    }

    private fun encodePlaylistsJson(map: Map<String, List<String>>): String {
        val obj = JSONObject()
        for ((k, list) in map) {
            obj.put(k, JSONArray(list))
        }
        return obj.toString()
    }
}
