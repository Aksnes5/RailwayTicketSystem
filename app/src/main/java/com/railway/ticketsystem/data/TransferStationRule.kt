package com.railway.ticketsystem.data

import com.railway.ticketsystem.model.Station

/**
 * 智能中转换乘规则
 * 区分同站换乘与同城跨站换乘（如北京西到北京南），合理控制换乘时间容差
 */
object TransferStationRule {

    /** 同站换乘最低缓冲时间（分钟） */
    const val MIN_SAME_STATION_TRANSFER_MINUTES = 20

    /** 同城跨站换乘最低缓冲时间（分钟）：预留市内地铁/打车等通勤时间 75~90 分钟，基准取 80 分钟 */
    const val MIN_CROSS_STATION_TRANSFER_MINUTES = 80

    /** 最大合理换乘等待时间（6小时） */
    const val MAX_TRANSFER_MINUTES = 360

    /**
     * 判断两个车站是否在同一个城市
     */
    fun isSameCity(stationA: String, stationB: String): Boolean {
        if (stationA == stationB) return true
        val cityA = getCity(stationA)
        val cityB = getCity(stationB)
        if (cityA.isNotEmpty() && cityB.isNotEmpty() && cityA == cityB) return true
        // 车站名前缀匹配（如 "北京南" 和 "北京西" 前缀均为 "北京"）
        val prefixA = extractCityPrefix(stationA)
        val prefixB = extractCityPrefix(stationB)
        return prefixA.isNotEmpty() && prefixA == prefixB
    }

    /**
     * 判断是否为同城跨站换乘
     */
    fun isCrossStation(stationA: String, stationB: String): Boolean {
        return stationA != stationB && isSameCity(stationA, stationB)
    }

    /**
     * 获取两个车站换乘所需的最低缓冲时间（分钟）
     */
    fun getMinimumTransferMinutes(stationA: String, stationB: String): Int {
        return if (isCrossStation(stationA, stationB)) {
            MIN_CROSS_STATION_TRANSFER_MINUTES
        } else {
            MIN_SAME_STATION_TRANSFER_MINUTES
        }
    }

    /**
     * 检查给定的换乘时间是否符合官方业务容差标准
     */
    fun isTransferTimeValid(stationA: String, stationB: String, transferMinutes: Int): Boolean {
        val minMinutes = getMinimumTransferMinutes(stationA, stationB)
        return transferMinutes in minMinutes..MAX_TRANSFER_MINUTES
    }

    private fun getCity(stationName: String): String {
        return ChinaRailwayData.getStationByName(stationName)?.city
            ?: extractCityPrefix(stationName)
    }

    private fun extractCityPrefix(name: String): String {
        val clean = name.trim()
        val suffixes = listOf("东", "西", "南", "北", "新", "机场", "新区", "高新", "站")
        for (suffix in suffixes) {
            if (clean.endsWith(suffix) && clean.length > suffix.length + 1) {
                return clean.substring(0, clean.length - suffix.length)
            }
        }
        return if (clean.length in 2..4) clean else ""
    }

