package com.streammaster.tv.player

import android.content.Context
import android.net.http.HttpEngine
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.HttpEngineDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.cronet.CronetDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.upstream.CmcdConfiguration
import com.streammaster.tv.data.model.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import org.chromium.net.CronetEngine
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "StreamMaster"

/**
 * Load-error policy (GUIDE B16): no retry on 404/403 (fail fast so the fallback chain
 * can try the next mirror / engine), exponential backoff 1s / 2s / 4s / 8s on network
 * and timeout errors, then give up (TIME_UNSET) so [PlayerManagerImpl] takes over.
 */
@OptIn(UnstableApi::class)
internal class StreamMasterLoadErrorPolicy : DefaultLoadErrorHandlingPolicy(
    /* minimumLoadableRetryCount = */ 4
) {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val exception = loadErrorInfo.exception
        if (exception is HttpDataSource.InvalidResponseCodeException) {
            if (exception.responseCode == 404 || exception.responseCode == 403) {
                return C.TIME_UNSET // Fail fast: retrying a dead URL is pointless.
            }
        }
        val errorCount = loadErrorInfo.errorCount
        if (errorCount > 4) return C.TIME_UNSET
        // 1s, 2s, 4s, 8s (capped).
        return minOf(1000L shl (errorCount - 1), 8000L)
    }
}

/**
 * All network / datasource construction for the player (GUIDE B14–B22, D12).
 *
 * Stack selection (B14): HttpEngine on API 34+ (HTTP/2 + HTTP/3 over QUIC), else
 * Cronet (embedded), else OkHttp, else the built-in DefaultHttpDataSource.
 * Connect/read timeouts are 8s / 8s everywhere (B15).
 *
 * The [ResolvingDataSource] wrapper (B17) is the hook for token refresh and mirror
 * switching (D2/D11): [setMirror] points the resolver at the current mirror URL and
 * the player re-prepares with the original URL — only the manifest request itself
 * is rewritten, never segment requests.
 *
 * VOD caching (B20/D12): a 300 MB [SimpleCache] with LRU eviction, wrapped in a
 * [CacheDataSource] with FLAG_IGNORE_CACHE_ON_ERROR. It is used for VOD ONLY —
 * live playlists/segments are never cached (a cached live playlist freezes the
 * playlist and stalls playback at the live edge).
 */
