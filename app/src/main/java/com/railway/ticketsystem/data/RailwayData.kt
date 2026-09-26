package com.railway.ticketsystem.data

import com.railway.ticketsystem.model.Station
import com.railway.ticketsystem.model.Train
import com.railway.ticketsystem.model.RailwayRoute
import java.text.SimpleDateFormat
import java.util.*

object RailwayData {
    
    // 延迟初始化标志
    private var isInitialized = false
    
    // 使用图数据管理器
    val stations: List<Station> get() = RailwayGraphManager.getAllStations()
    val trains: List<Train> get() = RailwayGraphManager.getAllTrains()
    
    /**
     * 预加载数据（在启动时调用）
     */
    fun preloadData() {
        if (!isInitialized) {
            try {
                android.util.Log.d("RailwayData", "开始预加载铁路数据")
                
                // 初始化真实铁路线路
                RailwayGraphManager.initializeRealRoutes()
                
                // 调试信息：检查线路是否加载成功
                val routes = RealRailwayRoutes.getAllRoutes()
                android.util.Log.d("RailwayData", "预加载的线路数量: ${routes.size}")
                
                isInitialized = true
                android.util.Log.d("RailwayData", "铁路数据预加载完成")
            } catch (e: Exception) {
                e.printStackTrace()
                android.util.Log.e("RailwayData", "预加载失败: ${e.message}")
                // 如果预加载失败，至少确保基本数据可用
                isInitialized = true
            }
        }
    }
    
    /**
     * 初始化数据（保持向后兼容）
     */
    private fun ensureInitialized() {
        if (!isInitialized) {
            preloadData()
        }
    }
    
    fun getTrainsByRoute(departure: String, arrival: String): List<Train> {
        ensureInitialized()
        return ServiceSeparatedTrainCatalog.find(departure, arrival)
    }
    
    fun getStationByName(name: String): Station? {
        ensureInitialized()
        if (!PassengerServiceStationPolicy.isPassengerServiceStation(name)) return null
        return stations.find { it.name == name }
    }
    
    fun searchStations(query: String): List<Station> {
        ensureInitialized()
        return RailwayGraphManager.searchStations(query)
    }
    
    fun getHotStations(): List<Station> {
        ensureInitialized()
        return RailwayGraphManager.getHotStations()
    }
    
    fun getRecommendedRoutes(from: String, to: String): List<com.railway.ticketsystem.data.RailwayGraphManager.RouteRecommendation> {
        ensureInitialized()
        return RailwayGraphManager.getRecommendedRoutes(from, to)
    }
    
    fun findShortestPath(from: String, to: String): RailwayGraph.PathResult? {
        ensureInitialized()
        return RailwayGraphManager.findShortestPath(from, to)
    }
    
    /**
     * 获取所有铁路线路
     */
    fun getAllRoutes(): List<RailwayRoute> {
        ensureInitialized()
        return RealRailwayRoutes.getAllRoutes()
    }
    
    /**
     * 根据起点和终点查找线路
     */
    fun findRoute(fromStation: String, toStation: String): RailwayRoute? {
        ensureInitialized()
        return RealRailwayRoutes.findRoute(fromStation, toStation)
    }
    
    /**
     * 获取两个车站之间的价格
     */
    fun getPriceBetweenStations(
        fromStation: String,
        toStation: String,
        routeType: com.railway.ticketsystem.model.RouteType = com.railway.ticketsystem.model.RouteType.HIGH_SPEED
    ): Double {
        ensureInitialized()
        return com.railway.ticketsystem.model.RailwayRouteManager
            .getPriceBetweenStations(fromStation, toStation, routeType)
    }
    
    /**
     * 获取两个车站之间的耗时
     */
    fun getDurationBetweenStations(
        fromStation: String,
        toStation: String,
        routeType: com.railway.ticketsystem.model.RouteType = com.railway.ticketsystem.model.RouteType.HIGH_SPEED
    ): String {
        ensureInitialized()
        return com.railway.ticketsystem.model.RailwayRouteManager
            .getDurationBetweenStations(fromStation, toStation, routeType)
    }
    
    /**
     * 获取线路的途径车站
     */
    fun getRouteStations(
        fromStation: String,
        toStation: String,
        routeType: com.railway.ticketsystem.model.RouteType = com.railway.ticketsystem.model.RouteType.HIGH_SPEED
    ): List<String> {
        ensureInitialized()
        return com.railway.ticketsystem.model.RailwayRouteManager
            .getRouteStations(fromStation, toStation, routeType)
    }
    
