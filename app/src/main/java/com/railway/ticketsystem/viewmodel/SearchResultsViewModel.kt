package com.railway.ticketsystem.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.railway.ticketsystem.data.DepartureTimingPolicy
import com.railway.ticketsystem.data.RailwayData
import com.railway.ticketsystem.data.RealRailwayRoutes
import com.railway.ticketsystem.data.TransferStationRule
import com.railway.ticketsystem.model.Train
import com.railway.ticketsystem.model.TransferTrain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SearchSortMode { EARLIEST, SHORTEST, CHEAPEST }

sealed class SearchUiState {
    object Loading : SearchUiState()
    data class Empty(val isDirect: Boolean, val message: String) : SearchUiState()
    data class DirectSuccess(
        val originalTrains: List<Train>,
        val displayTrains: List<Train>,
        val sortMode: SearchSortMode,
        val filterTypes: Set<String> = emptySet()
    ) : SearchUiState()
    data class TransferSuccess(
        val transferTrains: List<TransferTrain>
    ) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}

class SearchResultsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Loading)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var allDirectTrains: List<Train> = emptyList()
    private var allTransferTrains: List<TransferTrain> = emptyList()
    private var currentSortMode: SearchSortMode = SearchSortMode.EARLIEST
    private val activeFilters = mutableSetOf<String>()
    private var currentIsDirect = true
    private var departureStation = ""
    private var arrivalStation = ""
    private var departureDate = ""

    fun searchTickets(departure: String, arrival: String, date: String, isDirect: Boolean) {
        departureStation = departure
        arrivalStation = arrival
        departureDate = date
        currentIsDirect = isDirect

        viewModelScope.launch {
            _uiState.value = SearchUiState.Loading
            try {
                if (isDirect) {
                    val rawTrains = withContext(Dispatchers.IO) {
                        RailwayData.getTrainsSortedByTime(departure, arrival)
                    }
                    val sellableTrains = rawTrains.filter {
                        DepartureTimingPolicy.isBookable(date, it.departureTime)
                    }
                    allDirectTrains = sellableTrains
                    if (sellableTrains.isEmpty()) {
                        _uiState.value = SearchUiState.Empty(
                            isDirect = true,
                            message = "很抱歉，按您的查询条件，当前未找到从${departure}到${arrival}的直达列车。您可使用中转换乘功能，查询途中换乘一次的部分列车余票情况。"
                        )
                    } else {
                        applyDirectSortAndFilter()
                    }
                } else {
                    val transferRoutes = withContext(Dispatchers.IO) {
                        findTransferRoutes(departure, arrival)
                    }
                    allTransferTrains = transferRoutes
                    if (transferRoutes.isEmpty()) {
                        _uiState.value = SearchUiState.Empty(
                            isDirect = false,
                            message = "很抱歉，按您的查询条件，当前未找到从${departure}到${arrival}的中转列车。"
                        )
                    } else {
                        _uiState.value = SearchUiState.TransferSuccess(transferRoutes)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = SearchUiState.Error(e.message ?: "查询车次失败")
            }
        }
    }

    fun setSortMode(mode: SearchSortMode) {
        currentSortMode = mode
        if (currentIsDirect) {
            applyDirectSortAndFilter()
        }
    }

    fun toggleFilter(type: String) {
        if (activeFilters.contains(type)) {
            activeFilters.remove(type)
        } else {
            activeFilters.add(type)
        }
        if (currentIsDirect) {
            applyDirectSortAndFilter()
        }
    }

    private fun applyDirectSortAndFilter() {
        if (allDirectTrains.isEmpty()) {
            _uiState.value = SearchUiState.Empty(
                isDirect = true,
                message = "很抱歉，当前无符合筛选条件的车次。"
            )
            return
        }

        var filtered = if (activeFilters.isEmpty()) {
            allDirectTrains
        } else {
            allDirectTrains.filter { train ->
                activeFilters.any { type -> train.number.startsWith(type, ignoreCase = true) }
            }
        }

        filtered = when (currentSortMode) {
            SearchSortMode.EARLIEST -> filtered.sortedBy { parseTimeToMinutes(it.departureTime) }
            SearchSortMode.SHORTEST -> filtered.sortedBy { parseDurationToMinutes(it.duration) }
            SearchSortMode.CHEAPEST -> filtered.sortedBy { it.price }
        }

        _uiState.value = SearchUiState.DirectSuccess(
            originalTrains = allDirectTrains,
            displayTrains = filtered,
            sortMode = currentSortMode,
            filterTypes = activeFilters.toSet()
        )
    }

    private fun findTransferRoutes(from: String, to: String): List<TransferTrain> {
        val transferList = mutableListOf<TransferTrain>()
        val allRoutes = RealRailwayRoutes.getAllRoutes()
        val possibleTransferStations = mutableSetOf<String>()

        allRoutes.forEach { route ->
            val stationNames = route.stations.map { it.name }
            if (stationNames.contains(from) && stationNames.contains(to)) {
                val departureIndex = stationNames.indexOf(from)
                val arrivalIndex = stationNames.indexOf(to)
                if (departureIndex != -1 && arrivalIndex != -1) {
                    val startIndex = minOf(departureIndex, arrivalIndex)
                    val endIndex = maxOf(departureIndex, arrivalIndex)
                    for (i in startIndex + 1 until endIndex) {
                        possibleTransferStations.add(stationNames[i])
                    }
                }
            } else if (stationNames.contains(from)) {
                val departureIndex = stationNames.indexOf(from)
                for (i in departureIndex + 1 until stationNames.size) {
                    possibleTransferStations.add(stationNames[i])
                }
            } else if (stationNames.contains(to)) {
                val arrivalIndex = stationNames.indexOf(to)
                for (i in 0 until arrivalIndex) {
                    possibleTransferStations.add(stationNames[i])
                }
            }
        }

        for (transferStation in possibleTransferStations) {
            if (transferStation != from && transferStation != to) {
                val firstLegTrains = RailwayData.getTrainsSortedByTime(from, transferStation)
                val secondLegTrains = RailwayData.getTrainsSortedByTime(transferStation, to)
                if (firstLegTrains.isNotEmpty() && secondLegTrains.isNotEmpty()) {
                    for (first in firstLegTrains) {
                        for (second in secondLegTrains) {
                            val transferTime = calculateTransferMinutes(first.arrivalTime, second.departureTime)
                            if (TransferStationRule.isTransferTimeValid(first.arrivalStation, second.departureStation, transferTime)) {
                                val totalPrice = first.price + second.price
                                val totalDuration = calculateTotalDuration(first.duration, second.duration, transferTime)
                                transferList.add(
                                    TransferTrain(
                                        firstLeg = first,
                                        secondLeg = second,
                                        transferStation = transferStation,
                                        transferTime = transferTime,
                                        totalPrice = totalPrice,
                                        totalDuration = totalDuration
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        return transferList.sortedBy { parseDurationToMinutes(it.totalDuration) }.take(20)
    }

    private fun calculateTransferMinutes(firstArrivalTime: String, secondDepartureTime: String): Int {
        val firstArrival = parseTimeToMinutes(firstArrivalTime)
        val secondDeparture = parseTimeToMinutes(secondDepartureTime)
        var diff = secondDeparture - firstArrival
        if (diff < 0) diff += 24 * 60
        return diff
    }

    private fun parseTimeToMinutes(time: String): Int {
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return hour * 60 + minute
    }

    private fun parseDurationToMinutes(duration: String): Int {
        var total = 0
        val hourMatch = Regex("(\\d+)\\s*小时").find(duration)
        if (hourMatch != null) total += (hourMatch.groupValues[1].toIntOrNull() ?: 0) * 60
        val minuteMatch = Regex("(\\d+)\\s*分").find(duration)
        if (minuteMatch != null) total += (minuteMatch.groupValues[1].toIntOrNull() ?: 0)
        return if (total > 0) total else Int.MAX_VALUE
    }

    private fun calculateTotalDuration(firstDuration: String, secondDuration: String, transferMinutes: Int): String {
        val totalMinutes = parseDurationToMinutes(firstDuration) + parseDurationToMinutes(secondDuration) + transferMinutes
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}小时${minutes}分" else "${minutes}分"
    }
}
