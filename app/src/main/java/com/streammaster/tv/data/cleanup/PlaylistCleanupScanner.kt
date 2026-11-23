package com.streammaster.tv.data.cleanup

import com.streammaster.tv.data.db.StreamMasterDatabase
import com.streammaster.tv.data.util.TitleNormalizer
import com.streammaster.tv.ui.viewmodel.CleanupKind
import com.streammaster.tv.ui.viewmodel.CleanupReport
import com.streammaster.tv.ui.viewmodel.DeadItem
import com.streammaster.tv.ui.viewmodel.DuplicateGroup

/**
 * Playlist cleanup (§4.36.6) — ViewModel-facing scanner.
 *
 * CONTRACT (imposed by CleanupViewModel): an object in `data/cleanup/`
 * with exactly
 *   `suspend fun scan(): CleanupReport`
 * where CleanupReport / DuplicateGroup / DeadItem / CleanupKind are the
 * models defined in `ui/viewmodel/CleanupViewModel.kt`. The scanner
 * detects duplicate and dead channels, films and series of the ACTIVE
 * playlist (same normalized-title rule as the smart failover).
 *
 * DATA SOURCE: a no-arg object cannot be Hilt-injected, so the database
 * is handed in once via [install] — called from
 * ContentRepositoryImpl.init (a @Singleton created when the user reaches
 * Home, always before the cleanup screen can open). Until installed,
 * [scan] returns an empty report instead of crashing.
 *
 * The pure, testable detection for channels used to live in
 * `data/util/PlaylistCleanupScanner.kt` (deleted 2026-11-23: unreferenced
 * duplicate of this scanner); this file is now the only scanner.
 * Deletion itself is the ViewModel's job (ChannelDao.deleteById,
 * FilmDao.deleteById, SeriesDao.deleteById).
 */
object PlaylistCleanupScanner {

    @Volatile
    private var database: StreamMasterDatabase? = null

    fun install(db: StreamMasterDatabase) {
        database = db
    }

    suspend fun scan(): CleanupReport {
        val db = database ?: return CleanupReport()
        val pid = db.playlistDao().getActive()?.id
            ?: db.playlistDao().getAll().firstOrNull()?.id
            ?: return CleanupReport()
        val duplicates = mutableListOf<DuplicateGroup>()
        val dead = mutableListOf<DeadItem>()

        // Channels.
        val channels = db.channelDao().getAll(pid)
        channels.groupBy { TitleNormalizer.normalize(it.customName ?: it.name) }
            .filter { (name, group) -> name.isNotBlank() && group.size > 1 }
            .forEach { (_, group) ->
                duplicates += DuplicateGroup(
                    kind = CleanupKind.CHANNEL,
                    title = group.first().customName ?: group.first().name,
                    ids = group.map { it.id }
                )
            }
        channels.forEach { c ->
            val title = c.customName ?: c.name
            when {
                c.streamUrl.isBlank() -> dead += DeadItem(CleanupKind.CHANNEL, c.id, title, "lien vide")
                c.isBroken -> dead += DeadItem(CleanupKind.CHANNEL, c.id, title, "marquée en panne")
            }
        }

        // Films.
        val films = db.filmDao().getAll(pid)
        films.groupBy { TitleNormalizer.normalize(it.customName ?: it.title) }
            .filter { (name, group) -> name.isNotBlank() && group.size > 1 }
            .forEach { (_, group) ->
                duplicates += DuplicateGroup(
                    kind = CleanupKind.FILM,
                    title = group.first().customName ?: group.first().title,
                    ids = group.map { it.id }
                )
            }
        films.forEach { f ->
            val title = f.customName ?: f.title
            when {
                f.streamUrl.isBlank() -> dead += DeadItem(CleanupKind.FILM, f.id, title, "lien vide")
                f.isBroken -> dead += DeadItem(CleanupKind.FILM, f.id, title, "marqué en panne")
            }
        }

        // Series (no stream URL on the series row: only the broken flag).
        val series = db.seriesDao().getAll(pid)
        series.groupBy { TitleNormalizer.normalize(it.customName ?: it.title) }
            .filter { (name, group) -> name.isNotBlank() && group.size > 1 }
            .forEach { (_, group) ->
                duplicates += DuplicateGroup(
                    kind = CleanupKind.SERIES,
                    title = group.first().customName ?: group.first().title,
                    ids = group.map { it.id }
                )
            }
        series.filter { it.isBroken }.forEach { s ->
            dead += DeadItem(CleanupKind.SERIES, s.id, s.customName ?: s.title, "marquée en panne")
        }

        return CleanupReport(duplicates = duplicates, dead = dead)
    }
}
