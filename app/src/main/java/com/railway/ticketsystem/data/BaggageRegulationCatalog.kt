package com.railway.ticketsystem.data

/**
 * 铁路旅客随身携带物品与安检目录数据库
 * 依据最新《铁路旅客禁止、限制携带和托运物品目录》与国家铁路局安全规范
 */
object BaggageRegulationCatalog {

    enum class ComplianceLevel(val label: String, val badgeColorHex: String) {
        SAFE("🟢 合规携带", "#34C759"),
        RESTRICTED("🟡 限量携带", "#FF9500"),
        PROHIBITED("🔴 严禁随身携带", "#FF3B30")
    }

    data class BaggageItem(
        val name: String,
        val category: String,
        val level: ComplianceLevel,
        val limitDescription: String,
        val officialRule: String,
        val suggestion: String
    )

    val items = listOf(
        // 数码与电池
        BaggageItem(
            name = "充电宝 / 锂电池",
            category = "数码电池",
            level = ComplianceLevel.RESTRICTED,
            limitDescription = "标志清晰，单块额定能量 ≤ 100Wh（约27000mAh@3.7V）",
            officialRule = "额定能量 > 100Wh 且 ≤ 160Wh 需经铁路运输企业同意；> 160Wh 或无标志者严禁携带",
            suggestion = "请随身携带，切勿放入行李托运，列车运行中请勿在行李架上持续充电"
        ),
        BaggageItem(
            name = "笔记本电脑 / 平板 / 手机",
            category = "数码电池",
            level = ComplianceLevel.SAFE,
            limitDescription = "随身携带件数无严格限制，需过安检机单独检查",
            officialRule = "个人自用电子产品正常随身携带",
            suggestion = "安检时如遇要求，请配合单独取出平放于安检筐内"
        ),
        BaggageItem(
            name = "无人机 / 相机备用锂电池",
            category = "数码电池",
            level = ComplianceLevel.RESTRICTED,
            limitDescription = "备用锂电池绝缘包装，单块 ≤ 100Wh",
            officialRule = "严禁在列车运行中或车站范围内放飞无人机",
            suggestion = "电池端子需绝缘封贴，备用电池需随身携带"
        ),

        // 喷雾与化妆品
        BaggageItem(
            name = "防晒喷雾 / 补水喷雾",
            category = "喷雾日化",
            level = ComplianceLevel.RESTRICTED,
            limitDescription = "单体容器容积 ≤ 150ml，每种限带 1 件，累计不超过 600ml",
            officialRule = "瓶身标明容积超出 150ml（即使剩余液体不足）亦禁止携带",
            suggestion = "请选用 150ml 以内的小包装或分装便携瓶"
        ),
        BaggageItem(
            name = "定型摩丝 / 发胶喷雾",
            category = "喷雾日化",
            level = ComplianceLevel.RESTRICTED,
            limitDescription = "单体容积 ≤ 150ml，每种限带 1 件",
            officialRule = "属于带有自加压包装喷雾，严格受容量与件数限制",
            suggestion = "建议携带固体发蜡或小于 100ml 便携啫喱"
        ),
        BaggageItem(
            name = "香水 / 花露水",
            category = "喷雾日化",
            level = ComplianceLevel.RESTRICTED,
            limitDescription = "单体容积 ≤ 100ml",
            officialRule = "含乙醇等易燃溶剂的液体需密封完好，限量携带",
            suggestion = "使用便携分装喷瓶，避免颠簸泄漏"
        ),
        BaggageItem(
            name = "指甲油 / 去光水",
            category = "喷雾日化",
            level = ComplianceLevel.RESTRICTED,
            limitDescription = "累计不得超过 50ml",
            officialRule = "属于微量易燃液体溶剂，限制随身携带量",
            suggestion = "携带小型瓶装，避免在车厢内涂抹影响他人"
        ),

        // 酒类与食品
        BaggageItem(
            name = "包装密封白酒 / 红酒",
            category = "酒水食品",
            level = ComplianceLevel.RESTRICTED,
            limitDescription = "包装完好、标志清晰，24%~70%酒精度，累计 ≤ 3000ml",
            officialRule = "酒精度 > 70% 属于易燃易爆品严禁携带；散装自酿白酒严禁携带",
            suggestion = "请确保外包装封签完好未拆封，随身妥善包裹防碎"
        ),
        BaggageItem(
            name = "散装白酒 / 自酿米酒",
            category = "酒水食品",
            level = ComplianceLevel.PROHIBITED,
            limitDescription = "一律禁止随身携带与托运",
            officialRule = "散装液体无法核验真实酒精纯度与安全性，存在自燃闪爆隐患",
            suggestion = "请勿携带散装自酿酒进入车站"
        ),
        BaggageItem(
            name = "自热火锅 / 自热米饭发热包",
            category = "酒水食品",
            level = ComplianceLevel.PROHIBITED,
            limitDescription = "禁止在列车和车站内使用发热包加热食品",
            officialRule = "发热包遇水反应剧烈释放高温易燃易爆气体，极易触发车厢烟感报警",
            suggestion = "建议食用保温餐饮或列车点餐，勿在车厢冲水加热"
        ),
        BaggageItem(
            name = "大闸蟹 / 鲜活海鲜鱼虾",
            category = "酒水食品",
            level = ComplianceLevel.RESTRICTED,
            limitDescription = "必须使用硬质密封保鲜箱，严禁任何液体漏洒",
            officialRule = "作为食品且封闭于泡沫箱/硬箱中可随身携带，严禁散放或水滴漏污损车厢",
            suggestion = "使用泡沫保温箱密封并缠紧胶带，箱内置冰袋勿放冰块融水"
        ),

        // 刀具与日用工具
        BaggageItem(
            name = "折叠水果刀 / 美工刀",
            category = "刀具工具",
            level = ComplianceLevel.PROHIBITED,
            limitDescription = "刀刃长度超过 60mm 禁止随身携带（可托运）",
            officialRule = "刃长 ≤ 60mm 的小剪刀、指甲刀、刮胡刀允许携带",
            suggestion = "长刃刀具请提前办理行李托运或快递邮寄"
        ),
        BaggageItem(
            name = "修眉刀 / 指甲剪 / 刮胡刀",
            category = "刀具工具",
            level = ComplianceLevel.SAFE,
            limitDescription = "个人日用洗漱工具，正常随身携带",
            officialRule = "符合日常自用标准的无锁闭装置小型修护用具",
            suggestion = "收纳在洗漱包内即可，过机安检无碍"
        ),

        // 运动出行与宠物
        BaggageItem(
            name = "折叠自行车 / 折叠滑板车",
            category = "出行运动",
            level = ComplianceLevel.RESTRICTED,
            limitDescription = "长宽高之和不超过 130cm，必须使用专用包装袋完全包裹",
            officialRule = "严禁推行散装或裸装平衡车/自行车进入站台与车厢",
            suggestion = "折叠后放入专用装车袋，上车后妥善放置于车厢大件行李存放区"
        ),
        BaggageItem(
            name = "猫狗宠物 / 活体动物",
            category = "出行运动",
            level = ComplianceLevel.PROHIBITED,
            limitDescription = "严禁随身携带（持证导盲犬除外）",
            officialRule = "活体动物可能影响公共卫生与乘车安全，必须走中铁快运合规宠物托运",
            suggestion = "如需带宠出行，请提前办理动物检疫合格证明并在车站中铁快运柜台托运"
        )
    )

