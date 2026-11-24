package com.streammaster.tv.data.repository

import com.streammaster.tv.data.db.StreamMasterDatabase
import com.streammaster.tv.data.model.ContentKind
import com.streammaster.tv.data.model.Download
import com.streammaster.tv.data.model.DownloadStatus
import com.streammaster.tv.data.model.ScheduledRecording
import com.streammaster.tv.data.model.SeriesRecordingRule
import com.streammaster.tv.data.model.UserProfile
import com.streammaster.tv.data.model.WatchLaterItem
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Room-backed [WatchLaterRepository]. */
@Singleton
class WatchLaterRepositoryImpl @Inject constructor(
    private val db: StreamMasterDatabase
) : WatchLaterRepository {

    override fun observeWatchLater(profileId: String): Flow<List<WatchLaterItem>> =
        db.watchLaterDao().observeByProfile(profileId).map { list -> list.map { it.toModel() } }

    override suspend fun isSaved(kind: ContentKind, refId: String, profileId: String): Boolean =
        db.watchLaterDao().get(kind.name, refId, profileId) != null

    override suspend fun toggle(kind: ContentKind, refId: String, title: String, profileId: String) {
        // LG-15: read-then-write inside one transaction — two concurrent
        // toggles used to both read "absent" and insert twice.
        db.withTransaction {
            val dao = db.watchLaterDao()
            if (dao.get(kind.name, refId, profileId) != null) {
                dao.delete(kind.name, refId, profileId)
            } else {
                dao.upsert(
                    WatchLaterItem(kind, refId, title, System.currentTimeMillis(), profileId).toEntity()
                )
            }
        }
    }

    override suspend fun remove(kind: ContentKind, refId: String, profileId: String) {
        db.watchLaterDao().delete(kind.name, refId, profileId)
    }
}

/** Room-backed [ProfileRepository]. */
@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val db: StreamMasterDatabase
) : ProfileRepository {

    override fun observeProfiles(): Flow<List<UserProfile>> =
        db.profileDao().observeAll().map { list -> list.map { it.toModel() } }

    override suspend fun getProfile(id: String): UserProfile? =
        db.profileDao().getById(id)?.toModel()

    override suspend fun saveProfile(profile: UserProfile) {
        db.profileDao().upsert(profile.toEntity())
    }

    override suspend fun deleteProfile(id: String) {
        // LG-15: deleting a profile used to leave its history and
        // watch-later rows orphaned — remove them in the same transaction.
        db.withTransaction {
            db.profileDao().deleteById(id)
            db.historyDao().clearByProfile(id)
            db.watchLaterDao().deleteByProfile(id)
        }
    }
}

/** Room-backed [ScheduledRecordingRepository]. */
@Singleton
class ScheduledRecordingRepositoryImpl @Inject constructor(
    private val db: StreamMasterDatabase
) : ScheduledRecordingRepository {

    override fun observeScheduled(): Flow<List<ScheduledRecording>> =
        db.scheduledRecordingDao().observeScheduled().map { list -> list.map { it.toModel() } }

    override suspend fun getScheduled(id: String): ScheduledRecording? =
        db.scheduledRecordingDao().getById(id)?.toModel()

    override suspend fun save(scheduled: ScheduledRecording) {
        db.scheduledRecordingDao().upsert(scheduled.toEntity())
    }

    override suspend fun delete(id: String) {
        db.scheduledRecordingDao().deleteById(id)
    }
}

/** Room-backed [SeriesRecordingRepository]. */
@Singleton
class SeriesRecordingRepositoryImpl @Inject constructor(
    private val db: StreamMasterDatabase
) : SeriesRecordingRepository {

    override fun observeRules(): Flow<List<SeriesRecordingRule>> =
        db.seriesRecordingRuleDao().observeAll().map { list -> list.map { it.toModel() } }

    override suspend fun getRule(channelId: String, normalizedTitle: String): SeriesRecordingRule? =
        db.seriesRecordingRuleDao().getByChannelAndTitle(channelId, normalizedTitle)?.toModel()

    override suspend fun save(rule: SeriesRecordingRule) {
        db.seriesRecordingRuleDao().upsert(rule.toEntity())
    }

    override suspend fun delete(id: String) {
        db.seriesRecordingRuleDao().deleteById(id)
    }
}

/** Room-backed [DownloadRepository]. */
@Singleton
class DownloadRepositoryImpl @Inject constructor(
    private val db: StreamMasterDatabase
) : DownloadRepository {

    override fun observeDownloads(): Flow<List<Download>> =
        db.downloadDao().observeAll().map { list -> list.map { it.toModel() } }

    override suspend fun getDownload(id: String): Download? =
        db.downloadDao().getById(id)?.toModel()

    override suspend fun getByRef(kind: ContentKind, refId: String): Download? =
        db.downloadDao().getByRef(kind.name, refId)?.toModel()

    override suspend fun getNextQueued(): Download? =
        db.downloadDao().getNextQueued()?.toModel()

    override suspend fun save(download: Download) {
        db.downloadDao().upsert(download.toEntity())
    }

    override suspend fun updateProgress(id: String, downloadedBytes: Long, totalBytes: Long, status: DownloadStatus) {
        db.downloadDao().updateProgress(id, downloadedBytes, totalBytes, status.name)
    }

    override suspend fun delete(id: String) {
        db.downloadDao().deleteById(id)
    }
}
