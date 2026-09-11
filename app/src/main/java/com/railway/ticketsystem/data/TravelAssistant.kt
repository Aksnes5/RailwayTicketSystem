package com.railway.ticketsystem.data

import com.railway.ticketsystem.model.Order
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Presentation rules shared by the trip dashboard and individual trip cards. */
object TravelAssistant {
    private const val DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm"

    // All callers run on the UI thread (activities, fragments, adapter binding), which is what
    // makes sharing these safe — SimpleDateFormat is not thread-safe.  Hoisted so that the
    // per-minute countdown refresh on the trip screen does not allocate formatters each tick.
    private val dateTimeFormat = SimpleDateFormat(DATE_TIME_PATTERN, Locale.CHINA).apply { isLenient = false }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    private val clockFormat = SimpleDateFormat("HH:mm", Locale.CHINA).apply { isLenient = false }

    fun nextUpcoming(orders: List<Order>, now: Date = Date()): Order? =
        orders.filter { isUpcoming(it, now) }
            .minByOrNull { departureAt(it)?.time ?: Long.MAX_VALUE }

    fun isUpcoming(order: Order, now: Date = Date()): Boolean =
        order.status == "已支付" && (departureAt(order)?.after(now) == true)

    /**
     * A trip enters history only after its durable order state changes.  Merely
     * reaching its scheduled departure time must not hide an active same-day
     * ticket from the "今天" list.
     */
    fun isHistory(order: Order): Boolean =
        order.status == "已完成" || order.status == "已取消"

    fun departureCountdown(order: Order, now: Date = Date()): String {
        val departure = departureAt(order) ?: return "请以车票日期为准"
        val remainingMinutes = (departure.time - now.time) / 60_000L
        if (remainingMinutes <= 0) return "已到发车时间，请留意站内广播"

        val days = remainingMinutes / (24 * 60)
        val hours = (remainingMinutes % (24 * 60)) / 60
        val minutes = remainingMinutes % 60
        return when {
            days > 0 -> "距发车 ${days}天${hours}小时"
            hours > 0 -> "距发车 ${hours}小时${minutes}分钟"
            else -> "距发车 ${minutes}分钟"
        }
    }

    /**
     * The single journey-state line on the ticket detail card.  Arrival is derived
     * because orders only store a clock time, so an overnight leg wraps by 24h — the
     * same convention the displayed travel duration already uses.
     */
    fun journeyStatusText(order: Order, now: Date = Date()): String {
        val departure = departureAt(order) ?: return "请以车票日期为准"
        if (order.status == "已完成") return "已到达"
        val arrival = arrivalMillis(order)
        if (arrival != null && now.time >= arrival) return "已到达"
        if (!now.before(departure)) return "旅途进行中"

        // Round up so a journey 40 seconds away never reads "计划0分钟后出发".
        val remainingMinutes = (departure.time - now.time + 59_999L) / 60_000L
        val days = remainingMinutes / (24 * 60)
        val hours = (remainingMinutes % (24 * 60)) / 60
        val minutes = remainingMinutes % 60
        return when {
            days > 0 -> "计划${days}天${hours}小时${minutes}分钟后出发"
            hours > 0 -> "计划${hours}小时${minutes}分钟后出发"
            else -> "计划${minutes}分钟后出发"
        }
    }

    /**
     * Epoch millis at which the journey ends, or null when the times cannot be parsed.
     *
     * Prefers the stored [Order.arrivalDate].  Orders written before that field existed carry
     * only a clock arrival time, so for those the arrival is derived by wrapping the clock
     * forward — which cannot distinguish an overnight leg from a same-day one.
     */
    fun arrivalMillis(order: Order): Long? {
        val storedDate = order.arrivalDate
        if (!storedDate.isNullOrBlank()) {
            runCatching { dateTimeFormat.parse("$storedDate ${order.arrivalTime}") }
                .getOrNull()?.let { return it.time }
        }
        val departure = departureAt(order) ?: return null
        return arrivalAt(order, departure)?.time
    }

    /** Calendar date a service arrives on, given its clocks; null when unparseable. */
    fun arrivalDateFor(departureDate: String, departureTime: String, arrivalTime: String): String? {
        val departure = runCatching {
            dateTimeFormat.parse("$departureDate $departureTime")
        }.getOrNull() ?: return null
        val departureClock = runCatching { clockFormat.parse(departureTime) }.getOrNull() ?: return null
        val arrivalClock = runCatching { clockFormat.parse(arrivalTime) }.getOrNull() ?: return null
        var duration = arrivalClock.time - departureClock.time
        if (duration < 0) duration += 24L * 60L * 60L * 1000L
        return dateFormat.format(Date(departure.time + duration))
    }

    /** Scheduled departure epoch used by the travel-concierge automatic stage transition. */
    fun departureMillis(order: Order): Long? = departureAt(order)?.time

    private fun arrivalAt(order: Order, departure: Date): Date? {
        val departureClock = runCatching { clockFormat.parse(order.departureTime) }.getOrNull() ?: return null
        val arrivalClock = runCatching { clockFormat.parse(order.arrivalTime) }.getOrNull() ?: return null
        var duration = arrivalClock.time - departureClock.time
        if (duration < 0) duration += 24L * 60L * 60L * 1000L
        return Date(departure.time + duration)
    }

    fun boardingReminder(order: Order, now: Date = Date()): String? {
        if (!isUpcoming(order, now)) return null
        val departure = departureAt(order) ?: return null
        val remainingMinutes = (departure.time - now.time) / 60_000L
        return when {
            remainingMinutes <= 30 -> "即将发车，请尽快完成检票进站"
            remainingMinutes <= 120 -> "建议现在前往车站，预留安检和检票时间"
            else -> "建议至少提前 30 分钟到站候车"
        }
    }

    private fun departureAt(order: Order): Date? = runCatching {
        dateTimeFormat.parse("${order.departureDate} ${order.departureTime}")
    }.getOrNull()
}
