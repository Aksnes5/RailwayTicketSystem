package com.railway.ticketsystem.data

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.random.Random

data class RouteDisplayPlan(
    val stations: List<String>,
    val skippedStationCount: Int,
    val adjustedDuration: String
)

/**
 * Produces a stable express-service pattern for a train detail page.
 * A pattern is randomized per train number, but provincial-capital stations,
 * departure and arrival stations are mandatory calls.
 */
object RoutePresentationPlanner {
    fun plan(
        fullStations: List<String>,
        trainNumber: String,
        originalDuration: String,
        requiredCalls: Set<String> = emptySet()
    ): RouteDisplayPlan {
        val route = fullStations.filter { it.isNotBlank() }
        if (route.size <= 2) {
            return RouteDisplayPlan(route, 0, originalDuration)
        }

        val candidates = route.indices.filter { index ->
            index != 0 && index != route.lastIndex &&
                route[index] !in requiredCalls &&
                !LatestRailwayNetwork.isMajorHubStation(route[index])
        }
        if (candidates.isEmpty()) return RouteDisplayPlan(route, 0, originalDuration)

        val random = Random(trainNumber.hashCode())
        // Most generated services are through or limited-stop services.  The
        // Origin, terminal, protected hubs and the queried boarding/alighting
        // calls are excluded from candidates above, so this never skips them.
        val minimum = max(1, ceil(candidates.size * 0.65).toInt())
        val maximum = minOf(candidates.size, max(minimum, ceil(candidates.size * 0.85).toInt()))
        val skippedTarget = random.nextInt(minimum, maximum + 1)
        val skippedIndexes = candidates.shuffled(random).take(skippedTarget).toSet()
        val displayed = route.filterIndexed { index, _ -> index !in skippedIndexes }

        return RouteDisplayPlan(
            stations = displayed,
            skippedStationCount = skippedIndexes.size,
            adjustedDuration = shortenDuration(originalDuration, skippedIndexes.size, trainNumber)
        )
    }

    private fun shortenDuration(value: String, skipped: Int, key: String): String {
        val minutes = durationMinutes(value)
        if (minutes <= 0 || skipped == 0) return value

        // Express patterns save a modest amount of dwell and acceleration time.
        val requestedSaving = skipped * (3 + abs(key.hashCode() % 3))
        val safeMaximum = max(2, minutes / 7)
        return formatMinutes((minutes - requestedSaving.coerceAtMost(safeMaximum)).coerceAtLeast(8))
    }

    fun arrivalTime(departure: String, duration: String): String {
        val pieces = departure.split(":")
        val departureMinutes = (pieces.getOrNull(0)?.toIntOrNull() ?: 0) * 60 +
            (pieces.getOrNull(1)?.toIntOrNull() ?: 0)
        val arrival = (departureMinutes + durationMinutes(duration)) % (24 * 60)
        return "%02d:%02d".format(arrival / 60, arrival % 60)
    }

    fun durationMinutes(value: String): Int {
        val hours = Regex("(\\d+)小时").find(value)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val minutes = Regex("(\\d+)分").find(value)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        return hours * 60 + minutes
    }

    fun formatMinutes(total: Int): String = if (total >= 60) {
        "${total / 60}小时${total % 60}分钟"
    } else {
        "${total}分钟"
    }
}
