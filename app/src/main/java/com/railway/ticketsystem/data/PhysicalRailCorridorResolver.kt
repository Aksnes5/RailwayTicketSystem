package com.railway.ticketsystem.data

import com.railway.ticketsystem.model.RailwayRouteManager
import com.railway.ticketsystem.model.RouteType

/**
 * Converts a logical passenger service into physical railway sections for the route map.
 * It is map-only: the order's published calls and timetable never change here.
 *
 * High-speed/city rail and conventional rail are separate networks.  Parallel lines that
 * share a city name are never allowed to borrow one another's geometry.
 */
enum class MapRailNetwork { HIGH_SPEED, CONVENTIONAL }

object PhysicalRailCorridorResolver {
    data class ResolvedRoute(
        val stationNames: List<String>,
        val legCorridorHints: List<String?>
    )

    private data class Corridor(
        val id: String,
        val network: MapRailNetwork,
        val mapCorridor: String?,
        val stationNames: List<String>
    ) {
        fun sectionBetween(from: String, to: String): List<String>? {
            val first = stationNames.indexOf(from)
            val second = stationNames.indexOf(to)
            if (first < 0 || second < 0 || first == second) return null
            return if (first < second) stationNames.subList(first, second + 1)
            else stationNames.subList(second, first + 1).asReversed()
        }
        fun containsLeg(from: String, to: String): Boolean = sectionBetween(from, to)?.size == 2
    }

    /** No matching OSM way is bundled yet: retain the actual station chain, never snap to a parallel line. */
    private const val MANUAL_ALIGNMENT = "__manual_physical_alignment__"

    private val baseCorridors = listOf(
        Corridor("wuhan-hub", MapRailNetwork.HIGH_SPEED, "武汉枢纽连接线", listOf("武汉", "汉口", "武昌", "武汉东")),
        Corridor("wuyi-hanyi", MapRailNetwork.HIGH_SPEED, "武宜高铁", listOf("汉口", "汉川北", "天门", "京山南", "钟祥南", "荆门西", "当阳西", "宜昌北", "宜昌东")),
        Corridor("hanxiao-hanshi", MapRailNetwork.HIGH_SPEED, "汉十高铁", listOf("汉口", "后湖", "金银潭", "天河机场", "天河街", "闵集", "毛陈", "槐荫", "孝感东", "云梦东", "安陆西", "随州南", "随县", "枣阳", "襄阳东", "隆中", "谷城北", "丹江口南", "武当山西", "十堰东")),
        Corridor("wuhuang-wujiu", MapRailNetwork.HIGH_SPEED, "武九客专", listOf("武汉", "葛店南", "华容南", "鄂州", "鄂州东", "花湖", "黄石北", "大冶北", "白沙铺", "阳新", "枫林", "瑞昌西", "柴桑", "庐山", "九江")),
        Corridor("hanyi-ningrong", MapRailNetwork.HIGH_SPEED, "宁蓉铁路", listOf("汉口", "汉川", "天门南", "仙桃西", "潜江", "荆州", "枝江北", "宜昌东")),
        Corridor("jingguang-hsr", MapRailNetwork.HIGH_SPEED, "京广高铁", listOf("北京西", "涿州东", "高碑店东", "保定东", "石家庄", "邯郸东", "安阳东", "鹤壁东", "郑州东", "许昌东", "漯河西", "驻马店西", "信阳东", "孝感北", "武汉", "岳阳东", "长沙南", "衡阳东", "郴州西", "韶关", "广州南")),
        Corridor("changfu-main", MapRailNetwork.HIGH_SPEED, "昌福铁路", listOf("南昌西", "抚州", "南城", "南丰", "建宁县北", "泰宁", "将乐", "三明北", "尤溪", "永泰", "福州")),
        // 尤溪往厦门方向经永泰接入永莆、福厦方向。两段均有本地 GIS 实体轨迹，
        // 不能再标记为手工直线，否则尤溪之后会错误地折向福州。
        Corridor("yongpu-branch", MapRailNetwork.HIGH_SPEED, "永莆铁路", listOf("永泰", "莆田")),
        Corridor("fuxia-main", MapRailNetwork.HIGH_SPEED, "福厦高铁", listOf("福州", "福州南", "福清", "莆田", "仙游", "泉州", "晋江", "厦门北", "厦门")),
        Corridor("qinshen-jingha", MapRailNetwork.HIGH_SPEED, "秦沈客专", listOf("秦皇岛", "山海关", "东戴河", "绥中北", "兴城西", "葫芦岛北", "锦州南", "凌海南", "盘锦北", "台安", "辽中", "沈阳北")),

        // Conventional trunk lines intentionally do not use high-speed corridor hints.
        Corridor("jingguang-conventional", MapRailNetwork.CONVENTIONAL, null, listOf("北京", "保定", "石家庄", "邯郸", "安阳", "新乡", "郑州", "漯河", "驻马店", "信阳", "孝感", "武昌", "岳阳", "长沙", "株洲", "衡阳", "郴州", "韶关东", "广州")),
        Corridor("jinghu-conventional", MapRailNetwork.CONVENTIONAL, null, listOf("北京", "廊坊北", "天津", "静海", "沧州", "德州", "济南", "泰山", "兖州", "枣庄西", "徐州", "宿州", "蚌埠", "滁州北", "南京", "镇江", "常州", "无锡", "苏州", "昆山", "上海")),
        Corridor("hukun-conventional", MapRailNetwork.CONVENTIONAL, null, listOf("上海", "嘉兴", "杭州", "金华", "上饶", "鹰潭", "南昌", "萍乡", "株洲", "湘潭", "娄底", "怀化", "玉屏", "凯里", "贵阳", "安顺", "六盘水", "宣威", "曲靖", "昆明")),
        Corridor("longhai-conventional", MapRailNetwork.CONVENTIONAL, null, listOf("连云港东", "徐州", "商丘", "开封", "郑州", "洛阳", "三门峡", "灵宝", "华山", "渭南", "西安", "宝鸡", "天水", "甘谷", "陇西", "定西", "兰州"))
    )

