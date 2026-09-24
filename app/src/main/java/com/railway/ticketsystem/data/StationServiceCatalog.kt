package com.railway.ticketsystem.data

/** Shared catalogue so every entry point opens the same station-service product. */
data class StationServiceSpec(
    val label: String,
    val subtitle: String,
    val scheduleHint: String,
    val detailHint: String,
    val notice: String,
    val highlights: List<String>,
    val requiresDetail: Boolean = false,
    val priceCents: Long = 0L
) {
    fun confirmation(schedule: String, details: String): String = when {
        schedule.isNotBlank() -> schedule
        details.isNotBlank() -> details.take(28)
        else -> "工作人员将通过预留联系方式确认"
    }
}

object StationServiceCatalog {
    const val PICKUP_DROPOFF = "接送站"
    const val PARKING = "停车预约"
    const val PRIORITY_PASSENGER = "重点旅客服务"
    const val LOST_FOUND = "遗失物登记"
    const val LOUNGE = "贵宾厅预约"
    const val LUGGAGE_CONSIGNMENT = "行李托运"
    const val LUGGAGE_DELIVERY = "同城送达"

    private val products = listOf(
        StationServiceSpec(PICKUP_DROPOFF, "预约接站或送站用车，出站后可按约定地点与司机会合。", "接站/送站时间，例如 9月12日 08:30", "接站或送站、乘车人数、行李情况", "提交后将由服务人员通过预留联系方式确认车辆、会合点与费用。", listOf("支持到站后 5 公里内接送", "可填写同行人数与大件行李", "司机接单后推送会合信息")),
        StationServiceSpec(PARKING, "提前登记车辆，查询车站停车场余位和入场指引。", "预计入场时间", "车牌号码、车型、预计停车时长", "车位以到场时实际情况为准，请按引导标识入场。", listOf("按车站余位动态安排", "支持车辆与到场时间登记", "服务详情内可查看入场指引"), requiresDetail = true),
        StationServiceSpec(PRIORITY_PASSENGER, "为老幼病残孕等重点旅客预约进站、候车、乘降协助。", "预计到站时间 / 车次", "需要的协助、同行人数、辅助器具情况", "请至少提前 2 小时提交；现场工作人员将按实际条件提供协助。", listOf("进站、安检、候车、乘降全程协助", "可备注轮椅、婴儿车与无障碍需求", "工作人员确认后发送服务进度"), requiresDetail = true),
        StationServiceSpec(LOST_FOUND, "登记遗失物特征与地点，便于车站失物招领核查联系。", "遗失时间 / 可能地点", "物品名称、颜色、品牌、特征及可证明信息", "请勿填写银行卡完整号码、密码等敏感信息。", listOf("支持车站内与列车上遗失物登记", "登记后保留查询编号", "核查到匹配物品后发送通知"), requiresDetail = true),
        StationServiceSpec(LOUNGE, "预约高铁贵宾候车区，享受候车休息、餐饮与专属引导服务。", "到厅时间 / 关联车次", "使用人数、是否使用贵宾候车厅券", "贵宾厅开放及席位以车站当日安排为准。", listOf("候车休息与茶饮轻食", "专属检票提醒与引导", "可填写贵宾厅券使用需求")),
        StationServiceSpec(LUGGAGE_CONSIGNMENT, "在车站预约寄存或托运，工作人员到约定服务点收取行李。", "预计交运行李时间", "行李件数、尺寸、重量及取件人信息", "单笔服务含 2 件行李；贵重物品、易燃易爆品及违禁品不可交运。", listOf("支持寄存、托运与到站取件", "服务范围与禁运物品提前告知", "提交后生成交接编号"), requiresDetail = true, priceCents = 1800L),
        StationServiceSpec(LUGGAGE_DELIVERY, "将行李从车站配送至 5 公里内酒店、住所或指定服务点。", "预计送达时间", "收件地址、房号/门牌、行李件数和收件人", "同城配送范围为车站周边 5 公里，送达前会联系收件人确认。", listOf("车站周边 5 公里同城送达", "支持酒店、住所与服务点", "送达前联系收件人确认"), requiresDetail = true, priceCents = 2600L)
    )

    fun all(): List<StationServiceSpec> = products
    fun find(label: String): StationServiceSpec? = products.firstOrNull { it.label == label }
}

