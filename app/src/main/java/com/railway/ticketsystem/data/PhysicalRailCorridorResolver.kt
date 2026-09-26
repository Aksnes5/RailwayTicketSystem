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
        Corridor("wuxiao-intercity", MapRailNetwork.HIGH_SPEED, "武孝城际铁路", listOf("汉口", "后湖", "金银潭", "天河机场", "天河街", "闵集", "毛陈", "槐荫", "孝感东")),
        Corridor("hangwen-hsr", MapRailNetwork.HIGH_SPEED, "杭温高铁", listOf("桐庐东", "浦江", "义乌", "横店", "磐安", "仙居", "楠溪江", "温州北", "温州南")),
        Corridor("chihuang-hsr", MapRailNetwork.HIGH_SPEED, "池黄高铁", listOf("池州", "九华山", "黄山西", "黄山北")),
        Corridor("meilong-hsr", MapRailNetwork.HIGH_SPEED, "梅龙高铁", listOf("梅州西", "兴宁南", "五华", "龙川西")),
        Corridor("guangzhan-hsr", MapRailNetwork.HIGH_SPEED, "广湛高铁", listOf("广州白云", "佛山", "佛肇", "新兴南", "阳春东", "阳江北", "马踏", "茂名南", "吴川", "湛江北")),
        Corridor("tianjin-hub-hsr", MapRailNetwork.HIGH_SPEED, "天津枢纽高速联络线", listOf("天津南", "天津西", "天津")),
        Corridor("xian-hub-hsr", MapRailNetwork.HIGH_SPEED, "西安枢纽高速联络线", listOf("西安北", "西安东")),
        Corridor("xiyan-hsr", MapRailNetwork.HIGH_SPEED, "西延高铁", listOf("西安东", "高陵", "富平南", "铜川", "铜川北", "宜君", "黄陵", "洛川", "富县北", "甘泉北", "延安")),
        Corridor("nanchang-hub", MapRailNetwork.HIGH_SPEED, "南昌枢纽联络线", listOf("南昌西", "南昌")),
        Corridor("chengziyi-hsr", MapRailNetwork.HIGH_SPEED, "成自宜高铁", listOf("成都东", "天府机场", "自贡", "宜宾")),
        Corridor("chuannan-intercity", MapRailNetwork.HIGH_SPEED, "川南城际铁路", listOf("内江北", "自贡", "富顺", "泸县", "泸州")),
        Corridor("chengmianle-hsr", MapRailNetwork.HIGH_SPEED, "成绵乐客专", listOf("江油", "绵阳", "德阳", "成都东", "成都南", "双流机场", "眉山东", "乐山", "峨眉山")),
        Corridor("wuhuang-wujiu", MapRailNetwork.HIGH_SPEED, "武九客专", listOf("武汉", "葛店南", "华容南", "鄂州", "鄂州东", "花湖", "黄石北", "大冶北", "白沙铺", "阳新", "枫林", "瑞昌西", "柴桑", "庐山", "九江")),
        Corridor("hanyi-ningrong", MapRailNetwork.HIGH_SPEED, "宁蓉铁路", listOf("汉口", "汉川", "天门南", "仙桃西", "潜江", "荆州", "枝江北", "宜昌东")),
        Corridor("jingguang-hsr", MapRailNetwork.HIGH_SPEED, "京广高铁", listOf("北京西", "涿州东", "高碑店东", "保定东", "石家庄", "邯郸东", "安阳东", "鹤壁东", "郑州东", "许昌东", "漯河西", "驻马店西", "信阳东", "孝感北", "武汉", "岳阳东", "长沙南", "衡阳东", "郴州西", "韶关", "广州南")),
        Corridor("hefu-hsr", MapRailNetwork.HIGH_SPEED, "合福高铁", listOf("合肥南", "长临河", "巢湖东", "无为", "铜陵北", "南陵", "泾县", "旌德", "绩溪北", "歙县北", "黄山北", "婺源", "德兴", "上饶", "五府山", "武夷山北", "南平市", "建瓯西", "延平", "古田北", "闽清北", "福州", "福州南")),
        Corridor("hukun-hsr", MapRailNetwork.HIGH_SPEED, "沪昆高铁", listOf("上海虹桥", "松江南", "金山北", "嘉善南", "嘉兴南", "桐乡", "海宁西", "余杭", "杭州东", "诸暨", "义乌", "金华", "龙游", "衢州", "江山", "玉山南", "上饶", "弋阳", "鹰潭北", "抚州东", "进贤南", "南昌西", "高安", "新余北", "宜春", "萍乡北", "醴陵东", "长沙南", "湘潭北", "韶山南", "娄底南", "邵阳北", "新化南", "溆浦南", "怀化南", "芷江", "新晃西", "铜仁南", "三穗", "凯里南", "贵定北", "贵阳北", "平坝南", "安顺西", "关岭", "普安县", "盘州", "富源北", "曲靖北", "嵩明", "昆明南")),
        Corridor("changfu-main", MapRailNetwork.HIGH_SPEED, "昌福铁路", listOf("南昌西", "抚州", "南城", "南丰", "建宁县北", "泰宁", "将乐", "三明北", "尤溪", "永泰", "福州", "福州南")),
        Corridor("fuzhou-hub", MapRailNetwork.HIGH_SPEED, "福厦高铁", listOf("福州", "福州南")),
        Corridor("yongpu-branch", MapRailNetwork.HIGH_SPEED, "永莆铁路", listOf("永泰", "莆田")),
        Corridor("fuxia-main", MapRailNetwork.HIGH_SPEED, "福厦高铁", listOf("福州", "福州南", "福清", "莆田", "仙游", "泉州", "晋江", "厦门北", "厦门")),
        Corridor("jinghu-hsr", MapRailNetwork.HIGH_SPEED, "京沪高铁", listOf("北京南", "廊坊", "天津南", "沧州西", "德州东", "济南西", "泰安", "曲阜东", "滕州东", "枣庄", "徐州东", "宿州东", "蚌埠南", "定远", "滁州", "南京南", "镇江南", "丹阳北", "常州北", "无锡东", "苏州北", "昆山南", "上海虹桥")),
        Corridor("hangshen-hsr", MapRailNetwork.HIGH_SPEED, "杭深铁路", listOf("杭州东", "绍兴北", "余姚北", "宁波", "宁海", "三门县", "临海", "台州", "温岭", "雁荡山", "乐清", "温州南", "瑞安", "平阳", "苍南", "福鼎", "太姥山", "霞浦", "福安", "宁德", "连江", "福州南", "福清", "涵江", "莆田", "仙游", "惠安", "泉州", "晋江", "厦门北", "角美", "漳州", "漳浦", "云霄", "诏安", "饶平", "潮汕", "潮阳", "普宁", "葵潭", "陆丰", "汕尾", "鲘门", "惠东", "惠州南", "深圳坪山", "深圳北")),
        Corridor("guiguang-hsr", MapRailNetwork.HIGH_SPEED, "贵广高铁", listOf("贵阳北", "贵阳东", "龙洞堡", "龙里北", "贵定县", "都匀东", "三都县", "榕江", "从江", "三江南", "桂林北", "桂林西", "阳朔", "恭城", "钟山西", "贺州", "怀集", "广宁", "肇庆东", "三水南", "佛山西", "广州南")),
        Corridor("xilan-hsr", MapRailNetwork.HIGH_SPEED, "徐兰高铁", listOf("徐州东", "萧县北", "永城北", "砀山南", "商丘", "民权北", "兰考南", "开封北", "郑州东", "郑州西", "巩义南", "洛阳龙门", "渑池南", "三门峡南", "灵宝西", "华山北", "渭南北", "临潼东", "西安北", "咸阳秦都", "杨陵南", "岐山", "宝鸡南", "东岔", "天水南", "秦安", "通渭", "定西北", "榆中", "兰州西")),
        Corridor("chengyu-hsr", MapRailNetwork.HIGH_SPEED, "成渝高铁", listOf("成都东", "简阳南", "资阳北", "资中北", "内江北", "隆昌北", "荣昌北", "大足南", "永川东", "璧山", "沙坪坝", "重庆")),
        Corridor("qinshen-jingha", MapRailNetwork.HIGH_SPEED, "秦沈客专", listOf("秦皇岛", "山海关", "东戴河", "绥中北", "兴城西", "葫芦岛北", "锦州南", "凌海南", "盘锦北", "台安", "辽中", "沈阳北")),
        Corridor("lanxin-hsr", MapRailNetwork.HIGH_SPEED, "兰新高铁", listOf("兰州西", "民和南", "海东", "西宁", "大通西", "门源", "山丹马场", "民乐", "张掖西", "临泽南", "高台南", "酒泉南", "嘉峪关南", "玉门", "柳园南", "哈密", "吐哈", "鄯善北", "吐鲁番北", "乌鲁木齐")),

        // Conventional trunk lines intentionally do not use high-speed corridor hints.
        Corridor("jingguang-conventional", MapRailNetwork.CONVENTIONAL, null, listOf("北京", "保定", "石家庄", "邯郸", "安阳", "新乡", "郑州", "漯河", "驻马店", "信阳", "孝感", "武昌", "岳阳", "长沙", "株洲", "衡阳", "郴州", "韶关东", "广州")),
        Corridor("jinghu-conventional", MapRailNetwork.CONVENTIONAL, null, listOf("北京", "廊坊北", "天津", "静海", "沧州", "德州", "济南", "泰山", "兖州", "枣庄西", "徐州", "宿州", "蚌埠", "滁州北", "南京", "镇江", "常州", "无锡", "苏州", "昆山", "上海")),
        Corridor("hukun-conventional", MapRailNetwork.CONVENTIONAL, null, listOf("上海", "嘉兴", "杭州", "金华", "上饶", "鹰潭", "南昌", "萍乡", "株洲", "湘潭", "娄底", "怀化", "玉屏", "凯里", "贵阳", "安顺", "六盘水", "宣威", "曲靖", "昆明")),
        Corridor("longhai-conventional", MapRailNetwork.CONVENTIONAL, null, listOf("连云港东", "徐州", "商丘", "开封", "郑州", "洛阳", "三门峡", "灵宝", "华山", "渭南", "西安", "宝鸡", "天水", "甘谷", "陇西", "定西", "兰州")),
        Corridor("jingjiu-conventional", MapRailNetwork.CONVENTIONAL, null, listOf("北京丰台", "霸州", "任丘", "衡水", "聊城", "菏泽", "商丘南", "阜阳", "麻城", "九江", "南昌", "吉安", "赣州", "龙川", "河源", "惠州", "东莞东", "深圳")),
        Corridor("baocheng-conventional", MapRailNetwork.CONVENTIONAL, null, listOf("宝鸡", "凤县", "略阳", "阳平关", "广元", "江油", "绵阳", "德阳", "成都")),
        Corridor("chengkun-conventional", MapRailNetwork.CONVENTIONAL, null, listOf("成都", "眉山", "乐山", "峨眉", "西昌", "攀枝花", "元谋", "昆明")),
        Corridor("jiaoliu-conventional", MapRailNetwork.CONVENTIONAL, null, listOf("焦作", "洛阳", "宝丰", "南阳", "襄阳", "荆门", "当阳", "宜昌", "张家界", "吉首", "怀化", "融安", "柳州"))
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
            val replacement = direct ?: findConnectedSection(from, to, scoped)
            if (replacement == null || replacement.size <= 2) {
                index++
            } else {
                val intermediate = replacement.subList(1, replacement.lastIndex)
                stations.addAll(index + 1, intermediate)
                index += intermediate.size + 1
            }
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