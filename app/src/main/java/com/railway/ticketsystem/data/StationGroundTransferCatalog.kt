package com.railway.ticketsystem.data

/**
 * 枢纽车站“最后一公里”地面交通接驳指南数据源
 * 覆盖全国主要特等站、一等高铁枢纽站的地铁免安检换乘、网约车上客区与出租车动线。
 */
object StationGroundTransferCatalog {

    data class GroundTransferGuide(
        val stationName: String,
        val metroLines: String,
        val metroSecurityFree: Boolean,
        val metroGuide: String,
        val taxiRideHailLocation: String,
        val busAirportShuttle: String,
        val fastExitTip: String
    )

    private val guides = mapOf(
        "汉口" to GroundTransferGuide(
            stationName = "汉口站",
            metroLines = "地铁 2 号线",
            metroSecurityFree = true,
            metroGuide = "出站闸机后直行 40 米进入地下换乘大厅，铁路旅客享【免二次安检通道】直达 2 号线站厅，首班 06:00 / 末班 23:45",
            taxiRideHailLocation = "南广场地下停车场 B1 层（网约车上客区 1-3 号柱）；出租车请走地下负一层西侧专属通道",
            busAirportShuttle = "南广场公交枢纽站（机场大巴天河机场直通专线，早 07:00 至晚 21:00 每半小时一班）",
            fastExitTip = "持前部车厢（1-4车）车票建议从西出站口出站，直达地铁安检豁免闸机"
        ),
        "武汉" to GroundTransferGuide(
            stationName = "武汉站",
            metroLines = "地铁 4 号线 / 5 号线 / 19 号线",
            metroSecurityFree = true,
            metroGuide = "东、西出站口均设地铁【免安检单向换乘口】，刷码即可进站，首班 06:00 / 末班 23:30",
            taxiRideHailLocation = "东广场地下 P1/P2 网约车专区；西广场地下出租车调度站",
            busAirportShuttle = "东广场公路客运站，直达武汉天河国际机场 T3 航站楼（约 50 分钟）",
            fastExitTip = "前往光谷高新区建议乘坐 19 号线，前往武昌核心区建议乘坐 4 号线"
        ),
        "武昌" to GroundTransferGuide(
            stationName = "武昌站",
            metroLines = "地铁 4 号线 / 7 号线",
            metroSecurityFree = true,
            metroGuide = "地下出站通道直连地铁 4/7 号线武昌火车站站厅，经单向隔离通道免安检",
            taxiRideHailLocation = "东广场地下网约车集散点；西广场架空层出租车上客点",
            busAirportShuttle = "综合体公交站（518路、538路通往光谷/高校区）",
            fastExitTip = "乘坐普速夜班车出站，西出站口通宵出租车运力充足"
        ),
        "北京南" to GroundTransferGuide(
            stationName = "北京南站",
            metroLines = "地铁 4 号线 / 14 号线",
            metroSecurityFree = true,
            metroGuide = "地下快速进站口免二次安检，出站大厅居中直达 4 号线与 14 号线站厅，首班 05:15 / 末班 23:30",
            taxiRideHailLocation = "地下 M 层东、西出租车站；网约车专属上客区设在地下停车场 D 区/西地库",
            busAirportShuttle = "北广场设有大兴国际机场城市航站楼及机场快线巴士",
            fastExitTip = "出站避开高峰期出租车排队，使用 14 号线换乘更为快捷"
        ),
        "上海虹桥" to GroundTransferGuide(
            stationName = "上海虹桥站",
            metroLines = "地铁 2 号线 / 10 号线 / 17 号线",
            metroSecurityFree = true,
            metroGuide = "地下一层出站大厅全域直通地铁站厅，全流程免安检无感换乘",
            taxiRideHailLocation = "南出站口 P9 停车场、北出站口 P10 停车场（内设网约车精准上客泊位）",
            busAirportShuttle = "地下通道步行 8 分钟直达虹桥国际机场 T2 航站楼与虹桥长途汽车站",
            fastExitTip = "前往浦东国际机场可直乘 2 号线或换乘磁悬浮"
        ),
        "广州南" to GroundTransferGuide(
            stationName = "广州南站",
            metroLines = "地铁 2 号线 / 7 号线 / 22 号线 / 佛山 2 号线",
            metroSecurityFree = true,
            metroGuide = "一楼到达大厅各出站口均直通地下地铁站厅，持高铁票免安检通行",
            taxiRideHailLocation = "西广场地下快速接客区 P1/P3 停车场（智能车牌自动寻客系统）",
            busAirportShuttle = "东广场汽车客运站，直达白云国际机场与珠三角各主要城市",
            fastExitTip = "前往广州白云机场可直接在站内换乘地铁 22 号线快速通达"
        )
    )

    fun getGuide(stationName: String): GroundTransferGuide {
        val cleanName = stationName.replace("站", "").trim()
        return guides[cleanName] ?: GroundTransferGuide(
            stationName = "$cleanName 站",
            metroLines = "城市轨道交通 / 地铁接驳",
            metroSecurityFree = true,
            metroGuide = "站内出站口按导向标识直通地下公共交通换乘厅，支持铁路出站旅客免二次安检进站",
            taxiRideHailLocation = "出站口按地面标识前往地下停车场网约车专属上客区或出租车站台",
            busAirportShuttle = "站前综合交通枢纽广场设有机场大巴接驳专线与多条市内日间/夜班公交干线",
            fastExitTip = "建议提前准备好城市公共交通乘车码，跟随出站蓝色指引标识快速通行"
        )
    }
}
