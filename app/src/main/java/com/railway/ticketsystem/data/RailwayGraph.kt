package com.railway.ticketsystem.data

import com.railway.ticketsystem.model.Station
import com.railway.ticketsystem.model.Train
import java.util.*

/**
 * 铁路图数据结构
 * 节点：车站
 * 边：两站之间的路线和价格
 */
class RailwayGraph {
    private val stations = mutableMapOf<String, Station>()
    private val routes = mutableMapOf<String, MutableList<Route>>()
    private val trains = mutableListOf<Train>()
    
    data class Route(
        val from: String,
        val to: String,
        val distance: Int, // 距离（公里）
        val basePrice: Double, // 基础价格（二等座）
        val duration: Int // 运行时间（分钟）
    )
    
    /**
     * 添加车站
     */
    fun addStation(station: Station) {
        stations[station.name] = station
        routes[station.name] = mutableListOf()
    }
    
    /**
     * 添加路线
     */
    fun addRoute(from: String, to: String, distance: Int, basePrice: Double, duration: Int) {
        routes[from]?.add(Route(from, to, distance, basePrice, duration))
    }
    
    /**
     * 添加车次
     */
    fun addTrain(train: Train) {
        trains.add(train)
    }
    
    /**
     * 获取所有车站
     */
    fun getAllStations(): List<Station> = stations.values.toList()
    
    /**
     * 获取所有车次
     */
    fun getAllTrains(): List<Train> = trains
    
    /**
     * 根据起点和终点查找车次
     */
    fun findTrains(from: String, to: String): List<Train> {
        return trains.filter { 
            it.departureStation == from && it.arrivalStation == to 
        }
    }
    
    /**
     * 计算两站之间的最短路径和总价格
     */
    fun findShortestPath(from: String, to: String): PathResult? {
        if (!stations.containsKey(from) || !stations.containsKey(to)) return null
        
        val distances = mutableMapOf<String, Double>()
        val previous = mutableMapOf<String, String>()
        val visited = mutableSetOf<String>()
        
        // 初始化距离
        stations.keys.forEach { station ->
            distances[station] = if (station == from) 0.0 else Double.MAX_VALUE
        }
        
        val queue = PriorityQueue<Pair<String, Double>>(compareBy { it.second })
        queue.offer(Pair(from, 0.0))
        
        while (queue.isNotEmpty()) {
            val (current, currentDist) = queue.poll()
            if (visited.contains(current)) continue
            visited.add(current)
            
            if (current == to) break
            
            routes[current]?.forEach { route ->
                val newDist = currentDist + route.basePrice
                if (newDist < (distances[route.to] ?: Double.MAX_VALUE)) {
                    distances[route.to] = newDist
                    previous[route.to] = current
                    queue.offer(Pair(route.to, newDist))
                }
            }
        }
        
        if (distances[to] == Double.MAX_VALUE) return null
        
        // 重建路径
        val path = mutableListOf<String>()
        var current = to
        while (current != from) {
            path.add(0, current)
            current = previous[current] ?: break
        }
        path.add(0, from)
        
        return PathResult(path, distances[to] ?: 0.0)
    }
    
    data class PathResult(
        val path: List<String>,
        val totalPrice: Double
    )
    
    /**
     * 搜索车站（支持中文名搜索）
     */
    fun searchStations(query: String): List<Station> {
        if (query.isEmpty()) return getAllStations()
        
        return stations.values.filter { station ->
            station.name.contains(query, ignoreCase = true) ||
            station.code.contains(query, ignoreCase = true) ||
            station.city.contains(query, ignoreCase = true)
        }
    }
}


