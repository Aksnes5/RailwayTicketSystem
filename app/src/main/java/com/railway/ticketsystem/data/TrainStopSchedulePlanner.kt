package com.railway.ticketsystem.data

import com.railway.ticketsystem.model.RailwayRouteManager
import com.railway.ticketsystem.model.RouteType
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Builds the published calls for one opened train-detail page.
 *
 * It deliberately has no use in search or ticket query flows: times are created
 * only after the traveler opens a particular train, keeping list rendering light.
 */
data class TrainStopSchedule(
    val stationName: String,
    val arrivalTime: String,
    val departureTime: String,
    val dwellLabel: String,
    /** Empty before arrival; live displays fill this with 正点、晚点或早点。 */
    val operationalStatus: String = ""
)

/** A full operating timetable whose queried section is locked to the search row's clocks. */
data class AnchoredTimetable(
    val stops: List<TrainStopSchedule>,
    val serviceDuration: String
)

object TrainStopSchedulePlanner {
    fun minimumDurationMinutes(stopCount: Int): Int {
        val segments = (stopCount - 1).coerceAtLeast(0)
        val intermediate = (stopCount - 2).coerceAtLeast(0)
        return segments + intermediate * 2
    }

    fun resolvedDuration(duration: String, stopCount: Int): String =
        RoutePresentationPlanner.formatMinutes(
            maxOf(RoutePresentationPlanner.durationMinutes(duration), minimumDurationMinutes(stopCount))
        )

    fun create(
        stations: List<String>,
        trainNumber: String,
        departureTime: String,
        duration: String
    ): List<TrainStopSchedule> {
        if (stations.isEmpty()) return emptyList()

        val stopCount = stations.size
        val segmentCount = (stopCount - 1).coerceAtLeast(0)
        if (segmentCount == 0) {
            return listOf(TrainStopSchedule(stations.first(), "—", departureTime, "始发站"))
        }

        val totalMinutes = RoutePresentationPlanner.durationMinutes(
            resolvedDuration(duration, stopCount)
        )
        val dwellMinutes = buildDwellMinutes(stations, trainNumber, totalMinutes, segmentCount)
        val segmentWeights = stations.zipWithNext().map { (from, to) ->
            segmentRunningMinutes(from, to, trainNumber)
        }
        val runningMinutes = distributeRunningMinutes(
            (totalMinutes - dwellMinutes.sum()).coerceAtLeast(segmentCount),
            segmentCount,
            trainNumber,
            segmentWeights
        )

        var clock = toMinutes(departureTime)
        return stations.mapIndexed { index, station ->
            when (index) {
                0 -> TrainStopSchedule(
                    stationName = station,
                    arrivalTime = "—",
                    departureTime = formatClock(clock),
                    dwellLabel = "始发站"
                )
                stopCount - 1 -> {
                    clock += runningMinutes[index - 1]
                    TrainStopSchedule(
                        stationName = station,
                        arrivalTime = formatClock(clock),
                        departureTime = "—",
                        dwellLabel = "终到站"
                    )
                }
                else -> {
                    clock += runningMinutes[index - 1]
                    val arrival = formatClock(clock)
                    val dwell = dwellMinutes[index - 1]
                    clock += dwell
                    TrainStopSchedule(
                        stationName = station,
                        arrivalTime = arrival,
                        departureTime = formatClock(clock),
                        dwellLabel = "${dwell}分"
                    )
                }
            }
        }
    }

