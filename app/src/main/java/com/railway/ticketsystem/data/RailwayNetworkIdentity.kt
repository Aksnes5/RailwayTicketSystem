package com.railway.ticketsystem.data

import com.railway.ticketsystem.model.RouteType
import com.railway.ticketsystem.model.StationNetwork
import java.security.MessageDigest

/** Stable IDs retain the railway network and line context during map imports and caching. */
@JvmInline
value class RailwayStationId(val value: String)

@JvmInline
value class RailwayLineSegmentId(val value: String)

data class RailwayStationIdentity(
    val id: RailwayStationId,
    val displayName: String,
    val network: StationNetwork
)

object RailwayNetworkIdentity {
    private val aliases = mapOf(
        "永泰站" to "永泰",
        "福州永泰" to "永泰",
        "永泰（福州）" to "永泰",
        "厦门站" to "厦门",
        "福州站" to "福州",
        "汉口站" to "汉口",
        "武汉站" to "武汉",
        "北京站" to "北京",
        "广州站" to "广州"
    )

    fun canonicalName(rawName: String): String {
        val compact = rawName.trim().replace("　", "").replace(" ", "")
        return aliases[compact] ?: compact.removeSuffix("站")
    }

    fun station(
        rawName: String,
        network: StationNetwork = StationNetwork.HIGH_SPEED,
        officialCode: String? = null
    ): RailwayStationIdentity {
        val name = canonicalName(rawName)
        val code = officialCode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: stableToken(name)
        return RailwayStationIdentity(
            id = RailwayStationId("CN-RS-${network.name.take(1)}-$code"),
            displayName = name,
            network = network
        )
    }

    fun segment(lineId: String, fromStation: String, toStation: String, routeType: RouteType): RailwayLineSegmentId {
        val stationNetwork = if (routeType == RouteType.CONVENTIONAL) StationNetwork.CONVENTIONAL else StationNetwork.HIGH_SPEED
        val from = station(fromStation, stationNetwork).id.value
        val to = station(toStation, stationNetwork).id.value
        return RailwayLineSegmentId("CN-LS-${stationNetwork.name.take(1)}-${lineId.trim().uppercase()}-$from-$to")
    }

    fun coordinateLookupName(rawName: String): String = canonicalName(rawName)

    private fun stableToken(name: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(name.toByteArray(Charsets.UTF_8))
        return digest.take(6).joinToString("") { "%02X".format(it) }
    }
}
