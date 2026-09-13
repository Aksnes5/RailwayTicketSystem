package com.railway.ticketsystem.data

import com.railway.ticketsystem.model.Train
import com.railway.ticketsystem.model.RailwayRoute
import com.railway.ticketsystem.model.RailwayRouteManager
import com.railway.ticketsystem.model.RouteType
import java.text.SimpleDateFormat
import java.util.*

/**
 * 列车生成器
 * 为每条铁路线路生成随机列车
 */
object TrainGenerator {
    
    private val trainNumberPrefixes = listOf("G", "D", "C", "K", "T", "Z")
    private val random = Random()
    private enum class PathFlavor { SHORTEST_DURATION, MIN_STOPS }
    private data class PathOption(val path: List<String>, val flavor: PathFlavor)

    
    // 性能控制参数（快速模式）
    /** Service window: the first and last departure times a generated train may have. */
    private const val SERVICE_FIRST_DEPARTURE_MINUTES = 6 * 60          // 06:00
    private const val SERVICE_LAST_DEPARTURE_MINUTES = 22 * 60 + 30     // 22:30
    private const val MINUTES_PER_DAY = 24 * 60
    // The displayed slowest high-speed variant is 1.08× its base and the fastest
    // conventional variant is 0.92× its base.  2.36 keeps every K/T/Z result
    // at least twice as long as the comparable G/D/C result for the same pair.
    private const val CONVENTIONAL_TO_HIGH_SPEED_BASE_RATIO = 2.36
    private const val FASTEST_VARIANT_MULTIPLIER = 0.92
    private const val SLOWEST_VARIANT_MULTIPLIER = 1.08

    private const val MAX_TOTAL_TRAINS = 3000
    private const val MAX_CROSSLINE_TRAINS = 600
    private const val STATION_HUB_TOP_K = 40
    private const val STATION_RANDOM_SAMPLE = 60
    private const val PAIR_SAMPLE_RATE = 0.18
    
    /**
     * 为所有线路生成列车（不在启动时生成，采用按需生成策略）
     */
    fun generateAllTrains(): List<Train> {
        // 启动时不生成任何车次，只建立图结构
        // 车次在查询时通过 generateTrainsForPair() 按需生成
        android.util.Log.d("TrainGenerator", "采用按需生成策略，初始化时不生成车次")
        return emptyList()
    }

    /**
     * 即时为两站生成车次（在查询触发时调用）
     * 确保每对站点都有足够的车次选择，无论跨多少条线路
     */
    fun generateTrainsForPair(
        from: String,
        to: String,
        minCount: Int = 40,
        maxCount: Int = 60,
        routeType: RouteType = RouteType.HIGH_SPEED
    ): List<Train> {
        // 检查路径是否存在
        val minStopPath = RailwayRouteManager.getRouteStationsMinStops(from, to, routeType)
        val shortestDurationPath = RailwayRouteManager.getRouteStationsShortestDuration(from, to, routeType)

        val pathOptions = mutableListOf<PathOption>()

        if (shortestDurationPath.isNotEmpty()) {
            pathOptions.add(PathOption(shortestDurationPath, PathFlavor.SHORTEST_DURATION))
        }
        if (minStopPath.isNotEmpty() && minStopPath != shortestDurationPath) {
            pathOptions.add(PathOption(minStopPath, PathFlavor.MIN_STOPS))
        }

        if (pathOptions.isEmpty()) {
            if (minStopPath.isNotEmpty()) {
                pathOptions.add(PathOption(minStopPath, PathFlavor.MIN_STOPS))
            } else if (shortestDurationPath.isNotEmpty()) {
                pathOptions.add(PathOption(shortestDurationPath, PathFlavor.SHORTEST_DURATION))
            }
        }

        if (pathOptions.isEmpty()) {
            android.util.Log.w("TrainGenerator", "无法找到 $from -> $to 的路径")
            return emptyList()
        }
        
        val uniqueOptions = if (pathOptions.size <= 1) pathOptions else {
            // 保证最短耗时和最少站数各一条
            val distinct = mutableListOf<PathOption>()
            val durationOption = pathOptions.firstOrNull { it.flavor == PathFlavor.SHORTEST_DURATION }
            if (durationOption != null) distinct.add(durationOption)
            val minStopOption = pathOptions.firstOrNull { it.flavor == PathFlavor.MIN_STOPS }
            if (minStopOption != null) distinct.add(minStopOption)
            if (distinct.isEmpty()) pathOptions else distinct
        }

        if (uniqueOptions.size == 1) {
            val option = uniqueOptions.first()
            val count = determineTrainCountForStops(option.path.size, minCount, maxCount)
            return generateTrainsForPath(from, to, option, count, routeType)
        }

        val maxStops = uniqueOptions.maxOf { it.path.size }
        var perPathCount = determineTrainCountForStops(maxStops, minCount, maxCount) / uniqueOptions.size
        if (perPathCount < 5) {
            perPathCount = 5
        }
        perPathCount = perPathCount.coerceAtMost(maxCount)

        val trains = ArrayList<Train>(perPathCount * uniqueOptions.size)
        uniqueOptions.forEach { option ->
            trains.addAll(generateTrainsForPath(from, to, option, perPathCount, routeType))
        }
        return trains
    }
    