    /**
     * 获取同城跨站换乘的城市地铁/公交接驳全景指引
     */
    fun getCrossStationMetroGuide(stationA: String, stationB: String): MetroTransferGuide? {
        if (!isCrossStation(stationA, stationB)) return null
        val pairKey = "${stationA}->${stationB}"
        val reverseKey = "${stationB}->${stationA}"

        val knownGuides = mapOf(
            // 武汉
            "汉口->武汉" to MetroTransferGuide(stationA, stationB, "地铁2号线（中南路站站内同台换乘）地铁4号线", 52, 23, "6元", "两站均为地铁终点/枢纽站，乘车免出地面，换乘通道标识清晰"),
            "武汉->汉口" to MetroTransferGuide(stationA, stationB, "地铁4号线（中南路站站内同台换乘）地铁2号线", 52, 23, "6元", "两站均为地铁终点/枢纽站，乘车免出地面，换乘通道标识清晰"),
            "汉口->武昌" to MetroTransferGuide(stationA, stationB, "地铁2号线直达（螃蟹岬/积玉桥下）或地铁4号线", 35, 12, "4元", "可于汉口火车站地铁站直达积玉桥换乘"),
            "武昌->汉口" to MetroTransferGuide(stationA, stationB, "地铁4号线转2号线 或 地铁2号线直达", 35, 12, "4元", "武昌站地铁出站即达进站安检大厅"),
            "武昌->武汉" to MetroTransferGuide(stationA, stationB, "地铁4号线直达", 28, 13, "4元", "4号线直通两站，无需换线"),
            "武汉->武昌" to MetroTransferGuide(stationA, stationB, "地铁4号线直达", 28, 13, "4元", "4号线直通两站，无需换线"),

            // 北京
            "北京南->北京西" to MetroTransferGuide(stationA, stationB, "地铁4号线（菜市口站换乘）地铁7号线", 28, 7, "4元", "菜市口换乘约3分钟，直达北京西站地下候车层"),
            "北京西->北京南" to MetroTransferGuide(stationA, stationB, "地铁7号线（菜市口站换乘）地铁4号线", 28, 7, "4元", "菜市口换乘约3分钟，直达北京南站地下候车层"),
            "北京南->北京" to MetroTransferGuide(stationA, stationB, "地铁4号线（宣武门站换乘）地铁2号线", 26, 6, "4元", "宣武门同站换乘，出站即为北京站进站广场"),
            "北京->北京南" to MetroTransferGuide(stationA, stationB, "地铁2号线（宣武门站换乘）地铁4号线", 26, 6, "4元", "宣武门同站换乘，直达北京南站高架候车层"),
            "北京南->北京丰台" to MetroTransferGuide(stationA, stationB, "地铁14号线直达", 18, 5, "4元", "14号线直达丰台站地下进站大厅"),
            "北京丰台->北京南" to MetroTransferGuide(stationA, stationB, "地铁14号线直达", 18, 5, "4元", "14号线直达北京南站"),
            "北京西->北京朝阳" to MetroTransferGuide(stationA, stationB, "地铁7号线（九龙山站换乘）地铁14号线", 52, 16, "6元", "东四环方向接驳，建议预留充足地面通行时间"),

            // 上海
            "上海虹桥->上海南" to MetroTransferGuide(stationA, stationB, "地铁10号线（虹桥路站换乘）地铁3号线", 42, 12, "5元", "直达上海南站地下交通枢纽"),
            "上海南->上海虹桥" to MetroTransferGuide(stationA, stationB, "地铁3号线（虹桥路站换乘）地铁10号线", 42, 12, "5元", "直达虹桥2号航站楼/虹桥火车站"),
            "上海虹桥->上海" to MetroTransferGuide(stationA, stationB, "地铁2号线（人民广场换乘）地铁1号线 或 地铁10号线", 38, 11, "5元", "人民广场大站换乘，客流集中"),
            "上海->上海虹桥" to MetroTransferGuide(stationA, stationB, "地铁1号线（人民广场换乘）地铁2号线", 38, 11, "5元", "直通虹桥火车站候车大厅"),
            "上海->上海南" to MetroTransferGuide(stationA, stationB, "地铁1号线直达", 25, 9, "4元", "1号线南北贯通直达，全程无需换线"),
            "上海南->上海" to MetroTransferGuide(stationA, stationB, "地铁1号线直达", 25, 9, "4元", "1号线南北贯通直达，全程无需换线"),

            // 广州
            "广州南->广州" to MetroTransferGuide(stationA, stationB, "地铁2号线直达", 35, 15, "5元", "2号线贯通广州南站与越秀区老广州站"),
            "广州->广州南" to MetroTransferGuide(stationA, stationB, "地铁2号线直达", 35, 15, "5元", "2号线直达广州南站高架候车层"),
            "广州南->广州东" to MetroTransferGuide(stationA, stationB, "地铁2号线（公园前站换乘）地铁1号线", 45, 18, "6元", "直达天河区广州东站"),
            "广州东->广州南" to MetroTransferGuide(stationA, stationB, "地铁1号线（公园前站换乘）地铁2号线", 45, 18, "6元", "直达番禺区广州南站"),

            // 成都
            "成都东->成都南" to MetroTransferGuide(stationA, stationB, "地铁7号线直达（外环方向）", 16, 6, "3元", "环线直通，耗时极短"),
            "成都南->成都东" to MetroTransferGuide(stationA, stationB, "地铁7号线直达（内环方向）", 16, 6, "3元", "环线直通，耗时极短"),
            "成都东->成都西" to MetroTransferGuide(stationA, stationB, "地铁4号线直达", 32, 13, "5元", "4号线横贯东西直达"),

            // 重庆
            "重庆北->重庆西" to MetroTransferGuide(stationA, stationB, "地铁环线直达 或 5号线", 35, 11, "5元", "环线快车直达重庆西站"),
            "重庆西->重庆北" to MetroTransferGuide(stationA, stationB, "地铁环线直达 或 5号线", 35, 11, "5元", "环线快车直达重庆北站"),

            // 南京
            "南京南->南京" to MetroTransferGuide(stationA, stationB, "地铁1号线直达 或 地铁3号线直达", 24, 11, "4元", "双地铁直连，平均3分钟一班"),
            "南京->南京南" to MetroTransferGuide(stationA, stationB, "地铁1号线直达 或 地铁3号线直达", 24, 11, "4元", "双地铁直连，平均3分钟一班"),

            // 杭州
            "杭州东->杭州" to MetroTransferGuide(stationA, stationB, "地铁1号线直达", 16, 6, "3元", "1号线直达城站火车站"),
            "杭州->杭州东" to MetroTransferGuide(stationA, stationB, "地铁1号线直达", 16, 6, "3元", "直达杭州东站高架层"),
            "杭州东->杭州西" to MetroTransferGuide(stationA, stationB, "地铁19号线（机场快线）直达", 28, 6, "5元", "19号线快线时速快，停站少直通"),

            // 西安
            "西安北->西安" to MetroTransferGuide(stationA, stationB, "地铁2号线直达 或 4号线", 22, 8, "4元", "2号线中轴线直通西安站南广场"),
            "西安->西安北" to MetroTransferGuide(stationA, stationB, "地铁2号线直达 或 4号线", 22, 8, "4元", "2号线中轴线直通西安北站"),

            // 郑州
            "郑州东->郑州" to MetroTransferGuide(stationA, stationB, "地铁1号线直达", 20, 8, "3元", "1号线直通郑州火车站与郑州东站"),
            "郑州->郑州东" to MetroTransferGuide(stationA, stationB, "地铁1号线直达", 20, 8, "3元", "1号线直通郑州火车站与郑州东站")
        )

        return knownGuides[pairKey] ?: knownGuides[reverseKey] ?: MetroTransferGuide(
            fromStation = stationA,
            toStation = stationB,
            metroLineSummary = "建议乘坐市内轨道交通/机场快线接驳",
            estimatedMinutes = 45,
            stopsCount = 12,
            ticketPrice = "约4~7元",
            transferTip = "同城跨站换乘需出站，请预留充足市内通勤与二次安检时间（建议≥80分钟）"
        )
    }
}

/**
 * 同城跨站换乘接驳指引数据
 */
data class MetroTransferGuide(
    val fromStation: String,
    val toStation: String,
    val metroLineSummary: String,
    val estimatedMinutes: Int,
    val stopsCount: Int,
    val ticketPrice: String,
    val transferTip: String
)
