package com.railway.ticketsystem.data

import com.railway.ticketsystem.model.RailwayRoute
import com.railway.ticketsystem.model.RouteType
import com.railway.ticketsystem.model.Station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RailwayNetworkQualityGateTest {
    @Test
    fun rejectsBlankOrAdjacentDuplicateStations() {
        val report = RailwayNetworkQualityGate.inspectStructure(route("", listOf("福州", "福州")))

        assertFalse(report.isAccepted)
        assertTrue(report.issues.any { it.rule == "route_id" })
        assertTrue(report.issues.any { it.rule == "adjacent_duplicate" })
    }

    @Test
    fun detectsFallbackPinsAndImplausiblyLongSegments() {
        val report = RailwayNetworkQualityGate.auditGeography(
            route("quality-demo", listOf("甲", "乙")),
            coordinateResolver = { station ->
                when (station) {
                    "甲" -> StationCoordinate(100.0, 20.0, true)
                    "乙" -> StationCoordinate(120.0, 40.0, false)
                    else -> null
                }
            }
        )

        assertTrue(report.isAccepted)
        assertTrue(report.issues.any { it.rule == "city_fallback_pin" })
        assertTrue(report.issues.any { it.rule == "implausible_straight_segment" })
    }

    @Test
    fun resolvesFujianYongtaiAsOneStableStationIdentity() {
        assertEquals(RailwayNetworkIdentity.station("永泰").id, RailwayNetworkIdentity.station("福州永泰").id)
        assertEquals("永泰", RailwayNetworkIdentity.canonicalName("永泰站"))
    }

    private fun route(id: String, stationNames: List<String>) = RailwayRoute(
        routeId = id,
        routeName = "测试线路",
        stations = stationNames.map { Station(it, it) },
        segmentPrices = stationNames.zipWithNext().associate { (from, to) -> "$from-$to" to 1.0 },
        totalPrice = 1.0,
        totalDuration = "10分钟",
        routeType = RouteType.HIGH_SPEED
    )
}
