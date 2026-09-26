package com.railway.ticketsystem.data

/**
 * Data definitions for Train Rolling Stock Specs & Amenities Guide.
 * Pure static design specifications. No dynamic speed calculations.
 */
data class CarComposition(
    val carNumber: String,
    val seatClasses: String,
    val tag: String,
    val features: String,
    val isQuietCar: Boolean = false,
    val isDiningCar: Boolean = false,
    val isBarrierFree: Boolean = false
)

data class RollingStockSpec(
    val modelName: String,
    val seriesFamily: String,
    val speedDesignClass: String, // e.g. "350 km/h 级高速动车组"
    val formationType: String,    // e.g. "8辆标准编组 / 定员 576人"
    val manufacturer: String,     // e.g. "中车四方 / 中车长客"
    val highlights: List<String>,
    val cars: List<CarComposition>,
    val amenities: List<TrainAmenity>
)

data class TrainAmenity(
    val iconEmoji: String,
    val title: String,
    val description: String
)

object RollingStockSpecsData {

    fun getSpec(model: String): RollingStockSpec {
        val trimmed = model.trim()
        return when {
            trimmed.contains("CR400") || trimmed.contains("复兴号") -> fuxingSpec(trimmed)
            trimmed.contains("CRH380") -> crh380Spec(trimmed)
            trimmed.contains("CRH") || trimmed.contains("和谐号") -> crhSpec(trimmed)
            else -> conventionalSpec(trimmed)
        }
    }

    private fun fuxingSpec(modelName: String): RollingStockSpec {
        val isSmart = modelName.contains("-Z") || modelName.contains("-C")
        return RollingStockSpec(
            modelName = modelName,
            seriesFamily = if (isSmart) "复兴号智能动车组 (CR400 系列)" else "复兴号标准动车组 (CR400 系列)",
            speedDesignClass = "350 km/h 设计速度等级标准",
            formationType = "8辆编组 / 标称定员 576人",
            manufacturer = "中国中车 (CRRC) 四方 / 长客股份",
            highlights = listOf(
                "全列覆盖高速无线 Wi-Fi",
                "静音车厢专属安静约定",
                "5车设多功能无障碍设施区",
                "全座椅配备独立五孔电源与Type-C"
            ),
            cars = listOf(
                CarComposition("01车", "商务座 / 一等座", "VIP观光", "全平躺商务真皮电动座席、专属阅读灯、独立降噪耳麦", isQuietCar = true),
                CarComposition("02车", "二等座", "标准客车", "2+3人体工学座椅，全席配设五孔+Type-C快充口"),
                CarComposition("03车", "二等座", "静音车厢", "指定静音车厢，关闭广播外放，保持安静旅途环境", isQuietCar = true),
                CarComposition("04车", "二等座", "静音车厢", "指定静音车厢，柔和护眼阅读灯光，低分贝运行", isQuietCar = true),
                CarComposition("05车", "二等座 / 餐吧", "多功能车", "吧台餐售区、残障轮椅停泊区、母婴护理室、无障碍洗手间、AED急救设备", isDiningCar = true, isBarrierFree = true),
                CarComposition("06车", "二等座", "标准客车", "车厢端头大件行李存放柜、电热开水炉"),
                CarComposition("07车", "二等座", "标准客车", "超薄流线座椅背架、折叠小桌板、智能感应照明"),
                CarComposition("08车", "二等座 / 商务座", "车尾观光", "VIP全景商务席与二等座混合编组")
            ),
            amenities = standardHighSpeedAmenities()
        )
    }

    private fun crh380Spec(modelName: String): RollingStockSpec {
        return RollingStockSpec(
            modelName = modelName,
            seriesFamily = "和谐号高原型/高速动车组 (CRH380 系列)",
            speedDesignClass = "300 - 350 km/h 设计速度等级标准",
            formationType = "8辆编组 / 标称定员 556人",
            manufacturer = "中国中车 (CRRC)",
            highlights = listOf(
                "低阻力流线型仿生车头",
                "全席 220V 电源插座",
                "5车宽敞餐吧吧台",
                "车端微机控制真空集便系统"
            ),
            cars = listOf(
                CarComposition("01车", "商务座 / 一等座", "VIP车厢", "可旋转豪华躺椅，独立行李架"),
                CarComposition("02车", "二等座", "客座车厢", "2+3排列，座椅靠背可倾斜角度调节"),
                CarComposition("03车", "二等座", "静音推荐", "低噪音区间，舒适坐席体验", isQuietCar = true),
                CarComposition("04车", "二等座", "客座车厢", "居中平稳车厢，适合长途休憩"),
                CarComposition("05车", "餐车 / 二等座", "餐售车厢", "餐酒吧台、冷热饮品供应、微波加热设施、残障洗手间", isDiningCar = true, isBarrierFree = true),
                CarComposition("06车", "二等座", "客座车厢", "端头配备电茶炉及大件行李搁置架"),
                CarComposition("07车", "二等座", "客座车厢", "标准二等客座，视野开阔"),
                CarComposition("08车", "一等座 / 观光区", "观光座席", "2+2布局一等软座，头部视野空间佳")
            ),
            amenities = standardHighSpeedAmenities()
        )
    }