    /**
     * 格式化时长（分钟转为"X小时Y分钟"）
     */
    private fun formatDuration(totalMinutes: Int): String {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}小时${minutes}分钟"
            hours > 0 -> "${hours}小时"
            else -> "${minutes}分钟"
        }
    }
    
    /**
     * 为指定线路生成指定数量的列车
     */
    fun generateTrainsForRoute(route: RailwayRoute, count: Int): List<Train> {
        val trains = mutableListOf<Train>()
        
        for (i in 1..count) {
            val train = generateSingleTrain(route, i)
            trains.add(train)
        }
        
        return trains
    }
    
    /**
     * 生成单条列车（公开方法，供异步加载使用）
     */
    fun generateSingleTrain(route: RailwayRoute, index: Int): Train {
        val trainNumber = generateTrainNumber(route.routeType, index)
        val from = route.stations.first().name
        val to = route.stations.last().name
        val variants = buildVariants(from, to, routeType = route.routeType)
        val (duration, price) = variants[(index - 1) % variants.size]
        val departureTime = generateDepartureTime(duration, route.routeType)
        val arrivalTime = calculateArrivalTime(departureTime, duration)
        
        return Train(
            number = trainNumber,
            departureStation = from,
            arrivalStation = to,
            departureTime = departureTime,
            arrivalTime = arrivalTime,
            duration = duration,
            price = price,
            availableSeats = (50..200).random(),
            routeType = route.routeType
        )
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
            "G" -> (1000..9999).random()
            "D" -> (2000..9999).random()
            "C" -> (1000..9999).random()
            "K" -> (1000..9999).random()
            "T" -> (1000..9999).random()
            "Z" -> (1000..9999).random()
            else -> (1000..9999).random()
        }
        
        return "$prefix$number"
    }
    
    /**
     * 生成唯一的车次号（用于跨线车次）
     */
    private fun generateUniqueTrainNumber(routeType: com.railway.ticketsystem.model.RouteType): String {
        val prefix = when (routeType) {
            com.railway.ticketsystem.model.RouteType.HIGH_SPEED -> listOf("G", "D").random()
            com.railway.ticketsystem.model.RouteType.INTERCITY -> "C"
            com.railway.ticketsystem.model.RouteType.CONVENTIONAL -> listOf("K", "T", "Z").random()
        }
        
        // 使用时间戳确保唯一性
        val timestamp = System.currentTimeMillis()
        val randomSuffix = random.nextInt(1000)
        val number = ((timestamp % 9000) + 1000 + randomSuffix) % 10000
        
        return "$prefix$number"
    }
    
    /**
     * 生成出发时间
     */
    /**
     * Passenger services run from 06:00 to 22:30; nothing is scheduled to leave outside that
     * window, so the draw is taken over the window itself rather than hour-by-hour (which
     * would also allow a 22:59 departure).
     */
    private fun generateDepartureTime(duration: String, routeType: RouteType): String {
        val first = SERVICE_FIRST_DEPARTURE_MINUTES
        var last = SERVICE_LAST_DEPARTURE_MINUTES
        // A high-speed service that arrives after midnight is not a thing, so its last
        // departure is pulled back by the journey's own duration.  Conventional overnight
        // sleepers genuinely do run past midnight and keep the full window.
        if (routeType != RouteType.CONVENTIONAL) {
            last = minOf(last, MINUTES_PER_DAY - parseDurationToMinutes(duration))
        }
        if (last < first) last = first
        val minutes = (first..last).random()
        return String.format("%02d:%02d", minutes / 60, minutes % 60)
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
     * 计算价格（基于线路总价格，添加随机波动）
     */
    private fun calculatePrice(route: RailwayRoute): Double {
        val basePrice = route.totalPrice
        val variation = basePrice * 0.1 // 10%的价格波动
        val randomVariation = (random.nextDouble() - 0.5) * 2 * variation
        return (basePrice + randomVariation).let { if (it < 0) 0.0 else it }
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
     * 基于全局图生成列车：
     * 1) 将所有线路合并为一个无向图
     * 2) 对任意两个站点生成列车；节点度数越高，生成数量越多
     */
    private fun generateGraphBasedTrains(routes: List<RailwayRoute>): List<Train> {
        // 1. 构建全局邻接表
        val adjacency = buildGlobalAdjacency(routes)
        val allStations = adjacency.keys.toList()
        val degree = allStations.associateWith { adjacency[it]?.size ?: 0 }
        
        // 2. 选取站点子集：Top-K 枢纽 + 随机采样若干普通站，控制组合规模
        val hubs = allStations.sortedByDescending { degree[it] ?: 0 }.take(STATION_HUB_TOP_K).toMutableSet()
        val nonHubs = allStations.filterNot { it in hubs }.shuffled().take(STATION_RANDOM_SAMPLE)
        val stations = (hubs + nonHubs).toList()

        val generated = mutableListOf<Train>()
        var budget = MAX_TOTAL_TRAINS

        // 3. 为站点对生成列车（抽样 + 预算控制）
        for (i in stations.indices) {
            for (j in i + 1 until stations.size) {
                if (budget <= 0) break
                // 对站点对进行抽样，减少组合规模
                if (random.nextDouble() > PAIR_SAMPLE_RATE) continue
                val from = stations[i]
                val to = stations[j]

                // 仅在存在路径时生成（通过站点序列是否非空判断可达性）
                // 优先采用经停站数量最少的路径作为生成基准
                val pathStations = RailwayRouteManager.getRouteStationsMinStops(from, to)
                if (pathStations.isEmpty()) continue
                val variants = buildVariants(from, to)
                val count = determineTrainCount(degree[from] ?: 0, degree[to] ?: 0).coerceAtMost(budget)

                repeat(count) { idx ->
                    val number = generateUniqueTrainNumber(com.railway.ticketsystem.model.RouteType.HIGH_SPEED)
                    val (duration, price) = variants[idx % variants.size]
                    val dep = generateDepartureTime(duration, com.railway.ticketsystem.model.RouteType.HIGH_SPEED)
                    val arr = calculateArrivalTime(dep, duration)
                    // 为提升短路径覆盖率，更多生成短路径（经停站少）
                    if (pathStations.size <= 3 || random.nextDouble() < 0.6) {
                        generated.add(
                        Train(
                            number = number,
                            departureStation = from,
                            arrivalStation = to,
                            departureTime = dep,
                            arrivalTime = arr,
                            duration = duration,
                            price = price,
                            availableSeats = (60..220).random()
                        )
                        )
                    }
                }
                budget -= count
            }
            if (budget <= 0) break
        }

        return generated
    }

    private fun buildGlobalAdjacency(routes: List<RailwayRoute>): MutableMap<String, MutableSet<String>> {
        val adj = mutableMapOf<String, MutableSet<String>>()
        routes.forEach { route ->
            val names = route.stations.map { it.name }
            for (k in 0 until names.size - 1) {
                val a = names[k]
                val b = names[k + 1]
                adj.getOrPut(a) { mutableSetOf() }.add(b)
                adj.getOrPut(b) { mutableSetOf() }.add(a)
            }
        }
        return adj
    }

    private fun determineTrainCount(degA: Int, degB: Int): Int {
        // 提升非枢纽的基础车次数量，同时对枢纽更友好
        val base = 3 // 非枢纽至少3列
        val degreeBoost = ((degA + degB) / 5.0).toInt() // 度数每+5，总量+1
        val variation = (0..2).random() // 轻微随机，避免完全同质
        return (base + degreeBoost + variation).coerceIn(3, 10)
    }

    // 为站点对构建5种固定的时间/价格变体
    private fun buildVariants(
        from: String,
        to: String,
        pathStationsOverride: List<String>? = null,
        routeType: RouteType = RouteType.HIGH_SPEED
    ): List<Pair<String, Double>> {
        val pathStations = pathStationsOverride.takeUnless { it.isNullOrEmpty() }
            ?: RailwayRouteManager.getRouteStationsMinStops(from, to, routeType)
        val segments = (pathStations.size - 1).coerceAtLeast(1)
        val declaredMinutes = parseDurationToMinutes(
            RailwayRouteManager.getDurationBetweenStations(from, to, routeType)
        )
        val unadjustedMinutes = declaredMinutes.takeIf { it > 0 }
            ?: segments * if (routeType == RouteType.CONVENTIONAL) 55 else 20
        // A conventional route can share origin/destination cities with the high-speed
        // network. Do not let a path with fewer graph hops look as quick as G/D/C: the
        // timetable must include the lower line speed, more stopping and longer dwell.
        val comparableHighSpeedMinutes = if (routeType == RouteType.CONVENTIONAL) {
            parseDurationToMinutes(
                RailwayRouteManager.getDurationBetweenStations(from, to, RouteType.HIGH_SPEED)
            )
        } else 0
        val twiceHighSpeedFloor = if (comparableHighSpeedMinutes > 0) {
            kotlin.math.ceil(
                comparableHighSpeedMinutes * CONVENTIONAL_TO_HIGH_SPEED_BASE_RATIO
            ).toInt()
        } else 0
        val baseMinutes = if (routeType == RouteType.CONVENTIONAL) {
            maxOf(unadjustedMinutes, segments * 55, twiceHighSpeedFloor, 80)
        } else {
            unadjustedMinutes
        }
        // Price the exact graph path selected for this result.  For a
        // cross-line service this accumulates the proportional fare of every
        // physical section instead of multiplying by its number of stops.
        val declaredPrice = RailwayRouteManager.getPriceForPath(pathStations, routeType)
        val basePrice = declaredPrice.takeIf { it > 0.0 }
            ?: segments * if (routeType == RouteType.CONVENTIONAL) 8.0 else 20.0
        return listOf(
            FASTEST_VARIANT_MULTIPLIER, 0.96, 1.0, 1.04, SLOWEST_VARIANT_MULTIPLIER
        ).map { multiplier ->
            val duration = formatDuration((baseMinutes * multiplier).toInt().coerceAtLeast(2))
            // Timetables can vary by stopping pattern, but the same selected
            // section keeps its proportional base fare instead of getting a
            // station-count or speed multiplier.
            duration to basePrice.coerceAtLeast(1.0)
        }
    }

    private fun determineTrainCountForStops(stops: Int, minCount: Int, maxCount: Int): Int {
        // Search keeps its existing 40–60 default, while other consumers
        // (such as the station board) can ask the very same generator for a
        // compact sample without constructing dozens of unused services.
        val lowerBound = minOf(minCount, maxCount).coerceAtLeast(1)
        val upperBound = maxOf(minCount, maxCount).coerceAtLeast(lowerBound)
        val target = when {
            stops <= 2 -> 50 + (0..10).random()
            stops <= 5 -> 45 + (0..10).random()
            stops <= 10 -> 42 + (0..10).random()
            else -> 40 + (0..10).random()
        }
        return target.coerceIn(lowerBound, upperBound)
    }

    private fun generateTrainsForPath(
        from: String,
        to: String,
        option: PathOption,
        count: Int,
        routeType: RouteType
    ): List<Train> {
        val variants = buildVariants(from, to, option.path, routeType)
        val queryRoute = normalizeServiceRoute(option.path, from, to)
        // Build a small collection once per query, rather than once per result
        // row.  Search remains light-weight; station calls and their clocks are
        // still calculated only when a detail page or ticket is opened.
        val throughServiceRoutes = findThroughServiceRoutes(from, to, queryRoute, routeType)
        val directServiceRoute = hubHeadFor(from, to, queryRoute)
            ?.let { head -> head + queryRoute }
            ?: extendPastQueryToHub(queryRoute, from, to, routeType)
        val directOriginRate = if (LatestRailwayNetwork.isMajorHubStation(from)) 28 else 22
        val trains = ArrayList<Train>(count)
        repeat(count) { idx ->
            val number = generateUniqueTrainNumber(routeType)
            val (duration, price) = variants[idx % variants.size]
            val departureTime = generateDepartureTime(duration, routeType)
            val arrivalTime = calculateArrivalTime(departureTime, duration)
            val serviceSeed = kotlin.math.abs((number + from + to).hashCode()) % 100
            val isQueryOrigin = serviceSeed < directOriginRate || throughServiceRoutes.isEmpty()
            val serviceRoute = if (isQueryOrigin) {
                directServiceRoute
            } else {
                throughServiceRoutes[(idx + serviceSeed) % throughServiceRoutes.size]
            }
            trains.add(
                Train(
                    number = number,
                    departureStation = from,
                    arrivalStation = to,
                    departureTime = departureTime,
                    arrivalTime = arrivalTime,
                    duration = duration,
                    price = price,
                    availableSeats = (60..220).random(),
                    viaStations = queryRoute,
                    routeType = routeType,
                    serviceStations = serviceRoute
                )
            )
        }
        android.util.Log.d(
            "TrainGenerator",
            "为 $from -> $to 生成 ${trains.size} 趟${option.flavor}方案车次（查询区段: ${queryRoute.joinToString(" -> ")}；跨站方案: ${throughServiceRoutes.size}）"
        )
        return trains
    }

    /**
     * Finds realistic operating routes for a queried section.  The query's
     * departure/arrival stations must remain in this order, but most services
     * originate 2–6 calls earlier at a province or regional hub.  A small
     * share still begins at the queried station so the result list has variety.
     */
    private fun findThroughServiceRoutes(
        from: String,
        to: String,
        queryRoute: List<String>,
        routeType: RouteType
    ): List<List<String>> {
        if (queryRoute.size < 2) return emptyList()
        RailwayRouteManager.Probe.throughSearches++

        val candidates = LinkedHashSet<List<String>>()
        // The list begins with regional hubs (宜昌北、襄阳东等) and then provincial
        // capital hubs. Limit the graph searches so a first query stays fast.
        LatestRailwayNetwork.preferredServiceOriginHubs
            .asSequence()
            .filter { it != from && it != to }
            // A hub may be reached through one or more connected lines. This
            // is intentionally graph-wide within the same physical network,
            // rather than constrained to a single named railway line.
            .take(120)
            .forEach { origin ->
                val route = RailwayRouteManager.getRouteStationsMinStops(origin, to, routeType)
                if (isUsableThroughRoute(route, from, to)) candidates.add(route)
            }

        // If a station pair is contained in a single named line, retain the
        // line's real endpoint as a fallback even when that endpoint is not a
        // national hub. This prevents an artificial query-station origin on
        // isolated newer lines.
        RailwayData.getAllRoutes()
            .asSequence()
            .filter { it.routeType == routeType }
            .forEach { line ->
                val names = line.stations.map { it.name }
                val fromIndex = names.indexOf(from)
                val toIndex = names.indexOf(to)
                if (fromIndex >= 2 && toIndex > fromIndex) {
                    candidates.add(names.subList(0, toIndex + 1))
                }
            }

        return candidates
            .filter { isUsableThroughRoute(it, from, to) }
            .sortedWith(
                compareBy<List<String>> { route ->
                    val upstreamCalls = route.indexOf(from)
                    when (upstreamCalls) {
                        in 2..6 -> 0
                        in 7..12 -> 1
                        else -> 2
                    }
                }.thenBy { it.size }
            )
            .take(8)
            .map { extendPastQueryToHub(it, from, to, routeType) }
            .filter { isUsableThroughRoute(it, from, to) }
            .distinct()
            .take(8)
    }

    /**
     * The operating terminal should be a genuine hub when the chosen arrival
     * is an intermediate station. It may sit several physical calls beyond the
     * query station; this deliberately looks for the next practical hub rather
     * than merely appending the immediately adjacent station.
     */
    /**
     * Stations to prepend so a small intermediate station is not a service's origin, e.g.
     * 汉川→北京西 becomes 荆州→…→汉川→…→北京西.
     *
     * Two things this must get right, both of which an earlier attempt got wrong:
     *
     *  * The line is chosen by whether it carries the journey onward, not by how short the
     *    resulting head is.  Ranking by head length let a three-station stub that merely
     *    mentions 汉川 beat the real 宁蓉 line, which produced non-stop "成都东→汉川".
     *  * The head keeps every intermediate station between the hub and [from], taken straight
     *    from the line's own station list.
     *
     * Returns null when nothing suitable is found, so the caller falls back unchanged.  The
     * result is only accepted when prepending it still contains the whole query section.
     */
    private fun hubHeadFor(from: String, to: String, queryRoute: List<String>): List<String>? {
        val onward = queryRoute.getOrNull(1) ?: return null
        var best: List<String>? = null
        var bestLineLength = -1
        RealRailwayRoutes.getAllRoutes().forEach { line ->
            val names = line.stations.map { it.name }
            val index = names.indexOf(from)
            if (index <= 0) return@forEach
            val before = names.subList(0, index)      // line order, ends next to `from`
            val after = names.subList(index + 1, names.size)  // line order, starts next to `from`
            val onwardIsAfter = after.contains(onward)
            if (!onwardIsAfter && !before.contains(onward)) return@forEach

            // Walk away from `from` along the line and take the first hub met, so the train
            // starts at a hub without a needlessly long run-up.
            val head = if (onwardIsAfter) {
                val away = before.reversed()
                val hub = away.firstOrNull { LatestRailwayNetwork.isMajorHubStation(it) }
                    ?: return@forEach
                before.subList(before.indexOf(hub), before.size)
            } else {
                val hub = after.firstOrNull { LatestRailwayNetwork.isMajorHubStation(it) }
                    ?: return@forEach
                after.subList(0, after.indexOf(hub) + 1).reversed()
            }
            if (head.isEmpty()) return@forEach
            // Rank by the line's own length, never by the head's: a three-station stub that
            // merely mentions `from` yields the shortest head and would otherwise win,
            // producing a non-stop run from the hub.
            if (best == null || names.size > bestLineLength) {
                best = head
                bestLineLength = names.size
            }
        }
        val head = best ?: return null
        // A wrong pick must degrade to "no extension", never to a service that skips stops.
        val candidate = head + queryRoute
        return if (head.isNotEmpty() && candidate.toSet().containsAll(queryRoute)) head else null
    }

    private fun extendPastQueryToHub(
        route: List<String>,
        from: String,
        to: String,
        routeType: RouteType
    ): List<String> {
        if (LatestRailwayNetwork.isMajorHubStation(to)) return route
        RailwayRouteManager.Probe.hubExtensions++

        val origin = route.firstOrNull() ?: return route
        val extensions = LatestRailwayNetwork.preferredServiceOriginHubs
            .asSequence()
            .filter { it != origin && it != from && it != to }
            .take(120)
            .map { terminal -> RailwayRouteManager.getRouteStationsMinStops(origin, terminal, routeType) }
            .filter { isUsableServiceRoute(it, from, to) }
            .filter { path -> (path.lastIndex - path.indexOf(to)) in 1..48 }
            .sortedBy { path -> kotlin.math.abs((path.lastIndex - path.indexOf(to)) - 5) }
            .firstOrNull()
        return extensions ?: route
    }

    private fun isUsableThroughRoute(route: List<String>, from: String, to: String): Boolean {
        val departureIndex = route.indexOf(from)
        val arrivalIndex = route.indexOf(to)
        return isUsableServiceRoute(route, from, to) && departureIndex >= 2
    }

    private fun isUsableServiceRoute(route: List<String>, from: String, to: String): Boolean {
        val departureIndex = route.indexOf(from)
        val arrivalIndex = route.indexOf(to)
        return route.size >= 2 && departureIndex >= 0 && arrivalIndex > departureIndex &&
            route.distinct().size == route.size
    }

    private fun normalizeServiceRoute(path: List<String>, from: String, to: String): List<String> {
        val source = if (path.isEmpty()) listOf(from, to) else path
        val normalized = buildList {
            if (source.firstOrNull() != from) add(from)
            addAll(source)
            if (source.lastOrNull() != to) add(to)
        }.fold(mutableListOf<String>()) { result, station ->
            if (station.isNotBlank() && result.lastOrNull() != station) result.add(station)
            result
        }
        return if (normalized.indexOf(from) >= 0 && normalized.indexOf(to) > normalized.indexOf(from)) {
            normalized
        } else {
            listOf(from, to)
        }
    }

    // 与 RailwayRouteManager 中逻辑一致的确定性基因子（15..25）
    private fun baseFactorForPair(from: String, to: String): Int {
        val key = if (from <= to) "$from->$to" else "$to->$from"
        val hash = key.hashCode()
        val range = 11 // 15..25
        val offset = kotlin.math.abs(hash % range)
        return 15 + offset
    }
    
    /**
     * 生成特定区间的列车（用于中转查询）
     */
    fun generateTrainsForSegment(fromStation: String, toStation: String, count: Int = 10): List<Train> {
        val trains = mutableListOf<Train>()
        val route = RailwayRouteManager.findRoute(fromStation, toStation, RouteType.HIGH_SPEED)
            ?: RailwayRouteManager.findRoute(fromStation, toStation, RouteType.CONVENTIONAL)
        
        if (route != null) {
            val variants = buildVariants(fromStation, toStation, routeType = route.routeType)
            for (i in 1..count) {
                val trainNumber = generateTrainNumber(route.routeType, i)
                val (duration, price) = variants[(i - 1) % variants.size]
                val departureTime = generateDepartureTime(duration, route.routeType)
                val arrivalTime = calculateArrivalTime(departureTime, duration)
                
                trains.add(Train(
                    number = trainNumber,
                    departureStation = fromStation,
                    arrivalStation = toStation,
                    departureTime = departureTime,
                    arrivalTime = arrivalTime,
                    duration = duration,
                    price = price,
                    availableSeats = (20..100).random(),
                    routeType = route.routeType
                ))
            }
        }
        
        return trains
    }
    
    /**
     * 生成跨线车次
     * 分析线路连接点，生成跨线直达车次
     * 即使有直达车次也生成跨线车次
     * 确保每个中间站都有跨线车次
     */
    fun generateCrossLineTrains(routes: List<RailwayRoute>): List<Train> {
        val crossLineTrains = mutableListOf<Train>()
        
        try {
            android.util.Log.d("TrainGenerator", "开始生成跨线车次...")
            
            // 定义已知的线路连接点（扩展版本，包含更多连接点）
            // 特别标注：
            // - 武汉枢纽：武汉、汉口、武昌、武汉东
            // - 南昌枢纽：南昌西、南昌东、南昌南
            val connectionPoints = mapOf(
                "襄阳东" to listOf("汉十高铁", "襄荆高铁"),
                "汉口" to listOf("汉十高铁", "武宜高铁", "宁蓉铁路", "武汉枢纽连接线"),
                "荆门西" to listOf("武宜高铁", "襄荆高铁"),
                "荆州" to listOf("襄荆高铁", "宁蓉铁路"),
                "宜昌东" to listOf("武宜高铁", "宁蓉铁路"),
                "武汉" to listOf("武九客专", "武广高铁", "京广高铁", "武汉枢纽连接线"),
                "武昌" to listOf("武汉枢纽连接线"),
                "武汉东" to listOf("武汉枢纽连接线"),
                "九江" to listOf("武九客专", "昌九城际"),
                "南昌西" to listOf("昌九城际", "昌福铁路", "沪昆高铁", "南昌枢纽联络线"),
                "南昌东" to listOf("京港高铁", "南昌枢纽联络线"),
                "南昌南" to listOf("南昌枢纽联络线"),
                "福州" to listOf("昌福铁路", "合福高铁"),
                "厦门北" to listOf("厦深铁路"),
                "深圳北" to listOf("厦深铁路", "京港高铁", "广深港高铁"),
                "香港西九龙" to listOf("京港高铁", "广深港高铁"),
                "上海" to listOf("沪宁城际"),
                "上海虹桥" to listOf("京沪高铁", "沪昆高铁"),
                "北京南" to listOf("京沪高铁"),
                "北京丰台" to listOf("京港高铁"),
                "北京西" to listOf("京广高铁"),
                "郑州东" to listOf("京港高铁", "徐兰高铁", "京广高铁"),
                "西安北" to listOf("徐兰高铁", "西成高铁", "郑西高铁"),
                "成都东" to listOf("宁蓉铁路", "成渝高铁", "西成高铁"),
                "重庆北" to listOf("宁蓉铁路", "成渝高铁"),
                "广州南" to listOf("武广高铁", "广深港高铁", "京广高铁", "贵广高铁"),
                "长沙南" to listOf("武广高铁", "京广高铁", "沪昆高铁"),
                "合肥南" to listOf("宁蓉铁路", "合福高铁"),
                "贵阳北" to listOf("贵广高铁", "沪昆高铁"),
                "兰州西" to listOf("徐兰高铁", "兰新高铁")
            )
            
            // 1. 为预定义的连接点生成跨线车次
            var processedPairs = mutableSetOf<Pair<String, String>>()
            
            connectionPoints.forEach { (connectionStation, connectedRoutes) ->
                // 为该连接点的所有线路两两组合生成跨线车次
                for (i in connectedRoutes.indices) {
                    for (j in i + 1 until connectedRoutes.size) {
                        val routeName1 = connectedRoutes[i]
                        val routeName2 = connectedRoutes[j]
                        
                        val route1 = routes.find { it.routeName.contains(routeName1) }
                        val route2 = routes.find { it.routeName.contains(routeName2) }
                        
                        if (route1 != null && route2 != null) {
                            val pairKey = if (route1.routeId < route2.routeId) {
                                Pair(route1.routeId, route2.routeId)
                            } else {
                                Pair(route2.routeId, route1.routeId)
                            }
                            
                            if (!processedPairs.contains(pairKey)) {
                                processedPairs.add(pairKey)
                                // 生成完整的跨线车次矩阵
                                generateCrossLineMatrix(route1, route2, connectionStation, crossLineTrains)
                            }
                        }
                    }
                }
            }
            
            // 2. 自动检测其他可能的连接点（共享站点的线路）
            android.util.Log.d("TrainGenerator", "自动检测额外的连接点...")
            val stationToRoutes = mutableMapOf<String, MutableList<RailwayRoute>>()
            
            // 构建站点到线路的映射
            routes.forEach { route ->
                route.stations.forEach { station ->
                    stationToRoutes.getOrPut(station.name) { mutableListOf() }.add(route)
                }
            }
            
            // 为每个共享站点生成跨线车次
            stationToRoutes.forEach { (station, routeList) ->
                if (routeList.size >= 2) {
                    // 为该站点的所有线路两两组合生成跨线车次
                    for (i in routeList.indices) {
                        for (j in i + 1 until routeList.size) {
                            val route1 = routeList[i]
                            val route2 = routeList[j]
                            
                            val pairKey = if (route1.routeId < route2.routeId) {
                                Pair(route1.routeId, route2.routeId)
                            } else {
                                Pair(route2.routeId, route1.routeId)
                            }
                            
                            // 只处理未处理过的线路对
                            if (!processedPairs.contains(pairKey)) {
                                processedPairs.add(pairKey)
                                android.util.Log.d("TrainGenerator", "发现新连接点: $station 连接 ${route1.routeName} 和 ${route2.routeName}")
                                generateCrossLineMatrix(route1, route2, station, crossLineTrains)
                            }
                        }
                    }
                }
            }
            
            // 特别处理汉口-沙洋西的跨线车次（汉十高铁+襄荆高铁）
            val hanshiRoute = routes.find { it.routeName.contains("汉十") }
            val xiangjingRoute = routes.find { it.routeName.contains("襄荆") }
            
            if (hanshiRoute != null && xiangjingRoute != null) {
                generateCrossLineMatrix(hanshiRoute, xiangjingRoute, "襄阳东", crossLineTrains)
            }
            
            // 特别处理武汉-庐山-南昌西的跨线车次（武九客专+昌九城际）
            val wujiuRoute = routes.find { it.routeName.contains("武九") }
            val changjiuRoute = routes.find { it.routeName.contains("昌九") }
            
            if (wujiuRoute != null && changjiuRoute != null) {
                generateCrossLineMatrix(wujiuRoute, changjiuRoute, "九江", crossLineTrains)
            }
            
            // 生成更多跨线车次组合
            generateAdditionalCrossLineTrains(routes, crossLineTrains)
            
            android.util.Log.d("TrainGenerator", "成功生成 ${crossLineTrains.size} 条跨线车次")
            
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("TrainGenerator", "生成跨线车次失败: ${e.message}")
        }
        
        return crossLineTrains
    }
    
    /**
     * 生成跨线车次
     */
    private fun generateCrossLineTrain(route1: RailwayRoute, route2: RailwayRoute, connectionStation: String): Train? {
        return try {
            val fromStation = route1.stations.first().name
            val toStation = route2.stations.last().name
            
            // 避免生成重复的直达车次
            if (fromStation == toStation) return null
            
            val trainNumber = generateUniqueTrainNumber(route1.routeType)
            val variants = buildVariants(fromStation, toStation)
            val (totalDuration, totalPrice) = variants[random.nextInt(variants.size)]
            val departureTime = generateDepartureTime(totalDuration, route1.routeType)
            val arrivalTime = calculateArrivalTime(departureTime, totalDuration)

            Train(
                number = trainNumber,
                departureStation = fromStation,
                arrivalStation = toStation,
                departureTime = departureTime,
                arrivalTime = arrivalTime,
                duration = totalDuration,
                price = totalPrice,
                availableSeats = (30..150).random(),
                viaStations = generateCrossLineViaStations(route1, route2, connectionStation, fromStation, toStation) // 生成完整途径车站
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 生成特定的跨线车次（如汉口-沙洋西）
     */
    private fun generateSpecificCrossLineTrain(
        fromStation: String, 
        toStation: String,
        route1: RailwayRoute, 
        route2: RailwayRoute, 
        connectionStation: String
    ): Train? {
        return try {
            // 生成唯一的车次号
            val trainNumber = generateUniqueTrainNumber(route1.routeType)
            val variants = buildVariants(fromStation, toStation)
            val (totalDuration, totalPrice) = variants[random.nextInt(variants.size)]
            val departureTime = generateDepartureTime(totalDuration, route1.routeType)
            val arrivalTime = calculateArrivalTime(departureTime, totalDuration)

            Train(
                number = trainNumber,
                departureStation = fromStation,
                arrivalStation = toStation,
                departureTime = departureTime,
                arrivalTime = arrivalTime,
                duration = totalDuration,
                price = totalPrice,
                availableSeats = (30..150).random(),
                viaStations = generateCrossLineViaStations(route1, route2, connectionStation, fromStation, toStation) // 生成完整途径车站
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 计算跨线车次的总耗时
     */
    private fun calculateCrossLineDuration(duration1: String, duration2: String): String {
        val totalMinutes = parseDurationToMinutes(duration1) + parseDurationToMinutes(duration2)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return "${hours}小时${minutes}分钟"
    }
    
    /**
     * 计算跨线车次的总票价
     */
    private fun calculateCrossLinePrice(route1: RailwayRoute, route2: RailwayRoute, connectionStation: String): Double {
        // 简化计算：两段线路的全程票价之和，然后打9折（跨线优惠）
        val totalPrice = route1.totalPrice + route2.totalPrice
        return (totalPrice * 0.9).toInt().toDouble()
    }
    
    /**
     * 生成跨线车次矩阵
     * 为两条线路生成完整的跨线车次矩阵，确保每个中间站都有车
     */
    private fun generateCrossLineMatrix(route1: RailwayRoute, route2: RailwayRoute, connectionStation: String, crossLineTrains: MutableList<Train>) {
        try {
            android.util.Log.d("TrainGenerator", "生成跨线车次矩阵: ${route1.routeName} + ${route2.routeName}")
            
            // 增加车次数量，确保每对站点都有足够车次
            val trainCount = 30 // 每对站点至少30趟车
            
            // 生成route1到route2的跨线车次
            for (i in 1..trainCount) {
                // route1起点到route2终点
                val crossLineTrain = generateCrossLineTrain(route1, route2, connectionStation)
                if (crossLineTrain != null) {
                    crossLineTrains.add(crossLineTrain)
                }
                
                // route1起点到route2中间站
                route2.stations.forEach { station ->
                    if (station.name != route2.stations.first().name && station.name != route2.stations.last().name) {
                        val midStationTrain = generateSpecificCrossLineTrain(
                            route1.stations.first().name, 
                            station.name,
                            route1, route2, 
                            connectionStation
                        )
                        if (midStationTrain != null) {
                            crossLineTrains.add(midStationTrain)
                        }
                    }
                }
                
                // route1中间站到route2终点
                route1.stations.forEach { station ->
                    if (station.name != route1.stations.first().name && station.name != route1.stations.last().name) {
                        val midStationTrain = generateSpecificCrossLineTrain(
                            station.name,
                            route2.stations.last().name,
                            route1, route2, 
                            connectionStation
                        )
                        if (midStationTrain != null) {
                            crossLineTrains.add(midStationTrain)
                        }
                    }
                }
                
                // route1中间站到route2中间站
                route1.stations.forEach { station1 ->
                    if (station1.name != route1.stations.first().name && station1.name != route1.stations.last().name) {
                        route2.stations.forEach { station2 ->
                            if (station2.name != route2.stations.first().name && station2.name != route2.stations.last().name) {
                                val midToMidTrain = generateSpecificCrossLineTrain(
                                    station1.name,
                                    station2.name,
                                    route1, route2, 
                                    connectionStation
                                )
                                if (midToMidTrain != null) {
                                    crossLineTrains.add(midToMidTrain)
                                }
                            }
                        }
                    }
                }
            }
            
            // 生成route2到route1的跨线车次
            for (i in 1..trainCount) {
                // route2起点到route1终点
                val reverseCrossLineTrain = generateCrossLineTrain(route2, route1, connectionStation)
                if (reverseCrossLineTrain != null) {
                    crossLineTrains.add(reverseCrossLineTrain)
                }
                
                // route2起点到route1中间站
                route1.stations.forEach { station ->
                    if (station.name != route1.stations.first().name && station.name != route1.stations.last().name) {
                        val midStationTrain = generateSpecificCrossLineTrain(
                            route2.stations.first().name, 
                            station.name,
                            route2, route1, 
                            connectionStation
                        )
                        if (midStationTrain != null) {
                            crossLineTrains.add(midStationTrain)
                        }
                    }
                }
                
                // route2中间站到route1终点
                route2.stations.forEach { station ->
                    if (station.name != route2.stations.first().name && station.name != route2.stations.last().name) {
                        val midStationTrain = generateSpecificCrossLineTrain(
                            station.name,
                            route1.stations.last().name,
                            route2, route1, 
                            connectionStation
                        )
                        if (midStationTrain != null) {
                            crossLineTrains.add(midStationTrain)
                        }
                    }
                }
            }
            
            android.util.Log.d("TrainGenerator", "为 ${route1.routeName} + ${route2.routeName} 生成了跨线车次")
            
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("TrainGenerator", "生成跨线车次矩阵失败: ${e.message}")
        }
    }
    
    /**
     * 生成跨线车次的完整途径车站
     */
    private fun generateCrossLineViaStations(route1: RailwayRoute, route2: RailwayRoute, connectionStation: String, fromStation: String, toStation: String): List<String> {
        val viaStations = mutableListOf<String>()
        
        try {
            // 动态确定fromStation和toStation分别在哪个路线上
            val fromRoute: RailwayRoute
            val toRoute: RailwayRoute
            val fromIndex: Int
            val toIndex: Int
            val connectionIndex1: Int
            val connectionIndex2: Int
            
            // 检查fromStation在哪个路线上
            val fromIndex1 = route1.stations.indexOfFirst { it.name == fromStation }
            val fromIndex2 = route2.stations.indexOfFirst { it.name == fromStation }
            
            // 检查toStation在哪个路线上
            val toIndex1 = route1.stations.indexOfFirst { it.name == toStation }
            val toIndex2 = route2.stations.indexOfFirst { it.name == toStation }
            
            if (fromIndex1 != -1 && toIndex2 != -1) {
                // fromStation在route1，toStation在route2
                fromRoute = route1
                toRoute = route2
                fromIndex = fromIndex1
                toIndex = toIndex2
                connectionIndex1 = route1.stations.indexOfFirst { it.name == connectionStation }
                connectionIndex2 = route2.stations.indexOfFirst { it.name == connectionStation }
            } else if (fromIndex2 != -1 && toIndex1 != -1) {
                // fromStation在route2，toStation在route1
                fromRoute = route2
                toRoute = route1
                fromIndex = fromIndex2
                toIndex = toIndex1
                connectionIndex1 = route2.stations.indexOfFirst { it.name == connectionStation }
                connectionIndex2 = route1.stations.indexOfFirst { it.name == connectionStation }
            } else {
                android.util.Log.e("TrainGenerator", "无法确定跨线车次路线: $fromStation -> $toStation")
                return emptyList()
            }
            
            if (connectionIndex1 != -1 && connectionIndex2 != -1) {
                // 添加fromRoute中fromStation到connectionStation之间的车站
                if (fromIndex < connectionIndex1) {
                    // 正向：fromStation在connectionStation之前
                    for (i in fromIndex + 1..connectionIndex1) {
                        if (i < fromRoute.stations.size) {
                            viaStations.add(fromRoute.stations[i].name)
                        }
                    }
                } else {
                    // 反向：fromStation在connectionStation之后
                    for (i in connectionIndex1 + 1..fromIndex) {
                        if (i < fromRoute.stations.size) {
                            viaStations.add(fromRoute.stations[i].name)
                        }
                    }
                }
                
                // 添加toRoute中connectionStation到toStation之间的车站
                if (connectionIndex2 < toIndex) {
                    // 正向：connectionStation在toStation之前
                    for (i in connectionIndex2 + 1..toIndex) {
                        if (i < toRoute.stations.size) {
                            viaStations.add(toRoute.stations[i].name)
                        }
                    }
                } else {
                    // 反向：connectionStation在toStation之后
                    for (i in toIndex + 1..connectionIndex2) {
                        if (i < toRoute.stations.size) {
                            viaStations.add(toRoute.stations[i].name)
                        }
                    }
                }
            }
            
            android.util.Log.d("TrainGenerator", "跨线车次途径车站: $fromStation -> ${viaStations.joinToString(" -> ")} -> $toStation")
            android.util.Log.d("TrainGenerator", "路线信息: ${fromRoute.routeName} -> ${toRoute.routeName}")
            
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("TrainGenerator", "生成跨线车次途径车站失败: ${e.message}")
        }
        
        return viaStations
    }
    
    /**
     * 生成更多跨线车次组合
     */
    private fun generateAdditionalCrossLineTrains(routes: List<RailwayRoute>, crossLineTrains: MutableList<Train>) {
        try {
            // 生成汉口-襄阳东-荆门西的跨线车次
            val hanshiRoute = routes.find { it.routeName.contains("汉十") }
            val xiangjingRoute = routes.find { it.routeName.contains("襄荆") }
            
            if (hanshiRoute != null && xiangjingRoute != null) {
                // 汉口-荆门西（通过襄阳东）
                val hankouToJingmenTrain = generateSpecificCrossLineTrain(
                    "汉口", "荆门西",
                    hanshiRoute, xiangjingRoute,
                    "襄阳东"
                )
                if (hankouToJingmenTrain != null) {
                    crossLineTrains.add(hankouToJingmenTrain)
                }
                
                // 汉口-宜城（通过襄阳东）
                val hankouToYichengTrain = generateSpecificCrossLineTrain(
                    "汉口", "宜城",
                    hanshiRoute, xiangjingRoute,
                    "襄阳东"
                )
                if (hankouToYichengTrain != null) {
                    crossLineTrains.add(hankouToYichengTrain)
                }
                
                // 汉口-荆州（通过襄阳东）
                val hankouToJingzhouTrain = generateSpecificCrossLineTrain(
                    "汉口", "荆州",
                    hanshiRoute, xiangjingRoute,
                    "襄阳东"
                )
                if (hankouToJingzhouTrain != null) {
                    crossLineTrains.add(hankouToJingzhouTrain)
                }
            }
            
            // 生成武汉-南昌西的跨线车次（武九客专+昌九城际）
            val wujiuRoute = routes.find { it.routeName.contains("武九") }
            val changjiuRoute = routes.find { it.routeName.contains("昌九") }
            
            if (wujiuRoute != null && changjiuRoute != null) {
                val wuhanToNanchangTrain = generateSpecificCrossLineTrain(
                    "武汉", "南昌西",
                    wujiuRoute, changjiuRoute,
                    "九江"
                )
                if (wuhanToNanchangTrain != null) {
                    crossLineTrains.add(wuhanToNanchangTrain)
                }
            }
            
            // 生成北京-深圳的跨线车次（京港高铁）
            val jinggangRoute = routes.find { it.routeName.contains("京港") }
            if (jinggangRoute != null) {
                val beijingToShenzhenTrain = generateSpecificCrossLineTrain(
                    "北京丰台", "深圳北",
                    jinggangRoute, jinggangRoute,
                    "郑州东"
                )
                if (beijingToShenzhenTrain != null) {
                    crossLineTrains.add(beijingToShenzhenTrain)
                }
            }
            
            android.util.Log.d("TrainGenerator", "生成了额外的跨线车次组合")
            
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("TrainGenerator", "生成额外跨线车次失败: ${e.message}")
        }
    }
    
}
