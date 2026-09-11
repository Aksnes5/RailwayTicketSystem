package com.railway.ticketsystem.data

import java.math.BigDecimal
import java.math.RoundingMode

/** One yuan equals ten points across earning, refund, and ticket-change flows. */
object PointsPolicy {
    private val pointsPerYuan = BigDecimal.TEN

    fun fromAmount(amount: Double): Int = BigDecimal.valueOf(amount)
        .multiply(pointsPerYuan)
        .setScale(0, RoundingMode.DOWN)
        .max(BigDecimal.ZERO)
        .toInt()
}
