package com.railway.ticketsystem.data

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * A departure that has already happened cannot be sold.
 *
 * The timetable generator picks a time of day (06:00–22:59) without consulting the clock,
 * so a search made in the afternoon naturally contains trains that left in the morning.
 * Nothing downstream re-checked the departure against the current time either, so those
 * trains stayed listed and could be ordered.
 */
object DepartureTimingPolicy {
    /** Sales close this long before departure, leaving time to be checked in. */
    const val SALES_CLOSE_BEFORE_MILLIS = 5L * 60L * 1000L

    /** A waitlist request cannot realistically be fulfilled any later than this. */
    const val WAITLIST_CLOSE_BEFORE_MILLIS = 30L * 60L * 1000L

    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
        .apply { isLenient = false }

    fun departureMillis(travelDate: String, departureTime: String): Long? = runCatching {
        dateTimeFormat.parse("$travelDate $departureTime")?.time
    }.getOrNull()

    /** True while tickets for this departure are still on sale. */
    fun isBookable(
        travelDate: String,
        departureTime: String,
        now: Long = System.currentTimeMillis()
    ): Boolean = isOpenFor(travelDate, departureTime, SALES_CLOSE_BEFORE_MILLIS, now)

    /** True while a waitlist request for this departure can still be placed. */
    fun isWaitlistOpen(
        travelDate: String,
        departureTime: String,
        now: Long = System.currentTimeMillis()
    ): Boolean = isOpenFor(travelDate, departureTime, WAITLIST_CLOSE_BEFORE_MILLIS, now)

    /** An unparseable date or time stays open, rather than closing sales on a data problem. */
    private fun isOpenFor(
        travelDate: String,
        departureTime: String,
        leadMillis: Long,
        now: Long
    ): Boolean {
        if (travelDate.isBlank() || departureTime.isBlank()) return true
        val departure = departureMillis(travelDate, departureTime) ?: return true
        return now < departure - leadMillis
    }
}
