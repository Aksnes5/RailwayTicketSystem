package com.railway.ticketsystem.model

enum class TransferRisk { STEADY, TIGHT, NOT_RECOMMENDED }

/**
 * 中转车次数据类
 * 包含两段车次的详细信息和中转站信息
 */
data class TransferTrain(

    val firstLeg: Train,           // 第一段车次
    val secondLeg: Train,          // 第二段车次
    val transferStation: String,   // 中转站
    val transferTime: Int,         // 中转时间（分钟）
    val totalPrice: Double,        // 总价格
    val totalDuration: String      // 总耗时
) {
    // 获取显示用的车次号
    val displayNumber: String
        get() = "中转${firstLeg.number}→${secondLeg.number}"
    
    // 获取出发站
    val departureStation: String
        get() = firstLeg.departureStation
    
    // 获取到达站
    val arrivalStation: String
        get() = secondLeg.arrivalStation
    
    // 获取出发时间
    val departureTime: String
        get() = firstLeg.departureTime
    
    // 获取到达时间
    val arrivalTime: String
        get() = secondLeg.arrivalTime
    
    // 获取可用座位数（取两段车次的最小值）
    val availableSeats: Int
        get() = minOf(firstLeg.availableSeats, secondLeg.availableSeats)
    
    // 获取中转站显示信息
    val transferInfo: String
        get() = "在${transferStation}中转${transferTime}分钟"
    val risk: TransferRisk
        get() = when {
            transferTime >= 90 -> TransferRisk.STEADY
            transferTime >= 45 -> TransferRisk.TIGHT
            else -> TransferRisk.NOT_RECOMMENDED
        }

    val riskLabel: String
        get() = when (risk) {
            TransferRisk.STEADY -> "稳妥换乘"
            TransferRisk.TIGHT -> "时间较紧"
            TransferRisk.NOT_RECOMMENDED -> "不建议换乘"
        }

    val stationGuide: String
        get() = when (risk) {
            TransferRisk.STEADY -> "到达后按站内换乘标识前往候车区，建议提前 ${transferTime - 30} 分钟开始候车。"
            TransferRisk.TIGHT -> "建议到达后直接前往下一程检票口，预留安检与步行时间。"
            TransferRisk.NOT_RECOMMENDED -> "换乘时间低于 45 分钟，存在较高误车风险，建议优先选择其他方案。"
        }
    
    // 获取两段车次的详细信息
    val legDetails: String
        get() = "${firstLeg.number} ${firstLeg.departureStation}→${firstLeg.arrivalStation} " +
                "${firstLeg.departureTime}→${firstLeg.arrivalTime} (${firstLeg.duration})\n" +
                "${secondLeg.number} ${secondLeg.departureStation}→${secondLeg.arrivalStation} " +
                "${secondLeg.departureTime}→${secondLeg.arrivalTime} (${secondLeg.duration})"
}
