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
import kotlin.math.absoluteValue

/** A station-side dashboard: waiting-area crowding, facilities, parking and lounge availability. */
class StationFacilityActivity : ImmersiveActivity() {
    private var station = ""
    private var ticketId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        station = intent.getStringExtra(EXTRA_STATION).orEmpty().ifBlank { "当前" }
        ticketId = intent.getStringExtra(EXTRA_TICKET_ORDER_ID)
        setContentView(screen())
    }

    private fun screen() = ScrollView(this).apply {
        setBackgroundResource(R.drawable.bg_page_backdrop)
        addView(LinearLayout(this@StationFacilityActivity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(14), dp(18), dp(30))
            addView(header())
            addView(text("$station 站内服务", 15, R.color.text_secondary, false), margin(bottom = 16))
            val seed = (station.hashCode().absoluteValue % 100)
            addView(summaryCard(seed))
            addView(text("候车与出行服务", 18, R.color.text_primary, true), margin(top = 20, bottom = 2))
            addView(serviceCard("候车区舒适度", "${if (seed % 3 == 0) "较为繁忙" else "舒适"} · 推荐前往 ${if (seed % 2 == 0) "A区" else "B区"} 候车", "前往候车区") { openMap("候车区") }, margin(top = 10))
            addView(serviceCard("停车场余位", "社会车辆停车区剩余 ${(18 + seed % 47)} 个车位 · 入口已开放", "查看停车路线") { openMap("停车场") }, margin(top = 10))
            addView(serviceCard("贵宾候车厅", "当前等候 ${1 + seed % 9} 人 · 可使用贵宾候车厅券或在车站服务页预约", "前往贵宾厅") { openMap("贵宾候车厅") }, margin(top = 10))
            addView(serviceCard("无障碍与亲子设施", "无障碍电梯、重点旅客服务台、母婴室和第三卫生间均可按标识到达", "查看无障碍路线") { openMap("无障碍电梯") }, margin(top = 10))
            addView(serviceCard("站内商店", "候车层设有便利店、咖啡轻食、书报零售与特产店；营业以现场为准", "前往餐饮零售") { openMap("餐饮区") }, margin(top = 10))
        })
    }

    private fun summaryCard(seed: Int) = card().apply { addView(LinearLayout(this@StationFacilityActivity).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16)); addView(text("车站服务概览", 17, R.color.text_primary, true)); addView(text("候车客流 ${42 + seed % 39}% · 安检通道 ${2 + seed % 4} 条开放 · 请以车站电子屏实时信息为准", 14, R.color.text_secondary, false), margin(top = 7))
    }) }
    private fun serviceCard(title: String, detail: String, action: String, click: () -> Unit) = card().apply { addView(LinearLayout(this@StationFacilityActivity).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(17), dp(15), dp(17), dp(15)); addView(text(title, 17, R.color.text_primary, true)); addView(text(detail, 13, R.color.text_secondary, false).apply { setLineSpacing(dp(2).toFloat(), 1f) }, margin(top = 5)); addView(quietButton(action, click), margin(top = 12))
    }) }
    private fun openMap(target: String) = startActivity(Intent(this, StationGuideMapActivity::class.java).putExtra(StationGuideMapActivity.EXTRA_STATION, station).putExtra(StationGuideMapActivity.EXTRA_TARGET, target).putExtra(StationGuideMapActivity.EXTRA_TICKET_ORDER_ID, ticketId))
    private fun header() = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; addView(MaterialButton(this@StationFacilityActivity).apply { text = "‹"; textSize = 30f; minWidth = dp(48); insetTop = 0; insetBottom = 0; contentDescription = "返回"; setOnClickListener { finish() } }, LinearLayout.LayoutParams(dp(52), dp(48))); addView(text("车站实时服务", 23, R.color.text_primary, true), LinearLayout.LayoutParams(0, -2, 1f)) }
    private fun card() = MaterialCardView(this).apply { radius = dp(28).toFloat(); cardElevation = 0f; strokeWidth = dp(1); strokeColor = ContextCompat.getColor(this@StationFacilityActivity, R.color.divider); setCardBackgroundColor(ContextCompat.getColor(this@StationFacilityActivity, R.color.surface_container)) }
    private fun text(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply { text = value; textSize = size.toFloat(); setTextColor(ContextCompat.getColor(this@StationFacilityActivity, color)); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD) }
    private fun quietButton(value: String, click: () -> Unit) = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { text = value; textSize = 14f; isAllCaps = false; insetTop = 0; insetBottom = 0; setOnClickListener { click() } }
    private fun margin(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top); bottomMargin = dp(bottom) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    companion object { const val EXTRA_STATION = "station_facility_station"; const val EXTRA_TICKET_ORDER_ID = "station_facility_ticket_order_id" }
}
