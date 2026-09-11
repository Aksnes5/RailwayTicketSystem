package com.railway.ticketsystem.data

import com.railway.ticketsystem.model.RouteType
import com.railway.ticketsystem.model.Train

/**
 * Provides a stable, user-facing EMU type for a generated high-speed service.
 * The same train number always resolves to the same type, so the detail page
 * and the purchased-ticket detail do not disagree. Conventional services are
 * intentionally excluded.
 */
object TrainSetResolver {
    private val gSeries = listOf(
        "和谐号 CRH380A", "和谐号 CRH380AL",
        "和谐号 CRH380B", "和谐号 CRH380BL", "和谐号 CRH380BJ",
        "和谐号 CRH380C", "和谐号 CRH380CL", "和谐号 CRH380D", "和谐号 CRH380DL",
        "复兴号 CR400AF", "复兴号 CR400AF-A", "复兴号 CR400AF-B", "复兴号 CR400AF-C",
        "复兴号 CR400AF-G", "复兴号 CR400AF-S", "复兴号 CR400AF-Z",
        "复兴号 CR400BF", "复兴号 CR400BF-A", "复兴号 CR400BF-B", "复兴号 CR400BF-C",
        "复兴号 CR400BF-G", "复兴号 CR400BF-S", "复兴号 CR400BF-Z"
    )

    private val dSeries = listOf(
        "和谐号 CRH1A", "和谐号 CRH1B", "和谐号 CRH1E", "和谐号 CRH1A-A",
        "和谐号 CRH2A", "和谐号 CRH2B", "和谐号 CRH2C", "和谐号 CRH2E", "和谐号 CRH2G", "和谐号 CRH2H",
        "和谐号 CRH3A", "和谐号 CRH3C", "和谐号 CRH3G",
        "和谐号 CRH5A", "和谐号 CRH5E", "和谐号 CRH5G"
    )

    private val cSeries = listOf(
        "和谐号 CRH2A", "和谐号 CRH2C", "和谐号 CRH3C",
        "和谐号 CRH380D", "复兴号 CR400AF", "复兴号 CR400BF"
    )

    fun modelFor(train: Train): String? = modelFor(train.number, train.routeType)

    fun modelFor(trainNumber: String, routeType: RouteType? = null): String? {
        if (routeType == RouteType.CONVENTIONAL) return null
        val number = trainNumber.trim().uppercase()
        val choices = when (number.firstOrNull()) {
            'G' -> gSeries
            'D' -> dSeries
            'C' -> cSeries
            else -> return null
        }
        return choices[Math.floorMod(number.hashCode(), choices.size)]
    }
}
