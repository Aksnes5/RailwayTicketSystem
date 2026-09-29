package com.railway.ticketsystem.data

import com.railway.ticketsystem.model.Order
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TravelRecoveryPlannerTest {
    @Test
    fun pendingPaymentOnlyOffersPaymentContinuation() {
        val plan = TravelRecoveryPlanner.create(order(status = "待支付"))

        assertEquals("待完成支付", plan.status)
        assertEquals(listOf(TravelRecoveryOption.Type.MONITOR), plan.options.map { it.type })
    }

    @Test
    fun transferJourneyGetsConnectionSafeguardBeforeDeparture() {
        val plan = TravelRecoveryPlanner.create(order(itineraryId = "ITINERARY_1"), now = 1L)

        assertEquals("出发前保障中", plan.status)
        assertTrue(plan.options.any { it.type == TravelRecoveryOption.Type.CONNECTION })
        assertTrue(plan.detail.contains("G123"))
    }

    private fun order(status: String = "已支付", itineraryId: String? = null) = Order(
        id = "ORDER_1",
        userId = "USER_1",
        trainNumber = "G123",
        departureStation = "武汉",
        arrivalStation = "广州南",
        departureTime = "08:00",
        arrivalTime = "12:00",
        departureDate = "2099-01-01",
        seatType = "二等座",
        seatNumber = "01A",
        carNumber = "03",
        passengerName = "测试乘客",
        passengerIdCard = "420000199001010000",
        passengerPhone = "13800000000",
        basePrice = 500.0,
        finalPrice = 500.0,
        status = status,
        createTime = "2098-12-01 10:00:00",
        payTime = null,
        itineraryId = itineraryId
    )
}
