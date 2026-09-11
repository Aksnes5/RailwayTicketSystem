package com.railway.ticketsystem.data

import com.railway.ticketsystem.model.Station
import com.railway.ticketsystem.model.RailwayRoute
import com.railway.ticketsystem.model.RailwayRouteManager

/**
 * 车站导入管理器
 * 负责从已有线路中提取车站并导入到系统中
 */
object StationImportManager {
    
    /**
     * 从所有线路中提取车站
     * @return 提取到的车站列表（去重）
     */
    fun extractStationsFromRoutes(): List<Station> {
        val allRoutes = RailwayRouteManager.getAllRoutes()
        val stationSet = mutableSetOf<Station>()
        
        allRoutes.forEach { route ->
            route.stations.forEach { station ->
                stationSet.add(station)
            }
        }
        
        return stationSet.toList().sortedBy { it.name }
    }
    
    /**
     * 从指定线路中提取车站
     * @param routeId 线路ID
     * @return 该线路的车站列表
     */
    fun extractStationsFromRoute(routeId: String): List<Station> {
        val allRoutes = RailwayRouteManager.getAllRoutes()
        val route = allRoutes.find { it.routeId == routeId }
        
        return route?.stations ?: emptyList()
    }
    
    /**
     * 从指定线路名称中提取车站
     * @param routeName 线路名称
     * @return 该线路的车站列表
     */
    fun extractStationsFromRouteByName(routeName: String): List<Station> {
        val allRoutes = RailwayRouteManager.getAllRoutes()
        val route = allRoutes.find { it.routeName == routeName }
        
        return route?.stations ?: emptyList()
    }
    
    /**
     * 获取所有线路信息
     * @return 线路信息列表，包含线路ID、名称和车站数量
     */
    fun getAllRouteInfo(): List<RouteInfo> {
        val allRoutes = RailwayRouteManager.getAllRoutes()
        
        return allRoutes.map { route ->
            RouteInfo(
                routeId = route.routeId,
                routeName = route.routeName,
                stationCount = route.stations.size,
                routeType = route.routeType,
                startStation = route.stations.firstOrNull()?.name ?: "",
                endStation = route.stations.lastOrNull()?.name ?: ""
            )
        }
    }
    
    /**
     * 检查车站是否已存在于系统中
     * @param station 要检查的车站
     * @return 是否已存在
     */
    fun isStationExists(station: Station): Boolean {
        return RailwayGraphManager.isStationExists(station.name)
    }
    
    /**
     * 获取需要导入的车站（排除已存在的）
     * @param stations 要检查的车站列表
     * @return 需要导入的车站列表
     */
    fun getStationsToImport(stations: List<Station>): List<Station> {
        return stations.filter { !isStationExists(it) }
    }
    
    /**
     * 获取重复的车站（已存在的）
     * @param stations 要检查的车站列表
     * @return 重复的车站列表
     */
    fun getDuplicateStations(stations: List<Station>): List<Station> {
        return stations.filter { isStationExists(it) }
    }
    
    /**
     * 导入车站到系统中
     * @param stations 要导入的车站列表
     * @return 导入结果
     */
    fun importStations(stations: List<Station>): ImportResult {
        val newStations = stations.filter { !isStationExists(it) }
        val duplicateStations = stations.filter { isStationExists(it) }
        
        try {
            // 通过RailwayGraphManager添加新车站
            RailwayGraphManager.addStations(newStations)
            
            return ImportResult(
                totalStations = stations.size,
                newStations = newStations.size,
                duplicateStations = duplicateStations.size,
                success = true,
                message = "成功导入 ${newStations.size} 个新车站，跳过 ${duplicateStations.size} 个重复车站"
            )
        } catch (e: Exception) {
            return ImportResult(
                totalStations = stations.size,
                newStations = 0,
                duplicateStations = duplicateStations.size,
                success = false,
                message = "导入失败: ${e.message}"
            )
        }
    }
    
    /**
     * 线路信息数据类
     */
    data class RouteInfo(
        val routeId: String,
        val routeName: String,
        val stationCount: Int,
        val routeType: com.railway.ticketsystem.model.RouteType,
        val startStation: String,
        val endStation: String
    )
    
    /**
     * 导入结果数据类
     */
    data class ImportResult(
        val totalStations: Int,
        val newStations: Int,
        val duplicateStations: Int,
        val success: Boolean,
        val message: String = ""
    )
}
