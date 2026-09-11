package com.railway.ticketsystem.data

import kotlin.random.Random

/**
 * Local single-user inventory simulator. Stock is refreshed only when the user confirms an order.
 * A sold-out result routes the user into the candidate workflow instead of requiring another account.
 */
object BookingAvailability {
    fun canConfirmDirectTicket(): Boolean = Random.nextInt(100) >= 30
}
