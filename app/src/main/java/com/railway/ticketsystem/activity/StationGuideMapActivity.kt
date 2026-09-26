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
import com.railway.ticketsystem.data.OrderRepository
import com.railway.ticketsystem.data.TicketTravelUpdates
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.model.Order
import com.railway.ticketsystem.view.StationGuideMapView

/** Visual companion to the ticket-aware station walking route. */
class StationGuideMapActivity : ImmersiveActivity() {
    private var ticket: Order? = null
    private var station = ""
    private var target = "检票口"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val user = UserRepository(this).getCurrentUser()
        ticket = intent.getStringExtra(EXTRA_TICKET_ORDER_ID)?.let { id -> user?.let { OrderRepository(this).getOrderById(id, it.id) } }
        station = intent.getStringExtra(EXTRA_STATION).orEmpty().ifBlank { ticket?.departureStation.orEmpty() }
        target = intent.getStringExtra(EXTRA_TARGET).orEmpty().ifBlank { "检票口" }
        setContentView(buildScreen())
    }

    private fun buildScreen(): ScrollView = ScrollView(this).apply {
        setBackgroundResource(R.drawable.bg_page_backdrop)
        addView(LinearLayout(this@StationGuideMapActivity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(14), dp(18), dp(30))
            addView(header())
            addView(text("${station.ifBlank { "当前" }}站 · 站内服务导览图", 15, R.color.text_secondary, false), margin(top = 2, bottom = 16))
            val gate = ticket?.let { TicketTravelUpdates.getGate(this@StationGuideMapActivity, it) } ?: "请以现场为准"
            addView(card().apply {
                addView(LinearLayout(this@StationGuideMapActivity).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(10), dp(10), dp(14))
                    addView(StationGuideMapView(this@StationGuideMapActivity).apply { this.target = this@StationGuideMapActivity.target; this.gate = gate; contentDescription = "从进站口经安检和候车区前往${this@StationGuideMapActivity.target}的站内路线图" })
                    addView(text("路线：进站口 → 安检区 → 候车区 → $target", 14, R.color.railway_blue_deep, true), margin(top = 4, bottom = 2))
                })
            })
            addView(infoCard("使用提示", "该平面图用于辅助识别功能区域与推荐动线；检票口、候车区与通道调整请以现场电子屏和工作人员指引为准。"), margin(top = 14))
            addView(quietButton("返回路线详情") { finish() }, margin(top = 16))
        })
    }

    private fun header() = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        val backBtn = android.widget.ImageView(this@StationGuideMapActivity).apply {
            setImageResource(R.drawable.ic_arrow_back)
            imageTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this@StationGuideMapActivity, R.color.railway_blue_deep))
            contentDescription = "返回"
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { finish() }
        }
        addView(backBtn, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(8) })
        addView(text("站内地图", 21, R.color.text_primary, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }
    private fun infoCard(title: String, detail: String) = card().apply { addView(LinearLayout(this@StationGuideMapActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16)); addView(text(title, 16, R.color.text_primary, true)); addView(text(detail, 14, R.color.text_secondary, false).apply { setLineSpacing(dp(3).toFloat(), 1f) }, margin(top = 5)) }) }
    private fun card() = MaterialCardView(this).apply { radius = dp(24).toFloat(); cardElevation = 0f; strokeWidth = dp(1); strokeColor = ContextCompat.getColor(this@StationGuideMapActivity, R.color.divider); setCardBackgroundColor(android.graphics.Color.parseColor("#B8FFFFFF")) }
    private fun text(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply { text = value; textSize = size.toFloat(); setTextColor(ContextCompat.getColor(this@StationGuideMapActivity, color)); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD) }
    private fun quietButton(value: String, click: () -> Unit) = MaterialButton(this).apply {
        text = value; textSize = 15f; cornerRadius = dp(16); insetTop = 0; insetBottom = 0; isAllCaps = false
        strokeWidth = dp(1)
        strokeColor = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this@StationGuideMapActivity, R.color.divider))
        backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this@StationGuideMapActivity, R.color.surface_container))
        setTextColor(ContextCompat.getColor(this@StationGuideMapActivity, R.color.railway_blue))
        setOnClickListener { click() }
    }
    private fun margin(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top); bottomMargin = dp(bottom) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    companion object { const val EXTRA_STATION = "station_guide_station"; const val EXTRA_TARGET = "station_guide_target"; const val EXTRA_TICKET_ORDER_ID = "station_guide_ticket_order_id" }
}
