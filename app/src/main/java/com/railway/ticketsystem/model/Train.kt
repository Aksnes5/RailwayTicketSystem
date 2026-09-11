package com.railway.ticketsystem.model

import java.io.Serializable

data class Train(
    val number: String,
    val departureStation: String,
    val arrivalStation: String,
    val departureTime: String,
    val arrivalTime: String,
    val duration: String,
    val price: Double, // 二等座价格
    val availableSeats: Int,
    val viaStations: List<String> = emptyList(), // 途径车站列表
    /** The generator and router must never mix this with the other service network. */
    val routeType: RouteType? = RouteType.HIGH_SPEED,
    /**
     * The actual service route. A search result remains the traveller's selected
     * section, while this list may begin earlier or terminate later.
     */
    val serviceStations: List<String> = emptyList()
) : Serializable

data class SeatType(
    val name: String,
    val multiplier: Double,
    val availableSeats: List<String>
)

object SeatTypes {
    val SECOND_CLASS = SeatType("二等座", 1.0, listOf("A", "B", "C", "D", "F"))
    val FIRST_CLASS = SeatType("一等座", 1.6, listOf("A", "B", "D", "F"))
    val BUSINESS_CLASS = SeatType("商务座", 3.0, listOf("A", "F"))
    val HARD_SEAT = SeatType("硬座", 1.0, listOf("A", "B", "C", "D", "F"))
    val HARD_SLEEPER = SeatType("硬卧", 1.55, listOf("上", "中", "下"))
    val SOFT_SLEEPER = SeatType("软卧", 2.2, listOf("上", "下"))
    
    val allTypes = listOf(SECOND_CLASS, FIRST_CLASS, BUSINESS_CLASS)
    val conventionalTypes = listOf(HARD_SEAT, HARD_SLEEPER, SOFT_SLEEPER)

    fun forTrain(train: Train): List<SeatType> = when {
        train.routeType == RouteType.CONVENTIONAL -> conventionalTypes
        train.number.trim().startsWith("D", ignoreCase = true) -> listOf(SECOND_CLASS, FIRST_CLASS)
        else -> allTypes
    }
}
