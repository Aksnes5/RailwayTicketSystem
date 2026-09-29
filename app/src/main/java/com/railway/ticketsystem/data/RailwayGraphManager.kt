package com.railway.ticketsystem.data

import com.railway.ticketsystem.model.Station
import com.railway.ticketsystem.model.Train

/**
 * 铁路图数据管理器
 * 使用图数据结构管理车站和路线关系
 */
object RailwayGraphManager {
    
    private val graph = RailwayGraph()
    private var realRouteEdgesInstalled = false
    
    init {
        initializeGraph()
    }
    
    /**
     * 初始化图数据
     */
    private fun initializeGraph() {
        // 添加所有车站到图中
        PassengerServiceStationPolicy.filterStations(ChinaRailwayData.stations).forEach { station ->
            graph.addStation(station)
        }
        
        // Real route edges are installed after the complete bundled corridor catalogue loads.
        // Do not connect every station pair here: that would create fictional railway paths.
        
        // 添加车次数据
        ChinaRailwayData.trains.filter(::isPassengerTrain).forEach { train ->
            graph.addTrain(train)
        }
    }
    
    /**
     * 初始化真实铁路线路数据
     */
    @Synchronized
    fun initializeRealRoutes() {
        try {
            RealRailwayRoutes.initializeRoutes()
            val realRoutes = RealRailwayRoutes.getAllRoutes()
            // Add the service-specific station catalog without clearing previously connected edges,
            // then build only real adjacent railway sections.
            realRoutes.flatMap { it.stations }.distinctBy { it.name }.forEach(::addStation)
            if (!realRouteEdgesInstalled) {
                connectRealRouteEdges(realRoutes)
                realRouteEdgesInstalled = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /** Builds the generic path graph solely from registered physical railway sections. */
    private fun connectRealRouteEdges(routes: List<com.railway.ticketsystem.model.RailwayRoute>) {
        routes.forEach { route ->
            val sectionCount = (route.stations.size - 1).coerceAtLeast(1)
            val sectionDuration = parseRouteDurationMinutes(route.totalDuration) / sectionCount
            route.stations.zipWithNext().forEach { (from, to) ->
                val fare = route.segmentPrices["${from.name}-${to.name}"] ?: 0.0
                val distance = estimateSegmentDistance(fare, route.routeType)
                val duration = sectionDuration.coerceAtLeast(1)
                graph.addRoute(from.name, to.name, distance, fare, duration, route.routeType)
                graph.addRoute(to.name, from.name, distance, fare, duration, route.routeType)
            }
        }
    }

    private fun parseRouteDurationMinutes(value: String): Int {
        val hours = Regex("(\\d+)小时").find(value)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val minutes = Regex("(\\d+)分").find(value)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return (hours * 60 + minutes).coerceAtLeast(1)
    }

    private fun estimateSegmentDistance(fare: Double, routeType: com.railway.ticketsystem.model.RouteType): Int {
        val kmPerYuan = if (routeType == com.railway.ticketsystem.model.RouteType.CONVENTIONAL) 7.0 else 3.0
        return (fare.coerceAtLeast(1.0) * kmPerYuan).toInt().coerceAtLeast(1)
    }
    /**
     * 获取所有车站
     */
    fun getAllStations(): List<Station> = graph.getAllStations()
        .filter { PassengerServiceStationPolicy.isPassengerServiceStation(it.name) }
    
    /**
     * 获取所有车次
     */
    fun getAllTrains(): List<Train> = graph.getAllTrains()
    
    /**
     * 添加车次到图中
     */
    fun addTrains(trains: List<Train>) {
        trains.filter(::isPassengerTrain).forEach { train ->
            graph.addTrain(train)
        }
    }
    
    /**
     * 添加单个车站到图中
     */
    fun addStation(station: Station) {
        if (PassengerServiceStationPolicy.isPassengerServiceStation(station.name)) graph.addStation(station)
    }
    
    /**
     * 批量添加车站到图中
     */
    fun addStations(stations: List<Station>) {
        PassengerServiceStationPolicy.filterStations(stations).forEach { station ->
            graph.addStation(station)
        }
    }
    
    /**
     * 检查车站是否已存在
     */
    fun isStationExists(stationName: String): Boolean {
        return PassengerServiceStationPolicy.isPassengerServiceStation(stationName) &&
            graph.getAllStations().any { it.name == stationName }
    }
    
    /**
     * 根据起点和终点查找车次
     * 确保每对站点都有充足的车次（至少40趟）
     */
    fun findTrains(from: String, to: String): List<Train> {
        android.util.Log.d("RailwayGraphManager", "查找车次: $from -> $to")
        if (!PassengerServiceStationPolicy.isPassengerServiceStation(from) ||
            !PassengerServiceStationPolicy.isPassengerServiceStation(to)
        ) return emptyList()
        
        // 所有旅客查询仅从按网络隔离的目录取得结果；不再回退到旧的混合图。
        return ServiceSeparatedTrainCatalog.find(from, to)
    }
    
    /**
     * 搜索车站
     */
    fun searchStations(query: String): List<Station> {
        return graph.searchStations(query).filter { PassengerServiceStationPolicy.isPassengerServiceStation(it.name) }
    }
    
    /**
     * 查找最短路径和总价格
     */
    fun findShortestPath(
        from: String,
        to: String,
        routeType: com.railway.ticketsystem.model.RouteType = com.railway.ticketsystem.model.RouteType.HIGH_SPEED
    ): RailwayGraph.PathResult? {
        return graph.findShortestPath(from, to, routeType)
    }
    
    /**
     * 获取热门车站（基于车次数量）
     */
    fun getHotStations(): List<Station> {
        val allStations = graph.getAllStations()
        val trains = graph.getAllTrains()
        if (trains.isNotEmpty()) {
            val stationTrainCount = mutableMapOf<String, Int>()
            trains.forEach { train ->
                stationTrainCount[train.departureStation] = (stationTrainCount[train.departureStation] ?: 0) + 1
                stationTrainCount[train.arrivalStation] = (stationTrainCount[train.arrivalStation] ?: 0) + 1
            }
            return stationTrainCount.entries
                .sortedByDescending { it.value }
                .take(15)
                .mapNotNull { entry -> allStations.find { it.name == entry.key } }
        }
        // 回退：基于真实线路的邻接度作为“热度”
        val degree = mutableMapOf<String, MutableSet<String>>()
        try {
            val routes = RealRailwayRoutes.getAllRoutes()
            routes.forEach { route ->
                val names = route.stations.map { it.name }
                for (i in 0 until names.size - 1) {
                    val a = names[i]
                    val b = names[i + 1]
                    degree.getOrPut(a) { mutableSetOf() }.add(b)
                    degree.getOrPut(b) { mutableSetOf() }.add(a)
                }
            }
        } catch (_: Exception) {}
        return degree.entries
            .sortedByDescending { it.value.size }
            .mapNotNull { entry -> allStations.find { it.name == entry.key } }
            .take(15)
    }
    
    /**
     * 获取推荐路线（基于价格和时间）
     */
    fun getRecommendedRoutes(from: String, to: String): List<RouteRecommendation> {
        val directTrains = findTrains(from, to)
        val recommendations = mutableListOf<RouteRecommendation>()
        
        // 添加直达车次
        directTrains.forEach { train ->
            recommendations.add(
                RouteRecommendation(
                    listOf(train),
                    train.price,
                    calculateTotalDuration(train.departureTime, train.arrivalTime),
                    "直达"
                )
            )
        }
        
        // 添加中转路线（简化实现）
        if (directTrains.isEmpty()) {
            val nearbyStations = getNearbyStations(from, 3)
            nearbyStations.forEach { transferStation ->
                val firstLeg = findTrains(from, transferStation.name)
                val secondLeg = findTrains(transferStation.name, to)
                
                if (firstLeg.isNotEmpty() && secondLeg.isNotEmpty()) {
                    val firstTrain = firstLeg.first()
                    val secondTrain = secondLeg.first()
                    val totalPrice = firstTrain.price + secondTrain.price
                    val totalDuration = calculateTotalDuration(
                        firstTrain.departureTime, 
                        secondTrain.arrivalTime
                    )
                    
                    recommendations.add(
                        RouteRecommendation(
                            listOf(firstTrain, secondTrain),
                            totalPrice,
                            totalDuration,
                            "中转：${transferStation.name}"
                        )
                    )
                }
            }
        }
        
        return recommendations.sortedBy { it.totalPrice }
    }
    
    /**
     * 推荐换乘只从真实物理相邻站中选择，绝不随机跳到无关城市。
     */
    private fun getNearbyStations(stationName: String, count: Int): List<Station> =
        graph.getDirectNeighbours(stationName).take(count)

    private fun isPassengerTrain(train: Train): Boolean =
        PassengerServiceStationPolicy.isPassengerServiceStation(train.departureStation) &&
            PassengerServiceStationPolicy.isPassengerServiceStation(train.arrivalStation) &&
            train.viaStations.all { PassengerServiceStationPolicy.isPassengerServiceStation(it) }
    
    /**
     * 计算总运行时间
     */
    private fun calculateTotalDuration(departureTime: String, arrivalTime: String): Int {
        val departure = parseTime(departureTime)
        val arrival = parseTime(arrivalTime)
        
        var duration = arrival - departure
        if (duration < 0) duration += 24 * 60 // 跨天情况
        
        return duration
    }
    
    /**
     * 解析时间字符串为分钟数
     */
    private fun parseTime(timeStr: String): Int {
        val parts = timeStr.split(":")
        val hours = parts[0].toInt()
        val minutes = parts[1].toInt()
        return hours * 60 + minutes
    }
    
    /**
     * 路线推荐数据类
     */
    data class RouteRecommendation(
        val trains: List<Train>,
        val totalPrice: Double,
        val totalDuration: Int,
        val description: String
    )
}