    /**
     * Builds a full-service timetable without changing the time shown for the
     * passenger's searched section. A through train may start several calls
     * before the boarding station, but that station's departure and the chosen
     * arrival station's arrival remain exactly those shown in the result list.
     */
    fun createAnchored(
        stations: List<String>,
        trainNumber: String,
        queryDepartureStation: String,
        queryArrivalStation: String,
        queryDepartureTime: String,
        queryDuration: String
    ): AnchoredTimetable {
        if (RealTrainCatalog.hasTimetable(trainNumber)) {
            val anchored = RealTrainCatalog.getAnchoredTimetable(trainNumber, queryDepartureStation, queryArrivalStation)
            if (anchored != null) {
                return anchored
            }
        }

        val startIndex = stations.indexOf(queryDepartureStation)
        val endIndex = stations.indexOf(queryArrivalStation)
        if (startIndex < 0 || endIndex <= startIndex) {
            val fallback = create(stations, trainNumber, queryDepartureTime, queryDuration)
            return AnchoredTimetable(fallback, resolvedDuration(queryDuration, stations.size))
        }

        val lastIndex = stations.lastIndex
        val dwellMinutes = IntArray(stations.size) { index ->
            when (index) {
                0, lastIndex -> 0
                else -> dwellForStation(stations[index], trainNumber, index)
            }
        }
        val segmentCount = endIndex - startIndex
        val queryMinimum = segmentCount +
            (startIndex + 1 until endIndex).sumOf { dwellMinutes[it] }
        val queryTotal = maxOf(RoutePresentationPlanner.durationMinutes(queryDuration), queryMinimum)
        val queryWeights = (startIndex until endIndex).map { idx ->
            segmentRunningMinutes(stations[idx], stations[idx + 1], trainNumber)
        }
        val queryRunning = distributeRunningMinutes(
            (queryTotal - (startIndex + 1 until endIndex).sumOf { dwellMinutes[it] })
                .coerceAtLeast(segmentCount),
            segmentCount,
            "$trainNumber#query",
            queryWeights
        )

        val arrivals = IntArray(stations.size)
        val departures = IntArray(stations.size)
        departures[startIndex] = toMinutes(queryDepartureTime)
        if (startIndex > 0) arrivals[startIndex] = departures[startIndex] - dwellMinutes[startIndex]

        // Fill the paid/query segment first so the list and ticket always agree.
        for (index in startIndex until endIndex) {
            arrivals[index + 1] = departures[index] + queryRunning[index - startIndex]
            if (index + 1 < endIndex) {
                departures[index + 1] = arrivals[index + 1] + dwellMinutes[index + 1]
            }
        }

        // Then work backwards to the actual origin and forward to the actual
        // terminal. These are generated only when this timetable is requested.
        for (index in startIndex - 1 downTo 0) {
            departures[index] = arrivals[index + 1] - segmentRunningMinutes(stations[index], stations[index + 1], trainNumber)
            if (index > 0) arrivals[index] = departures[index] - dwellMinutes[index]
        }
        if (endIndex < lastIndex) departures[endIndex] = arrivals[endIndex] + dwellMinutes[endIndex]
        for (index in endIndex until lastIndex) {
            arrivals[index + 1] = departures[index] + segmentRunningMinutes(stations[index], stations[index + 1], trainNumber)
            if (index + 1 < lastIndex) departures[index + 1] = arrivals[index + 1] + dwellMinutes[index + 1]
        }

        val rows = stations.mapIndexed { index, station ->
            when (index) {
                0 -> TrainStopSchedule(station, "—", formatClock(departures[index]), "始发站")
                lastIndex -> TrainStopSchedule(station, formatClock(arrivals[index]), "—", "终到站")
                else -> TrainStopSchedule(
                    stationName = station,
                    arrivalTime = formatClock(arrivals[index]),
                    departureTime = formatClock(departures[index]),
                    dwellLabel = "${dwellMinutes[index]}分"
                )
            }
        }
        val serviceMinutes = (arrivals[lastIndex] - departures[0]).coerceAtLeast(queryTotal)
        return AnchoredTimetable(rows, RoutePresentationPlanner.formatMinutes(serviceMinutes))
    }

    private fun buildDwellMinutes(
        stations: List<String>,
        trainNumber: String,
        totalMinutes: Int,
        segmentCount: Int
    ): List<Int> {
        val intermediate = (1 until stations.lastIndex).toList()
        if (intermediate.isEmpty()) return emptyList()

        val preferred = intermediate.map { index ->
            dwellForStation(stations[index], trainNumber, index)
        }.toMutableList()
        val availableDwell = (totalMinutes - segmentCount).coerceAtLeast(intermediate.size * 2)

        while (preferred.sum() > availableDwell) {
            val candidate = preferred.indices
                .filter { preferred[it] > 2 }
                .maxByOrNull { preferred[it] }
                ?: break
            preferred[candidate]--
        }
        return preferred
    }

