package com.railway.ticketsystem.data

import com.railway.ticketsystem.model.RailwayRouteManager
import com.railway.ticketsystem.model.RouteType
import com.railway.ticketsystem.model.Train

/**
 * 同车接续 / 分段补票方案规划器
 * 当直达车票紧张或候补时，在同一趟列车内规划"前段有座 + 后段无座/换座"的两段式乘车方案，
 * 旅客无需下车换乘，实现无缝出行。
 */
object SplitTicketingPlanner {

    data class SplitLeg(
        val fromStation: String,
        val toStation: String,
        val carriage: String,
        val seatType: String,
        val seatNumber: String,
        val price: Double,
        val statusText: String
    )

    data class SplitPlan(
        val trainNumber: String,
        val intermediateStation: String,
        val firstLeg: SplitLeg,
        val secondLeg: SplitLeg,
        val totalPrice: Double,
        val tip: String
    )

    /**
     * 为指定列车规划同车分段补票方案
     */
    fun findSameTrainSplitPlan(train: Train, departureDate: String): SplitPlan? {
        val stops = RailwayRouteManager.getRouteStationsMinStops(
            train.departureStation,
            train.arrivalStation,
            train.routeType ?: RouteType.HIGH_SPEED
        )

        // 选取中间停靠站作为换座/接续点
        val splitStation = if (stops.size >= 3) {
            stops[stops.size / 2]
        } else {
            "孝感东" // 默认就近中途站
        }

        val basePrice = train.price
        val firstPrice = (basePrice * 0.58).coerceAtLeast(10.0)
        val secondPrice = (basePrice * 0.42).coerceAtLeast(10.0)

        val firstLeg = SplitLeg(
            fromStation = train.departureStation,
            toStation = splitStation,
            carriage = "03车",
            seatType = "二等座",
            seatNumber = "08A (靠窗)",
            price = firstPrice,
            statusText = "有票 · 独立座位"
        )

        val secondLeg = SplitLeg(
            fromStation = splitStation,
            toStation = train.arrivalStation,
            carriage = "03车",
            seatType = "无座/就近补座",
            seatNumber = "车内就近补票 / 站内乘车",
            price = secondPrice,
            statusText = "免下车 · 列车内续乘"
        )

        return SplitPlan(
            trainNumber = train.number,
            intermediateStation = splitStation,
            firstLeg = firstLeg,
            secondLeg = secondLeg,
            totalPrice = firstPrice + secondPrice,
            tip = "全程乘坐同一趟 ${train.number} 次列车，无需出站或换乘，到 $splitStation 后车内换座或续乘"
        )
    }
}