    /**
     * 充电宝能量计算公式：Wh = (mAh * V) / 1000
     */
    fun calculatePowerBankWh(mah: Double, voltage: Double = 3.7): Double {
        return (mah * voltage) / 1000.0
    }

    enum class PowerBankEvaluation(val title: String, val level: ComplianceLevel, val message: String) {
        SAFE_TO_CARRY(
            "符合规定 · 准予随身携带",
            ComplianceLevel.SAFE,
            "额定能量 ≤ 100Wh，符合国家铁路局《铁路旅客携带品规定》，可随身携带进站乘车。"
        ),
        NEEDS_APPROVAL(
            "限量范围 · 需铁路运输企业同意",
            ComplianceLevel.RESTRICTED,
            "额定能量在 100Wh ~ 160Wh 之间，每名旅客限带数量通常受限，进站安检时请主动向工作人员出示。"
        ),
        FORBIDDEN(
            "超标超量 · 严禁随身携带",
            ComplianceLevel.PROHIBITED,
            "额定能量 > 160Wh 或无清晰容量标示，属于铁路违禁物品，严禁携带进站上车，请选择快递寄存。"
        )
    }

    fun evaluatePowerBank(wh: Double): PowerBankEvaluation {
        return when {
            wh <= 100.0 -> PowerBankEvaluation.SAFE_TO_CARRY
            wh <= 160.0 -> PowerBankEvaluation.NEEDS_APPROVAL
            else -> PowerBankEvaluation.FORBIDDEN
        }
    }
}
