package com.streammaster.tv.data.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.streammaster.tv.data.db.StreamMasterDatabase
import com.streammaster.tv.data.db.ChannelTimer as ChannelTimerEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * §4.58 — channel timer contract + scheduling (2026-11-23).
 *
 * The user picks a channel and a time; at that time the app switches to
 * the channel by itself. This file owns the scheduling (exact AlarmManager
 * alarm, same pattern as [ReminderScheduler]/[RecordingScheduler]) and the
 * [ChannelTimer] model contract; [ChannelTimerReceiver] owns the firing.
 *
 * CONTRACT for the data builder: the Room entity `ChannelTimer` already
 * exists in `data/db/Entities.kt` (table `channel_timers`: `id: Long`
 * @PrimaryKey autoGenerate, `channelId`, `channelName`,
 * `triggerAtMillis: Long`, `enabled: Boolean = true`) with a
 * `ChannelTimerDao` (`observeAll/getAll/getById/upsert/update/deleteById`)
 * in `Daos.kt` — wired into `StreamMasterDatabase` (v8) by the
 * integration step, together with [ChannelTimerScheduler.install] (called
 * from `ContentRepositoryImpl.init`, same pattern as the cleanup
 * scanner) and [ChannelTimerScheduler.observeTimers]. BootReceiver
 * re-arms the saved timers on boot from the DAO.
 *
 * THE BRIDGE (single canonical place): the entity and the model use
 * different ids — the entity row `id: Long` is the storage key, the
 * model `id: String` is the alarm identity used for the PendingIntent
 * request code. [toModel] / [toEntity] below are the ONLY conversions:
 * a persisted timer's model id is `"db:<rowId>"`; a brand-new timer
 * (UUID id from the UI) converts to an entity with id 0, which Room
 * auto-generates on insert. The alarm request code derives from the
 * model id string, so a re-armed alarm (same "db:<rowId>") matches the
 * original PendingIntent exactly.
 * The UI builder (ChannelTimerViewModel) already builds against THIS
 * model: `ChannelTimer(id: String, channelId, channelName, triggerMillis)`
 * + `schedule(context, timer)` + `cancelById(context, timerId)`.
 *
 * Firing: [ChannelTimerReceiver] sends the existing player deep link
 * `streammaster://player/live/{channelId}` to MainActivity (singleTask):
 * cold start opens the app on the channel; warm start re-navigates to it.
 * Player-open case (OP-13, fixed 2026-11-24 in MainActivity):
 * when a timer deep link arrives while the current destination is
 * already PLAYER, `maybeNavigateToDeepLink` navigates to the new
 * channel (a no-op when it names the channel already playing) instead
 * of leaving the link queued — so the timer switches channels even
 * with the player open.
 */
data class ChannelTimer(
    val id: String,
    val channelId: String,
    val channelName: String,
    /** Epoch millis at which the app must switch to the channel. */
    val triggerMillis: Long
)

object ChannelTimerScheduler {

    const val EXTRA_TIMER_ID = "extra_timer_id"
    const val EXTRA_CHANNEL_ID = "extra_timer_channel_id"
    const val EXTRA_CHANNEL_NAME = "extra_timer_channel_name"

    /** Database handed in once (ContentRepositoryImpl.init) — same
     *  pattern as the cleanup scanner: a no-arg object cannot be
     *  Hilt-injected. Until installed, [observeTimers] is empty. */
    @Volatile
    private var database: StreamMasterDatabase? = null

    fun install(db: StreamMasterDatabase) {
        database = db
    }

    /** All saved timers, as scheduler models, live from the DAO. */
    fun observeTimers(): Flow<List<ChannelTimer>> {
        val db = database ?: return flowOf(emptyList())
        return db.channelTimerDao().observeAll().map { rows -> rows.map(::toModel) }
    }

    fun schedule(context: Context, timer: ChannelTimer) {
        val intent = Intent(context, ChannelTimerReceiver::class.java).apply {
            putExtra(EXTRA_TIMER_ID, timer.id)
            putExtra(EXTRA_CHANNEL_ID, timer.channelId)
            putExtra(EXTRA_CHANNEL_NAME, timer.channelName)
        }
        val pending = PendingIntent.getBroadcast(
            context, requestCode(timer.id), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        setExact(am, timer.triggerMillis, pending)
    }

    fun cancel(context: Context, timer: ChannelTimer) {
        cancelById(context, timer.id)
    }

    fun cancelById(context: Context, timerId: String) {
        val intent = Intent(context, ChannelTimerReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, requestCode(timerId), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending)
        pending.cancel()
    }

    private fun requestCode(timerId: String): Int = ("channeltimer|$timerId").hashCode()

    private fun setExact(am: AlarmManager, atMillis: Long, pending: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            }
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        }
    }
}

/**
 * THE bridge between the Room row and the scheduler model — the ONLY
 * place the two shapes convert (see the KDoc at the top of this file).
 * Persisted timer: model id `"db:<rowId>"`; new timer (UUID id from the
 * UI): entity id 0, auto-generated by Room on insert.
 */
fun toModel(entity: ChannelTimerEntity): ChannelTimer =
    ChannelTimer(
        id = "db:${entity.id}",
        channelId = entity.channelId,
        channelName = entity.channelName,
        triggerMillis = entity.triggerAtMillis
    )

fun toEntity(timer: ChannelTimer): ChannelTimerEntity =
    ChannelTimerEntity(
        id = timer.id.removePrefix("db:").toLongOrNull() ?: 0L,
        channelId = timer.channelId,
        channelName = timer.channelName,
        triggerAtMillis = timer.triggerMillis,
        enabled = true
    )
