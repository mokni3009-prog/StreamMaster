package com.streammaster.tv.data.demo

import com.streammaster.tv.data.model.Bouquet
import com.streammaster.tv.data.model.BouquetKind
import com.streammaster.tv.data.model.Channel
import com.streammaster.tv.data.model.EpgProgram
import com.streammaster.tv.data.model.Episode
import com.streammaster.tv.data.model.Film
import com.streammaster.tv.data.model.Playlist
import com.streammaster.tv.data.model.PlaylistType
import com.streammaster.tv.data.model.Series
import java.util.Calendar

/**
 * EXACT demo data from the approved interactive prototype (stream-master.html).
 * Same ids, names, categories, now/next, ratings, years, fav flags and broken
 * flags (c6, m7, s6 broken). Demo stream URLs are public test HLS streams.
 */
// TODO: replace with real server URLs
private const val DEMO_STREAM_1 = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
private const val DEMO_STREAM_2 = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8"
private const val DEMO_STREAM_3 = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8"

object DemoData {

    const val DEMO_PLAYLIST_ID = "demo"
    const val DEMO_PLAYLIST_NAME = "Démo"

    val demoPlaylist = Playlist(
        id = DEMO_PLAYLIST_ID,
        name = DEMO_PLAYLIST_NAME,
        type = PlaylistType.XTREAM,
        serverUrl = "",
        username = "",
        password = "",
        portalUrl = "",
        mac = "",
        m3uUrl = "",
        isActive = true,
        expiryDate = "31/12/2026",
        connections = 1,
        lastUpdate = "19/11/2026",
        status = "Actif"
    )

    val bouquets: List<Bouquet> = listOf(
        Bouquet("b-live-cinema", "Cinéma", BouquetKind.LIVE, 0, false),
        Bouquet("b-live-sport", "Sport", BouquetKind.LIVE, 1, false),
        Bouquet("b-live-info", "Information", BouquetKind.LIVE, 2, false),
        Bouquet("b-live-doc", "Documentaires", BouquetKind.LIVE, 3, false),
        Bouquet("b-live-famille", "Famille", BouquetKind.LIVE, 4, false),
        Bouquet("b-films", "Films", BouquetKind.FILMS, 0, false),
        Bouquet("b-series", "Séries", BouquetKind.SERIES, 0, false)
    )

    private fun bouquetIdForCategory(category: String): String = when (category) {
        "Cinéma" -> "b-live-cinema"
        "Sport" -> "b-live-sport"
        "Information" -> "b-live-info"
        "Documentaires" -> "b-live-doc"
        "Famille" -> "b-live-famille"
        else -> "b-live-cinema"
    }

