package com.railway.ticketsystem.data

import com.railway.ticketsystem.model.RailwayRoute
import com.railway.ticketsystem.model.Station
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Passenger-facing catalog policy.  A physical rail station is not necessarily
 * a current ticketing stop; suspended passenger stations must never appear in
 * station search, route planning, generated trains or timetable details.
 */
object PassengerServiceStationPolicy {
    private val indefiniteSuspensions = setOf(
        // 武黄/武九共线段：已停止办理铁路客运业务。
        "花山南",
        "左岭"
    )

    // 施工期间暂停办理客运；到期后自动恢复到可查询目录，避免永久误删。
    private val temporarySuspensionsUntil = mapOf(
        "烟台南" to parseEndOfDay("2027-06-30")
    )

    fun isPassengerServiceStation(name: String, now: Long = System.currentTimeMillis()): Boolean {
        val station = name.trim()
        if (station.isEmpty() || station in indefiniteSuspensions) return false
        val suspendedUntil = temporarySuspensionsUntil[station] ?: return true
        return now > suspendedUntil
    }

    fun filterStations(stations: Iterable<Station>): List<Station> =
        stations.filter { isPassengerServiceStation(it.name) }

    /**
     * Removes non-ticketing calls while preserving the original order and
     * folds every skipped segment's price into the next visible segment.
     */
    fun passengerRoute(route: RailwayRoute): RailwayRoute {
        val original = route.stations
        val retained = original.withIndex().filter { isPassengerServiceStation(it.value.name) }
        if (retained.size == original.size) return route
        if (retained.size < 2) return route.copy(stations = emptyList(), segmentPrices = emptyMap())

        val prices = retained.zipWithNext().associate { (from, to) ->
            val fromIndex = from.index
            val toIndex = to.index
            val foldedPrice = (fromIndex until toIndex).sumOf { index ->
                val a = original[index].name
                val b = original[index + 1].name
                route.segmentPrices["$a-$b"] ?: route.segmentPrices["$b-$a"] ?: 0.0
            }
            "${from.value.name}-${to.value.name}" to foldedPrice
        }
        return route.copy(stations = retained.map { it.value }, segmentPrices = prices)
    }

    private fun parseEndOfDay(value: String): Long = runCatching {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).apply { isLenient = false }
            .parse("$value 23:59:59")!!.time
    }.getOrElse { Date(0).time }
}
