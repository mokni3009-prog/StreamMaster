package com.streammaster.tv.data.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesDataStore
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.streammaster.tv.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** An update the automatic check found (§4.25b). */
data class PendingUpdate(
    val version: String,
    val versionCode: Int,
    val changelog: String,
    val downloadUrl: String
)

private val Context.updatePromptDataStore by preferencesDataStore(name = "update_prompt")

private val KEY_VERSION = stringPreferencesKey("pending_version")
private val KEY_VERSION_CODE = intPreferencesKey("pending_version_code")
private val KEY_CHANGELOG = stringPreferencesKey("pending_changelog")
private val KEY_DOWNLOAD_URL = stringPreferencesKey("pending_download_url")

/**
 * In-memory prompt state shared with MainActivity's popup, backed by a
 * small DataStore so "remind me later" survives until the next app
 * launch: dismissing hides the popup for this session only, and the
 * next launch shows it again until the user updates (§4.25b).
 */
object UpdatePromptState {
    private val _pending = MutableStateFlow<PendingUpdate?>(null)
    val pending: StateFlow<PendingUpdate?> = _pending.asStateFlow()

    /** Version the user dismissed this session — never re-shown until relaunch. */
    private var dismissedVersionCode: Int? = null

    fun set(pending: PendingUpdate?) {
        if (pending != null && pending.versionCode == dismissedVersionCode) return
        _pending.value = pending
    }

    /** "Remind me later": hide now, keep the pending record for next launch. */
    fun dismissForSession() {
        _pending.value?.let { dismissedVersionCode = it.versionCode }
        _pending.value = null
    }

    /** Loads the persisted pending update on every app launch. A pending
     *  update whose versionCode is no longer newer than the installed
     *  build (i.e. the user already updated) is dropped. */
    suspend fun loadPending(context: Context) {
        val pending = context.updatePromptDataStore.data.map { prefs ->
            val code = prefs[KEY_VERSION_CODE] ?: 0
            val url = prefs[KEY_DOWNLOAD_URL].orEmpty()
            if (code <= 0 || url.isBlank()) null
            else PendingUpdate(
                version = prefs[KEY_VERSION].orEmpty(),
                versionCode = code,
                changelog = prefs[KEY_CHANGELOG].orEmpty(),
                downloadUrl = url
            )
        }.first()
        if (pending != null && pending.versionCode > BuildConfig.VERSION_CODE) {
            set(pending)
        } else {
            clear(context)
        }
    }

    suspend fun save(context: Context, pending: PendingUpdate) {
        context.updatePromptDataStore.edit { prefs ->
            prefs[KEY_VERSION] = pending.version
            prefs[KEY_VERSION_CODE] = pending.versionCode
            prefs[KEY_CHANGELOG] = pending.changelog
            prefs[KEY_DOWNLOAD_URL] = pending.downloadUrl
        }
        set(pending)
    }

    suspend fun clear(context: Context) {
        context.updatePromptDataStore.edit { it.clear() }
        _pending.value = null
    }
}

/**
 * Automatic update check (§4.25b): every 24h, reads the public
 * `update.json` on GitHub and, when a newer versionCode exists, stores
 * it as pending so MainActivity shows the popup on the next launch.
 * Never downloads or installs anything by itself.
 */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val info = runCatching {
            AppUpdateChecker(OkHttpClient())
                .check(DEFAULT_UPDATE_URL, BuildConfig.VERSION_CODE)
        }.getOrNull() ?: return Result.success()
        if (info.available && info.downloadUrl.isNotBlank()) {
            UpdatePromptState.save(
                applicationContext,
                PendingUpdate(
                    version = info.version,
                    versionCode = info.versionCode,
                    changelog = info.changelog,
                    downloadUrl = info.downloadUrl
                )
            )
        } else {
            UpdatePromptState.clear(applicationContext)
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "update-check"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(24, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}

/**
 * Starts the APK download with DownloadManager — the same request as
 * SettingsViewModel.downloadUpdate (§4.25b (4)): the system notifies
 * when done and tapping it installs over the old version (same package,
 * same signature, higher versionCode; data is kept). No silent install.
 */
object UpdateDownloader {
    fun download(context: Context, downloadUrl: String): Boolean = runCatching {
        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle("Stream Master")
            .setDescription("Mise à jour")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "stream-master.apk")
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
        true
    }.getOrDefault(false)
}
