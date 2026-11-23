package com.streammaster.tv.data.matchday

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/** One match of the day, as produced by the external Match Day script
 *  (workspace `match-day/` — it merges ESPN / jdwal / m-yala / yallakora
 *  every 6h into a single JSON file). Times are already Tunis time. */
data class DayMatch(
    val id: String,
    val league: String,
    val leagueLogo: String?,
    val homeTeam: String,
    val awayTeam: String,
    val homeLogo: String?,
    val awayLogo: String?,
    /** Kickoff, epoch millis (parsed from `kickoff_tunis`). 0 when unknown. */
    val kickoffMillis: Long,
    /** Kickoff as "HH:mm" in Tunis time, for display. */
    val kickoffText: String,
    val stadium: String?,
    /** Broadcast channels, in the script's priority order (first = main). */
    val channels: List<String>,
    /** "scheduled" | "live" | "finished" | "cancelled". */
    val status: String
)

data class LeagueGroup(
    val league: String,
    val leagueLogo: String?,
    val matches: List<DayMatch>
)

data class DayFeed(
    val date: String,
    val leagues: List<LeagueGroup>
)

/** Whole file: today + tomorrow + the generation time. */
data class MatchDayFeed(
    val today: DayFeed,
    val tomorrow: DayFeed,
    /** Generation time, epoch millis (from `generated_at`, fallback `updatedAt`). */
    val updatedAtMillis: Long
)

/**
 * Match Day (§4.35): downloads the static `match-day.json` produced by
 * the external script. No API key, no account — the file is public on
 * GitHub. Anything unparsable is skipped, never thrown.
 */
@Singleton
class MatchDayApi @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "MatchDay"

        /** Static JSON URL (raw GitHub). Changeable here; the file is
         *  committed at the repo root as `match-day.json`. */
        const val DEFAULT_MATCH_DAY_URL =
            "https://raw.githubusercontent.com/mokni3009-prog/StreamMaster/main/match-day.json"

        private val TUNIS_TZ: TimeZone = TimeZone.getTimeZone("Africa/Tunis")
    }

    suspend fun fetch(url: String = DEFAULT_MATCH_DAY_URL): MatchDayFeed? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url)
                    .header("Accept", "application/json").build()
                val body = okHttpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.string()
                } ?: return@withContext null
                parse(body)
            } catch (e: Exception) {
                Log.w(TAG, "match-day fetch failed", e)
                null
            }
        }

    private fun parse(json: String): MatchDayFeed? {
        return try {
            val root = JSONObject(json)
            val updatedAt = parseIsoMillis(
                root.optString("generated_at", "").ifBlank { root.optString("updatedAt", "") }
            )
            MatchDayFeed(
                today = parseDay(root.optJSONObject("today")),
                tomorrow = parseDay(root.optJSONObject("tomorrow")),
                updatedAtMillis = updatedAt
            )
        } catch (e: Exception) {
            Log.w(TAG, "match-day parse failed", e)
            null
        }
    }

    private fun parseDay(day: JSONObject?): DayFeed {
        if (day == null) return DayFeed("", emptyList())
        val date = day.optString("date", "")
        val leagues = mutableListOf<LeagueGroup>()
        val arr = day.optJSONArray("leagues") ?: return DayFeed(date, emptyList())
        for (i in 0 until arr.length()) {
            try {
                val lg = arr.getJSONObject(i)
                val league = lg.optString("league", "").ifBlank { "Football" }
                val leagueLogo = lg.optString("league_logo", "").takeIf { it.isNotBlank() }
                val matches = mutableListOf<DayMatch>()
                val mArr = lg.optJSONArray("matches") ?: JSONArray()
                for (j in 0 until mArr.length()) {
                    try {
                        matches += parseMatch(mArr.getJSONObject(j), league, leagueLogo)
                    } catch (_: Exception) {
                        // One bad match never kills the league.
                    }
                }
                if (matches.isNotEmpty()) leagues += LeagueGroup(league, leagueLogo, matches)
            } catch (_: Exception) {
                // One bad league never kills the day.
            }
        }
        return DayFeed(date, leagues)
    }

    private fun parseMatch(m: JSONObject, league: String, leagueLogo: String?): DayMatch {
        val kickoffRaw = m.optString("kickoff_tunis", "")
        val kickoffMillis = parseIsoMillis(kickoffRaw)
        val channels = mutableListOf<String>()
        val chArr = m.optJSONArray("channels")
        if (chArr != null) {
            for (i in 0 until chArr.length()) {
                chArr.optString(i, "").takeIf { it.isNotBlank() }?.let { channels += it }
            }
        }
        return DayMatch(
            id = m.optString("id", "${m.optString("home_team", "")}-${m.optString("away_team", "")}"),
            league = m.optString("league", league).ifBlank { league },
            leagueLogo = m.optString("league_logo", "").takeIf { it.isNotBlank() } ?: leagueLogo,
            homeTeam = m.optString("home_team", "?"),
            awayTeam = m.optString("away_team", "?"),
            homeLogo = m.optString("home_logo", "").takeIf { it.isNotBlank() },
            awayLogo = m.optString("away_logo", "").takeIf { it.isNotBlank() },
            kickoffMillis = kickoffMillis,
            kickoffText = if (kickoffMillis > 0) formatTunisTime(kickoffMillis) else "",
            stadium = m.optString("stadium", "").takeIf { it.isNotBlank() },
            channels = channels,
            status = m.optString("status", "scheduled").ifBlank { "scheduled" }
        )
    }

    /** "2026-11-23T14:45:00+01:00" (fractional seconds tolerated) -> millis. */
    internal fun parseIsoMillis(raw: String): Long {
        if (raw.isBlank()) return 0L
        // Drop fractional seconds: SimpleDateFormat handles at most millis.
        val cleaned = raw.replace(Regex("\\.\\d+"), "")
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mmXXX"
        )
        for (p in patterns) {
            try {
                val fmt = SimpleDateFormat(p, Locale.US)
                if (p.endsWith("ss") || p.endsWith("mmXXX")) fmt.timeZone = TUNIS_TZ
                return fmt.parse(cleaned)?.time ?: 0L
            } catch (_: Exception) {
                // Try the next pattern.
            }
        }
        return 0L
    }

    private fun formatTunisTime(millis: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            timeZone = TUNIS_TZ
        }.format(millis)
}
