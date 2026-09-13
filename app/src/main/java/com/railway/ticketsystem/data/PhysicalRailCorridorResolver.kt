package com.railway.ticketsystem.data

/**
 * Resolves a train's logical stop sequence into the physical railway corridor
 * used by the route map.
 *
 * Railway service names do not always match the civil-engineering corridor
 * name.  For example, the 汉十 services enter Wuhan through the 汉孝城际
 * section; a legacy service can therefore only carry `汉口 → 孝感东`, even
 * though the map has to draw every physical section in between.  Keeping this
 * translation here prevents a map-only one-off for each such relationship.
 *
 * The resolver deliberately changes neither a ticket's calls nor its timetable.
 * It only supplies non-call physical stations and a corridor hint for map
 * snapping.  Call markers continue to come exclusively from the timetable.
 */
object PhysicalRailCorridorResolver {
    data class ResolvedRoute(
        val stationNames: List<String>,
        /** One OSM corridor label for each adjacent pair in [stationNames]. */
        val legCorridorHints: List<String?>
    )

    private data class Corridor(
        val id: String,
        /** `corridor` value in railway_network.js, not a user-facing line name. */
        val mapCorridor: String,
        val stationNames: List<String>,
        /** Service names that use, share, or continue on this physical alignment. */
        val serviceAliases: Set<String>
    ) {
        fun sectionBetween(from: String, to: String): List<String>? {
            val fromIndex = stationNames.indexOf(from)
            val toIndex = stationNames.indexOf(to)
            if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return null
            return if (fromIndex < toIndex) {
                stationNames.subList(fromIndex, toIndex + 1)
            } else {
                stationNames.subList(toIndex, fromIndex + 1).asReversed()
            }
        }

        fun containsLeg(from: String, to: String): Boolean =
            sectionBetween(from, to)?.size == 2
    }

    /**
     * Each entry represents a verified shared/coupled passenger corridor.  A
     * logical route can skip these stations but the track itself cannot, so it
     * is safe to insert them only for its map geometry.
     */
    private val corridors = listOf(
        Corridor(
            id = "wuhan-hub-connector",
            mapCorridor = "武汉枢纽连接线",
            // 枢纽内既有客车联络段：它不是在建的“武汉枢纽直通线”主工程，
            // 但确实承担武汉、汉口、武昌、武汉东之间的接续走行。
            stationNames = listOf("武汉", "汉口", "武昌", "武汉东"),
            serviceAliases = setOf("武汉枢纽连接线", "武汉枢纽联络线")
        ),
        Corridor(
            id = "wuyi-hanyi-connectors",
            mapCorridor = "武宜高铁",
            // 武宜正线及宜昌北—宜昌东的既有接续段。站序仅用于把地图
            // 锚定到轨道；中间站是否办理客运仍由列车实际时刻表决定。
            stationNames = listOf(
                "汉口", "汉川北", "天门", "京山南", "钟祥南", "荆门西", "当阳西", "宜昌北", "宜昌东"
            ),
            serviceAliases = setOf("武宜高铁", "汉宜铁路", "宁蓉铁路")
        ),
        Corridor(
            id = "hanxiao-hanshi",
            mapCorridor = "汉十高铁",
            stationNames = listOf(
                "汉口", "后湖", "金银潭", "天河机场", "天河街", "闵集", "毛陈", "槐荫", "孝感东",
                "云梦东", "安陆西", "随州南", "随县", "枣阳", "襄阳东", "隆中", "谷城北",
                "丹江口南", "武当山西", "十堰东"
            ),
            serviceAliases = setOf("汉十高铁", "武孝城际铁路")
        ),
        Corridor(
            id = "wuhuang-wujiu",
            mapCorridor = "武九客专",
            stationNames = listOf(
                "武汉", "葛店南", "华容南", "鄂州", "鄂州东", "花湖", "黄石北", "大冶北",
                "白沙铺", "阳新", "枫林", "瑞昌西", "柴桑", "庐山", "九江"
            ),
            serviceAliases = setOf("武黄城际铁路", "武九客专")
        ),
        Corridor(
            id = "hanyi-ningrong",
            mapCorridor = "宁蓉铁路",
            stationNames = listOf(
                "汉口", "汉川", "天门南", "仙桃西", "潜江", "荆州", "枝江北", "宜昌东"
            ),
            serviceAliases = setOf("汉宜铁路", "宁蓉铁路")
        ),
        Corridor(
            id = "qinshen-jingha",
            mapCorridor = "秦沈客专",
            stationNames = listOf(
                "秦皇岛", "山海关", "东戴河", "绥中北", "兴城西", "葫芦岛北", "锦州南",
                "凌海南", "盘锦北", "台安", "辽中", "沈阳北"
            ),
            serviceAliases = setOf("秦沈客专", "京哈高铁", "京哈高铁京沈段")
        )
    )

    /**
     * Inserts a known physical section only when a legacy route puts both ends
     * next to each other.  This is idempotent: after expansion every inserted
     * leg is adjacent and will not be expanded again.
     */
    fun resolveForMap(rawStations: List<String>): ResolvedRoute {
        val stations = rawStations.asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .fold(mutableListOf<String>()) { result, station ->
                if (result.lastOrNull() != station) result.add(station)
                result
            }
        if (stations.size < 2) return ResolvedRoute(stations, emptyList())

        var index = 0
        while (index < stations.lastIndex) {
            val from = stations[index]
            val to = stations[index + 1]
            val replacement = corridors.asSequence()
                .mapNotNull { corridor ->
                    corridor.sectionBetween(from, to)
                        ?.takeIf { it.size > 2 }
                        ?.let { section -> corridor to section }
                }
                // Prefer the shortest verified physical section if multiple
                // corridors share a pair of named stations.
                .minByOrNull { (_, section) -> section.size }
                ?.second
            if (replacement == null) {
                index++
            } else {
                stations.addAll(index + 1, replacement.drop(1))
                index += replacement.lastIndex
            }
        }

        val hints = stations.zipWithNext { from, to ->
            corridors.firstOrNull { it.containsLeg(from, to) }?.mapCorridor
        }
        return ResolvedRoute(stations, hints)
    }
}
