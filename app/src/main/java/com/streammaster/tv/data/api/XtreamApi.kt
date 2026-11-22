package com.streammaster.tv.data.api

import android.util.Log
import com.google.gson.annotations.SerializedName
import com.streammaster.tv.data.model.Bouquet
import com.streammaster.tv.data.model.BouquetKind
import com.streammaster.tv.data.model.Channel
import com.streammaster.tv.data.model.EpgProgram
import com.streammaster.tv.data.model.Episode
import com.streammaster.tv.data.model.Film
import com.streammaster.tv.data.model.Playlist
import com.streammaster.tv.data.model.Series
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

private const val TAG = "StreamMaster"

/** Raw Xtream Codes player_api.php responses (subset of fields we use). */
data class XtreamCategory(
    @SerializedName("category_id") val categoryId: String?,
    @SerializedName("category_name") val categoryName: String?,
    @SerializedName("parent_id") val parentId: Int?
)

data class XtreamLiveStream(
    @SerializedName("stream_id") val streamId: Int?,
    val num: Int?,
    val name: String?,
    @SerializedName("stream_type") val streamType: String?,
    @SerializedName("stream_icon") val streamIcon: String?,
    @SerializedName("epg_channel_id") val epgChannelId: String?,
    @SerializedName("added") val added: String?,
    @SerializedName("category_id") val categoryId: String?,
    @SerializedName("custom_sid") val customSid: String?,
    @SerializedName("tv_archive") val tvArchive: Int?,
    @SerializedName("direct_source") val directSource: String?,
    @SerializedName("tv_archive_duration") val tvArchiveDuration: Int?
)

data class XtreamVodStream(
    @SerializedName("stream_id") val streamId: Int?,
    val num: Int?,
    val name: String?,
    @SerializedName("stream_type") val streamType: String?,
    @SerializedName("stream_icon") val streamIcon: String?,
    val rating: String?,
    @SerializedName("rating_5based") val rating5: Double?,
    @SerializedName("added") val added: String?,
    @SerializedName("category_id") val categoryId: String?,
    @SerializedName("container_extension") val containerExtension: String?,
    @SerializedName("custom_sid") val customSid: String?,
    @SerializedName("direct_source") val directSource: String?
)

data class XtreamSeriesItem(
    @SerializedName("series_id") val seriesId: Int?,
    val num: Int?,
    val name: String?,
    @SerializedName("series_name") val seriesName: String?,
    val cover: String?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("last_modified") val lastModified: String?,
    val rating: String?,
    @SerializedName("rating_5based") val rating5: Double?,
    @SerializedName("backdrop_path") val backdropPath: List<String>?,
    @SerializedName("youtube_trailer") val youtubeTrailer: String?,
    @SerializedName("episode_run_time") val episodeRunTime: String?,
    @SerializedName("category_id") val categoryId: String?
)

data class XtreamSeriesInfo(
    val seasons: List<XtreamSeason>?,
    val info: XtreamSeriesItem?,
    val episodes: Map<String, List<XtreamEpisode>>?
)

data class XtreamSeason(
    @SerializedName("air_date") val airDate: String?,
    @SerializedName("episode_count") val episodeCount: Int?,
    val id: Int?,
    val name: String?,
    val overview: String?,
    @SerializedName("season_number") val seasonNumber: Int?,
    @SerializedName("cover") val cover: String?,
    @SerializedName("cover_big") val coverBig: String?
)

data class XtreamEpisode(
    val id: String?,
    @SerializedName("episode_num") val episodeNum: Int?,
    val title: String?,
    @SerializedName("container_extension") val containerExtension: String?,
    val info: XtreamEpisodeInfo?,
    @SerializedName("custom_sid") val customSid: String?,
    @SerializedName("added") val added: String?,
    @SerializedName("season") val season: Int?,
    @SerializedName("direct_source") val directSource: String?
)