    /** Every non-terminal call is at least two minutes, including regional hubs. */
    private fun dwellForStation(station: String, trainNumber: String, index: Int): Int {
        val seed = abs((trainNumber + station + index).hashCode())
        return if (LatestRailwayNetwork.isMajorHubStation(station)) 3 + seed % 3 else 2 + seed % 2
    }

    fun segmentRunningMinutes(
        fromStation: String,
        toStation: String,
        trainNumber: String
    ): Int {
        val routeType = when {
            trainNumber.startsWith("G", ignoreCase = true) || trainNumber.startsWith("D", ignoreCase = true) ->
                RouteType.HIGH_SPEED
            trainNumber.startsWith("C", ignoreCase = true) ->
                RouteType.INTERCITY
            else ->
                RouteType.CONVENTIONAL
        }

        // 1. 获取两站之间真实的物理耗时（按相应路网类型寻径）
        val durationStr = try {
            RailwayRouteManager.getDurationBetweenStations(fromStation, toStation, routeType)
        } catch (_: Throwable) {
            ""
        }
        val parsed = RoutePresentationPlanner.durationMinutes(durationStr)
        if (parsed > 0) return parsed

        // 反向寻径作为保障
        val altStr = try {
            RailwayRouteManager.getDurationBetweenStations(toStation, fromStation, routeType)
        } catch (_: Throwable) {
            ""
        }
        val altParsed = RoutePresentationPlanner.durationMinutes(altStr)
        if (altParsed > 0) return altParsed

        // 2. 根据车站地理坐标距离与平均运行时速进行物理计算
        val c1 = StationCoordinateCatalog.resolve(fromStation)
        val c2 = StationCoordinateCatalog.resolve(toStation)
        if (c1 != null && c2 != null) {
            val dist = approximateDistanceKm(c1.latitude, c1.longitude, c2.latitude, c2.longitude)
            if (dist > 5.0) {
                val speed = when (routeType) {
                    RouteType.HIGH_SPEED -> 240.0
                    RouteType.INTERCITY -> 160.0
                    RouteType.CONVENTIONAL -> 85.0
                }
                return (dist / speed * 60).toInt().coerceAtLeast(12)
            }
        }

        // 3. 兜底方案：基于车站名哈希保证运行时间在合理区间
        val seed = abs((trainNumber + fromStation + toStation).hashCode())
        val base = if (routeType == RouteType.CONVENTIONAL) 65 else 35
        return base + (seed % 35)
    }

    private fun approximateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return 6371.0 * c
    }

    private fun distributeRunningMinutes(
        total: Int,
        segmentCount: Int,
        trainNumber: String,
        segmentWeights: List<Int>? = null
    ): List<Int> {
        val weights = segmentWeights?.takeIf { it.size == segmentCount } ?: (0 until segmentCount).map { index ->
            1 + abs((trainNumber + "#segment" + index).hashCode() % 7)
        }
        val base = MutableList(segmentCount) { 1 }
        var remainder = (total - segmentCount).coerceAtLeast(0)
        val totalWeight = weights.sum().coerceAtLeast(1)
        weights.forEachIndexed { index, weight ->
            val extra = ((total - segmentCount).coerceAtLeast(0) * weight) / totalWeight
            base[index] += extra
            remainder -= extra
        }
        var cursor = abs(trainNumber.hashCode()) % segmentCount
        while (remainder-- > 0) {
            base[cursor]++
            cursor = (cursor + 1) % segmentCount
        }
        return base
    }

    private fun toMinutes(value: String): Int {
        val pieces = value.split(":")
        return (pieces.getOrNull(0)?.toIntOrNull() ?: 0) * 60 +
            (pieces.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    private fun formatClock(minutes: Int): String {
        val normalized = ((minutes % (24 * 60)) + (24 * 60)) % (24 * 60)
        return "%02d:%02d".format(normalized / 60, normalized % 60)
    }
}
