package com.railway.ticketsystem.activity

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.railway.ticketsystem.R
import com.railway.ticketsystem.data.CarriageServiceRepository
import com.railway.ticketsystem.data.MealOrderRepository
import com.railway.ticketsystem.data.MessageRepository
import com.railway.ticketsystem.data.OrderRepository
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.model.Order

/** On-board support center: every action is durable and attached to the current ticket. */
class CarriageServiceActivity : ImmersiveActivity() {
    private lateinit var userRepository: UserRepository
    private lateinit var orderRepository: OrderRepository
    private lateinit var carriageRepository: CarriageServiceRepository
    private lateinit var body: LinearLayout
    private var ticket: Order? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userRepository = UserRepository(this)
        orderRepository = OrderRepository(this)
        carriageRepository = CarriageServiceRepository(this)
        val user = userRepository.getCurrentUser()
        ticket = intent.getStringExtra(EXTRA_TICKET_ORDER_ID)?.let { user?.let { current -> orderRepository.getOrderById(it, current.id) } }
        setContentView(buildScreen())
    }

    override fun onResume() { super.onResume(); render() }

    private fun buildScreen(): ScrollView = ScrollView(this).apply {
        setBackgroundResource(R.drawable.bg_page_backdrop)
        addView(LinearLayout(this@CarriageServiceActivity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(14), dp(18), dp(32))
            addView(header())
            addView(text("服务进度、到座餐饮与乘务协助，都将同步保存在本次行程中。", 14, R.color.text_secondary, false), margin(top = 4, bottom = 16))
            body = LinearLayout(this@CarriageServiceActivity).apply { orientation = LinearLayout.VERTICAL }
            addView(body)
        })
    }

    private fun header() = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(MaterialButton(this@CarriageServiceActivity).apply { text = "‹"; textSize = 30f; minWidth = dp(48); insetTop = 0; insetBottom = 0; contentDescription = "返回"; setOnClickListener { finish() } }, LinearLayout.LayoutParams(dp(52), dp(48)))
        addView(text("车厢服务", 23, R.color.text_primary, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun render() {
        if (!::body.isInitialized) return
        body.removeAllViews()
        val order = ticket
        if (order == null) {
            body.addView(infoCard("车票信息暂不可用", "请从有效车票详情进入车厢服务。")); return
        }
        body.addView(ticketCard(order))
        body.addView(text("快捷服务", 18, R.color.text_primary, true), margin(top = 20, bottom = 8))
        body.addView(actionCard("呼叫乘务服务", "饮水、空调、座椅、卫生等服务需求", "提交请求") { showRequestDialog("乘务服务呼叫", "请描述您的需要，例如：需要一杯温水") })
        body.addView(actionCard("到座餐饮进度", mealDescription(order), if (mealExists(order)) "查看订餐" else "去订餐") {
            startActivity(Intent(this, MealOrderActivity::class.java).putExtra("orderId", order.id))
        }, margin(top = 10))
        body.addView(actionCard("遗失物登记", "登记遗失物品、位置和特征，乘务组会协助核查", "登记") { showRequestDialog("遗失物登记", "例如：黑色双肩包，可能遗留在 ${order.carNumber} 车") }, margin(top = 10))
        body.addView(actionCard("乘车偏好", "静音提醒、儿童友好服务等偏好将在本程生效", "设置") { showPreferenceDialog() }, margin(top = 10))
        body.addView(text("本程服务进度", 18, R.color.text_primary, true), margin(top = 20, bottom = 8))
        val user = userRepository.getCurrentUser()
        val requests = user?.let { carriageRepository.getByTicket(it.id, order.id) }.orEmpty()
        if (requests.isEmpty()) body.addView(infoCard("暂无服务请求", "需要协助时可使用上方快捷服务；提交后的处理状态会在此更新。"))
        else requests.forEach { request -> body.addView(requestCard(request), margin(top = 9)) }
        body.addView(infoCard("乘务通知", "列车运行中请保管好随身物品；到站前请留意车内广播和显示屏。"), margin(top = 18))
    }

    private fun ticketCard(order: Order) = card().apply {
        addView(LinearLayout(this@CarriageServiceActivity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16))
            addView(text("${order.trainNumber} · ${order.carNumber}车 ${order.seatNumber}号", 20, R.color.railway_blue_deep, true))
            addView(text("${order.departureStation} → ${order.arrivalStation}", 15, R.color.text_primary, true), margin(top = 6))
            addView(text("${order.departureDate} ${order.departureTime} · ${order.seatType}", 13, R.color.text_secondary, false), margin(top = 4))
        })
    }

    private fun actionCard(title: String, detail: String, action: String, click: () -> Unit) = card().apply {
        isClickable = true; setOnClickListener { click() }
        addView(LinearLayout(this@CarriageServiceActivity).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(18), dp(15), dp(14), dp(15))
            addView(LinearLayout(this@CarriageServiceActivity).apply { orientation = LinearLayout.VERTICAL; addView(text(title, 17, R.color.text_primary, true)); addView(text(detail, 13, R.color.text_secondary, false).apply { maxLines = 2 }, margin(top = 4)) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(quietButton(action) { click() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)).apply { marginStart = dp(8) })
        })
    }

    private fun requestCard(request: com.railway.ticketsystem.data.CarriageServiceRequest) = card().apply {
        addView(LinearLayout(this@CarriageServiceActivity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(15), dp(18), dp(15))
            addView(LinearLayout(this@CarriageServiceActivity).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(text(request.type, 16, R.color.text_primary, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(text(carriageRepository.displayStatus(request), 13, R.color.railway_blue_deep, true))
            })
            addView(text(request.details, 14, R.color.text_secondary, false), margin(top = 5))
            addView(text("${request.carNumber}车 ${request.seatNumber}号 · ${request.createdAt}", 12, R.color.text_secondary, false), margin(top = 5))
        })
    }

    private fun showRequestDialog(type: String, hint: String) {
        val input = EditText(this).apply { this.hint = hint; minLines = 3; setPadding(dp(18), dp(10), dp(18), dp(10)) }
        AlertDialog.Builder(this).setTitle(type).setView(input).setNegativeButton("取消", null).setPositiveButton("提交", null).create().also { dialog ->
            dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val detail = input.text.toString().trim()
                if (detail.isBlank()) { input.error = "请补充服务内容"; return@setOnClickListener }
                if (saveRequest(type, detail)) { dialog.dismiss(); render() }
            } }
        }.show()
    }

    private fun showPreferenceDialog() {
        val choices = arrayOf("静音提醒：减少不必要的语音提示", "儿童友好：需要儿童用品或陪护协助")
        AlertDialog.Builder(this).setTitle("选择本程偏好").setItems(choices) { _, which ->
            if (saveRequest("乘车偏好", choices[which])) render()
        }.show()
    }

    private fun saveRequest(type: String, detail: String): Boolean {
        val user = userRepository.getCurrentUser() ?: run { Toast.makeText(this, "请先登录后使用车厢服务", Toast.LENGTH_SHORT).show(); return false }
        val order = ticket ?: return false
        val request = CarriageServiceRepository.newRequest(user.id, order.id, order.trainNumber, order.carNumber, order.seatNumber, type, detail)
        if (!carriageRepository.save(request)) { Toast.makeText(this, "提交失败，请重试", Toast.LENGTH_SHORT).show(); return false }
        MessageRepository(this).add(user.id, MessageRepository.TRAVEL, "$type 已受理", "${order.trainNumber} ${order.carNumber}车 ${order.seatNumber}号 · $detail", order.id, "carriage_service:${request.id}")
        Toast.makeText(this, "已提交乘务组，将在本页更新进度", Toast.LENGTH_LONG).show()
        return true
    }

    private fun mealExists(order: Order): Boolean = userRepository.getCurrentUser()?.let { MealOrderRepository(this).getByTicket(it.id, order.id).isNotEmpty() } == true
    private fun mealDescription(order: Order): String {
        val user = userRepository.getCurrentUser() ?: return "可提前选购盒饭、饮品和零食，到座配送"
        val latest = MealOrderRepository(this).getByTicket(user.id, order.id).firstOrNull() ?: return "可提前选购盒饭、饮品和零食，到座配送"
        val items = latest.lines.joinToString("、") { "${it.name}×${it.quantity}" }
        return "${latest.status} · $items"
    }

    private fun infoCard(title: String, detail: String) = card().apply { addView(LinearLayout(this@CarriageServiceActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(17), dp(18), dp(17)); addView(text(title, 16, R.color.text_primary, true)); addView(text(detail, 14, R.color.text_secondary, false).apply { setLineSpacing(dp(3).toFloat(), 1f) }, margin(top = 5)) }) }
    private fun card() = MaterialCardView(this).apply { radius = dp(28).toFloat(); cardElevation = 0f; strokeWidth = dp(1); strokeColor = ContextCompat.getColor(this@CarriageServiceActivity, R.color.divider); setCardBackgroundColor(ContextCompat.getColor(this@CarriageServiceActivity, R.color.surface_container)) }
    private fun text(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply { text = value; textSize = size.toFloat(); setTextColor(ContextCompat.getColor(this@CarriageServiceActivity, color)); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD) }
    private fun quietButton(value: String, click: () -> Unit) = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { text = value; textSize = 13f; insetTop = 0; insetBottom = 0; isAllCaps = false; setOnClickListener { click() } }
    private fun margin(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top); bottomMargin = dp(bottom) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object { const val EXTRA_TICKET_ORDER_ID = "carriage_service_ticket_order_id" }
}
