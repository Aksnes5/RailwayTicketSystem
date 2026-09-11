package com.railway.ticketsystem.data

import com.railway.ticketsystem.model.RailwayRoute
import com.railway.ticketsystem.model.RailwayRouteManager
import com.railway.ticketsystem.model.RouteType
import com.railway.ticketsystem.model.Station

/**
 * Newly commissioned passenger routes curated from public transport authority releases.
 * The catalog is packaged with the app so station search stays available offline.
 */
object LatestRailwayNetwork {
    @Volatile
    private var installed = false

    val stations: List<Station> = listOf(
        station("沈阳北", "SBT", "沈阳"), station("伯官", "BGO", "沈阳"),
        station("抚顺", "FSH", "抚顺"), station("东韩家", "DHJ", "抚顺"),
        station("新宾", "XBN", "抚顺"), station("通化", "THA", "通化"),
        station("白山东", "BSD", "白山"), station("江源东", "JYD", "白山"),
        station("长白山西", "CBS", "白山"), station("长白山", "CBH", "延边"),

        station("广州白云", "GBY", "广州"), station("佛山", "FSQ", "佛山"),
        station("佛肇", "FZH", "肇庆"), station("新兴南", "XXN", "云浮"),
        station("阳春东", "YCD", "阳江"), station("阳江北", "YJB", "阳江"),
        station("马踏", "MTA", "茂名"), station("茂名南", "MMN", "茂名"),
        station("吴川", "WCH", "湛江"), station("湛江北", "ZJB", "湛江"),

        station("重庆东", "CQD", "重庆"), station("巴南", "BAN", "重庆"),
        station("南川北", "NCB", "重庆"), station("水江西", "SJX", "重庆"),
        station("武隆南", "WLN", "重庆"), station("彭水西", "PSX", "重庆"),
        station("黔江", "QJG", "重庆"),

        station("桐庐东", "TLD", "杭州"), station("浦江", "PUJ", "金华"),
        station("义乌", "YIW", "金华"), station("横店", "HED", "金华"),
        station("磐安", "PAN", "金华"), station("仙居", "XJU", "台州"),
        station("楠溪江", "NXJ", "温州"), station("温州北", "WZB", "温州"),
        station("温州南", "WZN", "温州"),

        station("梅州西", "MZX", "梅州"), station("兴宁南", "XNN", "梅州"),
        station("五华", "WHA", "梅州"), station("龙川西", "LCX", "河源"),

        station("池州", "CZH", "池州"), station("九华山", "JHS", "池州"),
        station("黄山西", "HSX", "黄山"), station("黄山北", "HSB", "黄山"),

        // 华北—东北高铁网：石济、津秦、秦沈与京沈段共同提供
        // 武汉/郑州—石家庄—天津—秦皇岛—沈阳—哈尔滨的非北京枢纽通道。
        station("石家庄", "SJP", "石家庄"), station("藁城南", "GCN", "石家庄"),
        station("辛集南", "XJN", "辛集"), station("衡水北", "HSN", "衡水"),
        station("景州", "JZP", "衡水"), station("德州东", "DZP", "德州"),
        station("平原东", "PYD", "德州"), station("禹城东", "YCD", "德州"),
        station("济南东", "JND", "济南"),

        station("天津西", "TXP", "天津"), station("天津", "TJP", "天津"),
        station("军粮城北", "JLB", "天津"), station("滨海西", "BXP", "天津"),
        station("滨海北", "BHB", "天津"), station("唐山", "TSP", "唐山"),
        station("滦河", "LHP", "唐山"), station("北戴河", "BDH", "秦皇岛"),
        station("秦皇岛", "QHD", "秦皇岛"),

        station("山海关", "SHG", "秦皇岛"), station("东戴河", "DDH", "葫芦岛"),
        station("绥中北", "SZB", "葫芦岛"), station("兴城西", "XCX", "葫芦岛"),
        station("葫芦岛北", "HLB", "葫芦岛"), station("锦州南", "JZN", "锦州"),
        station("凌海南", "LHN", "锦州"), station("盘锦北", "PJB", "盘锦"),
        station("台安", "TAN", "鞍山"), station("辽中", "LZP", "沈阳"),
        station("沈阳北", "SBT", "沈阳"),

        station("北京朝阳", "BJP", "北京"), station("顺义西", "SYX", "北京"),
        station("怀柔南", "HRN", "北京"), station("密云", "MIY", "北京"),
        station("兴隆县西", "XLX", "承德"), station("安匠", "ANJ", "承德"),
        station("承德南", "CDN", "承德"), station("承德县北", "CDB", "承德"),
        station("平泉北", "PQB", "承德"), station("牛河梁", "NHL", "朝阳"),
        station("喀左", "KZU", "朝阳"), station("奈林皋", "NLG", "朝阳"),
        station("辽宁朝阳", "LCY", "朝阳"), station("北票", "BIP", "朝阳"),
        station("乌兰木图", "WLT", "阜新"), station("阜新", "FXP", "阜新"),
        station("黑山北", "HSB", "锦州"), station("新民北", "XMB", "沈阳"),
        station("沈阳西", "SYX", "沈阳"), station("沈阳", "SYP", "沈阳"),

        // 已运营干线与枢纽联络线：这些连接使跨线车次可在高速路网内连续寻径，
        // 不会因省界或枢纽被拆成两段线路。
        station("天津南", "TNP", "天津"),
        station("郑州东", "ZZP", "郑州"), station("平顶山西", "PDW", "平顶山"),
        station("方城", "FCP", "南阳"), station("南阳东", "NYF", "南阳"),
        station("邓州东", "DDF", "邓州"), station("襄阳东", "XYD", "襄阳"),
        station("谷城北", "GCB", "襄阳"), station("保康县", "BKX", "襄阳"),
        station("神农架", "SNJ", "神农架"), station("巴东", "BDN", "恩施"),
        station("巫山", "WSN", "重庆"), station("奉节", "FJP", "重庆"),
        station("万州北", "WKB", "重庆"), station("梁平南", "LPN", "重庆"),
        station("重庆北", "CQB", "重庆"),

        station("盘州", "PZQ", "六盘水"), station("保田", "BTN", "兴义"),
        station("兴义南", "XYN", "兴义"),

        station("西安北", "EAY", "西安"), station("西安东", "XAD", "西安"),
        station("高陵", "GLG", "西安"), station("富平南", "FPN", "渭南"),
        station("铜川", "TCN", "铜川"), station("铜川北", "TCB", "铜川"),
        station("宜君", "YJN", "铜川"), station("黄陵", "HLN", "延安"),
        station("洛川", "LCN", "延安"), station("富县北", "FXB", "延安"),
        station("甘泉北", "GQB", "延安"), station("延安", "YNA", "延安"),

        station("包头", "BTC", "包头"), station("白彦花西", "BYX", "巴彦淖尔"),
        station("乌拉特前旗", "WLQ", "巴彦淖尔"), station("五原", "WYP", "巴彦淖尔"),
        station("巴彦淖尔", "BYN", "巴彦淖尔"), station("磴口", "DKP", "巴彦淖尔"),
        station("碱柜", "JGP", "鄂尔多斯"), station("乌海", "WHP", "乌海"),
        station("乌海南", "WHN", "乌海"), station("惠农南", "HNN", "石嘴山"),
        station("石嘴山", "SZS", "石嘴山"), station("沙湖", "SHU", "银川"),
        station("银川", "YCP", "银川")
    ).distinctBy { it.name }