    private fun crhSpec(modelName: String): RollingStockSpec {
        return RollingStockSpec(
            modelName = modelName,
            seriesFamily = "和谐号城际/干线动车组 (CRH 系列)",
            speedDesignClass = "200 - 250 km/h 设计速度等级标准",
            formationType = "8辆编组 / 标称定员 610人",
            manufacturer = "中国中车 (CRRC)",
            highlights = listOf(
                "大开门宽体客舱设计",
                "全列冷暖变频变风量空调",
                "车厢两端冷温开水供应",
                "无障碍乘车设施"
            ),
            cars = listOf(
                CarComposition("01车", "一等座", "优选车厢", "2+2座椅布局，间距宽敞舒适"),
                CarComposition("02车", "二等座", "标准车厢", "2+3布局，带可折叠后置餐桌"),
                CarComposition("03车", "二等座", "标准车厢", "配备电源插座与大件行李架"),
                CarComposition("04车", "二等座", "标准车厢", "中间运行平稳车厢"),
                CarComposition("05车", "餐车 / 二等座", "餐吧车厢", "食品售卖窗口、开水供应处、无障碍洗手间", isDiningCar = true, isBarrierFree = true),
                CarComposition("06车", "二等座", "标准车厢", "标准二等座席"),
                CarComposition("07车", "二等座", "标准车厢", "标准二等座席"),
                CarComposition("08车", "一等座 / 二等座", "端头车厢", "舒适乘车体验")
            ),
            amenities = standardHighSpeedAmenities()
        )
    }

    private fun conventionalSpec(modelName: String): RollingStockSpec {
        val name = modelName.ifEmpty { "25T/25G 型准高速客车" }
        return RollingStockSpec(
            modelName = name,
            seriesFamily = "普速铁路干线客车",
            speedDesignClass = "120 - 160 km/h 设计速度等级标准",
            formationType = "16-18 节机辆编组",
            manufacturer = "中车四方 / 浦镇 / 长客",
            highlights = listOf(
                "硬卧/软卧舒适长途卧铺",
                "全列独立温控车厢空调",
                "独立餐车热食现炒现制",
                "每节车厢均设电茶炉"
            ),
            cars = listOf(
                CarComposition("01-04车", "硬座车 (YZ)", "经济坐席", "3+2对坐茶几座椅布局"),
                CarComposition("05-08车", "硬卧车 (YW)", "卧铺车厢", "上中下三层舒适卧铺，配独立阅读灯"),
                CarComposition("09车", "餐车 (CA)", "现制餐车", "独立餐桌椅，提供现炒热菜与客饭快餐", isDiningCar = true),
                CarComposition("10-12车", "软卧车 (RW)", "包厢卧铺", "四人独立封闭包厢，带门锁与独立广播开关"),
                CarComposition("13-16车", "硬卧车 (YW)", "卧铺车厢", "标准长途舒适卧铺车厢")
            ),
            amenities = listOf(
                TrainAmenity("🔌", "乘车充电", "硬卧走廊每隔3米设充电插座，软卧包厢内设专用插座"),
                TrainAmenity("🍳", "餐车服务", "餐车提供热菜快餐、咖啡热饮及流动推车小吃"),
                TrainAmenity("💧", "饮水服务", "每节车厢乘务员室旁设电加热开水炉"),
                TrainAmenity("🧳", "行李存放", "硬卧下铺床底宽大行李位及走廊上方宽阔行李架"),
                TrainAmenity("❄️", "空调新风", "全列配备中央空调系统，换气滤网持续循环"),
                TrainAmenity("🚪", "私密空间", "软卧车厢配备滑动推拉门，保障夜间睡眠安宁")
            )
        )
    }

    private fun standardHighSpeedAmenities(): List<TrainAmenity> = listOf(
        TrainAmenity("⚡", "全席充电接口", "每个座椅下方均配有国标五孔 220V 交流插座及 USB/Type-C 手机充电口"),
        TrainAmenity("📶", "旅客 Wi-Fi", "全列覆盖「国铁WiFi」高速无线网络，手机连接即可流畅浏览与在线娱乐"),
        TrainAmenity("♿", "无障碍出行", "5号车设专用轮椅固定位、残障人士宽体卫生间（带 SOS 紧急呼叫按钮）"),
        TrainAmenity("🍼", "母婴专属关爱", "5号车设独立哺乳折叠护理台、恒温冲奶用水与婴儿换尿布台"),
        TrainAmenity("💧", "饮用水供应", "各车厢连接处均设有智能电茶炉，提供 100℃ 开水与温饮用水"),
        TrainAmenity("🧳", "大件行李柜", "每节车厢前后两端均配置双层大件行李存放架，附设弹力固定防护栏"),
        TrainAmenity("🔇", "静音车厢约定", "指定车厢关闭多媒体外放、将手机调至静音或振动，提供高静谧休息空间"),
        TrainAmenity("❄️", "变频恒温空调", "智能变频微风送风技术，全列气压差自动阻尼调节，进出隧道耳部无压迫感")
    )
}
