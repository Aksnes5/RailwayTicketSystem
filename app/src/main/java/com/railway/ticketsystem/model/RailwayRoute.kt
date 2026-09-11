package com.railway.ticketsystem.model

import com.railway.ticketsystem.data.PassengerServiceStationPolicy
import java.io.Serializable
import java.util.PriorityQueue
import java.util.ArrayDeque

/**
 * 铁路线路数据类
 * 包含一条完整线路的所有信息
 */
data class RailwayRoute(
    val routeId: String,                    // 线路ID
    val routeName: String,                  // 线路名称，如"汉口-天门南"
    val stations: List<Station>,            // 途径车站列表
    val segmentPrices: Map<String, Double>, // 各段价格，key为"起点-终点"，value为价格
    val totalPrice: Double,                 // 全程价格
    val totalDuration: String,              // 全程耗时
    val routeType: RouteType                // 线路类型
) : Serializable

/**
 * 线路类型枚举
 */
enum class RouteType {
    HIGH_SPEED,     // 高速铁路
    INTERCITY,      // 城际铁路（归属高速铁路网）
    CONVENTIONAL    // 普通铁路
}

/**
 * 线路段数据类
 * 表示两个相邻车站之间的路段
 */
data class RouteSegment(
    val fromStation: String,    // 起点站
    val toStation: String,      // 终点站
    val price: Double,          // 该段价格
    val duration: String,       // 该段耗时
    val distance: Int           // 该段距离（公里）
) : Serializable

/**
 * 线路管理器
 * 负责管理所有铁路线路数据
 */
object RailwayRouteManager {
    private val routes = mutableListOf<RailwayRoute>()
    private val segments = mutableMapOf<String, RouteSegment>()
    
    // 图结构：邻接表。节点为站点名称，边包含价格/耗时/距离等属性
    private data class EdgeAttrs(
        val price: Double,
        val durationMinutes: Int,
        val distanceKm: Int
    )
    private data class Edge(
        val to: String,
        val attrs: EdgeAttrs
    )
    /**
     * 物理路网只分为两套：高速（含城际）与普速。
     * C 字头是列车服务类型，不是第三张可独立寻径的线路网。
     */
    private val graphs = mutableMapOf<RouteType, MutableMap<String, MutableList<Edge>>>()
    private fun physicalNetwork(routeType: RouteType): RouteType =
        if (routeType == RouteType.CONVENTIONAL) RouteType.CONVENTIONAL else RouteType.HIGH_SPEED

    private fun graphFor(routeType: RouteType) = graphs.getOrPut(physicalNetwork(routeType)) { mutableMapOf() }

    /**
     * 最少站数路径的记忆化。
     *
     * 一条车次的生成会拿 120 个枢纽分别搜一次到终点的路径，而换乘搜索要对几十个候选中转站
     * 各生成两段车次 —— 其中一半调用的终点是同一个站，那 120 次搜索结果完全一样却重复计算。
     * 实测这些重复占了中转搜索 56 秒里的绝大部分。图在 addRoute() 时清空缓存保证一致性。
     */
    private val minStopsCache = mutableMapOf<String, List<String>>()

    // 可序列化的图快照
    data class GraphSnapshot(val adjacency: Map<String, List<Triple<String, Double, Int>>>) : Serializable

    /**
     * 查询耗时探针。中转搜索要在几十个候选中转站上各生成两段车次，每次生成又会做上百次
     * 图搜索；只读代码判断不出时间花在哪一类搜索上（前几次归因都猜错了）。这里只做计数和
     * 纳秒累加，开销可以忽略，跑完在日志里对照着看。
     */
    object Probe {
        var minStopsCalls = 0L; var minStopsNanos = 0L; var minStopsCacheHits = 0L
        var dijkstraCalls = 0L; var dijkstraNanos = 0L
        var generations = 0L
        var generateNanos = 0L
        var throughSearches = 0L
        var hubExtensions = 0L