    val channels: List<Channel> = listOf(
        Channel(
            id = "c1", num = "01", name = "Cinéma Premium 4K", code = "CP", category = "Cinéma",
            quality = "4K", nowTitle = "Le Souffle du Désert", nowTime = "20:30 – 22:45",
            progress = 56, hue = 44,
            description = "Un voyage épique au cœur des dunes dorées, diffusé en Ultra Haute Définition avec son Dolby Digital 5.1.",
            next = listOf("22:45" to "La Nuit des Rois", "00:30" to "Horizon Secret"),
            isFavorite = true, isBroken = false, streamUrl = DEMO_STREAM_1, epgId = "c1",
            customNum = null, hidden = false, customName = null, bouquetId = bouquetIdForCategory("Cinéma")
        ),
        Channel(
            id = "c2", num = "02", name = "Sport 1 Direct UHD", code = "S1", category = "Sport",
            quality = "4K", nowTitle = "Ligue des Champions — Quart de finale", nowTime = "20:45 – 22:55",
            progress = 38, hue = 40,
            description = "Le grand match en direct et en intégralité, avec commentaires en français et statistiques en temps réel.",
            next = listOf("22:55" to "Débriefing Champions", "00:00" to "Magazine Auto"),
            isFavorite = true, isBroken = false, streamUrl = DEMO_STREAM_2, epgId = "c2",
            customNum = null, hidden = false, customName = null, bouquetId = bouquetIdForCategory("Sport")
        ),
        Channel(
            id = "c3", num = "03", name = "Actualités 24 HD", code = "A24", category = "Information",
            quality = "FHD", nowTitle = "Le Grand Journal du Soir", nowTime = "20:00 – 21:30",
            progress = 74, hue = 46,
            description = "Toute l’actualité internationale et nationale décryptée en direct par nos éditorialistes.",
            next = listOf("21:30" to "Débat Économique", "22:30" to "Édition de la Nuit"),
            isFavorite = false, isBroken = false, streamUrl = DEMO_STREAM_3, epgId = "c3",
            customNum = null, hidden = false, customName = null, bouquetId = bouquetIdForCategory("Information")
        ),
        Channel(
            id = "c4", num = "04", name = "Découverte & Nature", code = "DN", category = "Documentaires",
            quality = "4K", nowTitle = "Les Trésors Cachés des Pharaons", nowTime = "20:15 – 21:50",
            progress = 62, hue = 48,
            description = "Exploration inédite des tombeaux royaux et des secrets de l’Égypte ancienne, en 4K HDR.",
            next = listOf("21:50" to "Planète Océan", "23:20" to "Les Secrets du Cosmos"),
            isFavorite = false, isBroken = false, streamUrl = DEMO_STREAM_1, epgId = "c4",
            customNum = null, hidden = false, customName = null, bouquetId = bouquetIdForCategory("Documentaires")
        ),
        Channel(
            id = "c5", num = "05", name = "Séries Max Gold", code = "SM", category = "Cinéma",
            quality = "FHD", nowTitle = "Dynastie Impériale — S02 E06", nowTime = "20:40 – 21:35",
            progress = 41, hue = 42,
            description = "Soirée spéciale série dramatique : intrigues de palais et jeux de pouvoir.",
            next = listOf("21:35" to "Dynastie — S02 E07", "22:30" to "Alpha Zéro"),
            isFavorite = false, isBroken = false, streamUrl = DEMO_STREAM_2, epgId = "c5",
            customNum = null, hidden = false, customName = null, bouquetId = bouquetIdForCategory("Cinéma")
        ),
        Channel(
            id = "c6", num = "06", name = "Sport 2 Arena", code = "S2", category = "Sport",
            quality = "FHD", nowTitle = "Grand Prix Formule 1 — Essais nocturnes", nowTime = "20:00 – 22:00",
            progress = 68, hue = 36,
            description = "Séance nocturne en direct du circuit international, avec télémétrie embarquée.",
            next = listOf("22:00" to "Paddock Club", "23:00" to "Boxe Championship"),
            isFavorite = false, isBroken = true, streamUrl = DEMO_STREAM_3, epgId = "c6",
            customNum = null, hidden = false, customName = null, bouquetId = bouquetIdForCategory("Sport")
        ),
        Channel(
            id = "c7", num = "07", name = "Jeunesse & Famille", code = "JF", category = "Famille",
            quality = "FHD", nowTitle = "Les Aventuriers de l’Or Perdu", nowTime = "20:10 – 21:40",
            progress = 49, hue = 50,
            description = "Film d’animation familial : une chasse au trésor pleine d’humour pour petits et grands.",
            next = listOf("21:40" to "Contes Dorés", "22:30" to "Nuit Magique"),
            isFavorite = false, isBroken = false, streamUrl = DEMO_STREAM_1, epgId = "c7",
            customNum = null, hidden = false, customName = null, bouquetId = bouquetIdForCategory("Famille")
        )
    )

