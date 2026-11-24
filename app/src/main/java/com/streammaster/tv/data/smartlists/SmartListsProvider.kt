package com.streammaster.tv.data.smartlists

import com.streammaster.tv.data.model.Channel
import com.streammaster.tv.data.model.Film
import com.streammaster.tv.data.model.Series
import com.streammaster.tv.data.util.SmartListsProvider as UtilProvider
import com.streammaster.tv.ui.viewmodel.SmartList
import java.util.Locale

/**
 * Smart lists (§4.51) — ViewModel-facing adapter.
 *
 * CONTRACT (imposed by SmartListsViewModel): an object in `data/smartlists/`
 * with exactly
 *   `fun getSmartLists(channels: List<Channel>, films: List<Film>,
 *                       series: List<Series>): List<SmartList>`
 * where SmartList is the model defined in `ui/viewmodel/SmartListsViewModel.kt`.
 * (Yes — this data-layer file imports a ui model; that inversion is the
 * ViewModel's stated contract, not a choice made here. If the coordinator
 * later moves SmartList to `data/model`, this file becomes a one-line
 * re-export of the util provider.)
 *
 * The generation itself lives in the pure core,
 * [com.streammaster.tv.data.util.SmartListsProvider]; this file only maps
 * its lists onto the ViewModel's SmartList shape and adds the channel
 * lists (top categories, ≥3 channels) that the core does not generate.
 */
object SmartListsProvider {

    fun getSmartLists(
        channels: List<Channel>,
        films: List<Film>,
        series: List<Series>
    ): List<SmartList> {
        val out = UtilProvider.getSmartLists(films, series).map { util ->
            SmartList(
                id = util.id,
                title = util.title,
                subtitle = when {
                    util.films.isNotEmpty() -> "${util.films.size} films"
                    util.series.isNotEmpty() -> "${util.series.size} séries"
                    else -> ""
                },
                films = util.films,
                series = util.series
            )
        }.toMutableList()

        channels.filter { it.category.isNotBlank() }
            .groupBy { it.category }
            .entries
            .sortedByDescending { it.value.size }
            .take(2)
            .forEach { (category, items) ->
                if (items.size >= 3) {
                    out += SmartList(
                        // LG-20: locale-independent id slug (see util provider).
                        id = "smart-channels-${category.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), "-")}",
                        title = "Chaînes $category",
                        subtitle = "${items.size} chaînes",
                        channels = items.sortedBy { it.name }.take(20)
                    )
                }
            }
        return out
    }
}