    fun resolveForMap(rawStations: List<String>, network: MapRailNetwork = MapRailNetwork.HIGH_SPEED): ResolvedRoute {
        val stations = rawStations.asSequence().map(String::trim).filter(String::isNotBlank)
            .fold(mutableListOf<String>()) { result, station -> if (result.lastOrNull() != station) result.add(station); result }
        if (stations.size < 2) return ResolvedRoute(stations, emptyList())
        // The static list preserves known alias and hub connectors. The route
        // catalogue then contributes every installed passenger line as a
        // physical station chain, so newly imported routes are not reduced to
        // a single city-to-city chord on the map.
        val staticScoped = baseCorridors.filter { it.network == network }
        val scoped = staticScoped + catalogCorridors(network)
        var index = 0
        while (index < stations.lastIndex) {
            val from = stations[index]
            val to = stations[index + 1]
            val direct = scoped.asSequence().mapNotNull { it.sectionBetween(from, to)?.takeIf { section -> section.size > 2 } }
                .minByOrNull { it.size }
            val replacement = direct ?: findConnectedSection(from, to, staticScoped)
            if (replacement == null || replacement.size <= 2) index++
            else { stations.addAll(index + 1, replacement.drop(1)); index += replacement.lastIndex }
        }
        return ResolvedRoute(stations, stations.zipWithNext { from, to -> scoped.firstOrNull { it.containsLeg(from, to) }?.mapCorridor })
    }

    /**
     * Routes are registered before any search result can be opened. Reusing
     * that one catalogue keeps new lines (including 沈白、广湛等) mapped
     * without maintaining a second, error-prone station list.
     *
     * The preferred corridor name is deliberately soft: the web map falls
     * back to a same-line OSM match if a particular imported corridor is not
     * part of the bundled geometry yet.
     */
    // Some catalog services are commercial names for a physical trunk whose
    // OSM geometry is stored under the operational line name. Keep this map
    // deliberately small and evidence-based; a fuzzy match could put a
    // service on a parallel railway.
    private val mapCorridorAliases = mapOf(
        "贵昆铁路" to "沪昆铁路",
        "兰张高铁" to "兰新高铁",
        "合安高铁" to "京港高铁"
    )

    private fun catalogCorridors(network: MapRailNetwork): List<Corridor> =
        RailwayRouteManager.getAllRoutes().asSequence()
            .filter { route ->
                val samePhysicalNetwork = if (network == MapRailNetwork.CONVENTIONAL) {
                    route.routeType == RouteType.CONVENTIONAL
                } else {
                    route.routeType != RouteType.CONVENTIONAL
                }
                samePhysicalNetwork && route.stations.size > 1
            }
            .map { route ->
                Corridor(
                    id = "catalog:${route.routeId}",
                    network = network,
                    mapCorridor = mapCorridorAliases[route.routeName] ?: route.routeName,
                    stationNames = route.stations.map { it.name }
                )
            }
            .toList()

    /** Follows only explicit shared endpoints, preserving real junctions and registered connectors. */
    private fun findConnectedSection(from: String, to: String, scoped: List<Corridor>): List<String>? {
        val links = mutableMapOf<String, MutableSet<String>>()
        scoped.forEach { corridor -> corridor.stationNames.zipWithNext { first, second ->
            links.getOrPut(first) { linkedSetOf() }.add(second)
            links.getOrPut(second) { linkedSetOf() }.add(first)
        } }
        if (from !in links || to !in links) return null
        val queue = ArrayDeque<List<String>>().apply { add(listOf(from)) }
        val visited = mutableSetOf(from)
        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            if (path.last() == to) return path.takeIf { it.size <= 40 }
            links[path.last()].orEmpty().forEach { next -> if (visited.add(next)) queue.add(path + next) }
        }
        return null
    }
}