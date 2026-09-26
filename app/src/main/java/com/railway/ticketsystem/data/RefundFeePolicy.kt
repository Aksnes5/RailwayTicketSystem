package com.railway.ticketsystem.data

import com.railway.ticketsystem.model.Order
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

data class RefundBreakdown(
    val ticketPrice: Double,
    val feeRatePercent: Int,
    val feeAmount: Double,
    val refundAmount: Double,
    val hoursUntilDeparture: Double,
    val isRefundable: Boolean,
    val policyExplanation: String
)

/**
 * 12306 官方阶梯退票手续费率规则：
 * 1. 开车前 8 天（含）以上：免费退票（手续费率 0%）
 * 2. 开车前 48 小时以上、不足 8 天：按票价 5% 计收
 * 3. 开车前 24 小时以上、不足 48 小时：按票价 10% 计收
 * 4. 不足 24 小时：按票价 20% 计收
 * 5. 开车后：不可退票（仅限当日办理改签）
 *
 * 最低手续费标准：按 2 元计算（若计算结果低于 2 元，按 2 元核收；若票价本身低于 2 元，则按实际票价核收）。
 */
object RefundFeePolicy {

    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).apply {
        isLenient = false
    }

    fun calculateRefund(order: Order, currentTimeMillis: Long = System.currentTimeMillis()): RefundBreakdown {
        val departureTimeMillis = parseDepartureTime(order.departureDate, order.departureTime)
        val diffMillis = departureTimeMillis - currentTimeMillis
        val hoursUntilDeparture = diffMillis.toDouble() / (1000.0 * 60.0 * 60.0)

        val price = order.finalPrice

        if (hoursUntilDeparture < 0) {
            return RefundBreakdown(
                ticketPrice = price,
                feeRatePercent = 100,
                feeAmount = price,
                refundAmount = 0.0,
                hoursUntilDeparture = hoursUntilDeparture,
                isRefundable = false,
                policyExplanation = "列车已开行，根据铁路客运规定已无法办理退票。"
            )
        }

        val feeRate: Int
        val explanation: String

        when {
            hoursUntilDeparture >= 8 * 24 -> {
                feeRate = 0
                explanation = "开车前8天以上退票，免收退票费"
            }
            hoursUntilDeparture >= 48 -> {
                feeRate = 5
                explanation = "开车前48小时至8天以内，按票价5%收取退票手续费"
            }
            hoursUntilDeparture >= 24 -> {
                feeRate = 10
                explanation = "开车前24小时至48小时以内，按票价10%收取退票手续费"
            }
            else -> {
                feeRate = 20
                explanation = "开车前24小时以内，按票价20%收取退票手续费"
            }
        }

        val calculatedFee = if (feeRate == 0) {
            0.0
        } else {
            val rawFee = price * (feeRate.toDouble() / 100.0)
            // 向上取整到角/元，最低2元（不超票面本身）
            val minFee = if (price < 2.0) price else 2.0
            max(minFee, ceil(rawFee))
        }

        val actualRefund = max(0.0, price - calculatedFee)

        return RefundBreakdown(
            ticketPrice = price,
            feeRatePercent = feeRate,
            feeAmount = calculatedFee,
            refundAmount = actualRefund,
            hoursUntilDeparture = hoursUntilDeparture,
            isRefundable = true,
            policyExplanation = explanation
        )
    }

    private fun parseDepartureTime(dateStr: String, timeStr: String): Long {
        return runCatching {
            dateTimeFormat.parse("$dateStr $timeStr")?.time
        }.getOrNull() ?: (System.currentTimeMillis() + 24 * 3600 * 1000L)
    }
}
