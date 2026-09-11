package com.railway.ticketsystem.model

data class Ticket(
    val id: String,
    val trainNumber: String,
    val departureStation: String,
    val arrivalStation: String,
    val departureTime: String,
    val arrivalTime: String,
    val price: Double,
    val seatNumber: String,
    val carNumber: String, // 车厢号
    val passengerName: String,
    val bookingTime: String
) {
    // 组合座位信息：座位类型 车厢号 座位号
    val seatInfo: String
        get() = "二等座 ${carNumber}车 ${seatNumber}号"
}