    /**
     * 获取车次的途径车站（支持跨线车次）
     */
    fun getTrainRouteStations(train: Train): List<String> {
        ensureInitialized()
        if (!PassengerServiceStationPolicy.isPassengerServiceStation(train.departureStation) ||
            !PassengerServiceStationPolicy.isPassengerServiceStation(train.arrivalStation)
        ) return emptyList()

        // New on-demand services retain the passenger's selected section on
        // Train, but carry their whole operating route separately. Do not
        // prepend the query station here: it can legitimately be a mid-route
        // boarding stop.
        if (train.serviceStations.isNotEmpty()) {
            val serviceRoute = train.serviceStations
                .filter { it.isNotBlank() && PassengerServiceStationPolicy.isPassengerServiceStation(it) }
                .fold(mutableListOf<String>()) { result, station ->
                    if (result.lastOrNull() != station) result.add(station)
                    result
                }
            val departureIndex = serviceRoute.indexOf(train.departureStation)
            val arrivalIndex = serviceRoute.indexOf(train.arrivalStation)
            if (departureIndex >= 0 && arrivalIndex > departureIndex) {
                android.util.Log.d(
                    "RailwayData",
                    "车次 ${train.number} 使用真实运行区段: ${serviceRoute.joinToString(" -> ")}"
                )
                return serviceRoute
            }
        }
        
        // Legacy data contains only the queried section, so preserve the old
        // normalization behaviour for existing orders and train data.
        if (train.viaStations.isNotEmpty()) {
            val normalized = buildList {
                add(train.departureStation)
                addAll(train.viaStations.filter {
                    it.isNotBlank() && it != train.departureStation && it != train.arrivalStation &&
                        PassengerServiceStationPolicy.isPassengerServiceStation(it)
                })
                add(train.arrivalStation)
            }.fold(mutableListOf<String>()) { result, station ->
                if (result.lastOrNull() != station) result.add(station)
                result
            }
            android.util.Log.d("RailwayData", "车次 ${train.number} 是跨线车次，途径车站: ${normalized.joinToString(" -> ")}")
            return normalized
        }
        
        // 否则使用原有方法获取途径车站
        val routeStations = getRouteStations(
            train.departureStation,
            train.arrivalStation,
            train.routeType ?: com.railway.ticketsystem.model.RouteType.HIGH_SPEED
        )
        android.util.Log.d("RailwayData", "车次 ${train.number} 是普通车次，途径车站: ${routeStations.joinToString(" -> ")}")
        return routeStations
    }
    
    /**
     * 按时间排序车次
     */
    fun getTrainsSortedByTime(departureStation: String, arrivalStation: String): List<Train> {
        ensureInitialized()
        
        try {
            return getTrainsByRoute(departureStation, arrivalStation)
                .sortedBy { parseTimeToMinutes(it.departureTime) }
        } catch (e: Exception) {
            android.util.Log.e("RailwayData", "查找车次时出错: ${e.message}")
            // 返回空列表而不是崩溃
            return emptyList()
        }
    }
    
    /**
     * 为线路的特定区间生成列车
     */
    private fun generateTrainsForSegment(route: com.railway.ticketsystem.model.RailwayRoute, fromStation: String, toStation: String, count: Int): List<Train> {
        val trains = mutableListOf<com.railway.ticketsystem.model.Train>()
        val variants = buildVariants(fromStation, toStation)
        
        for (i in 1..count) {
            val trainNumber = generateTrainNumber(route.routeType, i)
            val departureTime = generateDepartureTime()
            val (duration, price) = variants[(i - 1) % variants.size]
            val arrivalTime = calculateArrivalTime(departureTime, duration)
            
            trains.add(com.railway.ticketsystem.model.Train(
                number = trainNumber,
                departureStation = fromStation,
                arrivalStation = toStation,
                departureTime = departureTime,
                arrivalTime = arrivalTime,
                duration = duration,
                price = price,
                availableSeats = (20..100).random()
            ))
        }
        
        return trains
    }
    
