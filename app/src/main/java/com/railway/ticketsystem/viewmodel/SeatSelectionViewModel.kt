package com.railway.ticketsystem.viewmodel

import androidx.lifecycle.ViewModel
import com.railway.ticketsystem.data.SeatAvailability
import com.railway.ticketsystem.data.SeatInventoryRepository
import com.railway.ticketsystem.model.Passenger
import com.railway.ticketsystem.model.SeatType
import com.railway.ticketsystem.model.SeatTypes
import com.railway.ticketsystem.model.TicketType
import com.railway.ticketsystem.model.Train
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SeatSelectionUiState(
    val train: Train? = null,
    val departureDate: String = "",
    val isTransfer: Boolean = false,
    val availableSeatTypes: List<SeatType> = emptyList(),
    val selectedSeatType: SeatType? = null,
    val availabilities: Map<String, SeatAvailability> = emptyMap(),
    val selectedPassengers: List<Passenger> = emptyList(),
    val totalAmount: Double = 0.0,
    val requiresWaitlist: Boolean = false
)

class SeatSelectionViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SeatSelectionUiState())
    val uiState: StateFlow<SeatSelectionUiState> = _uiState.asStateFlow()

    private var seatInventoryRepository: SeatInventoryRepository? = null

    fun initialize(
        train: Train,
        departureDate: String,
        isTransfer: Boolean,
        repository: SeatInventoryRepository
    ) {
        seatInventoryRepository = repository
        val seatTypes = SeatTypes.forTrain(train)
        val initialSeatType = seatTypes.firstOrNull()
        val availabilities = repository.getAvailabilities(train, departureDate)
        val isWaitlist = initialSeatType?.let { availabilities[it.name]?.requiresWaitlist } ?: false

        _uiState.value = SeatSelectionUiState(
            train = train,
            departureDate = departureDate,
            isTransfer = isTransfer,
            availableSeatTypes = seatTypes,
            selectedSeatType = initialSeatType,
            availabilities = availabilities,
            totalAmount = computeTotal(train, initialSeatType, emptyList()),
            requiresWaitlist = isWaitlist
        )
    }

    fun selectSeatType(seatType: SeatType) {
        val state = _uiState.value
        val train = state.train ?: return
        val repo = seatInventoryRepository ?: return
        val availability = repo.getAvailability(train, state.departureDate, seatType.name)
        val isWaitlist = availability.requiresWaitlist

        _uiState.value = state.copy(
            selectedSeatType = seatType,
            requiresWaitlist = isWaitlist,
            totalAmount = computeTotal(train, seatType, state.selectedPassengers)
        )
    }

    fun updateSelectedPassengers(passengers: List<Passenger>) {
        val state = _uiState.value
        val train = state.train ?: return
        _uiState.value = state.copy(
            selectedPassengers = passengers,
            totalAmount = computeTotal(train, state.selectedSeatType, passengers)
        )
    }

    private fun computeTotal(train: Train, seatType: SeatType?, passengers: List<Passenger>): Double {
        if (seatType == null) return 0.0
        val basePrice = train.price * seatType.multiplier
        if (passengers.isEmpty()) return basePrice
        return passengers.sumOf { passenger ->
            val discount = TicketType.fromLabel(passenger.ticketType).discountRate
            basePrice * discount
        }
    }
}
