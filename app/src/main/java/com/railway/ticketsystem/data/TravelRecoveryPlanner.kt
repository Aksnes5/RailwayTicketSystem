package com.railway.ticketsystem.data

import com.railway.ticketsystem.model.Order
import java.util.Date

/** A conservative, explainable recovery plan built from the locally stored itinerary state. */
data class TravelRecoveryPlan(
    val status: String,
    val headline: String,
    val detail: String,
    val options: List<TravelRecoveryOption>
)

data class TravelRecoveryOption(
    val type: Type,
    val title: String,
    val description: String,
    val actionLabel: String
) {
    enum class Type { MONITOR, CONNECTION, HOTEL, MEAL, CHANGE, REIMBURSEMENT }
}

/**
 * Produces recovery choices without inventing a delay. It only describes an active issue when
 * the durable ticket state or its scheduled time proves one exists; otherwise it offers a
 * pre-departure contingency plan.
 */
object TravelRecoveryPlanner {
    fun create(order: Order, now: Long = System.currentTimeMillis()): TravelRecoveryPlan {
        val departure = TravelAssistant.departureMillis(order)
        val arrival = TravelAssistant.arrivalMillis(order)
        val hasTransfer = !order.itineraryId.isNullOrBlank()
        val baseOptions = mutableListOf<TravelRecoveryOption>()

        when {
            order.status == "待支付" -> {
                baseOptions += TravelRecoveryOption(
                    TravelRecoveryOption.Type.MONITOR,
                    "保留本次席位",
                    "请在支付倒计时结束前完成付款，席位和同行人信息会继续保留。",
                    "继续支付"
                )
                return TravelRecoveryPlan(
                    status = "待完成支付",
                    headline = "先完成支付，出行服务将自动接续",
                    detail = "支付成功后会立即建立检票、经停、到站接驳与提醒计划。",
                    options = baseOptions
                )
            }
            order.status == "已取消" -> {
                baseOptions += TravelRecoveryOption(
                    TravelRecoveryOption.Type.CHANGE,
                    "重新规划车次",
                    "可重新查询相同日期或相邻日期的可售车次。",
                    "查询车票"
                )
                return TravelRecoveryPlan(
                    status = "本程已取消",
                    headline = "该行程不再占用席位",
                    detail = "已取消订单仍保留在历史记录中，方便查看退款与报销信息。",
                    options = baseOptions
                )
            }
            arrival != null && now >= arrival || order.status == "已完成" -> {
                baseOptions += TravelRecoveryOption(
                    TravelRecoveryOption.Type.REIMBURSEMENT,
                    "整理报销凭证",
                    "已出行车票可进入电子凭证与报销汇总。",
                    "查看凭证"
                )
                return TravelRecoveryPlan(
                    status = "已抵达目的地",
                    headline = "本次行程已结束",
                    detail = "如有报销、发票或行程证明需求，可在下方直接处理。",
                    options = baseOptions
                )
            }
            departure != null && now >= departure -> {
                baseOptions += TravelRecoveryOption(
                    TravelRecoveryOption.Type.MONITOR,
                    "查看运行进度",
                    "持续关注经停与到站状态；如状态发生变化，消息中心会保留通知记录。",
                    "查看动态"
                )
                baseOptions += TravelRecoveryOption(
                    TravelRecoveryOption.Type.MEAL,
                    "调整车上服务",
                    "可查看本趟列车可配送餐品与车厢服务。",
                    "打开服务"
                )
                if (hasTransfer) {
                    baseOptions += TravelRecoveryOption(
                        TravelRecoveryOption.Type.CONNECTION,
                        "复核换乘衔接",
                        "根据已保存的两段行程，提前检查换乘时间和站内步行路径。",
                        "查看接驳"
                    )
                }
                return TravelRecoveryPlan(
                    status = "乘车途中",
                    headline = "行程保障已开启",
                    detail = "当前展示基于已保存时刻的服务预案；以车站现场广播和列车公告为准。",
                    options = baseOptions
                )
            }
            else -> {
                baseOptions += TravelRecoveryOption(
                    TravelRecoveryOption.Type.MONITOR,
                    "开启出发前关注",
                    "发车前会持续关联检票口、经停和出发提醒，重要变化将进入消息中心。",
                    "开启关注"
                )
                if (hasTransfer) {
                    baseOptions += TravelRecoveryOption(
                        TravelRecoveryOption.Type.CONNECTION,
                        "预检换乘方案",
                        "提前查看站内动线、换乘余量和抵达后接驳建议。",
                        "查看换乘"
                    )
                }
                baseOptions += TravelRecoveryOption(
                    TravelRecoveryOption.Type.HOTEL,
                    "预留晚到住宿方案",
                    "抵达时间变化时，可快速查看目的站周边已订酒店与备选房型。",
                    "查看住宿"
                )
                return TravelRecoveryPlan(
                    status = "出发前保障中",
                    headline = "当前未发现需要调整的行程冲突",
                    detail = "已为 ${order.trainNumber} 次建立出发、途中与到站三段保障预案。",
                    options = baseOptions
                )
            }
        }
    }
}
