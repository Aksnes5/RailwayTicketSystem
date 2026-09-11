package com.railway.ticketsystem.data

/**
 * WGS-84 station coordinates used by the journey map. The seed set covers the
 * national hubs and the stations on the app's primary corridors. A city
 * coordinate is only used as a graceful coverage fallback for a station that
 * has not yet been individually indexed; it is marked as such in the payload.
 *
 * Data convention follows the public China Railway GTFS / OpenStreetMap station
 * datasets (WGS-84). The map page credits those sources to the traveller.
 */
data class StationCoordinate(
    val longitude: Double,
    val latitude: Double,
    val isExactStation: Boolean
)

object StationCoordinateCatalog {
    private val exactStations = mapOf(
        "北京" to point(116.427, 39.904), "北京南" to point(116.378, 39.865),
        "北京西" to point(116.322, 39.894), "北京丰台" to point(116.304, 39.849),
        "北京朝阳" to point(116.497, 39.953), "北京北" to point(116.353, 39.947),
        "天津" to point(117.215, 39.143), "天津西" to point(117.177, 39.167), "天津南" to point(117.028, 38.953),
        "衡水北" to point(115.702, 37.736), "德州东" to point(116.365, 37.471),
        "唐山" to point(118.180, 39.630), "秦皇岛" to point(119.608, 39.938),
        "山海关" to point(119.758, 39.978), "锦州南" to point(121.161, 41.033),
        // 盘锦北位于盘山县甜水镇，不在盘锦市区；此前的城市中心坐标会把
        // 秦沈客专在此处拉成一段斜线。
        "盘锦北" to point(121.807, 41.336),
        "上海" to point(121.490, 31.249), "上海虹桥" to point(121.321, 31.200), "上海南" to point(121.429, 31.154),
        "南京" to point(118.798, 32.088), "南京南" to point(118.796, 31.971),
        "杭州东" to point(120.213, 30.291), "杭州西" to point(119.996, 30.336),
        "合肥南" to point(117.292, 31.800), "福州" to point(119.313, 26.081), "福州南" to point(119.363, 25.986),
        "厦门" to point(118.087, 24.469), "厦门北" to point(117.999, 24.638), "泉州" to point(118.599, 24.937),
        "郑州" to point(113.658, 34.746), "郑州东" to point(113.785, 34.759),
        "武汉" to point(114.421, 30.609), "汉口" to point(114.253, 30.621), "武昌" to point(114.321, 30.530),
        "武汉东" to point(114.434, 30.501), "武汉站" to point(114.421, 30.609),
        "汉川北" to point(113.840, 30.661), "天门" to point(113.174, 30.641),
        "京山南" to point(113.111, 31.005), "钟祥南" to point(112.576, 31.106),
        "荆门西" to point(112.143, 30.990), "当阳西" to point(111.803, 30.821),
        "宜昌北" to point(111.287, 30.744), "宜昌东" to point(111.355, 30.641),
        "荆州" to point(112.267, 30.310), "襄阳东" to point(112.201, 32.037),
        "十堰东" to point(110.828, 32.660), "恩施" to point(109.517, 30.286),
        "长沙" to point(113.006, 28.196), "长沙南" to point(113.073, 28.151),
        "广州" to point(113.264, 23.147), "广州南" to point(113.269, 22.989),
        "广州东" to point(113.325, 23.151), "广州白云" to point(113.269, 23.222),
        "深圳" to point(114.118, 22.531), "深圳北" to point(114.029, 22.609),
        "香港西九龙" to point(114.166, 22.304), "东莞南" to point(113.932, 22.884),
        "湛江西" to point(110.370, 21.197), "湛江北" to point(110.315, 21.205), "茂名南" to point(110.931, 21.644),
        "南昌" to point(115.908, 28.671), "南昌西" to point(115.775, 28.629), "南昌东" to point(116.047, 28.666),
        "九江" to point(115.992, 29.714), "上饶" to point(117.996, 28.444), "赣州西" to point(114.894, 25.816),
        "济南" to point(116.999, 36.670), "济南西" to point(116.890, 36.665), "济南东" to point(117.216, 36.702),
        "青岛北" to point(120.363, 36.170), "烟台" to point(121.385, 37.533),
        "石家庄" to point(114.489, 38.035), "石家庄东" to point(114.652, 38.046),
        "太原" to point(112.580, 37.872), "太原南" to point(112.605, 37.809),
        "沈阳北" to point(123.429, 41.827), "沈阳南" to point(123.404, 41.695),
        "承德南" to point(117.981, 40.952),
        "辽宁朝阳" to point(120.495, 41.576), "阜新" to point(121.659, 42.020),
        "新民北" to point(122.812, 41.990),
        "长春" to point(125.327, 43.909), "长春西" to point(125.119, 43.898),
        "哈尔滨" to point(126.632, 45.755), "哈尔滨西" to point(126.536, 45.691),
        "西安" to point(108.948, 34.350), "西安北" to point(108.858, 34.376),
        "兰州" to point(103.849, 36.034), "兰州西" to point(103.781, 36.067),
        "成都东" to point(104.144, 30.630), "成都南" to point(104.073, 30.611),
        "重庆北" to point(106.552, 29.610), "重庆西" to point(106.455, 29.504), "重庆东" to point(106.632, 29.495),
        "贵阳北" to point(106.624, 26.617), "贵阳东" to point(106.842, 26.576),
        "昆明南" to point(102.872, 24.876), "南宁东" to point(108.442, 22.839),
        "乌鲁木齐" to point(87.601, 43.815), "乌鲁木齐南" to point(87.574, 43.780),
        "呼和浩特东" to point(111.785, 40.854), "西宁" to point(101.746, 36.648), "银川" to point(106.227, 38.491),
        "平顶山西" to point(113.167, 33.781), "南阳东" to point(112.590, 33.004),
        "万州北" to point(108.411, 30.820), "梁平南" to point(107.777, 30.679),
        "盘州" to point(104.469, 25.770), "兴义南" to point(104.958, 25.039),
        "延安" to point(109.500, 36.603)
    )

