package com.streammaster.tv.data.subtitles

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.subtitleDataStore: DataStore<Preferences> by preferencesDataStore(name = "subtitles")

/**
 * SPEC §4.48 — OpenSubtitles API key, stored in its own DataStore
 * ("subtitles"). The key comes from the user's own free OpenSubtitles
 * account (opensubtitles.com → API consumers); it is typed in from the
 * subtitle button dialog and NEVER hard-coded anywhere in the app.
 * The key is only ever sent to api.opensubtitles.com, in the `Api-Key`
 * header — never logged, never put in a URL.
 */
class SubtitlePrefs(private val context: Context) {

    companion object {
        val KEY_API_KEY = stringPreferencesKey("openSubtitlesApiKey")
    }

    val apiKey: Flow<String> = context.subtitleDataStore.data.map { it[KEY_API_KEY] ?: "" }

    suspend fun setApiKey(apiKey: String) {
        context.subtitleDataStore.edit { it[KEY_API_KEY] = apiKey.trim() }
    }

    suspend fun clearApiKey() {
        context.subtitleDataStore.edit { it.remove(KEY_API_KEY) }
    }
}