    /**
     * 计算区间耗时（支持双向）
     */
    private fun calculateSegmentDuration(route: com.railway.ticketsystem.model.RailwayRoute, fromStation: String, toStation: String): String {
        // 获取线路的所有站点
        val allStations = route.stations.map { it.name }
        
        // 找到起始站和终点站在线路中的位置
        val fromIndex = allStations.indexOf(fromStation)
        val toIndex = allStations.indexOf(toStation)
        
        if (fromIndex == -1 || toIndex == -1 || fromIndex == toIndex) {
            return "15分钟" // 默认值
        }
        
        // 计算总耗时（分钟）
        val totalDurationMinutes = parseDurationToMinutes(route.totalDuration)
        
        // 计算平均每站耗时
        val stationCount = allStations.size - 1 // 总站数减1
        val averageDurationPerStation = totalDurationMinutes.toFloat() / stationCount.toFloat()
        
        // 计算区间耗时
        val segmentStationCount = kotlin.math.abs(toIndex - fromIndex)
        val segmentDurationMinutes = (averageDurationPerStation * segmentStationCount).toInt()
        
        // 转换为小时分钟格式
        return if (segmentDurationMinutes >= 60) {
            val hours = segmentDurationMinutes / 60
            val minutes = segmentDurationMinutes % 60
            if (minutes > 0) {
                "${hours}小时${minutes}分钟"
            } else {
                "${hours}小时"
            }
        } else {
            "${segmentDurationMinutes}分钟"
        }
    }
    
    /**
     * 生成车次号
     */
    private fun generateTrainNumber(routeType: com.railway.ticketsystem.model.RouteType, index: Int): String {
        val prefix = when (routeType) {
            com.railway.ticketsystem.model.RouteType.HIGH_SPEED -> listOf("G", "D").random()
            com.railway.ticketsystem.model.RouteType.INTERCITY -> "C"
            com.railway.ticketsystem.model.RouteType.CONVENTIONAL -> listOf("K", "T", "Z").random()
        }
        
        val number = when (prefix) {
            "T", "Z" -> (1..998).random() // T/Z 在现实客运中绝无四位数
            "K" -> if ((1..4).random() == 1) (1..998).random() else (1001..9998).random()
            "G", "D" -> if ((1..3).random() == 1) (1..998).random() else (1001..9998).random()
            "C" -> if ((1..5).random() == 1) (1..998).random() else (1001..9998).random()
            else -> (1..998).random()
        }
        
        return "$prefix$number"
    }
    
    /**
     * 生成出发时间
     */
    private fun generateDepartureTime(): String {
        val hour = (6..22).random()
        val minute = (0..59).random()
        return String.format("%02d:%02d", hour, minute)
    }
    
    /**
     * 计算到达时间
     */
    private fun calculateArrivalTime(departureTime: String, duration: String): String {
        val departureMinutes = parseTimeToMinutes(departureTime)
        val durationMinutes = parseDurationToMinutes(duration)
        val arrivalMinutes = departureMinutes + durationMinutes
        
        val arrivalHour = (arrivalMinutes / 60) % 24
        val arrivalMinute = arrivalMinutes % 60
        
        return String.format("%02d:%02d", arrivalHour, arrivalMinute)
    }
    
    /**
     * 解析耗时字符串为分钟数
     */
    private fun parseDurationToMinutes(duration: String): Int {
        return try {
            if (duration.contains("小时")) {
                val parts = duration.split("小时")
                val hours = parts[0].toInt()
                val minutes = if (parts.size > 1) {
                    parts[1].replace("分钟", "").trim().toIntOrNull() ?: 0
                } else 0
                hours * 60 + minutes
            } else {
                duration.replace("分钟", "").trim().toIntOrNull() ?: 0
            }
        } catch (e: Exception) {
            0
        }
    }

    // 与全局一致：基于站段数与15..25因子的5种变体
    private fun buildVariants(from: String, to: String): List<Pair<String, Double>> {
        val pathStations = com.railway.ticketsystem.model.RailwayRouteManager.getRouteStationsMinStops(from, to)
        val segments = (pathStations.size - 1).coerceAtLeast(1)
        val base = baseFactorForPair(from, to)
        val candidates = listOf(base - 4, base - 2, base, base + 2, base + 4)
            .map { it.coerceIn(15, 25) }
        val unique = LinkedHashSet<Int>(candidates)
        var f = 15
        while (unique.size < 5) {
            if (!unique.contains(f)) unique.add(f)
            f++
            if (f > 25) f = 15
        }
        return unique.map { factor ->
            val minutes = segments * factor
            val duration = if (minutes >= 60) "${minutes / 60}小时${minutes % 60}分钟" else "${minutes}分钟"
            val price = (segments * (40 - factor)).coerceAtLeast(0).toDouble()
            duration to price
        }
    }

    private fun baseFactorForPair(from: String, to: String): Int {
        val key = if (from <= to) "$from->$to" else "$to->$from"
        val hash = key.hashCode()
        val range = 11
        val offset = kotlin.math.abs(hash % range)
        return 15 + offset
    }
    
    /**
     * 解析时间字符串为分钟数
     */
    private fun parseTimeToMinutes(time: String): Int {
        val parts = time.split(":")
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()
        return hour * 60 + minute
    }
}