        fun reset() {
            minStopsCalls = 0L; minStopsNanos = 0L; minStopsCacheHits = 0L
            dijkstraCalls = 0L; dijkstraNanos = 0L
            generations = 0L; generateNanos = 0L
            throughSearches = 0L; hubExtensions = 0L
        }

        fun summary(): String =
            "minStops=${minStopsCalls}次/${minStopsNanos / 1_000_000}ms(缓存命中$minStopsCacheHits) " +
                "dijkstra=${dijkstraCalls}次/${dijkstraNanos / 1_000_000}ms " +
                "生成=${generations}次/${generateNanos / 1_000_000}ms " +
                "跨站检索=$throughSearches 枢纽延伸=$hubExtensions $graphStats"
    }

    /** 图的规模，用来判断一次 BFS 的量级。 */
    val graphStats: String
        get() = graphs.entries.joinToString(" ") { (type, g) ->
            "$type=${g.size}站/${g.values.sumOf { it.size }}边"
        }
    
    /**
     * 添加线路
     */
    fun addRoute(route: RailwayRoute) {
        minStopsCache.clear()   // 图变了，已缓存的路径可能不再成立
        val passengerRoute = PassengerServiceStationPolicy.passengerRoute(route)
        if (passengerRoute.stations.size < 2 || !matchesServiceNetwork(passengerRoute)) return
        // A route declares one reasonable end-to-end second-class fare. Its
        // adjacent segments are weights, not independently additive fares:
        // normalise them once so a line with many close-together stations does
        // not become more expensive merely because it has more stops.
        val pricedRoute = passengerRoute.copy(
            segmentPrices = allocateRouteFareBySegmentWeight(passengerRoute)
        )
        routes.add(pricedRoute)
        
        // 将线路分解为路段
        for (i in 0 until pricedRoute.stations.size - 1) {
            val fromStation = pricedRoute.stations[i].name
            val toStation = pricedRoute.stations[i + 1].name
            val segmentKey = "${physicalNetwork(pricedRoute.routeType)}:$fromStation-$toStation"
            
            val segmentPrice = pricedRoute.segmentPrices["$fromStation-$toStation"] ?: 0.0
            val segmentDuration = calculateSegmentDuration(pricedRoute.totalDuration, pricedRoute.stations.size, i)
            val segmentDistance = calculateSegmentDistance(pricedRoute.stations[i], pricedRoute.stations[i + 1])
            
            segments[segmentKey] = RouteSegment(
                fromStation = fromStation,
                toStation = toStation,
                price = segmentPrice,
                duration = segmentDuration,
                distance = segmentDistance
            )
            // 同步构建图（双向边）
            val attrs = EdgeAttrs(
                price = segmentPrice,
                durationMinutes = parseDurationToMinutes(segmentDuration),
                distanceKm = segmentDistance
            )
            addEdge(pricedRoute.routeType, fromStation, toStation, attrs, bidirectional = true)
        }
    }
    
    /**
     * 获取所有线路
     */
    fun getAllRoutes(routeType: RouteType? = null): List<RailwayRoute> =
        routes.filter { routeType == null || it.routeType == routeType }
    
    /**
     * 根据起点和终点查找线路（支持双向）
     */
    fun findRoute(
        fromStation: String,
        toStation: String,
        routeType: RouteType = RouteType.HIGH_SPEED
    ): RailwayRoute? {
        return routes.find { route ->
            val samePhysicalNetwork = physicalNetwork(route.routeType) == physicalNetwork(routeType)
            val stationNames = route.stations.map { it.name }
            val fromIndex = stationNames.indexOf(fromStation)
            val toIndex = stationNames.indexOf(toStation)
            samePhysicalNetwork && fromIndex != -1 && toIndex != -1 && fromIndex != toIndex
        }
    }
    
