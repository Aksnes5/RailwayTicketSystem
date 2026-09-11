package com.railway.ticketsystem.model

import java.io.Serializable

data class TimetableStopSnapshot(
    val stationName: String,
    val arrivalTime: String,
    val departureTime: String,
    val dwellLabel: String
) : Serializable

data class Order(
    val id: String,
    val userId: String,
    val trainNumber: String,
    val departureStation: String,
    val arrivalStation: String,
    val departureTime: String,
    val arrivalTime: String,
    val departureDate: String,
    val seatType: String,
    val seatNumber: String,
    val carNumber: String, // 车厢号
    val passengerName: String,
    val passengerIdCard: String,
    val passengerPhone: String,
    val basePrice: Double,
    val finalPrice: Double,
    val status: String, // "待支付", "已支付", "已完成", "已取消"
    val createTime: String,
    val payTime: String?,
    val groupId: String? = null,
    val groupPassengerCount: Int = 1,
    val itineraryId: String? = null,
    val paymentBatchId: String? = null,
    val paymentDeadlineMillis: Long = 0L,
    val cancelReason: String? = null,
    val inventoryReleasePending: Boolean = false,
    val paymentEffectsPending: Boolean = false,
    /** Immutable calls and times shown to the traveler at checkout. */
    val timetableStops: List<TimetableStopSnapshot>? = null,
    val timetableDuration: String? = null,
    /** Complete physical route retained separately from the selected stopping pattern. */
    val routeStations: List<String>? = null,
    /**
     * Calendar date the train arrives on.
     *
     * Null for orders written before this field existed and for callers that have not filled
     * it in; readers then fall back to deriving the arrival from departure + duration, which
     * cannot tell an overnight leg from a same-day one.
     *
     * Declared last on purpose: several call sites construct an Order with positional
     * arguments, so adding a parameter anywhere earlier would silently shift them.
     */
    val arrivalDate: String? = null
) : Serializable {
    
    // 组合座位信息：座位类型 车厢号 座位号
    val seatInfo: String
        get() = "$seatType ${carNumber}车 ${seatNumber}号"
}
