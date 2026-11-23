package com.streammaster.tv.data.util

import com.streammaster.tv.data.model.Film
import com.streammaster.tv.data.model.Series
import java.util.Calendar

/**
 * Smart lists (§4.51): groups generated automatically from the catalog —
 * by genre, year and recency — with no manual ordering. LOGIC ONLY: the
 * UI builder renders the returned lists on Accueil.
 *
 * Titles are French (the app's default UI language).
 *
 * Limits of the current catalog (documented, not worked around):
 * - Film.year is 0 for most Xtream VOD rows, so "Films récents" only
 *   lists titles whose year is actually known.
 * - SeriesEntity carries no year/added date, so "Séries récentes" is
 *   the best-rated series, not a true recency order — true recency
 *   needs an added-date column on the series table.
 */
data class SmartList(
    val id: String,
    val title: String,
    val films: List<Film> = emptyList(),
    val series: List<Series> = emptyList()
)

object SmartListsProvider {

    private const val MAX_ITEMS = 20
    private const val TOP_GENRES = 3

    fun getSmartLists(films: List<Film>, series: List<Series>): List<SmartList> {
        val out = mutableListOf<SmartList>()
        val year = Calendar.getInstance().get(Calendar.YEAR)

        // "Films Action 2026" — one list per top film genre, best-rated first.
        films.filter { it.genre.isNotBlank() }
            .groupBy { it.genre }
            .entries
            .sortedByDescending { it.value.size }
            .take(TOP_GENRES)
            .forEach { (genre, items) ->
                if (items.size >= 2) {
                    out += SmartList(
                        id = "smart-films-genre-${genre.lowercase().replace(" ", "-")}",
                        title = "Films $genre $year",
                        films = items.sortedByDescending { it.rating }.take(MAX_ITEMS)
                    )
                }
            }

        // "Films récents" — known year within the last two years.
        val recentFilms = films
            .filter { it.year in (year - 1)..year }
            .sortedByDescending { it.year }
            .take(MAX_ITEMS)
        if (recentFilms.isNotEmpty()) {
            out += SmartList(
                id = "smart-films-recent",
                title = "Films récents",
                films = recentFilms
            )
        }

        // "Films les mieux notés".
        val topFilms = films.filter { it.rating >= 7.0 }
            .sortedByDescending { it.rating }
            .take(MAX_ITEMS)
        if (topFilms.isNotEmpty()) {
            out += SmartList(
                id = "smart-films-top",
                title = "Films les mieux notés",
                films = topFilms
            )
        }

        // "Séries Action" — one list per top series genre, best-rated first.
        series.filter { it.genre.isNotBlank() }
            .groupBy { it.genre }
            .entries
            .sortedByDescending { it.value.size }
            .take(TOP_GENRES)
            .forEach { (genre, items) ->
                if (items.size >= 2) {
                    out += SmartList(
                        id = "smart-series-genre-${genre.lowercase().replace(" ", "-")}",
                        title = "Séries $genre",
                        series = items.sortedByDescending { it.rating }.take(MAX_ITEMS)
                    )
                }
            }

        // "Séries récentes" — best-rated series (no year/added date on
        // SeriesEntity, so this is a rating order, not a true recency one).
        val topSeries = series.sortedByDescending { it.rating }.take(MAX_ITEMS)
        if (topSeries.isNotEmpty()) {
            out += SmartList(
                id = "smart-series-recent",
                title = "Séries récentes",
                series = topSeries
            )
        }

        return out
    }
}
