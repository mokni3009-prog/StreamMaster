package com.streammaster.tv.data.repository

import com.streammaster.tv.data.db.MyPlaylistBouquet
import com.streammaster.tv.data.db.MyPlaylistItem
import com.streammaster.tv.data.db.StreamMasterDatabase
import com.streammaster.tv.data.model.Bouquet
import com.streammaster.tv.data.model.BouquetKind
import com.streammaster.tv.data.model.Channel
import com.streammaster.tv.data.model.ContentKind
import com.streammaster.tv.data.model.Film
import com.streammaster.tv.data.model.Series
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "My Playlist" — SPEC §4.62: the virtual playlist #11.
 *
 * A My Playlist item is a REFERENCE (copy), never a move: the original
 * stays in its source subscription (1–10) and playback always uses the
 * source subscription's stream URL, so an item stops working when its
 * source subscription expires or the item disappears from the source.
 *
 * CONTRACT — the real types, as landed by the data builder in
 * Entities.kt / Daos.kt (this file compiles against them and nothing
 * else). Row ids are Long (autoGenerate):
 *
 *   MyPlaylistBouquet(id: Long = 0, name: String,
 *       type: String ("LIVE" | "FILM" | "SERIES"), sortOrder: Int = 0)
 *   MyPlaylistItem(id: Long = 0, bouquetId: Long, sourcePlaylistId: String,
 *       itemType: String ("CHANNEL" | "FILM" | "SERIES"), itemId: String)
 *
 *   MyPlaylistBouquetDao: observeAll(), getAll(), getByType(type),
 *       getById(id: Long), upsert(item), update(item), deleteById(id: Long)
 *   MyPlaylistItemDao: observeByBouquet(bouquetId: Long),
 *       getByBouquet(bouquetId: Long),
 *       getByBouquetAndItem(bouquetId: Long, itemType: String, itemId: String),
 *       upsert(item), deleteById(id: Long), deleteByBouquet(bouquetId: Long)
 *
 * The coordinator still has to wire the two DAOs into
 * StreamMasterDatabase (abstract funs myPlaylistBouquetDao() and
 * myPlaylistItemDao(), entities + migration) — this implementation
 * reaches them through the database, so no AppModule change is needed.
 *
 * My Playlist is virtual: it is NOT a PlaylistEntity, it is NOT counted
 * in MAX_PLAYLISTS (10 real subscriptions) and it has no server, no
 * expiry date and no refresh button (§4.62).
 *
 * DI note: the implementation takes only StreamMasterDatabase (already
 * provided by AppModule). The ViewModel injects the concrete
 * implementation directly for the same reason. The interface below stays
 * the contract for tests / callers.
 */

/** Entity type string for a [BouquetKind] ("FILM", not "FILMS"). */
internal fun BouquetKind.toMyType(): String = when (this) {
    BouquetKind.LIVE -> "LIVE"
    BouquetKind.FILMS -> "FILM"
    BouquetKind.SERIES -> "SERIES"
}

/** A My Playlist bouquet as a §3 [Bouquet] (for the bouquet strips). */
fun MyPlaylistBouquet.toBouquet(): Bouquet = Bouquet(
    id = id.toString(),
    name = name,
    kind = when (type) {
        "LIVE" -> BouquetKind.LIVE
        "FILM" -> BouquetKind.FILMS
        "SERIES" -> BouquetKind.SERIES
        else -> BouquetKind.LIVE
    },
    order = sortOrder,
    hidden = false
)

interface MyPlaylistRepository {
    /** Bouquets of one kind, in creation order. */
    fun observeBouquets(kind: BouquetKind): Flow<List<MyPlaylistBouquet>>
    suspend fun getBouquets(kind: BouquetKind): List<MyPlaylistBouquet>

    /** Creates a bouquet (appended after the existing ones) and returns it. */
    suspend fun createBouquet(name: String, kind: BouquetKind): MyPlaylistBouquet
    suspend fun renameBouquet(id: Long, name: String)
    /** Deletes the bouquet AND all its items. */
    suspend fun deleteBouquet(id: Long)

