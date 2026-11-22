package com.streammaster.tv.data.repository

import com.streammaster.tv.data.db.StreamMasterDatabase
import com.streammaster.tv.data.model.Recording
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Room-backed [RecordingRepository] (DVR, method 2: instant only). */
@Singleton
class RecordingRepositoryImpl @Inject constructor(
    private val db: StreamMasterDatabase
) : RecordingRepository {

    override fun observeRecordings(): Flow<List<Recording>> =
        db.recordingDao().observeAll().map { list -> list.map { it.toModel() } }

    override suspend fun getRecording(id: String): Recording? =
        db.recordingDao().getById(id)?.toModel()

    override suspend fun saveRecording(recording: Recording) {
        db.recordingDao().upsert(recording.toEntity())
    }

    override suspend fun deleteRecording(id: String) {
        db.recordingDao().deleteById(id)
    }
}