    val films: List<Film> = listOf(
        Film("m1", "Le Dernier Royaume", 2025, 8.6, "4K", 43, "Aventure", 148, true, 68, "44 min restantes",
            "Au cœur d’un désert légendaire, une jeune archéologue découvre la carte d’un royaume oublié. Traquée par des mercenaires, elle devra percer les secrets d’une civilisation disparue avant le lever du dernier soleil d’or.",
            false, DEMO_STREAM_1, null),
        Film("m2", "Nuit Dorée à Paris", 2024, 8.2, "4K", 39, "Drame", 122, true, 35, "1 h 18 min restantes",
            "Une nuit, deux inconnus se croisent sous les lumières de Paris. Entre confidences et rendez-vous manqués, ils découvrent que le hasard n’existe peut-être pas.",
            false, DEMO_STREAM_2, null),
        Film("m3", "Horizon Perdu", 2025, 8.9, "4K", 46, "Science-Fiction", 136, false, 82, "21 min restantes",
            "En 2140, la dernière station orbitale reçoit un signal impossible. Une équipe part explorer la frontière du système solaire et découvre que l’humanité n’est pas seule.",
            false, DEMO_STREAM_3, null),
        Film("m4", "Opération Phénix", 2023, 7.9, "FHD", 36, "Action", 118, false, 0, "",
            "Un ancien agent reconverti est rappelé pour une mission suicide : infiltrer un réseau international avant une attaque imminente.",
            false, DEMO_STREAM_1, null),
        Film("m5", "Les Sables du Temps", 2024, 8.4, "4K", 48, "Aventure", 155, false, 0, "",
            "Trois générations de nomades traversent le désert pour retrouver un puits mythique. Une fresque humaine sur la mémoire, l’honneur et la survie.",
            false, DEMO_STREAM_2, null),
        Film("m6", "L'Empire d'Ambre", 2025, 8.7, "4K", 41, "Fantastique", 142, false, 0, "",
            "Dans un empire où l’ambre conserve les souvenirs, une héritière découvre que sa famille a effacé un siècle entier de l’histoire du royaume.",
            false, DEMO_STREAM_3, null),
        Film("m7", "Mirage Solaire", 2023, 7.8, "FHD", 44, "Thriller", 111, false, 0, "",
            "Un convoi humanitaire disparaît dans le désert. Une enquêtrice découvre qu’un mirage peut cacher bien plus qu’une illusion.",
            true, DEMO_STREAM_1, null),
        Film("m8", "Code Impérial", 2024, 8.1, "4K", 38, "Espionnage", 129, false, 0, "",
            "Un cryptographe déchiffre par erreur un message capable de renverser un gouvernement. Commence alors une course-poursuite à travers trois continents.",
            false, DEMO_STREAM_2, null)
    )

    val series: List<Series> = listOf(
        Series("s1", "Dynastie d'Or", 3, 9.0, "4K", 45, "Drame historique", true, 54, "S02 E05 • 26 min restantes",
            "Le destin d’une famille royale déchirée entre l’or du désert et les intrigues de palais. Chaque épisode resserre l’étau autour du trône, jusqu’au point de rupture.",
            false, null),
        Series("s2", "Alpha Zéro", 2, 8.5, "FHD", 40, "Action", true, 76, "S01 E08 • 14 min restantes",
            "Une unité d’élite formée pour les missions impossibles. Mais quand l’un des leurs disparaît, la loyauté devient la première menace.",
            false, null),
        Series("s3", "Les Oubliés", 4, 8.8, "4K", 47, "Mystère", false, 0, "",
            "Dans un village rayé des cartes, chaque habitant a oublié son passé. Une journaliste décide de reconstituer la mémoire collective, au risque de la sienne.",
            false, null),
        Series("s4", "La Citadelle", 2, 8.3, "4K", 37, "Fantastique", false, 0, "",
            "La dernière forteresse de l’humanité résiste depuis mille ans. Mais cette année, une fissure apparaît dans le mur que nul ne devait franchir.",
            false, null),
        Series("s5", "Chronique Royale", 5, 8.9, "4K", 42, "Historique", false, 0, "",
            "Cinq siècles d’une lignée maudite, racontés à travers les yeux de la dernière archiviste du royaume.",
            false, null),
        Series("s6", "Sahara Secret", 1, 8.1, "FHD", 49, "Documentaire", false, 0, "",
            "Un voyage filmé au plus près des dunes, à la rencontre des peuples du désert et de leurs traditions millénaires.",
            true, null),
        Series("s7", "Le Cercle Doré", 3, 8.4, "4K", 39, "Thriller", false, 0, "",
            "Sept inconnus sont convoqués dans un manoir. Un seul peut repartir. Le Cercle Doré décide qui mérite de survivre.",
            false, null),
        Series("s8", "Mirage Noir", 2, 8.0, "FHD", 44, "Policier", false, 0, "",
            "Une inspectrice de nuit enquête sur des disparitions dans une ville où le soleil ne se couche jamais vraiment.",
            false, null)
    )

