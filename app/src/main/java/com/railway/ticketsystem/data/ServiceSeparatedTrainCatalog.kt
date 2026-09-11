package com.railway.ticketsystem.data

import com.railway.ticketsystem.model.RailwayRouteManager
import com.railway.ticketsystem.model.RouteType
import com.railway.ticketsystem.model.Train

/**
 * 查询缓存按物理路网隔离：高速网（含城际）与普速网。
 * C 字头不单独建图或缓存；因此不会与 G/D 车次隔离，更不会进入普速路径。
 */
object ServiceSeparatedTrainCatalog {
    private val lock = Any()
    private val cache = mutableMapOf<String, List<Train>>()

    fun find(from: String, to: String): List<Train> = synchronized(lock) {
        val types = listOf(RouteType.HIGH_SPEED, RouteType.CONVENTIONAL)
        types.flatMap { type ->
            val path = RailwayRouteManager.getRouteStationsMinStops(from, to, type)
            if (path.size < 2) {
                emptyList()
            } else {
                val key = "${type.name}:$from→$to"
                cache.getOrPut(key) {
                    TrainGenerator.generateTrainsForPair(from, to, routeType = type)
                }
            }
        }
    }

    fun find(from: String, to: String, routeType: RouteType): List<Train> = synchronized(lock) {
        if (RailwayRouteManager.getRouteStationsMinStops(from, to, routeType).size < 2) return@synchronized emptyList()
        cache.getOrPut("${routeType.name}:$from→$to") {
            TrainGenerator.generateTrainsForPair(from, to, routeType = routeType)
        }
    }
}
