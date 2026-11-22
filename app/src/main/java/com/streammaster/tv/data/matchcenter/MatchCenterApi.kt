package com.streammaster.tv.data.matchcenter

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/** One football match from the ESPN scoreboard. */
data class LiveMatch(
    val id: String,
    val leagueName: String,
    val homeTeam: String,
    val homeLogo: String?,
    val awayTeam: String,
    val awayLogo: String?,
    val homeScore: String?,
    val awayScore: String?,
    /** "pre" | "in" | "post". */
    val state: String,
    /** Displayed status: "67'" | "17:30" | "FT". */
    val statusText: String,
    val startMillis: Long,
    /** Goals scored so far (from the event's scoring-play details). */
    val goals: List<GoalEvent> = emptyList()
)

/**
 * One goal in a match, parsed from the ESPN scoreboard event's `details`
 * (scoring plays). `key` is stable across polls so the alert service can
 * tell a new goal from one it already announced.
 */
data class GoalEvent(
    val key: String,
    /** Minute as ESPN shows it, e.g. "67'". */
    val minute: String,
    /** Scorer's display name; "" when ESPN does not name one. */
    val scorer: String,
    /** Display name of the team that scored. */
    val scorerTeam: String
)

/**
 * Match Center (2026-11-22): live football scores from ESPN's public
 * scoreboard API — no key, no account. Eight leagues; one league failing
 * never kills the others. Anything unparsable is skipped, never thrown.
 */
@Singleton
class MatchCenterApi @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "MatchCenter"
        private val LEAGUES = listOf(
            "eng.1", "esp.1", "fra.1", "ita.1", "ger.1",
            "uefa.champions", "uefa.europa", "ned.1"
        )
    }

    suspend fun fetchToday(): List<LiveMatch> = withContext(Dispatchers.IO) {
        val out = mutableListOf<LiveMatch>()
        for (league in LEAGUES) {
            try {
                val request = Request.Builder()
                    .url("https://site.api.espn.com/apis/site/v2/sports/soccer/$league/scoreboard")
                    .header("Accept", "application/json")
                    .build()
                val body = okHttpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.string()
                } ?: continue
                out += parse(body)
            } catch (e: Exception) {
                Log.w(TAG, "scoreboard $league failed", e)
            }
        }
        // Live matches first, then by kickoff time.
        out.sortedWith(compareBy({ it.state != "in" }, { it.startMillis }))
    }

    private fun parse(json: String): List<LiveMatch> {
        val out = mutableListOf<LiveMatch>()
        val root = JSONObject(json)
        val leagueName = root.optJSONArray("leagues")
            ?.optJSONObject(0)?.optString("name", "")?.takeIf { it.isNotBlank() } ?: ""
        val events = root.optJSONArray("events") ?: return out
        for (i in 0 until events.length()) {
            try {
                val event = events.getJSONObject(i)
                val id = event.optString("id", "")
                val dateRaw = event.optString("date", "")
                val startMillis = parseDate(dateRaw)
                val status = event.optJSONObject("status")
                val type = status?.optJSONObject("type")
                val state = type?.optString("state", "pre") ?: "pre"
                val statusText = when (state) {
                    "in" -> status?.optString("displayClock", "")
                        ?.takeIf { it.isNotBlank() }
                        ?: type?.optString("shortDetail", "")?.takeIf { it.isNotBlank() }
                        ?: "En cours"
                    "post" -> "FT"
                    else -> {
                        if (startMillis > 0) {
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(startMillis)
                        } else ""
                    }
                }
                val competition = event.optJSONArray("competitions")?.optJSONObject(0)
                    ?: continue
                val competitors = competition.optJSONArray("competitors") ?: continue
                var home: JSONObject? = null
                var away: JSONObject? = null
                for (j in 0 until competitors.length()) {
                    val c = competitors.getJSONObject(j)
                    when (c.optString("homeAway")) {
                        "home" -> home = c
                        "away" -> away = c
                    }
                }
                if (home == null || away == null) continue
                val homeName = home.optJSONObject("team")?.optString("displayName", "?") ?: "?"
                val awayName = away.optJSONObject("team")?.optString("displayName", "?") ?: "?"
                val homeTeamId = home.optJSONObject("team")?.optString("id", "") ?: ""
                val awayTeamId = away.optJSONObject("team")?.optString("id", "") ?: ""
                out += LiveMatch(
                    id = id,
                    leagueName = leagueName,
                    homeTeam = homeName,
                    homeLogo = home.optJSONObject("team")?.optString("logo")?.takeIf { it.isNotBlank() },
                    awayTeam = awayName,
                    awayLogo = away.optJSONObject("team")?.optString("logo")?.takeIf { it.isNotBlank() },
                    homeScore = home.optString("score").takeIf { it.isNotBlank() },
                    awayScore = away.optString("score").takeIf { it.isNotBlank() },
                    state = state,
                    statusText = statusText,
                    startMillis = startMillis,
                    goals = parseGoals(
                        event = event,
                        eventId = id,
                        homeTeamId = homeTeamId,
                        awayTeamId = awayTeamId,
                        homeName = homeName,
                        awayName = awayName
                    )
                )
            } catch (_: Exception) {
                // One bad event never kills the league.
            }
        }
        return out
    }

    /**
     * Scoring plays from the event's `details` array. ESPN marks goals as
     * type text "Goal" (also "Goal - Penalty", "Goal - Own Goal"). A detail
     * without a clock or scorer is still a goal — the minute/scorer just
     * show as blank. Anything unparsable is skipped, never thrown.
     */
    private fun parseGoals(
        event: JSONObject,
        eventId: String,
        homeTeamId: String,
        awayTeamId: String,
        homeName: String,
        awayName: String
    ): List<GoalEvent> {
        val out = mutableListOf<GoalEvent>()
        val competition = event.optJSONArray("competitions")?.optJSONObject(0) ?: return out
        val details = competition.optJSONArray("details") ?: return out
        for (i in 0 until details.length()) {
            try {
                val detail = details.getJSONObject(i)
                val typeText = detail.optJSONObject("type")?.optString("text", "") ?: ""
                if (!typeText.contains("Goal", ignoreCase = true)) continue
                val minute = detail.optJSONObject("clock")?.optString("displayValue", "") ?: ""
                val scorer = detail.optJSONArray("athletesInvolved")
                    ?.optJSONObject(0)?.optString("displayName", "") ?: ""
                val scoreValue = detail.optString("scoreValue", "")
                val teamId = detail.optJSONObject("team")?.optString("id", "") ?: ""
                val scorerTeam = when (teamId) {
                    homeTeamId -> homeName
                    awayTeamId -> awayName
                    else -> ""
                }
                val key = "$eventId|$minute|$scorer|$scoreValue"
                out += GoalEvent(key = key, minute = minute, scorer = scorer, scorerTeam = scorerTeam)
            } catch (_: Exception) {
                // One bad detail never kills the match.
            }
        }
        return out
    }

    private fun parseDate(raw: String): Long {
        if (raw.isBlank()) return 0L
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(raw)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}
