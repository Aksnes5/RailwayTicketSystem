package com.railway.ticketsystem.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Produces stable, time-aware stop statuses without storing a second mutable timetable.
 * Calls remain blank until the train is due at that station; the origin is always marked --.
 */
object TimetableStopStatusResolver {
    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).apply { isLenient = false }

    fun resolve(
        stops: List<TrainStopSchedule>,
        trainNumber: String,
        departureDate: String,
        nowMillis: Long = System.currentTimeMillis()
    ): List<TrainStopSchedule> {
        if (stops.isEmpty()) return emptyList()
        val firstClock = stops.first().departureTime.takeUnless { it == "—" } ?: return stops
        val firstDeparture = parse(departureDate, firstClock) ?: return stops
        var previousCall = firstDeparture
        return stops.mapIndexed { index, stop ->
            if (index == 0) return@mapIndexed stop.copy(operationalStatus = "--")
            val clock = stop.arrivalTime.takeUnless { it == "—" } ?: stop.departureTime
            val scheduledArrival = parse(departureDate, clock)?.let { candidate ->
                var normalized = candidate
                while (normalized < previousCall) normalized += DAY_MILLIS
                normalized
            } ?: return@mapIndexed stop.copy(operationalStatus = "")
            previousCall = scheduledArrival
            val deviation = stableDeviationMinutes(trainNumber, departureDate, stop.stationName, index)
            val actualArrival = scheduledArrival + deviation * 60_000L
            val status = if (nowMillis < actualArrival) "" else deviationLabel(deviation)
            stop.copy(operationalStatus = status)
        }
    }

    private fun parse(date: String, clock: String): Long? =
        runCatching { dateTimeFormat.parse("$date $clock")?.time }.getOrNull()

    private fun stableDeviationMinutes(trainNumber: String, date: String, station: String, index: Int): Int {
        val seed = ("$trainNumber|$date|$station|$index").hashCode().toLong() and Long.MAX_VALUE
        return (seed % 11L).toInt() - 5
    }

    private fun deviationLabel(minutes: Int): String = when {
        minutes > 0 -> "晚点${minutes}分"
        minutes < 0 -> "早点${-minutes}分"
        else -> "正点"
    }
}
