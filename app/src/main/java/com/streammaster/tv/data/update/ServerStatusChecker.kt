package com.streammaster.tv.data.update

import android.os.SystemClock
import com.streammaster.tv.data.model.Playlist
import com.streammaster.tv.data.model.PlaylistType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

data class ServerStatus(
    val online: Boolean,
    val latencyMs: Long,
    val detail: String
)

/**
 * Server status check (2026-11-22): a light request to the active
 * playlist's server. Xtream gets a `player_api.php` call (proves the
 * account answers); Stalker/M3U get a plain request to the base URL.
 * Never throws — an unreachable server is an offline result.
 */
@Singleton
class ServerStatusChecker @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    suspend fun check(playlist: Playlist): ServerStatus = withContext(Dispatchers.IO) {
        val base = playlist.serverUrl.trim().trimEnd('/')
        if (base.isBlank()) return@withContext ServerStatus(false, 0, "no server")
        val url = if (playlist.type == PlaylistType.XTREAM) {
            "$base/player_api.php?username=${playlist.username}&password=${playlist.password}"
        } else base
        val start = SystemClock.elapsedRealtime()
        val req = Request.Builder().url(url).build()
        val resp = runCatching { okHttpClient.newCall(req).execute() }.getOrNull()
        val latency = SystemClock.elapsedRealtime() - start
        if (resp == null) return@withContext ServerStatus(false, latency, "unreachable")
        resp.use {
            ServerStatus(it.isSuccessful, latency, "HTTP ${it.code}")
        }
    }
}
