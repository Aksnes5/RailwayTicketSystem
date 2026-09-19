package com.railway.ticketsystem.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** A durable request created while the traveler is on board. */
data class CarriageServiceRequest(
    val id: String,
    val userId: String,
    val ticketOrderId: String,
    val trainNumber: String,
    val carNumber: String,
    val seatNumber: String,
    val type: String,
    val details: String,
    val status: String,
    val createdAt: String,
    val createdAtMillis: Long,
    val feedback: String? = null
)

/**
 * On-board requests intentionally live outside an order. A ticket can be refunded or
 * archived without losing the request, and every request is still scoped to its ticket.
 */
class CarriageServiceRepository(context: Context) {
    private val prefs = SecurePreferences.open(context.applicationContext, "secure_carriage_services", "carriage_services")
    private val gson = Gson()

    fun getByTicket(userId: String, ticketOrderId: String): List<CarriageServiceRequest> = read()
        .filter { it.userId == userId && it.ticketOrderId == ticketOrderId }
        .sortedByDescending { it.createdAtMillis }

    fun save(request: CarriageServiceRequest): Boolean {
        val all = read().filterNot { it.id == request.id }
        return prefs.edit().putString(KEY, gson.toJson((all + request).takeLast(160))).commit()
    }

    fun cancel(userId: String, requestId: String): Boolean = update(userId, requestId) {
        if (it.status != "已受理") it else it.copy(status = "已撤销")
    }

    fun leaveFeedback(userId: String, requestId: String, feedback: String): Boolean {
        if (feedback.isBlank()) return false
        return update(userId, requestId) { it.copy(feedback = feedback.trim().take(80)) }
    }

    private fun update(userId: String, requestId: String, transform: (CarriageServiceRequest) -> CarriageServiceRequest): Boolean {
        val all = read()
        val current = all.firstOrNull { it.userId == userId && it.id == requestId } ?: return false
        return save(transform(current))
    }

    /** The local service center advances ordinary calls without a foreground timer. */
    fun displayStatus(request: CarriageServiceRequest, now: Long = System.currentTimeMillis()): String = when {
        request.status == "已撤销" -> "已撤销"
        request.feedback != null -> "已评价"
        else -> when (request.type) {
            "遗失物登记" -> "待核查"
            "乘车偏好", "到站提醒" -> "已生效"
            else -> when ((now - request.createdAtMillis).coerceAtLeast(0L)) {
                in 0 until 90_000L -> "已受理"
                in 90_000L until 5 * 60_000L -> "乘务员处理中"
                else -> "已完成"
            }
        }
    }

    private fun read(): List<CarriageServiceRequest> {
        val type = object : TypeToken<List<CarriageServiceRequest>>() {}.type
        return runCatching { gson.fromJson<List<CarriageServiceRequest>>(prefs.getString(KEY, null), type) ?: emptyList() }
            .getOrDefault(emptyList())
    }

    companion object {
        private const val KEY = "carriage_service_requests"

        fun newRequest(
            userId: String,
            ticketOrderId: String,
            trainNumber: String,
            carNumber: String,
            seatNumber: String,
            type: String,
            details: String
        ): CarriageServiceRequest {
            val now = System.currentTimeMillis()
            return CarriageServiceRequest(
                id = "CRS_" + UUID.randomUUID().toString().take(8).uppercase(Locale.ROOT),
                userId = userId,
                ticketOrderId = ticketOrderId,
                trainNumber = trainNumber,
                carNumber = carNumber,
                seatNumber = seatNumber,
                type = type,
                details = details,
                status = "已受理",
                createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(now)),
                createdAtMillis = now
            )
        }
    }
}