    /**
     * 获取两个车站之间的价格（支持双向）
     */
    fun getPriceBetweenStations(
        fromStation: String,
        toStation: String,
        routeType: RouteType = RouteType.HIGH_SPEED
    ): Double {
        val stations = getRouteStationsMinStops(fromStation, toStation, routeType)
        return getPriceForPath(stations, routeType)
    }

    /**
     * Prices an explicit graph path by summing its already normalised physical
     * sections. This is used for cross-line services as well as a single named
     * railway, so neither type can accidentally be priced by station count.
     */
    fun getPriceForPath(stations: List<String>, routeType: RouteType = RouteType.HIGH_SPEED): Double {
        if (stations.size < 2) return 0.0
        val graph = graphFor(routeType)
        val price = stations.zipWithNext().sumOf { (from, to) ->
            graph[from].orEmpty()
                .filter { it.to == to }
                .minOfOrNull { it.attrs.price }
                ?: 0.0
        }
        return price.coerceAtLeast(0.0)
    }
    
    /**
     * 获取两个车站之间的耗时（支持双向）
     */
    fun getDurationBetweenStations(
        fromStation: String,
        toStation: String,
        routeType: RouteType = RouteType.HIGH_SPEED
    ): String {
        val stations = getRouteStationsMinStops(fromStation, toStation, routeType)
        if (stations.isEmpty()) return formatMinutes(0)
        val declaredRoute = findRoute(fromStation, toStation, routeType)
        if (declaredRoute != null) {
            val names = declaredRoute.stations.map { it.name }
            val span = kotlin.math.abs(names.indexOf(toStation) - names.indexOf(fromStation))
            val minutes = parseDurationToMinutes(declaredRoute.totalDuration) * span / (names.size - 1).coerceAtLeast(1)
            if (minutes > 0) return formatMinutes(minutes)
        }
        val segments = (stations.size - 1).coerceAtLeast(0)
        val factor = randomFactorForPair(fromStation, toStation)
        val minutes = segments * if (routeType == RouteType.CONVENTIONAL) factor * 2 else factor
        return formatMinutes(minutes)
    }
    
    /**
     * 获取线路的途径车站（支持双向）
     */
    fun getRouteStations(
        fromStation: String,
        toStation: String,
        routeType: RouteType = RouteType.HIGH_SPEED
    ): List<String> {
        val durationOptimal = shortestPath(fromStation, toStation, routeType) { it.durationMinutes.toDouble() }
        val minStopPath = getRouteStationsMinStops(fromStation, toStation, routeType)

        if (durationOptimal == null) {
            return minStopPath
        }

        if (minStopPath.isEmpty()) {
            return durationOptimal.first
        }

        return if (durationOptimal.first.size <= minStopPath.size) {
            durationOptimal.first
        } else {
            minStopPath
        }
    }

    fun getRouteStationsShortestDuration(
        fromStation: String,
        toStation: String,
        routeType: RouteType = RouteType.HIGH_SPEED
    ): List<String> {
        val result = shortestPath(fromStation, toStation, routeType) { it.durationMinutes.toDouble() }
        return result?.first ?: emptyList()
    }
    
    /**
     * 计算路段耗时
     */
    private fun calculateSegmentDuration(totalDuration: String, totalSegments: Int, segmentIndex: Int): String {
        val totalMinutes = parseDurationToMinutes(totalDuration)
        val segmentMinutes = totalMinutes / totalSegments
        val hours = segmentMinutes / 60
        val minutes = segmentMinutes % 60
        return if (hours > 0) {
            "${hours}小时${minutes}分"
        } else {
            "${minutes}分"
        }
    }
    
    /**
     * 计算路段距离
     */
    private fun calculateSegmentDistance(fromStation: Station, toStation: Station): Int {
        // 简化的距离计算，实际应该使用真实的地理坐标
        return (10..50).random()
    }