data class XtreamEpisodeInfo(
    val plot: String?,
    val duration: String?,
    @SerializedName("movie_image") val movieImage: String?,
    val rating: String?,
    @SerializedName("releasedate") val releaseDate: String?,
    val bitrate: Int?,
    val durationSecs: Int?
)

data class XtreamShortEpg(
    @SerializedName("epg_listings") val listings: List<XtreamEpgListing>?
)

data class XtreamEpgListing(
    val id: String?,
    @SerializedName("epg_id") val epgId: String?,
    val title: String?,
    val lang: String?,
    val start: String?,
    val end: String?,
    val description: String?,
    @SerializedName("channel_id") val channelId: String?,
    @SerializedName("start_timestamp") val startTimestamp: String?,
    @SerializedName("stop_timestamp") val stopTimestamp: String?
)

data class XtreamAuth(
    @SerializedName("user_info") val userInfo: XtreamUserInfo?,
    @SerializedName("server_info") val serverInfo: XtreamServerInfo?
)

data class XtreamUserInfo(
    val username: String?,
    val password: String?,
    val message: String?,
    val auth: Int?,
    val status: String?,
    @SerializedName("exp_date") val expDate: String?,
    @SerializedName("is_trial") val isTrial: String?,
    @SerializedName("active_cons") val activeCons: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("max_connections") val maxConnections: String?,
    @SerializedName("allowed_output_formats") val allowedFormats: List<String>?
)

data class XtreamServerInfo(
    val url: String?,
    val port: String?,
    @SerializedName("https_port") val httpsPort: String?,
    @SerializedName("server_protocol") val protocol: String?,
    val rtmpPort: String?,
    val timezone: String?,
    @SerializedName("timestamp_now") val timestampNow: Long?,
    val timeNow: String?
)

interface XtreamService {
    @GET("player_api.php")
    suspend fun authenticate(
        @Query("username") username: String,
        @Query("password") password: String
    ): XtreamAuth

    @GET("player_api.php?action=get_live_categories")
    suspend fun getLiveCategories(
        @Query("username") username: String,
        @Query("password") password: String
    ): List<XtreamCategory>

    @GET("player_api.php?action=get_vod_categories")
    suspend fun getVodCategories(
        @Query("username") username: String,
        @Query("password") password: String
    ): List<XtreamCategory>

    @GET("player_api.php?action=get_series_categories")
    suspend fun getSeriesCategories(
        @Query("username") username: String,
        @Query("password") password: String
    ): List<XtreamCategory>

    @GET("player_api.php?action=get_live_streams")
    suspend fun getLiveStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("category_id") categoryId: String? = null
    ): List<XtreamLiveStream>

    @GET("player_api.php?action=get_vod_streams")
    suspend fun getVodStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("category_id") categoryId: String? = null
    ): List<XtreamVodStream>

    @GET("player_api.php?action=get_series")
    suspend fun getSeries(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("category_id") categoryId: String? = null
    ): List<XtreamSeriesItem>

    @GET("player_api.php?action=get_series_info")
    suspend fun getSeriesInfo(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("series_id") seriesId: Int
    ): XtreamSeriesInfo

    @GET("player_api.php?action=get_short_epg")
    suspend fun getShortEpg(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("stream_id") streamId: Int,
        @Query("limit") limit: Int = 10
    ): XtreamShortEpg
}

/** Parsed result of a full Xtream playlist fetch, ready to merge into Room. */
data class XtreamContent(
    val bouquets: List<Bouquet>,
    val channels: List<Channel>,
    val films: List<Film>,
    val series: List<Series>,
    val episodes: List<Episode>,
    val expiryDate: String,
    val connections: Int,
    val status: String
)

/**
 * Xtream Codes client (player_api.php). Base URL comes from the playlist serverUrl;
 * auth is username/password query params on every call, per the Xtream API.
 */
class XtreamApi(private val okHttpClient: OkHttpClient) {

    private fun service(serverUrl: String): XtreamService {
        val base = serverUrl.trimEnd('/') + "/"
        return Retrofit.Builder()
            .baseUrl(base)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(XtreamService::class.java)
    }

