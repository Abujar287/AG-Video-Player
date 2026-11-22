package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "veloplay_prefs")

data class UserPreferences(
    val isDarkMode: Boolean = true,
    val autoPipOnHome: Boolean = true,
    val defaultSoftwareDecoder: Boolean = false,
    val githubRepoSlug: String = DEFAULT_GITHUB_REPO
) {
    companion object {
        const val DEFAULT_GITHUB_REPO = "owner/veloplay-android"
    }
}

class UserPreferencesRepository(private val context: Context) {
    private object Keys {
        val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        val AUTO_PIP_ON_HOME = booleanPreferencesKey("auto_pip_on_home")
        val DEFAULT_SW_DECODER = booleanPreferencesKey("default_sw_decoder")
        val GITHUB_REPO_SLUG = stringPreferencesKey("github_repo_slug")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            isDarkMode = prefs[Keys.IS_DARK_MODE] ?: true,
            autoPipOnHome = prefs[Keys.AUTO_PIP_ON_HOME] ?: true,
            defaultSoftwareDecoder = prefs[Keys.DEFAULT_SW_DECODER] ?: false,
            githubRepoSlug = prefs[Keys.GITHUB_REPO_SLUG]?.takeIf { it.isNotBlank() }
                ?: UserPreferences.DEFAULT_GITHUB_REPO
        )
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.IS_DARK_MODE] = enabled }
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
}
