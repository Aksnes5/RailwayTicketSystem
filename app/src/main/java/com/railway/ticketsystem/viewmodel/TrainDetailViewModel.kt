package com.railway.ticketsystem.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.railway.ticketsystem.data.RailwayData
import com.railway.ticketsystem.data.RoutePresentationPlanner
import com.railway.ticketsystem.data.TimetableStopStatusResolver
import com.railway.ticketsystem.data.TrainStopSchedule
import com.railway.ticketsystem.data.TrainStopSchedulePlanner
import com.railway.ticketsystem.model.Train
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TrainDetailViewModel : ViewModel() {

    private val _train = MutableStateFlow<Train?>(null)
    val train: StateFlow<Train?> = _train.asStateFlow()

    private val _departureDate = MutableStateFlow("")
    val departureDate: StateFlow<String> = _departureDate.asStateFlow()

    private val _timetable = MutableStateFlow<List<TrainStopSchedule>>(emptyList())
    val timetable: StateFlow<List<TrainStopSchedule>> = _timetable.asStateFlow()

    fun initialize(train: Train, departureDate: String) {
        _train.value = train
        _departureDate.value = departureDate
        loadTimetable(train, departureDate)
    }

    fun refreshStopStatuses() {
        val currentTrain = _train.value ?: return
        val currentTimetable = _timetable.value
        if (currentTimetable.isEmpty()) return

        val refreshed = TimetableStopStatusResolver.resolve(
            currentTimetable,
            currentTrain.number,
            _departureDate.value
        )
        _timetable.value = refreshed
    }

    private fun loadTimetable(train: Train, date: String) {
        viewModelScope.launch {
            val withStatuses = withContext(Dispatchers.IO) {
                val fullRouteStations = RailwayData.getTrainRouteStations(train)
                    .ifEmpty { listOf(train.departureStation, train.arrivalStation) }
                val displayPlan = RoutePresentationPlanner.plan(
                    fullStations = fullRouteStations,
                    trainNumber = train.number,
                    originalDuration = train.duration,
                    requiredCalls = setOf(train.departureStation, train.arrivalStation)
                )
                val timetable = TrainStopSchedulePlanner.createAnchored(
                    stations = displayPlan.stations,
                    trainNumber = train.number,
                    queryDepartureStation = train.departureStation,
                    queryArrivalStation = train.arrivalStation,
                    queryDepartureTime = train.departureTime,
                    queryDuration = train.duration
                )
                TimetableStopStatusResolver.resolve(timetable.stops, train.number, date)
            }
            _timetable.value = withStatuses
        }
    }
}