    suspend fun authenticate(playlist: Playlist): XtreamAuth =
        service(playlist.serverUrl).authenticate(playlist.username, playlist.password)

    /**
     * Full fetch: categories + live + VOD + series (+ episodes for each series).
     * [format] is the Settings "format" value ("auto" | "m3u8" | "ts").
     */
    suspend fun fetchAll(playlist: Playlist, format: String = "auto"): XtreamContent {
        val svc = service(playlist.serverUrl)
        val u = playlist.username
        val p = playlist.password
        val auth = runCatching { svc.authenticate(u, p) }.getOrNull()
        val expiry = auth?.userInfo?.expDate?.let { exp ->
            runCatching {
                val d = java.util.Date(exp.toLong() * 1000)
                java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.FRANCE).format(d)
            }.getOrNull()
        } ?: playlist.expiryDate
        val connections = auth?.userInfo?.maxConnections?.toIntOrNull() ?: playlist.connections
        val status = auth?.userInfo?.status ?: playlist.status

        val liveCats = runCatching { svc.getLiveCategories(u, p) }.getOrDefault(emptyList())
        val vodCats = runCatching { svc.getVodCategories(u, p) }.getOrDefault(emptyList())
        val seriesCats = runCatching { svc.getSeriesCategories(u, p) }.getOrDefault(emptyList())
        val live = runCatching { svc.getLiveStreams(u, p) }.getOrDefault(emptyList())
        val vod = runCatching { svc.getVodStreams(u, p) }.getOrDefault(emptyList())
        val seriesRaw = runCatching { svc.getSeries(u, p) }.getOrDefault(emptyList())

        val catName = mutableMapOf<String, String>()
        liveCats.forEach { it.categoryId?.let { id -> catName["L$id"] = it.categoryName ?: "Autre" } }
        vodCats.forEach { it.categoryId?.let { id -> catName["V$id"] = it.categoryName ?: "Films" } }
        seriesCats.forEach { it.categoryId?.let { id -> catName["S$id"] = it.categoryName ?: "Séries" } }

        val bouquets = mutableListOf<Bouquet>()
        liveCats.forEachIndexed { i, c ->
            bouquets += Bouquet("xt-live-${c.categoryId}", c.categoryName ?: "Autre", BouquetKind.LIVE, i, false)
        }
        vodCats.forEachIndexed { i, c ->
            bouquets += Bouquet("xt-vod-${c.categoryId}", c.categoryName ?: "Films", BouquetKind.FILMS, i, false)
        }
        seriesCats.forEachIndexed { i, c ->
            bouquets += Bouquet("xt-ser-${c.categoryId}", c.categoryName ?: "Séries", BouquetKind.SERIES, i, false)
        }

