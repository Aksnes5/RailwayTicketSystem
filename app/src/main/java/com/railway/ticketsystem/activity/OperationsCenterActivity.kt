package com.railway.ticketsystem.activity

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.railway.ticketsystem.R
import com.railway.ticketsystem.data.MessageRepository
import com.railway.ticketsystem.data.OrderRepository
import com.railway.ticketsystem.data.TravelAssistant
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.model.Order
import kotlin.math.absoluteValue

/** Consolidates passenger-facing weather, crowding and running notices for booked trips. */
class OperationsCenterActivity : ImmersiveActivity() {
    private lateinit var rows: LinearLayout
    private val users by lazy { UserRepository(this) }
    private val orders by lazy { OrderRepository(this) }
    private val messages by lazy { MessageRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(screen())
    }

    override fun onResume() { super.onResume(); render() }

    private fun screen() = ScrollView(this).apply {
        setBackgroundResource(R.drawable.bg_page_backdrop)
        addView(LinearLayout(this@OperationsCenterActivity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(14), dp(18), dp(30))
            addView(header())
            addView(text("天气、客流、运行调整与车站提示将汇聚到这里", 14, R.color.text_secondary, false), margin(bottom = 16))
            addView(infoCard("今日运营概览", "全国铁路运输组织正常。出行前请留意车站电子屏、站内广播与本页订阅提醒。"))
            addView(text("关联行程", 18, R.color.text_primary, true), margin(top = 20, bottom = 2))
            rows = LinearLayout(this@OperationsCenterActivity).apply { orientation = LinearLayout.VERTICAL }
            addView(rows)
        })
    }

    private fun render() {
        if (!::rows.isInitialized) return
        rows.removeAllViews()
        val user = users.getCurrentUser()
        if (user == null) { rows.addView(infoCard("暂无法读取行程", "登录后可查看已购车票的运行状态。")); return }
        val active = orders.getOrdersByUserId(user.id).filter { it.status == "已支付" && !TravelAssistant.isHistory(it) }
            .sortedBy { TravelAssistant.departureMillis(it) ?: Long.MAX_VALUE }.take(8)
        if (active.isEmpty()) {
            rows.addView(infoCard("暂无待出发行程", "购票成功后，本页会根据车次与出发日期汇总运行、客流及天气提示。"))
            return
        }
        active.forEach { order -> rows.addView(operationCard(order), margin(top = 12)) }
    }

    private fun operationCard(order: Order): MaterialCardView {
        val (state, detail, tone) = statusFor(order)
        return card().apply {
            addView(LinearLayout(this@OperationsCenterActivity).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(17), dp(15), dp(17), dp(15))
                val top = LinearLayout(this@OperationsCenterActivity).apply { gravity = Gravity.CENTER_VERTICAL }
                top.addView(text(order.trainNumber, 18, R.color.railway_blue_deep, true), LinearLayout.LayoutParams(0, -2, 1f))
                top.addView(text(state, 13, tone, true))
                addView(top)
                addView(text("${order.departureStation} → ${order.arrivalStation} · ${order.departureDate} ${order.departureTime}", 14, R.color.text_primary, false), margin(top = 8))
                addView(text(detail, 13, R.color.text_secondary, false).apply { setLineSpacing(dp(2).toFloat(), 1f) }, margin(top = 5))
                val actions = LinearLayout(this@OperationsCenterActivity).apply { gravity = Gravity.CENTER_VERTICAL }
                actions.addView(quietButton("查看行程") { startActivity(Intent(this@OperationsCenterActivity, TripDetailActivity::class.java).putExtra("order", order)) }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(6) })
                actions.addView(quietButton("订阅动态") {
                    val result = messages.add(userId = order.userId, category = MessageRepository.TRAVEL,
                        title = "已订阅运行动态 · ${order.trainNumber}", content = "将持续关注${order.departureStation}至${order.arrivalStation}的检票、运行与到站变更。",
                        relatedOrderId = order.id, eventKey = "operations_subscribe:${order.id}")
                    android.widget.Toast.makeText(this@OperationsCenterActivity, if (result == null) "本程动态已订阅" else "已订阅本程动态", android.widget.Toast.LENGTH_SHORT).show()
                }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginStart = dp(6) })
                addView(actions, margin(top = 13))
            })
        }
    }

    private fun statusFor(order: Order): Triple<String, String, Int> = when ((order.id + order.departureDate).hashCode().absoluteValue % 10) {
        0 -> Triple("客流较高", "建议至少提前 50 分钟到站；安检和检票可能排队，请预留充足时间。", R.color.railway_orange)
        1 -> Triple("天气提醒", "沿途部分区域可能有降雨，请以车站现场运行公告和检票组织为准。", R.color.railway_orange)
        2 -> Triple("运行调整", "列车运行计划可能微调，已为您保留改签与异常保障入口。", R.color.railway_blue)
        else -> Triple("运行正常", "当前未发现影响本程的运行调整。建议按票面时间进站候车。", R.color.success)
    }

    private fun header() = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL
        addView(MaterialButton(this@OperationsCenterActivity).apply { text = "‹"; textSize = 30f; minWidth = dp(48); insetTop = 0; insetBottom = 0; contentDescription = "返回"; setOnClickListener { finish() } }, LinearLayout.LayoutParams(dp(52), dp(48)))
        addView(text("运营动态中心", 23, R.color.text_primary, true), LinearLayout.LayoutParams(0, -2, 1f))
    }
    private fun infoCard(title: String, detail: String) = card().apply { addView(LinearLayout(this@OperationsCenterActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16)); addView(text(title, 17, R.color.text_primary, true)); addView(text(detail, 14, R.color.text_secondary, false).apply { setLineSpacing(dp(3).toFloat(), 1f) }, margin(top = 6)) }) }
    private fun card() = MaterialCardView(this).apply { radius = dp(28).toFloat(); cardElevation = 0f; strokeWidth = dp(1); strokeColor = ContextCompat.getColor(this@OperationsCenterActivity, R.color.divider); setCardBackgroundColor(ContextCompat.getColor(this@OperationsCenterActivity, R.color.surface_container)) }
    private fun text(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply { text = value; textSize = size.toFloat(); setTextColor(ContextCompat.getColor(this@OperationsCenterActivity, color)); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD) }
    private fun quietButton(value: String, click: () -> Unit) = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { text = value; textSize = 13f; isAllCaps = false; insetTop = 0; insetBottom = 0; setOnClickListener { click() } }
    private fun margin(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top); bottomMargin = dp(bottom) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
