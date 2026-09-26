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

    fun clearCache() = synchronized(lock) {
        cache.clear()
    }

    fun find(from: String, to: String): List<Train> = synchronized(lock) {
        if (DataSourceModePreferences.isRealMode()) {
            // 官方实盘模式：严格只返回真实官方车次，若两地无直达官方车次，如实返回空列表，绝不混杂模拟车次
            return@synchronized RealTrainCatalog.find(from, to)
        }

        val types = listOf(RouteType.HIGH_SPEED, RouteType.CONVENTIONAL)
        types.flatMap { type ->
            val path = RailwayRouteManager.getRouteStationsMinStops(from, to, type)
            if (path.size < 2) {
                emptyList()
            } else {
                val key = "${type.name}:$from→$to"
                cache.getOrPut(key) {
                    generate(from, to, type)
                }
            }
        }
    }

    fun find(from: String, to: String, routeType: RouteType): List<Train> = synchronized(lock) {
        if (DataSourceModePreferences.isRealMode()) {
            // 官方实盘模式：严格只返回真实官方车次，若两地无直达官方车次，如实返回空列表，绝不混杂模拟车次
            return@synchronized RealTrainCatalog.find(from, to).filter { it.routeType == routeType }
        }

        if (RailwayRouteManager.getRouteStationsMinStops(from, to, routeType).size < 2) return@synchronized emptyList()
        cache.getOrPut("${routeType.name}:$from→$to") {
            generate(from, to, routeType)
        }
    }

    /** 生成一次并按探针记账，便于把中转搜索的耗时拆到"生成了几对、共花多久"上。 */
    private fun generate(from: String, to: String, routeType: RouteType): List<Train> {
        val startedAt = System.nanoTime()
        val trains = TrainGenerator.generateTrainsForPair(from, to, routeType = routeType)
        RailwayRouteManager.Probe.generations++
        RailwayRouteManager.Probe.generateNanos += System.nanoTime() - startedAt
        return trains
    }
}