    private val cityFallbacks = mapOf(
        "北京" to point(116.407, 39.904), "天津" to point(117.200, 39.084), "上海" to point(121.474, 31.230),
        "重庆" to point(106.551, 29.563), "武汉" to point(114.305, 30.593), "广州" to point(113.264, 23.129),
        "深圳" to point(114.058, 22.543), "杭州" to point(120.155, 30.274), "南京" to point(118.797, 32.060),
        "合肥" to point(117.227, 31.820), "福州" to point(119.296, 26.074), "厦门" to point(118.089, 24.479),
        "郑州" to point(113.625, 34.746), "长沙" to point(112.938, 28.228), "南昌" to point(115.858, 28.683),
        "济南" to point(117.120, 36.652), "青岛" to point(120.382, 36.067), "石家庄" to point(114.515, 38.042),
        "太原" to point(112.549, 37.870), "沈阳" to point(123.432, 41.805), "长春" to point(125.323, 43.817),
        "哈尔滨" to point(126.535, 45.803), "西安" to point(108.940, 34.342), "兰州" to point(103.834, 36.061),
        "成都" to point(104.066, 30.572), "贵阳" to point(106.630, 26.647), "昆明" to point(102.833, 24.881),
        "南宁" to point(108.367, 22.817), "海口" to point(110.199, 20.044), "拉萨" to point(91.132, 29.660),
        "乌鲁木齐" to point(87.617, 43.826), "呼和浩特" to point(111.749, 40.842), "银川" to point(106.230, 38.487),
        "西宁" to point(101.778, 36.617), "宜昌" to point(111.287, 30.691), "襄阳" to point(112.122, 32.009),
        "荆州" to point(112.239, 30.335), "荆门" to point(112.199, 31.036), "十堰" to point(110.798, 32.630),
        "天门" to point(113.166, 30.663), "汉川" to point(113.839, 30.662), "京山" to point(113.111, 31.005),
        "钟祥" to point(112.588, 31.167), "当阳" to point(111.788, 30.821), "徐州" to point(117.185, 34.261),
        "蚌埠" to point(117.389, 32.916), "阜阳" to point(115.815, 32.890), "商丘" to point(115.656, 34.415),
        "洛阳" to point(112.454, 34.620), "南阳" to point(112.528, 32.990), "信阳" to point(114.091, 32.147),
        "宁波" to point(121.550, 29.874), "温州" to point(120.699, 27.994), "金华" to point(119.648, 29.079),
        "嘉兴" to point(120.755, 30.746), "湖州" to point(120.086, 30.894), "绍兴" to point(120.582, 30.030),
        "柳州" to point(109.429, 24.326), "桂林" to point(110.290, 25.274), "北海" to point(109.120, 21.481),
        "绵阳" to point(104.741, 31.465), "德阳" to point(104.398, 31.127), "乐山" to point(103.766, 29.553),
        "宜宾" to point(104.643, 28.752), "大理" to point(100.230, 25.591), "曲靖" to point(103.798, 25.496),
        "包头" to point(109.840, 40.658), "赤峰" to point(118.887, 42.257), "大同" to point(113.301, 40.076),
        "张家口" to point(114.885, 40.812), "唐山" to point(118.181, 39.630), "秦皇岛" to point(119.600, 39.935)
    )

    fun resolve(stationName: String): StationCoordinate? {
        exactStations[stationName]?.let { return it }
        // The directory's `city` field cannot be trusted on its own: RealRailwayRoutes stores
        // railway telecodes there, and because the graph is keyed by name with last-write-wins
        // those entries overwrite the correct city from ChinaRailwayData.  "汉川" ends up with
        // city "HCN" and its real fallback below becomes unreachable.  So also try the city
        // implied by the station name itself.
        val candidates = listOf(RailwayData.getStationByName(stationName)?.city, cityOf(stationName))
        return candidates.firstNotNullOfOrNull { key -> cityFallbacks[key.orEmpty()] }
            ?.copy(isExactStation = false)
    }

    /** "汉川北" → "汉川", "天门南" → "天门"; a name with no direction suffix is its own city. */
    private fun cityOf(stationName: String): String =
        stationName.trim().trimEnd('东', '南', '西', '北')

    private fun point(longitude: Double, latitude: Double) = StationCoordinate(longitude, latitude, true)
}