    /** EP_TITLES from the prototype, in order. */
    val epTitles: List<String> = listOf(
        "Le Serment", "Les Ombres du Passé", "La Trahison", "Le Trône d’Or", "Nuit de Cendres",
        "L’Héritier", "Sables Mouvants", "La Dernière Frontière", "Le Pacte Secret", "Révélations",
        "Le Choix", "Le Retour du Roi", "Les Chaînes Brisées", "L’Aube Nouvelle",
        "Le Prix du Pouvoir", "Dernier Souffle"
    )

    /** Same hash as the prototype's hashStr (unsigned 32-bit). */
    fun hashStr(s: String): Long {
        var h = 0L
        for (c in s) h = ((h * 31 + c.code) and 0xFFFFFFFFL)
        return h
    }

    /**
     * Episodes for a series/season, mirroring the prototype's getEpisodes logic:
     * 6–8 episodes per season, deterministic titles/durations from the series id.
     * Season is 1-based here (prototype used 0-based seasonIdx internally).
     */
    fun episodesFor(series: Series, season: Int): List<Episode> {
        val seasonIdx = season - 1
        val count = 6 + (hashStr(series.id + seasonIdx) % 3).toInt()
        val start = ((hashStr(series.id) + seasonIdx * 5) % epTitles.size).toInt()
        return (0 until count).map { i ->
            val code = "S" + season.toString().padStart(2, '0') + "E" + (i + 1).toString().padStart(2, '0')
            Episode(
                seriesId = series.id,
                season = season,
                number = i + 1,
                title = epTitles[(start + i) % epTitles.size],
                durationMin = 42 + (hashStr(series.id + i + seasonIdx) % 17).toInt(),
                code = code,
                streamUrl = episodeStreamUrl(code)
            )
        }
    }

    /** All episodes for all seasons of a series. */
    fun allEpisodes(series: Series): List<Episode> =
        (1..series.seasons).flatMap { episodesFor(series, it) }

    /**
     * Demo stream URL for a series episode code (S01E01): deterministic
     * rotation over the same public demo HLS pool the demo films/channels
     * use. Real-server episode URLs are resolved elsewhere (see
     * ContentRepositoryImpl.getStreamUrls).
     */
    fun episodeStreamUrl(code: String): String = when ((hashStr(code) % 3).toInt()) {
        0 -> DEMO_STREAM_1
        1 -> DEMO_STREAM_2
        else -> DEMO_STREAM_3
    }

    /**
     * Demo EPG derived from each channel's now/next: the "now" program runs over
     * its nowTime window today, followed by the two "next" programs. Times that
     * roll past midnight are placed on the next day.
     */
    fun epgPrograms(): List<EpgProgram> {
        val out = mutableListOf<EpgProgram>()
        val dayStart = startOfTodayMillis()
        fun millisOf(hhmm: String, dayOffset: Int): Long? {
            val bits = hhmm.split(":")
            if (bits.size != 2) return null
            val h = bits[0].toIntOrNull() ?: return null
            val m = bits[1].toIntOrNull() ?: return null
            return dayStart + dayOffset * 86_400_000L + (h * 60L + m) * 60_000L
        }
        for (channel in channels) {
            val parts = channel.nowTime.split("–", "-").map { it.trim() }
            if (parts.size < 2) continue
            val start = millisOf(parts[0], 0) ?: continue
            var end = millisOf(parts[1], 0) ?: continue
            if (end <= start) end += 86_400_000L
            out += EpgProgram(channel.id, channel.nowTitle, start, end, channel.description)
            var prevEnd = end
            var dayOffset = if (end > dayStart + 86_400_000L) 1 else 0
            for ((time, title) in channel.next) {
                val s = millisOf(time, dayOffset) ?: continue
                // Next programs: 1h slots in demo (end unknown).
                // If the computed start is before the previous end, push it to the next day.
                val adjStart = if (s < prevEnd) s + 86_400_000L else s
                val adjEnd = adjStart + 60 * 60_000L
                out += EpgProgram(channel.id, title, adjStart, adjEnd, "")
                prevEnd = adjEnd
                if (adjStart >= dayStart + 86_400_000L) dayOffset = 1
            }
        }
        return out
    }

    private fun startOfTodayMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