    /**
     * Segment-price maps supplied by route catalogues are treated as distance
     * proxies. Rescaling them to the declared end-to-end fare gives every line
     * one coherent total and keeps intermediate fares proportional.
     */
    private fun allocateRouteFareBySegmentWeight(route: RailwayRoute): Map<String, Double> {
        val names = route.stations.map { it.name }
        val weights = names.zipWithNext().map { (from, to) ->
            route.segmentPrices["$from-$to"]
                ?: route.segmentPrices["$to-$from"]
                ?: 1.0
        }.map { it.coerceAtLeast(0.01) }
        val totalFare = route.totalPrice.takeIf { it > 0.0 } ?: weights.sum()
        val weightSum = weights.sum().coerceAtLeast(0.01)
        var allocated = 0.0
        return names.zipWithNext().mapIndexed { index, (from, to) ->
            val segmentFare = if (index == weights.lastIndex) {
                (totalFare - allocated).coerceAtLeast(0.01)
            } else {
                (totalFare * weights[index] / weightSum * 100.0).toInt() / 100.0
            }
            allocated += segmentFare
            "$from-$to" to segmentFare
        }.toMap()
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
                    parts[1].replace("分钟", "").replace("分", "").trim().toIntOrNull() ?: 0
                } else 0
                hours * 60 + minutes
            } else {
                duration.replace("分钟", "").replace("分", "").trim().toIntOrNull() ?: 0
            }
        } catch (e: Exception) {
            0
        }
    }

    /**
     * 添加边到图
     */
    private fun addEdge(routeType: RouteType, from: String, to: String, attrs: EdgeAttrs, bidirectional: Boolean) {
        val graph = graphFor(routeType)
        graph.getOrPut(from) { mutableListOf() }.add(Edge(to, attrs))
        if (bidirectional) {
            graph.getOrPut(to) { mutableListOf() }.add(Edge(from, attrs))
        }
    }

    fun exportGraphSnapshot(routeType: RouteType = RouteType.HIGH_SPEED): GraphSnapshot {
        val graph = graphFor(routeType)
        val adj = graph.mapValues { (_, edges) ->
            edges.map { Triple(it.to, it.attrs.price, it.attrs.durationMinutes) }
        }
        return GraphSnapshot(adj)
    }

    fun importGraphSnapshot(snapshot: GraphSnapshot, routeType: RouteType = RouteType.HIGH_SPEED) {
        val graph = graphFor(routeType)
        graph.clear()
        snapshot.adjacency.forEach { (from, list) ->
            val edges = list.map { (to, price, duration) ->
                Edge(to, EdgeAttrs(price = price, durationMinutes = duration, distanceKm = 0))
            }
            graph[from] = edges.toMutableList()
        }
    }

    /**
     * Dijkstra 最短路径
     * 返回 Pair<路径节点列表, 累计代价>
     */
    private fun shortestPath(
        start: String,
        target: String,
        routeType: RouteType,
        costSelector: (EdgeAttrs) -> Double
    ): Pair<List<String>, Double>? {
        val startedAt = System.nanoTime()
        Probe.dijkstraCalls++
        try {
            return dijkstra(start, target, routeType, costSelector)
        } finally {
            Probe.dijkstraNanos += System.nanoTime() - startedAt
        }
    }

    private fun dijkstra(
        start: String,
        target: String,
        routeType: RouteType,
        costSelector: (EdgeAttrs) -> Double
    ): Pair<List<String>, Double>? {
        val graph = graphFor(routeType)
        if (start == target) return listOf(start) to 0.0
        if (!graph.containsKey(start) || (!graph.containsKey(target) && start != target)) return null

        data class State(val node: String, val cost: Double)

        val comparator = compareBy<State> { it.cost }
        val pq = PriorityQueue(comparator)
        val dist = mutableMapOf<String, Double>()
        val prev = mutableMapOf<String, String?>()

        pq.add(State(start, 0.0))
        dist[start] = 0.0
        prev[start] = null

        while (pq.isNotEmpty()) {
            val (node, cost) = pq.poll()
            if (node == target) break
            if (cost > (dist[node] ?: Double.POSITIVE_INFINITY)) continue

            val edges = graph[node] ?: continue
            for (edge in edges) {
                val w = costSelector(edge.attrs)
                val newCost = cost + w
                if (newCost < (dist[edge.to] ?: Double.POSITIVE_INFINITY)) {
                    dist[edge.to] = newCost
                    prev[edge.to] = node
                    pq.add(State(edge.to, newCost))
                }
            }
        }

        val total = dist[target] ?: return null
        // 重建路径
        val path = ArrayList<String>()
        var cur: String? = target
        while (cur != null) {
            path.add(cur)
            cur = prev[cur]
        }
        path.reverse()
        return path to total
    }

    private fun randomFactorForPair(fromStation: String, toStation: String): Int {
        val key = if (fromStation <= toStation) "$fromStation->$toStation" else "$toStation->$fromStation"
        val hash = key.hashCode()
        val range = 11 // 15..25 inclusive has 11 values
        val offset = kotlin.math.abs(hash % range)
        return 15 + offset
    }

    private fun formatMinutes(totalMinutes: Int): String {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) {
            "${hours}小时${minutes}分钟"
        } else {
            "${minutes}分钟"
        }
    }

    /**
     * 最少换乘（按经停站数量最少）路径，返回站点序列；不可达返回空
     */
    fun getRouteStationsMinStops(
        fromStation: String,
        toStation: String,
        routeType: RouteType = RouteType.HIGH_SPEED
    ): List<String> {
        val key = "${physicalNetwork(routeType)}|$fromStation|$toStation"
        minStopsCache[key]?.let {
            Probe.minStopsCalls++; Probe.minStopsCacheHits++
            return it
        }
        val startedAt = System.nanoTime()
        val path = computeMinStops(fromStation, toStation, routeType)
        Probe.minStopsCalls++
        Probe.minStopsNanos += System.nanoTime() - startedAt
        minStopsCache[key] = path
        return path
    }

    private fun computeMinStops(
        fromStation: String,
        toStation: String,
        routeType: RouteType
    ): List<String> {
        val graph = graphFor(routeType)
        if (fromStation == toStation) return listOf(fromStation)
        if (!graph.containsKey(fromStation)) return emptyList()
        val queue: ArrayDeque<String> = ArrayDeque()
        val prev = mutableMapOf<String, String?>()
        val visited = mutableSetOf<String>()
        queue.add(fromStation)
        visited.add(fromStation)
        prev[fromStation] = null
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            if (cur == toStation) break
            val edges = graph[cur] ?: continue
            for (e in edges) {
                if (visited.add(e.to)) {
                    prev[e.to] = cur
                    queue.add(e.to)
                }
            }
        }
        if (!prev.containsKey(toStation)) return emptyList()
        val path = ArrayList<String>()
        var c: String? = toStation
        while (c != null) {
            path.add(c)
            c = prev[c]
        }
        path.reverse()
        return path
    }

    private fun matchesServiceNetwork(route: RailwayRoute): Boolean {
        val expected = if (route.routeType == RouteType.CONVENTIONAL) {
            StationNetwork.CONVENTIONAL
        } else {
            StationNetwork.HIGH_SPEED
        }
        return route.stations.all { it.network == expected }
    }

    private fun declaredPrice(route: RailwayRoute, from: String, to: String): Double? {
        val names = route.stations.map { it.name }
        val fromIndex = names.indexOf(from)
        val toIndex = names.indexOf(to)
        if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return null
        val start = minOf(fromIndex, toIndex)
        val end = maxOf(fromIndex, toIndex)
        val sum = (start until end).sumOf { index ->
            route.segmentPrices["${names[index]}-${names[index + 1]}"]
                ?: route.segmentPrices["${names[index + 1]}-${names[index]}"]
                ?: 0.0
        }
        return if (sum > 0.0) sum else route.totalPrice * (end - start) / (names.size - 1).coerceAtLeast(1)
    }
}
