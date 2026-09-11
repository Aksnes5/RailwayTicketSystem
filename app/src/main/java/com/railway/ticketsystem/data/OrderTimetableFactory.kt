package com.railway.ticketsystem.data

import com.railway.ticketsystem.model.TimetableStopSnapshot
import com.railway.ticketsystem.model.Train

data class OrderTimetableSnapshot(
    val duration: String,
    val arrivalTime: String,
    val stops: List<TimetableStopSnapshot>,
    val routeStations: List<String>
)

/**
 * Captures the same stable timetable used by train detail, but only at checkout.
 * The stored snapshot is later rendered verbatim from the order record.
 */
object OrderTimetableFactory {
    fun capture(train: Train): OrderTimetableSnapshot {
        val fullRoute = RailwayData.getTrainRouteStations(train)
            .ifEmpty { listOf(train.departureStation, train.arrivalStation) }
        val plan = RoutePresentationPlanner.plan(
            fullStations = fullRoute,
            trainNumber = train.number,
            originalDuration = train.duration,
            requiredCalls = setOf(train.departureStation, train.arrivalStation)
        )
        val timetable = TrainStopSchedulePlanner.createAnchored(
            stations = plan.stations,
            trainNumber = train.number,
            queryDepartureStation = train.departureStation,
            queryArrivalStation = train.arrivalStation,
            queryDepartureTime = train.departureTime,
            queryDuration = train.duration
        )
        val stops = timetable.stops.map {
            TimetableStopSnapshot(
                stationName = it.stationName,
                arrivalTime = it.arrivalTime,
                departureTime = it.departureTime,
                dwellLabel = it.dwellLabel
            )
        }
        return OrderTimetableSnapshot(
            // The order is for the query section, not the entire through
            // service. Keep its ticket clocks and duration identical to the
            // result list while persisting the complete operating timetable.
            duration = train.duration,
            arrivalTime = train.arrivalTime,
            stops = stops,
            routeStations = fullRoute
        )
    }
}