    /** Provincial-capital and municipality core stations never disappear from a route view. */
    private val provincialCapitalStations = setOf(
        "北京", "北京南", "北京西", "北京北", "北京丰台", "北京朝阳",
        "天津", "天津西", "天津南", "上海", "上海虹桥", "上海南",
        "重庆", "重庆北", "重庆西", "重庆东",
        "石家庄", "石家庄东", "石家庄北", "太原", "太原南",
        "呼和浩特", "呼和浩特东", "沈阳", "沈阳北", "沈阳南",
        "长春", "长春西", "哈尔滨", "哈尔滨西",
        "济南", "济南西", "济南东", "南京", "南京南",
        "杭州东", "杭州西", "合肥南", "福州", "福州南",
        "南昌", "南昌西", "南昌东", "郑州", "郑州东",
        "武汉", "武汉东", "汉口", "武昌", "长沙", "长沙南",
        "广州", "广州南", "广州东", "广州白云", "南宁", "南宁东",
        "海口", "海口东", "成都", "成都东", "成都南",
        "贵阳", "贵阳北", "贵阳东", "昆明", "昆明南",
        "拉萨", "西安", "西安北", "兰州", "兰州西",
        "西宁", "银川", "乌鲁木齐", "乌鲁木齐南"
    )

    /**
     * Regional hubs are protected just like provincial-capital stations when
     * forming a limited-stop service. This keeps realistic origin/terminal
     * choices for cities such as 宜昌 and 襄阳 without turning every small
     * intermediate station into a compulsory call.
     */
    private val regionalHubStations = setOf(
        "宜昌东", "宜昌北", "襄阳东", "襄阳", "荆州", "荆门西", "十堰东", "恩施",
        "徐州东", "蚌埠南", "阜阳西", "商丘", "商丘东", "洛阳龙门", "南阳东", "信阳东",
        "赣州西", "上饶", "九江", "景德镇北", "黄山北",
        "宁波", "宁波站", "温州南", "温州北", "金华", "金华南", "义乌", "嘉兴南", "湖州", "绍兴北",
        "厦门", "厦门北", "泉州", "漳州", "龙岩",
        "深圳北", "深圳", "东莞南", "惠州北", "汕头", "湛江西", "湛江北", "茂名", "茂名南",
        "柳州", "桂林北", "北海", "玉林北",
        "绵阳", "德阳", "乐山", "宜宾西", "自贡", "内江北", "万州北", "重庆北",
        "大理", "曲靖北", "昭通", "攀枝花南", "盘州", "兴义南",
        "包头", "赤峰", "大同南", "张家口", "唐山", "秦皇岛", "延安",
        "青岛北", "烟台", "潍坊", "临沂北",
        "苏州北", "无锡东", "常州北", "扬州东", "连云港", "盐城"
    )

