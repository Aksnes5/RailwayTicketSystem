package com.railway.ticketsystem.data

import com.railway.ticketsystem.model.RailwayRoute
import com.railway.ticketsystem.model.RouteType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Deterministic quality checks for the locally bundled passenger railway network.
 * Structural checks are safe during graph initialisation; geographic checks run afterwards.
 */
enum class RailwayDataIssueSeverity { BLOCKER, WARNING }

data class RailwayDataIssue(
    val severity: RailwayDataIssueSeverity,
    val rule: String,
    val routeId: String,
    val stations: List<String>,
    val message: String
)

data class RailwayRouteQualityReport(
    val routeId: String,
    val issues: List<RailwayDataIssue>
) {
    val isAccepted: Boolean get() = issues.none { it.severity == RailwayDataIssueSeverity.BLOCKER }
    val warningCount: Int get() = issues.count { it.severity == RailwayDataIssueSeverity.WARNING }
}

object RailwayNetworkQualityGate {
    /** Checks that are safe before every call to RailwayRouteManager.addRoute(). */
    fun inspectStructure(route: RailwayRoute): RailwayRouteQualityReport {
        val issues = mutableListOf<RailwayDataIssue>()
        val stations = route.stations.map { RailwayNetworkIdentity.canonicalName(it.name) }
        fun issue(severity: RailwayDataIssueSeverity, rule: String, names: List<String>, message: String) {
            issues += RailwayDataIssue(severity, rule, route.routeId, names, message)
        }

        if (route.routeId.isBlank()) issue(RailwayDataIssueSeverity.BLOCKER, "route_id", emptyList(), "线路缺少稳定 routeId")
        if (stations.size < 2) issue(RailwayDataIssueSeverity.BLOCKER, "station_count", stations, "线路至少需要两个车站")
        stations.forEachIndexed { index, station ->
            if (station.isBlank()) issue(RailwayDataIssueSeverity.BLOCKER, "station_name", emptyList(), "第 ${index + 1} 个车站为空")
        }
        stations.zipWithNext().forEach { (from, to) ->
            if (from == to) issue(RailwayDataIssueSeverity.BLOCKER, "adjacent_duplicate", listOf(from, to), "相邻站点不能相同")
        }
        route.segmentPrices.forEach { (segment, price) ->
            if (!price.isFinite() || price < 0.0) issue(RailwayDataIssueSeverity.BLOCKER, "segment_fare", emptyList(), "区段 $segment 的票价无效")
        }
        return RailwayRouteQualityReport(route.routeId, issues)
    }

    /**
     * Run after route registration. It catches missing station pins, city fallbacks and
     * implausibly long straight segments before a route is exposed on the map.
     */
    fun auditGeography(
        route: RailwayRoute,
        coordinateResolver: (String) -> StationCoordinate? = StationCoordinateCatalog::resolve
    ): RailwayRouteQualityReport {
        val issues = inspectStructure(route).issues.toMutableList()
        val maxSegmentKm = if (route.routeType == RouteType.CONVENTIONAL) 520.0 else 360.0
        route.stations.zipWithNext().forEach { (from, to) ->
            val fromName = RailwayNetworkIdentity.canonicalName(from.name)
            val toName = RailwayNetworkIdentity.canonicalName(to.name)
            val fromPoint = coordinateResolver(fromName)
            val toPoint = coordinateResolver(toName)
            if (fromPoint == null || toPoint == null) {
                issues += RailwayDataIssue(
                    RailwayDataIssueSeverity.WARNING,
                    "missing_station_pin",
                    route.routeId,
                    listOfNotNull(if (fromPoint == null) fromName else null, if (toPoint == null) toName else null),
                    "地图缺少精确车站坐标，不能可靠绘制该区段"
                )
                return@forEach
            }
            if (!fromPoint.isExactStation || !toPoint.isExactStation) {
                issues += RailwayDataIssue(
                    RailwayDataIssueSeverity.WARNING,
                    "city_fallback_pin",
                    route.routeId,
                    listOf(fromName, toName),
                    "区段使用城市级坐标回退，需补充车站精确描点"
                )
            }
            val distance = haversineKilometres(fromPoint, toPoint)
            if (distance > maxSegmentKm) {
                issues += RailwayDataIssue(
                    RailwayDataIssueSeverity.WARNING,
                    "implausible_straight_segment",
                    route.routeId,
                    listOf(fromName, toName),
                    "相邻站直线距离 ${distance.toInt()} km，需核对线路或联络线描点"
                )
            }
        }
        return RailwayRouteQualityReport(route.routeId, issues)
    }

    fun auditAllGeography(
        routes: Collection<RailwayRoute>,
        coordinateResolver: (String) -> StationCoordinate? = StationCoordinateCatalog::resolve
    ): List<RailwayRouteQualityReport> = routes.map { auditGeography(it, coordinateResolver) }

    private fun haversineKilometres(a: StationCoordinate, b: StationCoordinate): Double {
        val latDelta = Math.toRadians(b.latitude - a.latitude)
        val lonDelta = Math.toRadians(b.longitude - a.longitude)
        val x = sin(latDelta / 2).pow(2) +
            cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(lonDelta / 2).pow(2)
        return 6_371.0088 * 2 * atan2(sqrt(x), sqrt(1 - x))
    }
}
