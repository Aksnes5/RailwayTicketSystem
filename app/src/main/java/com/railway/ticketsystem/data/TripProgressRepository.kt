package com.railway.ticketsystem.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.railway.ticketsystem.model.Order
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Durable, user-confirmed milestones for the travel concierge timeline. */
data class TripProgress(
    val orderId: String,
    val userId: String,
    val stage: String = STAGE_READY,
    val updatedAt: String
) {
    companion object {
        const val STAGE_READY = "待出发"
        const val STAGE_AT_STATION = "已到车站"
        const val STAGE_BOARDED = "已上车"
        const val STAGE_ARRIVED = "已到达"
    }
}

class TripProgressRepository(context: Context) {
    private val prefs: SharedPreferences = SecurePreferences.open(
        context.applicationContext,
        "secure_trip_progress_data",
        "trip_progress_data"
    )
    private val gson = Gson()

    companion object {
        private val progressLock = Any()
    }

    fun get(order: Order): TripProgress = synchronized(progressLock) {
        read(order.id)?.takeIf { it.userId == order.userId }
            ?: TripProgress(order.id, order.userId, TripProgress.STAGE_READY, order.payTime ?: timestamp())
    }

    /** Saves an ordered milestone. Repeating a completed step is intentionally idempotent. */
    fun advance(order: Order, nextStage: String): TripProgress? = synchronized(progressLock) {
        if (order.id.isBlank() || order.userId.isBlank() || nextStage !in stageOrder) return@synchronized null
        val current = get(order)
        if (stageIndex(nextStage) < stageIndex(current.stage)) return@synchronized current
        val updated = current.copy(stage = nextStage, updatedAt = timestamp())
        if (prefs.edit().putString(key(order.id), gson.toJson(updated)).commit()) updated else null
    }

    fun nextStage(progress: TripProgress): String? = when (progress.stage) {
        TripProgress.STAGE_READY -> TripProgress.STAGE_AT_STATION
        TripProgress.STAGE_AT_STATION -> TripProgress.STAGE_BOARDED
        TripProgress.STAGE_BOARDED -> TripProgress.STAGE_ARRIVED
        else -> null
    }

    /**
     * Advances the visual travel stage as the published timetable is reached. It never moves a
     * manually confirmed stage backwards and is safe to call every minute from the UI ticker.
     */
    fun synchronizeWithTimetable(order: Order, now: Long = System.currentTimeMillis()): TripProgress {
        val current = get(order)
        if (order.status !in setOf("已支付", "已完成")) return current
        val target = when {
            order.status == "已完成" || (TravelAssistant.arrivalMillis(order)?.let { now >= it } == true) -> TripProgress.STAGE_ARRIVED
            TravelAssistant.departureMillis(order)?.let { now >= it } == true -> TripProgress.STAGE_BOARDED
            else -> current.stage
        }
        return if (stageIndex(target) > stageIndex(current.stage)) advance(order, target) ?: current else current
    }

    private fun read(orderId: String): TripProgress? = runCatching {
        gson.fromJson(prefs.getString(key(orderId), null), TripProgress::class.java)
    }.getOrNull()

    private fun key(orderId: String) = "trip_progress_" + orderId
    private fun stageIndex(stage: String) = stageOrder.indexOf(stage).coerceAtLeast(0)
    private fun timestamp(): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())

    private val stageOrder = listOf(
        TripProgress.STAGE_READY,
        TripProgress.STAGE_AT_STATION,
        TripProgress.STAGE_BOARDED,
        TripProgress.STAGE_ARRIVED
    )
}
