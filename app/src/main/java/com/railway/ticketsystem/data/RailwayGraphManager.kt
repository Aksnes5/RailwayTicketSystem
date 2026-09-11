package com.railway.ticketsystem.data

import com.railway.ticketsystem.model.Station
import com.railway.ticketsystem.model.Train
import kotlin.random.Random

/**
 * 铁路图数据管理器
 * 使用图数据结构管理车站和路线关系
 */
object RailwayGraphManager {
    
    private val graph = RailwayGraph()
    
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
        
        // 添加路线关系（基于真实的地理位置和距离）
        addRoutes()
        
        // 添加车次数据
        ChinaRailwayData.trains.filter(::isPassengerTrain).forEach { train ->
            graph.addTrain(train)
        }
    }
    
    /**
     * 初始化真实铁路线路数据
     */
    fun initializeRealRoutes() {
        try {
            // 初始化真实铁路线路
            RealRailwayRoutes.initializeRoutes()
            // Add the service-specific station catalog to directory search. The route
            // manager, not this generic directory graph, decides which service can run.
            RealRailwayRoutes.getAllRoutes().flatMap { it.stations }.distinctBy { it.name }.forEach(::addStation)
            // 启动阶段不再生成车次；仅准备线路与图结构
        } catch (e: Exception) {
            e.printStackTrace()
            // 如果初始化失败，至少确保基本功能可用
        }
    }
    
    /**
     * 添加路线关系
     */
    private fun addRoutes() {
        val passengerStations = PassengerServiceStationPolicy.filterStations(ChinaRailwayData.stations)
        val stationMap = passengerStations.associateBy { it.name }
        
        // 为每个车站添加与其他车站的路线关系
        passengerStations.forEach { fromStation ->
            passengerStations.forEach { toStation ->
                if (fromStation != toStation) {
                    // 计算距离和价格（基于车站名称的哈希值）
                    val distance = calculateDistance(fromStation.name, toStation.name)
                    val basePrice = calculateBasePrice(distance)
                    val duration = calculateDuration(distance)
                    
                    // 只有距离合理的路线才添加
                    if (distance > 0 && distance < 2000) {
                        graph.addRoute(
                            fromStation.name,
                            toStation.name,
                            distance,
                            basePrice,
                            duration
                        )
                    }
                }
            }
        }
    }
    
    /**
     * 计算两站之间的距离（公里）
     */
    private fun calculateDistance(from: String, to: String): Int {
        // 基于车站名称的哈希值计算距离（简化实现）
        val hash1 = from.hashCode()
        val hash2 = to.hashCode()
        val distance = kotlin.math.abs(hash1 - hash2) % 1500 + 50
        
        return when {
            distance < 100 -> Random.nextInt(50, 200)
            distance < 300 -> Random.nextInt(200, 500)
            distance < 600 -> Random.nextInt(500, 1000)
            distance < 1000 -> Random.nextInt(1000, 1500)
            else -> Random.nextInt(1500, 2000)
        }
    }
    
    /**
     * 根据距离计算基础价格
     */
    private fun calculateBasePrice(distance: Int): Double {
        return when {
            distance < 100 -> Random.nextDouble(20.0, 80.0)
            distance < 300 -> Random.nextDouble(80.0, 200.0)
            distance < 600 -> Random.nextDouble(200.0, 400.0)
            distance < 1000 -> Random.nextDouble(400.0, 600.0)
            else -> Random.nextDouble(600.0, 1000.0)
        }
    }
    
    /**
     * 根据距离计算运行时间（分钟）
     */
    private fun calculateDuration(distance: Int): Int {
        // 高铁平均速度约300km/h
        val baseTime = (distance * 60) / 300
        return baseTime + Random.nextInt(-30, 30) // 添加随机变化
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
    fun findShortestPath(from: String, to: String): RailwayGraph.PathResult? {
        return graph.findShortestPath(from, to)
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
     * 获取附近车站
     */
    private fun getNearbyStations(stationName: String, count: Int): List<Station> {
        val allStations = getAllStations()
        val station = allStations.find { it.name == stationName } ?: return emptyList()
        
        // 简化实现：随机选择几个车站作为"附近"车站
        return allStations
            .filter { it.name != stationName }
            .shuffled()
            .take(count)
    }

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
