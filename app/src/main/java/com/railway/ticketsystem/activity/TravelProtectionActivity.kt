package com.railway.ticketsystem.activity

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.railway.ticketsystem.R
import com.railway.ticketsystem.data.MessageRepository
import com.railway.ticketsystem.data.OrderRepository
import com.railway.ticketsystem.data.TravelAssistant
import com.railway.ticketsystem.data.TravelProtectionCase
import com.railway.ticketsystem.data.TravelProtectionRepository
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.model.Order

/** A ticket-linked triage flow for delays, disruption, missed trains and baggage issues. */
class TravelProtectionActivity : ImmersiveActivity() {
    private val users by lazy { UserRepository(this) }
    private val orders by lazy { OrderRepository(this) }
    private val cases by lazy { TravelProtectionRepository(this) }
    private val messages by lazy { MessageRepository(this) }
    private lateinit var selection: TextView
    private lateinit var options: LinearLayout
    private lateinit var history: LinearLayout
    private var selected: Order? = null

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContentView(screen()) }
    override fun onResume() { super.onResume(); render() }

    private fun screen() = ScrollView(this).apply {
        setBackgroundResource(R.drawable.bg_page_backdrop)
        addView(LinearLayout(this@TravelProtectionActivity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(14), dp(18), dp(30))
            addView(header())
            addView(text("晚点、停运、误乘与行李问题可在这里留存处理凭据并获得下一步指引", 14, R.color.text_secondary, false), margin(bottom = 16))
            selection = text("请先选择一段已支付行程", 15, R.color.railway_blue_deep, true)
            addView(infoCard("保障行程", selection))
            options = LinearLayout(this@TravelProtectionActivity).apply { orientation = LinearLayout.VERTICAL }
            addView(options, margin(top = 14))
            addView(text("我的保障记录", 18, R.color.text_primary, true), margin(top = 22, bottom = 2))
            history = LinearLayout(this@TravelProtectionActivity).apply { orientation = LinearLayout.VERTICAL }
            addView(history)
        })
    }

    private fun render() {
        if (!::options.isInitialized) return
        val user = users.getCurrentUser() ?: return
        val trips = orders.getOrdersByUserId(user.id).filter { it.status == "已支付" && !TravelAssistant.isHistory(it) }
        if (selected == null || trips.none { it.id == selected?.id }) selected = trips.firstOrNull()
        selection.text = selected?.let { "${it.trainNumber} · ${it.departureStation} → ${it.arrivalStation} · ${it.departureDate}" } ?: "暂无可关联的已支付行程"
        options.removeAllViews()
        if (trips.isNotEmpty()) {
            options.addView(ticketPicker(trips), margin(bottom = 12))
            options.addView(text("选择需要协助的情况", 18, R.color.text_primary, true), margin(bottom = 2))
            supportDefinitions.forEach { definition -> options.addView(optionCard(definition), margin(top = 10)) }
        } else {
            options.addView(infoCard("暂不可发起保障", "保障服务需关联一段未完成的已支付行程。"))
        }
        history.removeAllViews()
        val records = cases.getByUser(user.id)
        if (records.isEmpty()) history.addView(infoCard("暂无保障记录", "发起一次异常保障后，处理进度会保存在这里，并同步发送到消息中心。"))
        else records.take(8).forEach { history.addView(historyCard(it), margin(top = 10)) }
    }

    private fun ticketPicker(trips: List<Order>) = card().apply { addView(LinearLayout(this@TravelProtectionActivity).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(17), dp(15), dp(17), dp(15)); addView(text("切换保障行程", 16, R.color.text_primary, true))
        val current = selected?.id
        trips.take(5).forEach { order -> addView(quietButton("${if (order.id == current) "✓ " else ""}${order.trainNumber}  ${order.departureStation} → ${order.arrivalStation}") { selected = order; render() }, margin(top = 8)) }
    }) }

    private fun optionCard(definition: SupportDefinition) = card().apply { addView(LinearLayout(this@TravelProtectionActivity).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(17), dp(15), dp(17), dp(15)); addView(text(definition.title, 17, R.color.text_primary, true)); addView(text(definition.description, 13, R.color.text_secondary, false), margin(top = 5)); addView(quietButton(definition.action) { openCase(definition) }, margin(top = 12))
    }) }

    private fun openCase(definition: SupportDefinition) {
        val user = users.getCurrentUser(); val order = selected
        if (user == null || order == null) { Toast.makeText(this, "请先选择一段行程", Toast.LENGTH_SHORT).show(); return }
        val record = cases.open(user.id, order.id, definition.title, definition.status, definition.solution)
        if (record == null) { Toast.makeText(this, "该保障事项已创建", Toast.LENGTH_SHORT).show(); return }
        messages.add(user.id, MessageRepository.TRAVEL, "${definition.title}已受理", "${order.trainNumber} · ${definition.solution}", order.id, "travel_protection:${record.id}")
        Toast.makeText(this, "已生成保障方案，可在消息中心查看", Toast.LENGTH_LONG).show(); render()
    }

    private fun historyCard(record: TravelProtectionCase) = card().apply { addView(LinearLayout(this@TravelProtectionActivity).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(17), dp(15), dp(17), dp(15)); val top = LinearLayout(this@TravelProtectionActivity).apply { gravity = Gravity.CENTER_VERTICAL; addView(text(record.type, 16, R.color.text_primary, true), LinearLayout.LayoutParams(0, -2, 1f)); addView(text(record.status, 13, R.color.railway_blue, true)) }; addView(top); addView(text(record.solution, 13, R.color.text_secondary, false), margin(top = 5)); addView(text("${record.createdAt} · ${record.id}", 12, R.color.text_secondary, false), margin(top = 7)); addView(quietButton("查看关联行程") { orders.getOrderById(record.orderId, record.userId)?.let { startActivity(Intent(this@TravelProtectionActivity, TripDetailActivity::class.java).putExtra("order", it)) } }, margin(top = 10))
    }) }

    private fun header() = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; addView(MaterialButton(this@TravelProtectionActivity).apply { text = "‹"; textSize = 30f; minWidth = dp(48); insetTop = 0; insetBottom = 0; contentDescription = "返回"; setOnClickListener { finish() } }, LinearLayout.LayoutParams(dp(52), dp(48))); addView(text("异常保障中心", 23, R.color.text_primary, true), LinearLayout.LayoutParams(0, -2, 1f)) }
    private fun infoCard(title: String, content: TextView) = card().apply { addView(LinearLayout(this@TravelProtectionActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(17), dp(15), dp(17), dp(15)); addView(text(title, 17, R.color.text_primary, true)); addView(content, margin(top = 6)) }) }
    private fun infoCard(title: String, detail: String) = infoCard(title, text(detail, 14, R.color.text_secondary, false))
    private fun card() = MaterialCardView(this).apply { radius = dp(28).toFloat(); cardElevation = 0f; strokeWidth = dp(1); strokeColor = ContextCompat.getColor(this@TravelProtectionActivity, R.color.divider); setCardBackgroundColor(ContextCompat.getColor(this@TravelProtectionActivity, R.color.surface_container)) }
    private fun text(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply { text = value; textSize = size.toFloat(); setTextColor(ContextCompat.getColor(this@TravelProtectionActivity, color)); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD) }
    private fun quietButton(value: String, click: () -> Unit) = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { text = value; textSize = 14f; isAllCaps = false; insetTop = 0; insetBottom = 0; setOnClickListener { click() } }
    private fun margin(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top); bottomMargin = dp(bottom) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private data class SupportDefinition(val title: String, val description: String, val action: String, val status: String, val solution: String)
    private val supportDefinitions = listOf(
        SupportDefinition("列车晚点", "查看候车提示与改签入口，避免因运行调整影响后续安排。", "生成候车与改签方案", "候车方案已生成", "已保留候车和改签服务入口；请持续关注检票口与运行动态。"),
        SupportDefinition("临时停运", "为受影响车票建立退票或改签处理凭据，并保留电子通知。", "发起退改保障", "退改保障已受理", "已生成退改保障凭据；您可在车票详情继续办理退票或改签。"),
        SupportDefinition("误乘或漏乘", "登记情况后查看后续出行建议，必要时可携带凭据咨询人工服务。", "获取后续出行建议", "人工服务指引已生成", "已记录行程异常，请携带本保障编号前往人工服务窗口核验后续方案。"),
        SupportDefinition("行李问题", "将行李遗失、损坏或取件异常关联至本程行程，便于站车核查。", "登记行李问题", "行李核查已受理", "已生成行李核查编号；可在车站服务中补充物品特征与联系信息。")
    )
}
