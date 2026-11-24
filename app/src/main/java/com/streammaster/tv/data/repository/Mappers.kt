package com.streammaster.tv.data.repository

import com.streammaster.tv.data.db.BouquetEntity
import com.streammaster.tv.data.db.ChannelEntity
import com.streammaster.tv.data.db.Converters
import com.streammaster.tv.data.db.DownloadEntity
import com.streammaster.tv.data.db.EpgEntity
import com.streammaster.tv.data.db.EpisodeEntity
import com.streammaster.tv.data.db.FilmEntity
import com.streammaster.tv.data.db.HistoryEntity
import com.streammaster.tv.data.db.PlaylistEntity
import com.streammaster.tv.data.db.ProfileEntity
import com.streammaster.tv.data.db.RecordingEntity
import com.streammaster.tv.data.db.ReminderEntity
import com.streammaster.tv.data.db.ScheduledRecordingEntity
import com.streammaster.tv.data.db.SeriesEntity
import com.streammaster.tv.data.db.SeriesRecordingRuleEntity
import com.streammaster.tv.data.db.WatchLaterEntity
import com.streammaster.tv.data.model.Bouquet
import com.streammaster.tv.data.model.BouquetKind
import com.streammaster.tv.data.model.Channel
import com.streammaster.tv.data.model.ContentKind
import com.streammaster.tv.data.model.Download
import com.streammaster.tv.data.model.DownloadStatus
import com.streammaster.tv.data.model.EpgProgram
import com.streammaster.tv.data.model.Episode
import com.streammaster.tv.data.model.Film
import com.streammaster.tv.data.model.HistoryEntry
import com.streammaster.tv.data.model.Playlist
import com.streammaster.tv.data.model.PlaylistType
import com.streammaster.tv.data.model.Recording
import com.streammaster.tv.data.model.RecordingStatus
import com.streammaster.tv.data.model.Reminder
import com.streammaster.tv.data.model.ScheduledRecording
import com.streammaster.tv.data.model.SeriesRecordingRule
import com.streammaster.tv.data.model.UserProfile
import com.streammaster.tv.data.model.WatchLaterItem
import com.streammaster.tv.data.model.Series

/** Entity <-> §3 model mappers. Customizations (hidden/customName/customNum/order) live on the entities. */

fun ChannelEntity.toModel(): Channel = Channel(
    id = id, num = num, name = customName ?: name, code = code, category = category,
    quality = quality, nowTitle = nowTitle, nowTime = nowTime, progress = progress, hue = hue,
    description = description, next = Converters.decodePairs(nextEncoded),
    isFavorite = isFavorite, isBroken = isBroken, streamUrl = streamUrl, epgId = epgId,
    logoUrl = logoUrl,
    customNum = customNum, hidden = hidden, customName = customName, bouquetId = bouquetId
)

fun Channel.toEntity(playlistId: String, orderIndex: Int, backupUrls: List<String> = emptyList()): ChannelEntity =
    ChannelEntity(
        // LG-05: store the ORIGINAL name here, never the custom one —
        // toModel() displays customName ?: name, so baking the custom name
        // into `name` made un-renaming impossible.
        id = id, num = num, name = name, code = code, category = category,
        quality = quality, nowTitle = nowTitle, nowTime = nowTime, progress = progress, hue = hue,
        description = description, nextEncoded = Converters.encodePairs(next),
        isFavorite = isFavorite, isBroken = isBroken, streamUrl = streamUrl,
        backupUrlsEncoded = Converters.encodeStrings(backupUrls), epgId = epgId,
        logoUrl = logoUrl,
        customNum = customNum, hidden = hidden, customName = customName,
        bouquetId = bouquetId, playlistId = playlistId, orderIndex = orderIndex
    )

fun FilmEntity.toModel(): Film = Film(
    id = id, title = customName ?: title, year = year, rating = rating, quality = quality,
    hue = hue, genre = genre, durationMin = durationMin, isFavorite = isFavorite,
    recentProgress = recentProgress, remainingText = remainingText, description = description,
    isBroken = isBroken, streamUrl = streamUrl, posterUrl = posterUrl,
    trailerUrl = trailerUrl,
    hidden = hidden, customName = customName
)

fun Film.toEntity(playlistId: String, orderIndex: Int): FilmEntity =
    FilmEntity(
        id = id, title = title, year = year, rating = rating, quality = quality, hue = hue,
        genre = genre, durationMin = durationMin, isFavorite = isFavorite,
        recentProgress = recentProgress, remainingText = remainingText, description = description,
        isBroken = isBroken, streamUrl = streamUrl, posterUrl = posterUrl,
        trailerUrl = trailerUrl,
        hidden = hidden, customName = customName, playlistId = playlistId, orderIndex = orderIndex
    )

fun SeriesEntity.toModel(): Series = Series(
    id = id, title = customName ?: title, seasons = seasons, rating = rating, quality = quality,
    hue = hue, genre = genre, isFavorite = isFavorite, recentProgress = recentProgress,
    remainingText = remainingText, description = description, isBroken = isBroken, posterUrl = posterUrl,
    trailerUrl = trailerUrl,
    hidden = hidden, customName = customName
)

fun Series.toEntity(playlistId: String, orderIndex: Int): SeriesEntity =
    SeriesEntity(
        id = id, title = title, seasons = seasons, rating = rating, quality = quality, hue = hue,
        genre = genre, isFavorite = isFavorite, recentProgress = recentProgress,
        remainingText = remainingText, description = description, isBroken = isBroken,
        posterUrl = posterUrl, trailerUrl = trailerUrl, hidden = hidden, customName = customName,
        playlistId = playlistId, orderIndex = orderIndex
    )