    fun observeItems(bouquetId: Long): Flow<List<MyPlaylistItem>>
    fun observeAllItems(): Flow<List<MyPlaylistItem>>

    /**
     * Adds one reference. Returns false when the item is already in this
     * bouquet (no duplicate is created).
     */
    suspend fun addItem(
        bouquetId: Long,
        sourcePlaylistId: String,
        itemType: ContentKind,
        itemId: String
    ): Boolean

    /** Adds several references; returns how many were actually added. */
    suspend fun addItems(
        bouquetId: Long,
        sourcePlaylistId: String,
        itemType: ContentKind,
        itemIds: List<String>
    ): Int

    /** Deletes one item row by its My Playlist row id. */
    suspend fun removeItem(itemId: Long)

    /** Deletes the reference to one source item inside one bouquet. */
    suspend fun removeItemByRef(bouquetId: Long, itemType: ContentKind, itemId: String)

    /** Resolved content of one bouquet (null = every My Playlist bouquet of that kind). */
    fun observeChannels(bouquetId: Long?): Flow<List<Channel>>
    fun observeFilms(bouquetId: Long?): Flow<List<Film>>
    fun observeSeries(bouquetId: Long?): Flow<List<Series>>

    suspend fun getChannels(bouquetId: Long?): List<Channel>
    suspend fun getFilms(bouquetId: Long?): List<Film>
    suspend fun getSeries(bouquetId: Long?): List<Series>
}

/** Channel item type, exactly as §4.62 writes it ("CHANNEL" / "FILM" / "SERIES"). */
internal fun ContentKind.toMyItemType(): String = when (this) {
    ContentKind.LIVE -> "CHANNEL"
    ContentKind.FILM -> "FILM"
    ContentKind.SERIES -> "SERIES"
}

/** Reads accept both spellings for channels ("CHANNEL" per §4.62, "LIVE" per ContentKind). */
internal fun MyPlaylistItem.isKind(kind: ContentKind): Boolean = when (kind) {
    ContentKind.LIVE -> itemType == "CHANNEL" || itemType == "LIVE"
    ContentKind.FILM -> itemType == "FILM"
    ContentKind.SERIES -> itemType == "SERIES"
}