    /** Public so the on-demand generator can prefer a real hub as origin. */
    val majorHubStations: Set<String> get() = provincialCapitalStations + regionalHubStations

    /** Regional hubs come first for nearby-station through services. */
    val preferredServiceOriginHubs: List<String>
        get() = (regionalHubStations + provincialCapitalStations).toList()

    fun isProvincialCapitalStation(name: String): Boolean = name in provincialCapitalStations

    fun isMajorHubStation(name: String): Boolean = name in majorHubStations

    fun install() {
        if (installed) return
        synchronized(this) {
            if (installed) return
            routes().forEach(RailwayRouteManager::addRoute)
            installed = true
        }
    }

    private fun routes(): List<RailwayRoute> = listOf(
        route(
            "SHENBAI2025", "沈白高铁",
            listOf("沈阳北", "伯官", "抚顺", "东韩家", "新宾", "通化", "白山东", "江源东", "长白山西", "长白山"),
            "1小时53分钟", 185.0
        ),
        route(
            "GUANGZHAN2025", "广湛高铁",
            listOf("广州白云", "佛山", "佛肇", "新兴南", "阳春东", "阳江北", "马踏", "茂名南", "吴川", "湛江北"),
            "1小时30分钟", 210.0
        ),
        route(
            "YUXIA2025", "渝厦高铁重庆东—黔江段",
            listOf("重庆东", "巴南", "南川北", "水江西", "武隆南", "彭水西", "黔江"),
            "1小时06分钟", 141.0
        ),
        route(
            "HANGWEN2024", "杭温高铁",
            listOf("桐庐东", "浦江", "义乌", "横店", "磐安", "仙居", "楠溪江", "温州北", "温州南"),
            "1小时20分钟", 180.0
        ),
        route(
            "MEILONG2024", "梅龙高铁",
            listOf("梅州西", "兴宁南", "五华", "龙川西"),
            "35分钟", 70.0
        ),
        route(
            "CHIHUANG2024", "池黄高铁",
            listOf("池州", "九华山", "黄山西", "黄山北"),
            "50分钟", 85.0
        ),
        route(
            "SHIJI_HSR", "石济客专",
            listOf("石家庄", "藁城南", "辛集南", "衡水北", "景州", "德州东", "平原东", "禹城东", "济南东"),
            "1小时40分钟", 128.0
        ),
        route(
            "JINQIN_HSR", "津秦高铁",
            listOf("天津西", "天津", "军粮城北", "滨海西", "滨海北", "唐山", "滦河", "北戴河", "秦皇岛"),
            "1小时20分钟", 155.0
        ),
        route(
            "QINSHEN_HSR", "秦沈客专",
            listOf("秦皇岛", "山海关", "东戴河", "绥中北", "兴城西", "葫芦岛北", "锦州南", "凌海南", "盘锦北", "台安", "辽中", "沈阳北"),
            "2小时05分钟", 205.0
        ),
        route(
            "JINGSHEN_HSR", "京哈高铁京沈段",
            listOf("北京朝阳", "顺义西", "怀柔南", "密云", "兴隆县西", "安匠", "承德南", "承德县北", "平泉北", "牛河梁", "喀左", "奈林皋", "辽宁朝阳", "北票", "乌兰木图", "阜新", "黑山北", "新民北", "沈阳西", "沈阳", "沈阳北"),
            "2小时45分钟", 360.0
        ),
        // 天津南—天津西是京沪、津秦两条高速走廊在天津的实际联络关系。
        route(
            "TIANJIN_HUB_HSR", "天津枢纽高速联络线",
            listOf("天津南", "天津西"), "18分钟", 15.0
        ),
        route(
            "ZHENGYU_HSR", "郑渝高铁",
            listOf("郑州东", "平顶山西", "方城", "南阳东", "邓州东", "襄阳东", "谷城北", "保康县", "神农架", "巴东", "巫山", "奉节", "万州北"),
            "3小时50分钟", 395.0
        ),
        route(
            "YUWAN_INTERCITY", "渝万城际铁路",
            listOf("万州北", "梁平南", "重庆北"), "1小时15分钟", 92.0
        ),
        route(
            "PANSX_HSR", "盘兴高铁",
            listOf("盘州", "保田", "兴义南"), "50分钟", 74.0
        ),
        route(
            "XIYAN_HSR", "西延高铁",
            listOf("西安东", "高陵", "富平南", "铜川", "铜川北", "宜君", "黄陵", "洛川", "富县北", "甘泉北", "延安"),
            "1小时10分钟", 246.0
        ),
        // 西安北与西安东间的高速联络关系让西延列车可以接入既有全国路网。
        route(
            "XIYAN_HUB_LINK", "西安枢纽高速联络线",
            listOf("西安北", "西安东"), "20分钟", 18.0
        ),
        route(
            "BAOYIN_HSR", "包银高铁",
            listOf("包头", "白彦花西", "乌拉特前旗", "五原", "巴彦淖尔", "磴口", "碱柜", "乌海", "乌海南", "惠农南", "石嘴山", "沙湖", "银川"),
            "2小时38分钟", 238.0
        )
    )

    private fun route(
        id: String,
        name: String,
        stationNames: List<String>,
        duration: String,
        totalPrice: Double
    ): RailwayRoute {
        val routeStations = stationNames.map { name -> stations.first { it.name == name } }
        val unitPrice = totalPrice / (routeStations.size - 1).coerceAtLeast(1)
        val prices = routeStations.zipWithNext().associate { (from, to) ->
            "${from.name}-${to.name}" to unitPrice
        }
        return RailwayRoute(
            routeId = id,
            routeName = name,
            stations = routeStations,
            segmentPrices = prices,
            totalPrice = totalPrice,
            totalDuration = duration,
            routeType = RouteType.HIGH_SPEED
        )
    }

    private fun station(name: String, code: String, city: String) = Station(name, code, city)
}
