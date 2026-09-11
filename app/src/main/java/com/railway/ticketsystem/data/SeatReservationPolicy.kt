package com.railway.ticketsystem.data

import com.railway.ticketsystem.model.Order

/** Rules shared by initial booking and ticket changes. */
object SeatReservationPolicy {
    private val inactiveStatuses = setOf("已取消", "已退款")

    fun isActive(order: Order): Boolean = order.status !in inactiveStatuses

    fun conflicts(existing: Iterable<Order>, candidate: Order): Boolean =
        existing.any { order ->
            order.id != candidate.id &&
                isActive(order) &&
                order.trainNumber == candidate.trainNumber &&
                order.departureDate == candidate.departureDate &&
                order.carNumber == candidate.carNumber &&
                order.seatNumber == candidate.seatNumber
        }
}