        val base = playlist.serverUrl.trimEnd('/')
        val ext = when (format) {
            "ts" -> "ts"
            "m3u8", "auto" -> "m3u8"
            else -> "m3u8"
        }
        val channels = live.mapIndexed { idx, s ->
            val catId = s.categoryId ?: ""
            Channel(
                id = "xt-${s.streamId}",
                num = (s.num ?: idx + 1).toString().padStart(2, '0'),
                name = s.name?.trim().orEmpty(),
                code = (s.name?.trim().orEmpty().take(2).uppercase()),
                category = catName["L$catId"] ?: "Autre",
                quality = "FHD",
                nowTitle = "",
                nowTime = "",
                progress = 0,
                hue = 44,
                description = "",
                next = emptyList(),
                isFavorite = false,
                isBroken = false,
                streamUrl = "$base/live/$u/$p/${s.streamId}.$ext",
                epgId = s.epgChannelId,
                customNum = null,
                hidden = false,
                customName = null,
                bouquetId = "xt-live-$catId"
            )
        }
        val films = vod.mapIndexed { idx, v ->
            Film(
                id = "xt-vod-${v.streamId}",
                title = v.name?.trim().orEmpty(),
                year = 0,
                rating = v.rating5 ?: v.rating?.toDoubleOrNull() ?: 0.0,
                quality = "FHD",
                hue = 43,
                genre = catName["V${v.categoryId}"] ?: "Films",
                durationMin = 0,
                isFavorite = false,
                recentProgress = 0,
                remainingText = "",
                description = "",
                isBroken = false,
                streamUrl = "$base/movie/$u/$p/${v.streamId}.${v.containerExtension ?: "mp4"}",
                posterUrl = v.streamIcon
            )
        }
        val series = seriesRaw.map { s ->
            Series(
                id = "xt-ser-${s.seriesId}",
                title = (s.seriesName ?: s.name)?.trim().orEmpty(),
                seasons = 0, // filled below from get_series_info
                rating = s.rating5 ?: s.rating?.toDoubleOrNull() ?: 0.0,
                quality = "FHD",
                hue = 45,
                genre = catName["S${s.categoryId}"] ?: (s.genre ?: "Séries"),
                isFavorite = false,
                recentProgress = 0,
                remainingText = "",
                description = s.plot ?: "",
                isBroken = false,
                posterUrl = s.cover,
                trailerUrl = s.youtubeTrailer
            )
        }
        // Episodes + season counts (best effort, one call per series).
        val episodes = mutableListOf<Episode>()
        val seriesWithSeasons = series.map { s ->
            val sid = s.id.removePrefix("xt-ser-").toIntOrNull()
            if (sid == null) return@map s
            val info = runCatching { svc.getSeriesInfo(u, p, sid) }.getOrNull() ?: return@map s
            val seasonCount = info.seasons?.size ?: 0
            info.episodes?.forEach { (seasonKey, eps) ->
                val season = seasonKey.toIntOrNull() ?: return@forEach
                eps.forEach { ep ->
                    episodes += Episode(
                        seriesId = s.id,
                        season = season,
                        number = ep.episodeNum ?: 0,
                        title = ep.title ?: "Épisode ${ep.episodeNum}",
                        durationMin = (ep.info?.durationSecs ?: 0) / 60,
                        code = "S" + season.toString().padStart(2, '0') +
                            "E" + (ep.episodeNum ?: 0).toString().padStart(2, '0'),
                        // Xtream episode stream id -> full series URL, carried
                        // through so playback can resolve it (it was dropped
                        // here before, and episodes showed the error screen).
                        streamUrl = ep.id?.let { id ->
                            episodeStreamUrl(playlist, id, ep.containerExtension ?: "mp4")
                        }
                    )
                }
            }
            s.copy(seasons = seasonCount)
        }
        return XtreamContent(bouquets, channels, films, seriesWithSeasons, episodes, expiry, connections, status)
    }

    /** Short EPG for one stream (limit programs ahead). Returns empty on failure. */
    suspend fun fetchShortEpg(playlist: Playlist, streamId: Int, limit: Int = 10): List<EpgProgram> {
        val svc = service(playlist.serverUrl)
        val resp = runCatching {
            svc.getShortEpg(playlist.username, playlist.password, streamId, limit)
        }.getOrNull() ?: return emptyList()
        return resp.listings.orEmpty().mapNotNull { l ->
            val start = l.startTimestamp?.toLongOrNull()?.times(1000) ?: return@mapNotNull null
            val end = l.stopTimestamp?.toLongOrNull()?.times(1000) ?: (start + 3600_000L)
            EpgProgram(
                channelId = "xt-$streamId",
                title = l.title ?: "",
                startMillis = start,
                endMillis = end,
                description = l.description ?: ""
            )
        }
    }

    /** Stream URL for a VOD episode (series playback). */
    fun episodeStreamUrl(playlist: Playlist, episodeId: String, containerExt: String = "mp4"): String {
        val base = playlist.serverUrl.trimEnd('/')
        return "$base/series/${playlist.username}/${playlist.password}/$episodeId.$containerExt"
    }
}
