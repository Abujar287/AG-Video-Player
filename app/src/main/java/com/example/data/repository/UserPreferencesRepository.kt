package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "veloplay_prefs")

data class UserPreferences(
    val isDarkMode: Boolean = true,
    val isGridView: Boolean = false,
    val autoPipOnHome: Boolean = true,
    val defaultSoftwareDecoder: Boolean = false,
    val githubRepoSlug: String = DEFAULT_GITHUB_REPO,
    val favoriteUris: Set<String> = emptySet(),
    val playlists: Map<String, List<String>> = emptyMap()
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
            playlists = parsePlaylists(prefs[Keys.PLAYLISTS_JSON])
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