fun EpisodeEntity.toModel(): Episode = Episode(seriesId, season, number, title, durationMin, code, streamUrl)
fun Episode.toEntity(): EpisodeEntity = EpisodeEntity(seriesId, season, number, title, durationMin, code, streamUrl)

fun BouquetEntity.toModel(): Bouquet = Bouquet(
    id = id, name = name, kind = runCatching { BouquetKind.valueOf(kind) }.getOrDefault(BouquetKind.LIVE),
    order = order, hidden = hidden
)

fun Bouquet.toEntity(playlistId: String): BouquetEntity =
    BouquetEntity(id = id, name = name, kind = kind.name, order = order, hidden = hidden, playlistId = playlistId)

fun PlaylistEntity.toModel(): Playlist = Playlist(
    id = id, name = name,
    type = runCatching { PlaylistType.valueOf(type) }.getOrDefault(PlaylistType.XTREAM),
    serverUrl = serverUrl, username = username, password = password, portalUrl = portalUrl,
    mac = mac, m3uUrl = m3uUrl, isActive = isActive, expiryDate = expiryDate,
    connections = connections, lastUpdate = lastUpdate, status = status, xmltvUrl = xmltvUrl
)

fun Playlist.toEntity(): PlaylistEntity = PlaylistEntity(
    id = id, name = name, type = type.name, serverUrl = serverUrl, username = username,
    password = password, portalUrl = portalUrl, mac = mac, m3uUrl = m3uUrl,
    isActive = isActive, expiryDate = expiryDate, connections = connections,
    lastUpdate = lastUpdate, status = status, xmltvUrl = xmltvUrl
)

fun EpgEntity.toModel(): EpgProgram = EpgProgram(channelId, title, startMillis, endMillis, description)
fun EpgProgram.toEntity(): EpgEntity = EpgEntity(channelId, title, startMillis, endMillis, description)

fun HistoryEntity.toModel(): HistoryEntry = HistoryEntry(
    id = id,
    kind = runCatching { ContentKind.valueOf(kind) }.getOrDefault(ContentKind.LIVE),
    refId = refId, title = title, timestampMillis = timestampMillis
)

fun ReminderEntity.toModel(): Reminder = Reminder(channelId, programTitle, startMillis)
fun Reminder.toEntity(): ReminderEntity = ReminderEntity(channelId, programTitle, startMillis)

fun RecordingEntity.toModel(): Recording = Recording(
    id = id, channelId = channelId, channelName = channelName, title = title,
    startMillis = startMillis, durationMs = durationMs, filePath = filePath,
    sizeBytes = sizeBytes,
    status = runCatching { RecordingStatus.valueOf(status) }.getOrDefault(RecordingStatus.DONE)
)

fun Recording.toEntity(): RecordingEntity = RecordingEntity(
    id = id, channelId = channelId, channelName = channelName, title = title,
    startMillis = startMillis, durationMs = durationMs, filePath = filePath,
    sizeBytes = sizeBytes, status = status.name
)

fun WatchLaterEntity.toModel(): WatchLaterItem = WatchLaterItem(
    kind = runCatching { ContentKind.valueOf(kind) }.getOrDefault(ContentKind.FILM),
    refId = refId, title = title, addedMillis = addedMillis, profileId = profileId
)

fun WatchLaterItem.toEntity(): WatchLaterEntity = WatchLaterEntity(
    kind = kind.name, refId = refId, title = title, addedMillis = addedMillis, profileId = profileId
)

fun ProfileEntity.toModel(): UserProfile =
    UserProfile(id = id, name = name, hue = hue, pin = pin, createdMillis = createdMillis)

fun UserProfile.toEntity(): ProfileEntity =
    ProfileEntity(id = id, name = name, hue = hue, pin = pin, createdMillis = createdMillis)

fun ScheduledRecordingEntity.toModel(): ScheduledRecording = ScheduledRecording(
    id = id, channelId = channelId, channelName = channelName, programTitle = programTitle,
    startMillis = startMillis, endMillis = endMillis, repeat = repeat, status = status
)

fun ScheduledRecording.toEntity(): ScheduledRecordingEntity = ScheduledRecordingEntity(
    id = id, channelId = channelId, channelName = channelName, programTitle = programTitle,
    startMillis = startMillis, endMillis = endMillis, repeat = repeat, status = status
)

fun SeriesRecordingRuleEntity.toModel(): SeriesRecordingRule = SeriesRecordingRule(
    id = id, channelId = channelId, channelName = channelName, programTitle = programTitle,
    normalizedTitle = normalizedTitle, createdMillis = createdMillis, active = active
)

fun SeriesRecordingRule.toEntity(): SeriesRecordingRuleEntity = SeriesRecordingRuleEntity(
    id = id, channelId = channelId, channelName = channelName, programTitle = programTitle,
    normalizedTitle = normalizedTitle, createdMillis = createdMillis, active = active
)

fun DownloadEntity.toModel(): Download = Download(
    id = id,
    kind = runCatching { ContentKind.valueOf(kind) }.getOrDefault(ContentKind.FILM),
    refId = refId, title = title, coverUrl = coverUrl, fileName = fileName, filePath = filePath,
    sourceUrl = sourceUrl,
    totalBytes = totalBytes, downloadedBytes = downloadedBytes,
    status = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.ERROR),
    createdMillis = createdMillis
)

fun Download.toEntity(): DownloadEntity = DownloadEntity(
    id = id, kind = kind.name, refId = refId, title = title, coverUrl = coverUrl,
    fileName = fileName, filePath = filePath, sourceUrl = sourceUrl, totalBytes = totalBytes,
    downloadedBytes = downloadedBytes, status = status.name, createdMillis = createdMillis
)