@OptIn(UnstableApi::class)
@Singleton
class NetworkStack @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val cronetEngine: CronetEngine?,
    private val bitrateStore: BitrateStore
) {
    companion object {
        const val CONNECT_TIMEOUT_MS = 8000
        const val READ_TIMEOUT_MS = 8000
        const val RTSP_TIMEOUT_MS = 8000L

        /** VOD disk cache cap (B20). */
        const val VOD_CACHE_MAX_BYTES = 300L * 1024 * 1024

        const val DEFAULT_UA = "StreamMaster/1.2.0 (Android TV)"
        const val CHROME_TV_UA =
            "Mozilla/5.0 (Linux; Android 11; Android TV) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        const val VLC_UA = "VLC/3.0.24 LibVLC/3.0.24"

        /**
         * Builds a [NetworkStack] without Hilt — used by the [PlayerFactory] top-level
         * functions so `createExoPlayer(context, settings)` keeps its exact CONTRACT
         * §9 signature. The DI path (AppModule) uses the @Inject constructor instead,
         * with the shared OkHttpClient / CronetEngine from the data agent.
         */
        fun create(context: Context, settings: AppSettings): NetworkStack {
            val stack = NetworkStack(
                context,
                defaultOkHttpClient(settings),
                defaultCronetEngine(context),
                BitrateStore(context)
            )
            return stack
        }

        private fun defaultOkHttpClient(settings: AppSettings): OkHttpClient {
            val ua = resolveUserAgentStatic(settings)
            return OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                .readTimeout(READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", ua)
                        .build()
                    chain.proceed(request)
                }
                .build()
        }

        private fun defaultCronetEngine(context: Context): CronetEngine =
            CronetEngine.Builder(context).build()

        /**
         * Maps the settings UA choice to a concrete User-Agent string. The data agent
         * stores the choice in [AppSettings.ua] ("default" / "chrome_tv" / "vlc" /
         * "custom" + [AppSettings.uaCustom]); unknown values are passed through
         * verbatim so a future UA list entry keeps working.
         */
        fun resolveUserAgentStatic(settings: AppSettings): String = when {
            settings.ua == "custom" -> settings.uaCustom.ifBlank { DEFAULT_UA }
            settings.ua.contains("chrome", ignoreCase = true) -> CHROME_TV_UA
            settings.ua.contains("vlc", ignoreCase = true) -> VLC_UA
            settings.ua == "default" || settings.ua.isBlank() -> DEFAULT_UA
            else -> settings.ua
        }
    }

    fun resolveUserAgent(settings: AppSettings): String = resolveUserAgentStatic(settings)

    fun bitrateStore(): BitrateStore = bitrateStore

    // ---- Mirror switching (B17 / D2) ----------------------------------------

    @Volatile private var mirrorOriginalUrl: String? = null
    @Volatile private var mirrorUrl: String? = null

    /** Point the resolver at [mirror] for requests whose URI is [original]. */
    fun setMirror(original: String, mirror: String?) {
        mirrorOriginalUrl = original
        mirrorUrl = mirror
    }

    fun clearMirror() {
        mirrorOriginalUrl = null
        mirrorUrl = null
    }

    // ---- HTTP stack (B14/B15) ------------------------------------------------

    /** Modern HTTP stack with 8s/8s timeouts and the settings User-Agent. */
    fun httpDataSourceFactory(settings: AppSettings): HttpDataSource.Factory {
        val ua = resolveUserAgent(settings)
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                val httpEngine = HttpEngine.Builder(context).build()
                return HttpEngineDataSource.Factory(
                    httpEngine,
                    Executors.newSingleThreadExecutor()
                )
                    .setConnectionTimeoutMs(CONNECT_TIMEOUT_MS)
                    .setReadTimeoutMs(READ_TIMEOUT_MS)
                    .setUserAgent(ua)
            } catch (e: Exception) {
                Log.w(TAG, "HttpEngine unavailable, falling back to Cronet", e)
            }
        }
        // Cronet is optional (the DI provider returns null when it is
        // unavailable on this device) — fall through to OkHttp then.
        val engine = cronetEngine
        if (engine != null) {
            try {
                return CronetDataSource.Factory(
                    engine,
                    Executors.newSingleThreadExecutor()
                )
                    .setConnectionTimeoutMs(CONNECT_TIMEOUT_MS)
                    .setReadTimeoutMs(READ_TIMEOUT_MS)
                    .setUserAgent(ua)
            } catch (e: Exception) {
                Log.w(TAG, "Cronet unavailable, falling back to OkHttp/DefaultHttp", e)
            }
        } else {
            Log.w(TAG, "CronetEngine not provided, falling back to OkHttp/DefaultHttp")
        }
        return try {
            OkHttpDataSource.Factory(okHttpClient)
        } catch (e2: Exception) {
            DefaultHttpDataSource.Factory()
                .setUserAgent(ua)
                .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
                .setReadTimeoutMs(READ_TIMEOUT_MS)
                .setAllowCrossProtocolRedirects(true)
        }
    }

    /**
     * ResolvingDataSource (B17): rewrites the manifest request to the current mirror
     * URL when one is set, and is the future hook for refreshing short-lived signed
     * tokens (D11) before each request.
     */
    fun resolvingFactory(settings: AppSettings): DataSource.Factory {
        val upstream = httpDataSourceFactory(settings)
        return ResolvingDataSource.Factory(upstream) { dataSpec ->
            val original = mirrorOriginalUrl
            val mirror = mirrorUrl
            if (original != null && mirror != null && dataSpec.uri.toString() == original) {
                dataSpec.withUri(android.net.Uri.parse(mirror))
            } else {
                dataSpec
            }
        }
    }

    // ---- VOD cache (B20/D12) --------------------------------------------------

    private val vodCache: SimpleCache by lazy {
        SimpleCache(
            File(context.cacheDir, "media"),
            LeastRecentlyUsedCacheEvictor(VOD_CACHE_MAX_BYTES),
            StandaloneDatabaseProvider(context)
        )
    }

    // ---- MediaSource factories -------------------------------------------------

    /**
     * MediaSource factory for one item. VOD goes through the disk cache, live never
     * does. CMCD is enabled on HLS/DASH sources (B19) via the DEFAULT factory.
     * The [DefaultMediaSourceFactory] infers HLS / DASH / progressive (.ts via
     * TsExtractor, B29) from the URL; RTSP is handled separately by
     * [rtspMediaSourceFactory] so TCP can be forced (B30).
     */
    fun mediaSourceFactory(settings: AppSettings, isVod: Boolean): MediaSource.Factory {
        val upstream = resolvingFactory(settings)
        val dataSourceFactory: DataSource.Factory = if (isVod) {
            CacheDataSource.Factory()
                .setCache(vodCache)
                .setUpstreamDataSourceFactory(upstream)
                .setCacheWriteDataSinkFactory(CacheDataSink.Factory().setCache(vodCache))
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } else {
            upstream
        }
        return DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(StreamMasterLoadErrorPolicy())
            .setCmcdConfigurationFactory(CmcdConfiguration.Factory.DEFAULT)
    }

    /**
     * RTSP source factory (B30): UDP first with automatic fallback to RTP over TCP
     * after [RTSP_TIMEOUT_MS], or forced TCP when [forceTcp] is true (player prefs
     * flag — there is no RTSP setting in AppSettings, see [BitrateStore]).
     */
    fun rtspMediaSourceFactory(settings: AppSettings, forceTcp: Boolean): MediaSource.Factory =
        RtspMediaSource.Factory()
            .setForceUseRtpTcp(forceTcp)
            .setTimeoutMs(RTSP_TIMEOUT_MS)
            .setUserAgent(resolveUserAgent(settings))

    /**
     * Builds the [MediaItem] for one URL. Live items get the LiveConfiguration
     * (B7): 8s target offset, 4s–12s window, playback speed 0.97–1.03 so the player
     * can drift back to the target offset without stalling or skipping.
     */
    fun buildMediaItem(url: String, mediaId: String, isLive: Boolean): MediaItem {
        val builder = MediaItem.Builder()
            .setUri(url)
            .setMediaId(mediaId)
        if (isLive) {
            builder.setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(8000)
                    .setMinOffsetMs(4000)
                    .setMaxOffsetMs(12000)
                    .setMinPlaybackSpeed(0.97f)
                    .setMaxPlaybackSpeed(1.03f)
                    .build()
            )
        }
        return builder.build()
    }
}
