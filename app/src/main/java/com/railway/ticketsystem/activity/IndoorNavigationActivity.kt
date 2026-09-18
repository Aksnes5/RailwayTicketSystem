package com.railway.ticketsystem.activity

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.railway.ticketsystem.R
import com.railway.ticketsystem.data.MessageRepository
import com.railway.ticketsystem.data.OrderRepository
import com.railway.ticketsystem.data.StationServiceRepository
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.model.Order
import com.railway.ticketsystem.model.Station
import kotlin.math.abs

/** Ticket-aware walking guidance generated locally, so it also remains usable offline. */
class IndoorNavigationActivity : ImmersiveActivity() {
    private lateinit var userRepository: UserRepository
    private lateinit var orderRepository: OrderRepository
    private lateinit var stationServices: StationServiceRepository
    private lateinit var stationButton: MaterialButton
    private lateinit var planContainer: LinearLayout
    private lateinit var beginButton: MaterialButton
    private var station = ""
    private var target = TARGET_GATE
    private var ticket: Order? = null

    private val stationPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val selected = result.data?.getSerializableExtra("selectedStation") as? Station ?: return@registerForActivityResult
        if (result.resultCode == RESULT_OK) {
            station = selected.name
            renderPlan()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userRepository = UserRepository(this)
        orderRepository = OrderRepository(this)
        stationServices = StationServiceRepository(this)
        val user = userRepository.getCurrentUser()
        ticket = intent.getStringExtra(EXTRA_TICKET_ORDER_ID)?.let { id -> user?.let { orderRepository.getOrderById(id, it.id) } }
        station = intent.getStringExtra(EXTRA_STATION).orEmpty().ifBlank { ticket?.departureStation.orEmpty() }
        setContentView(buildScreen())
        renderPlan()
    }

