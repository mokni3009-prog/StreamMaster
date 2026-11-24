package com.streammaster.tv.data.subtitles

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * SPEC §4.48 — OpenSubtitles REST API v1 (api.opensubtitles.com).
 * Verified 2026-11-23 against the public API docs:
 * - base `https://api.opensubtitles.com/api/v1`
 * - search: `GET /subtitles?query=…&languages=ar&year=…`
 * - download link: `POST /download { "file_id": … }` → `{ "link": … }`
 * - every call carries `Api-Key: <user key>` + a descriptive User-Agent.
 * The download-link call needs an authenticated (logged-in) account for
 * full quota; with an anonymous API key only it may answer 401 — the
 * downloader surfaces that as [SubtitleDownloadError.NeedsLogin] instead
 * of failing silently. No user/password login is implemented (no
 * credential storage for it); the free API key alone covers search and
 * most downloads.
 */
interface OpenSubtitlesService {

    @GET("subtitles")
    suspend fun search(
        @Header("Api-Key") apiKey: String,
        @Header("User-Agent") userAgent: String = USER_AGENT,
        @Query("query") query: String,
        @Query("languages") languages: String = "ar",
        @Query("year") year: Int? = null,
        @Query("order_by") orderBy: String = "download_count",
        @Query("order_direction") orderDirection: String = "desc"
    ): SubtitleSearchResponse

    @POST("download")
    suspend fun downloadLink(
        @Header("Api-Key") apiKey: String,
        @Header("User-Agent") userAgent: String = USER_AGENT,
        @Body request: DownloadRequest
    ): DownloadResponse

    companion object {
        const val USER_AGENT = "StreamMaster/1.2.2"
    }
}

data class DownloadRequest(
    @SerializedName("file_id") val fileId: Long
)

data class DownloadResponse(
    @SerializedName("link") val link: String = "",
    @SerializedName("file_name") val fileName: String = "",
    @SerializedName("remaining") val remaining: Int? = null
)

data class SubtitleSearchResponse(
    @SerializedName("data") val data: List<SubtitleEntry> = emptyList()
)

data class SubtitleEntry(
    @SerializedName("id") val id: String = "",
    @SerializedName("attributes") val attributes: SubtitleAttributes? = null
)

data class SubtitleAttributes(
    @SerializedName("language") val language: String = "",
    @SerializedName("download_count") val downloadCount: Int = 0,
    @SerializedName("release") val release: String = "",
    @SerializedName("files") val files: List<SubtitleFile> = emptyList()
)

data class SubtitleFile(
    @SerializedName("file_id") val fileId: Long = 0L,
    @SerializedName("file_name") val fileName: String = ""
)

internal fun openSubtitlesService(): OpenSubtitlesService = Retrofit.Builder()
    .baseUrl("https://api.opensubtitles.com/api/v1/")
    .addConverterFactory(GsonConverterFactory.create())
    .build()
    .create(OpenSubtitlesService::class.java)