@Singleton
class MyPlaylistRepositoryImpl @Inject constructor(
    private val db: StreamMasterDatabase
) : MyPlaylistRepository {

    private val bouquetDao get() = db.myPlaylistBouquetDao()
    private val itemDao get() = db.myPlaylistItemDao()

    override fun observeBouquets(kind: BouquetKind): Flow<List<MyPlaylistBouquet>> {
        val type = kind.toMyType()
        return bouquetDao.observeAll().map { list -> list.filter { it.type == type } }
    }

    override suspend fun getBouquets(kind: BouquetKind): List<MyPlaylistBouquet> =
        bouquetDao.getByType(kind.toMyType())

    override suspend fun createBouquet(name: String, kind: BouquetKind): MyPlaylistBouquet {
        val type = kind.toMyType()
        val nextOrder = (bouquetDao.getByType(type).maxOfOrNull { it.sortOrder } ?: -1) + 1
        val bouquet = MyPlaylistBouquet(
            name = name.trim(),
            type = type,
            sortOrder = nextOrder
        )
        bouquetDao.upsert(bouquet)
        // upsert() returns Unit (no generated id), so read back the newest
        // bouquet of this type — it is the one just created (highest order).
        return bouquetDao.getByType(type).maxByOrNull { it.sortOrder }
            ?: bouquet
    }

    override suspend fun renameBouquet(id: Long, name: String) {
        val bouquet = bouquetDao.getById(id) ?: return
        bouquetDao.update(bouquet.copy(name = name.trim()))
    }

    override suspend fun deleteBouquet(id: Long) {
        itemDao.deleteByBouquet(id)
        bouquetDao.deleteById(id)
    }

    override fun observeItems(bouquetId: Long): Flow<List<MyPlaylistItem>> =
        itemDao.observeByBouquet(bouquetId)

    override fun observeAllItems(): Flow<List<MyPlaylistItem>> =
        bouquetDao.observeAll().flatMapLatest { bouquets ->
            if (bouquets.isEmpty()) flowOf(emptyList())
            else combine(bouquets.map { itemDao.observeByBouquet(it.id) }) { arrays ->
                arrays.flatMap { it.toList() }
            }
        }

    override suspend fun addItem(
        bouquetId: Long,
        sourcePlaylistId: String,
        itemType: ContentKind,
        itemId: String
    ): Boolean {
        val type = itemType.toMyItemType()
        if (itemDao.getByBouquetAndItem(bouquetId, type, itemId) != null) return false
        itemDao.upsert(
            MyPlaylistItem(
                bouquetId = bouquetId,
                sourcePlaylistId = sourcePlaylistId,
                itemType = type,
                itemId = itemId
            )
        )
        return true
    }

    override suspend fun addItems(
        bouquetId: Long,
        sourcePlaylistId: String,
        itemType: ContentKind,
        itemIds: List<String>
    ): Int {
        var added = 0
        for (itemId in itemIds) {
            if (addItem(bouquetId, sourcePlaylistId, itemType, itemId)) added++
        }
        return added
    }

    override suspend fun removeItem(itemId: Long) {
        itemDao.deleteById(itemId)
    }

    override suspend fun removeItemByRef(bouquetId: Long, itemType: ContentKind, itemId: String) {
        val row = itemDao.getByBouquetAndItem(bouquetId, itemType.toMyItemType(), itemId) ?: return
        itemDao.deleteById(row.id)
    }

    // ─── Resolution: a reference resolves against the SOURCE subscription's
    // tables (ChannelDao / FilmDao / SeriesDao). Items whose source item
    // disappeared (subscription expired / item removed) resolve to nothing
    // and simply stop showing — §4.62: playback uses the source subscription.

    private suspend fun resolveChannels(items: List<MyPlaylistItem>): List<Channel> {
        val channelDao = db.channelDao()
        return items.filter { it.isKind(ContentKind.LIVE) }
            .mapNotNull { channelDao.getById(it.itemId)?.toModel() }
    }

    private suspend fun resolveFilms(items: List<MyPlaylistItem>): List<Film> {
        val filmDao = db.filmDao()
        return items.filter { it.isKind(ContentKind.FILM) }
            .mapNotNull { filmDao.getById(it.itemId)?.toModel() }
    }

    private suspend fun resolveSeries(items: List<MyPlaylistItem>): List<Series> {
        val seriesDao = db.seriesDao()
        return items.filter { it.isKind(ContentKind.SERIES) }
            .mapNotNull { seriesDao.getById(it.itemId)?.toModel() }
    }

    private fun itemsFlow(bouquetId: Long?): Flow<List<MyPlaylistItem>> =
        if (bouquetId == null) observeAllItems() else itemDao.observeByBouquet(bouquetId)

    private suspend fun itemsList(bouquetId: Long?): List<MyPlaylistItem> =
        if (bouquetId == null) {
            bouquetDao.getAll().flatMap { itemDao.getByBouquet(it.id) }
        } else {
            itemDao.getByBouquet(bouquetId)
        }

    override fun observeChannels(bouquetId: Long?): Flow<List<Channel>> =
        itemsFlow(bouquetId).mapLatest { resolveChannels(it) }

    override fun observeFilms(bouquetId: Long?): Flow<List<Film>> =
        itemsFlow(bouquetId).mapLatest { resolveFilms(it) }

    override fun observeSeries(bouquetId: Long?): Flow<List<Series>> =
        itemsFlow(bouquetId).mapLatest { resolveSeries(it) }

    override suspend fun getChannels(bouquetId: Long?): List<Channel> =
        resolveChannels(itemsList(bouquetId))

    override suspend fun getFilms(bouquetId: Long?): List<Film> =
        resolveFilms(itemsList(bouquetId))

    override suspend fun getSeries(bouquetId: Long?): List<Series> =
        resolveSeries(itemsList(bouquetId))
}