    private fun buildScreen(): ScrollView = ScrollView(this).apply {
        setBackgroundResource(R.drawable.bg_page_backdrop)
        addView(LinearLayout(this@IndoorNavigationActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(32))
            addView(header())
            addView(text("从进站到检票口的一站式步行引导，路线会根据当前车票与服务目标生成。", 14, R.color.text_secondary, false), margin(top = 4, bottom = 14))
            stationButton = quietButton("选择服务车站") { stationPicker.launch(Intent(this@IndoorNavigationActivity, StationSelectionActivity::class.java)) }
            addView(stationButton, margin(bottom = 14))
            addView(targetCard())
            addView(text("推荐路线", 18, R.color.text_primary, true), margin(top = 20, bottom = 8))
            planContainer = LinearLayout(this@IndoorNavigationActivity).apply { orientation = LinearLayout.VERTICAL }
            addView(planContainer)
            beginButton = primaryButton("开始导航") { savePlan() }
            addView(beginButton, margin(top = 18))
            addView(text("站内动线会因现场客流、施工和管控调整，请以车站标识及工作人员提示为准。", 13, R.color.text_secondary, false).apply {
                setLineSpacing(dp(3).toFloat(), 1f)
            }, margin(top = 12))
        })
    }

    private fun header() = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(MaterialButton(this@IndoorNavigationActivity).apply {
            text = "‹"; textSize = 30f; minWidth = dp(48); insetTop = 0; insetBottom = 0
            contentDescription = "返回"; setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(52), dp(48)))
        addView(text("站内导航", 23, R.color.text_primary, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun targetCard() = card().apply {
        addView(LinearLayout(this@IndoorNavigationActivity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16))
            addView(text("前往哪里", 17, R.color.railway_blue_deep, true))
            addView(text("选择目标区域后，自动生成步行路线与预计时长。", 13, R.color.text_secondary, false), margin(top = 4, bottom = 10))
            val first = LinearLayout(this@IndoorNavigationActivity).apply { gravity = Gravity.CENTER_VERTICAL }
            val second = LinearLayout(this@IndoorNavigationActivity).apply { gravity = Gravity.CENTER_VERTICAL }
            targets.take(3).forEach { item -> first.addView(targetButton(item), LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(6) }) }
            targets.drop(3).forEach { item -> second.addView(targetButton(item), LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(6) }) }
            addView(first); addView(second, margin(top = 7))
        })
    }

    private fun targetButton(value: String) = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
        text = value; textSize = 13f; insetTop = 0; insetBottom = 0; isAllCaps = false
        setOnClickListener { target = value; renderPlan() }
    }

    private fun renderPlan() {
        if (!::planContainer.isInitialized) return
        stationButton.text = if (station.isBlank()) "选择服务车站" else "$station · 更换车站"
        planContainer.removeAllViews()
        if (station.isBlank()) {
            planContainer.addView(emptyCard("请先选择车站", "选择车站后即可生成进站、候车与服务区域间的步行路线。"))
            beginButton.isEnabled = false
            return
        }
        beginButton.isEnabled = true
        val plan = buildPlan()
        planContainer.addView(summaryCard(plan))
        plan.steps.forEachIndexed { index, step ->
            planContainer.addView(stepCard(index + 1, plan.steps.size, step), margin(top = 9))
        }
    }

    private fun buildPlan(): NavigationPlan {
        val gate = 6 + abs((station + (ticket?.trainNumber ?: "")).hashCode() % 18)
        val common = mutableListOf(
            NavigationStep("进站口", "证件核验后进入候车区域", 1),
            NavigationStep("安检区", "通过安检，注意随身行李", 3)
        )
        when (target) {
            TARGET_PARKING -> common += listOf(NavigationStep("出站连廊", "沿停车指引前行", 3), NavigationStep("停车场", "网约车与社会车辆上客区", 2))
            TARGET_DINING -> common += listOf(NavigationStep("候车层", "乘扶梯至候车层", 2), NavigationStep("餐饮区", "靠近候车区的餐饮服务", 2))
            TARGET_ACCESSIBLE -> common += listOf(NavigationStep("无障碍电梯", "优先使用无障碍通道", 2), NavigationStep("候车区", "无障碍候车区", 2))
            TARGET_WAITING -> common += NavigationStep("候车区", "${ticket?.trainNumber ?: "本次列车"}候车区域", 3)
            else -> common += listOf(NavigationStep("候车区", "${ticket?.trainNumber ?: "本次列车"}候车区域", 3), NavigationStep("$gate 号检票口", "请在开检后凭车票进站", 2))
        }
        val minutes = common.sumOf { it.minutes }
        return NavigationPlan(common, minutes, minutes * 78)
    }

    private fun summaryCard(plan: NavigationPlan) = card().apply {
        addView(LinearLayout(this@IndoorNavigationActivity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16))
            addView(text("$station → $target", 19, R.color.text_primary, true))
            addView(text("约 ${plan.minutes} 分钟 · 约 ${plan.meters} 米", 14, R.color.railway_blue_deep, true), margin(top = 6))
            ticket?.let { addView(text("关联车票：${it.trainNumber} · ${it.departureDate} ${it.departureTime}", 13, R.color.text_secondary, false), margin(top = 5)) }
        })
    }

    private fun stepCard(index: Int, total: Int, step: NavigationStep) = card().apply {
        addView(LinearLayout(this@IndoorNavigationActivity).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(13), dp(16), dp(13))
            addView(text(index.toString(), 15, R.color.railway_blue_deep, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(32), dp(32)))
            addView(LinearLayout(this@IndoorNavigationActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(step.title, 16, R.color.text_primary, true))
                addView(text(step.hint, 13, R.color.text_secondary, false), margin(top = 3))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(9) })
            addView(text("${step.minutes} 分钟", 13, R.color.railway_blue_deep, true))
        })
    }

    private fun savePlan() {
        val user = userRepository.getCurrentUser() ?: run { Toast.makeText(this, "请先登录后使用站内导航", Toast.LENGTH_SHORT).show(); return }
        val plan = buildPlan()
        val request = StationServiceRepository.newRequest(
            userId = user.id, serviceType = "站内导航 · $target", station = station,
            schedule = ticket?.let { "${it.departureDate} ${it.departureTime} ${it.trainNumber}" }.orEmpty(),
            contact = user.phone.ifBlank { user.realName.ifBlank { user.username } },
            details = plan.steps.joinToString(" → ") { it.title }, status = "导航中", ticketOrderId = ticket?.id
        )
        if (!stationServices.save(request)) { Toast.makeText(this, "保存路线失败，请重试", Toast.LENGTH_SHORT).show(); return }
        MessageRepository(this).add(user.id, MessageRepository.TRAVEL, "站内导航已生成", "$station · 前往$target，预计 ${plan.minutes} 分钟", ticket?.id, "indoor_navigation:${request.id}")
        Toast.makeText(this, "路线已保存，可离线查看", Toast.LENGTH_LONG).show()
    }

    private fun emptyCard(title: String, detail: String) = card().apply { addView(LinearLayout(this@IndoorNavigationActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18)); addView(text(title, 17, R.color.text_primary, true)); addView(text(detail, 14, R.color.text_secondary, false), margin(top = 5)) }) }
    private fun card() = MaterialCardView(this).apply { radius = dp(28).toFloat(); cardElevation = 0f; strokeWidth = dp(1); strokeColor = ContextCompat.getColor(this@IndoorNavigationActivity, R.color.divider); setCardBackgroundColor(ContextCompat.getColor(this@IndoorNavigationActivity, R.color.surface_container)) }
    private fun text(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply { text = value; textSize = size.toFloat(); setTextColor(ContextCompat.getColor(this@IndoorNavigationActivity, color)); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD) }
    private fun primaryButton(value: String, click: () -> Unit) = MaterialButton(this).apply { text = value; textSize = 16f; insetTop = 0; insetBottom = 0; setOnClickListener { click() } }
    private fun quietButton(value: String, click: () -> Unit) = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { text = value; textSize = 15f; insetTop = 0; insetBottom = 0; isAllCaps = false; setOnClickListener { click() } }
    private fun margin(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top); bottomMargin = dp(bottom) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private data class NavigationStep(val title: String, val hint: String, val minutes: Int)
    private data class NavigationPlan(val steps: List<NavigationStep>, val minutes: Int, val meters: Int)
    companion object {
        const val EXTRA_STATION = "indoor_navigation_station"
        const val EXTRA_TICKET_ORDER_ID = "indoor_navigation_ticket_order_id"
        private const val TARGET_GATE = "检票口"
        private const val TARGET_WAITING = "候车区"
        private const val TARGET_DINING = "餐饮区"
        private const val TARGET_ACCESSIBLE = "无障碍通道"
        private const val TARGET_PARKING = "停车场"
        private val targets = listOf(TARGET_GATE, TARGET_WAITING, TARGET_DINING, TARGET_ACCESSIBLE, TARGET_PARKING)
    }
}
