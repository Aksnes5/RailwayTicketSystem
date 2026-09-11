package com.railway.ticketsystem.model

import java.io.Serializable

/** A consented automatic booking request for one specific selected seat. */
data class WaitlistRequest(
    val id: String,
    val userId: String,
    val requestedOrder: Order,
    val status: String, // 候补中、已兑现、已终止
    val createTime: String,
    val successProbability: Int = 60,
    val evaluationTime: Long = 0L,
    val fulfilledOrderId: String? = null,
    val fulfilledTime: String? = null,
    val resultMessage: String? = null
) : Serializable {
    val route: String
        get() = "${requestedOrder.departureStation} → ${requestedOrder.arrivalStation}"

    val seatInfo: String
        get() = requestedOrder.seatInfo
}
