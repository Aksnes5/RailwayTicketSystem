package com.railway.ticketsystem.activity

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.railway.ticketsystem.R
import com.railway.ticketsystem.data.MealLine
import com.railway.ticketsystem.data.MealOrder
import com.railway.ticketsystem.data.MealOrderRepository
import com.railway.ticketsystem.data.MembershipRepository
import com.railway.ticketsystem.data.MessageRepository
import com.railway.ticketsystem.data.OnboardMealCatalog
import com.railway.ticketsystem.data.OrderRepository
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.databinding.ActivityMealOrderBinding
import com.railway.ticketsystem.model.Order
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MealOrderActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMealOrderBinding
    private lateinit var userRepository: UserRepository
    private lateinit var orderRepository: OrderRepository
    private lateinit var mealRepository: MealOrderRepository
    private lateinit var membershipRepository: MembershipRepository
    private lateinit var messageRepository: MessageRepository

    private val quantities = linkedMapOf<String, Int>()
    private val quantityViews = mutableMapOf<String, TextView>()
    private var selectedTicket: Order? = null
    private var userId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMealOrderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        userRepository = UserRepository(this)
        orderRepository = OrderRepository(this)
        mealRepository = MealOrderRepository(this)
        membershipRepository = MembershipRepository(this)
        messageRepository = MessageRepository(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSubmitMealOrder.setOnClickListener { submitOrder() }
        loadTicket(intent.getStringExtra("orderId"))
        renderProducts()
    }

    private fun loadTicket(requestedOrderId: String?) {
        val user = userRepository.getCurrentUser()
        if (user == null) {
            Toast.makeText(this, "请先登录后使用高铁订餐", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        userId = user.id
        val available = orderRepository.getOrdersByUserId(userId).filter { it.status == "已支付" }
        selectedTicket = requestedOrderId?.let { id -> available.firstOrNull { it.id == id } } ?: available.firstOrNull()
        renderTicketChoices(available)
        renderTicket()
    }

    private fun renderTicketChoices(tickets: List<Order>) {
        binding.llMealTicketChoices.removeAllViews()
        if (tickets.size <= 1) return
        val caption = smallText("选择可订餐行程", 12, R.color.text_secondary)
        binding.llMealTicketChoices.addView(caption)
        tickets.take(6).forEach { ticket ->
            val choice = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "${ticket.trainNumber} · ${ticket.departureDate} · ${ticket.departureStation} → ${ticket.arrivalStation}"
                isAllCaps = false
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@MealOrderActivity, R.color.railway_blue))
                setOnClickListener {
                    selectedTicket = ticket
                    quantities.clear()
                    renderTicket()
                    renderProducts()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(42)
                ).apply { topMargin = dp(6) }
            }
            binding.llMealTicketChoices.addView(choice)
        }
    }

    private fun renderTicket() {
        val ticket = selectedTicket
        if (ticket == null) {
            binding.tvMealTicket.text = "暂无可订餐的已支付行程"
            binding.tvMealPickup.text = "购票成功后，可在车票详情或此页选择行程订餐。"
            binding.btnSubmitMealOrder.isEnabled = false
            binding.tvMealExisting.visibility = View.GONE
            return
        }
        binding.tvMealTicket.text = "${ticket.trainNumber}  ${ticket.departureStation} → ${ticket.arrivalStation}"
        binding.tvMealPickup.text = "${ticket.departureDate} ${ticket.departureTime} 发车 · 配送点：${ticket.departureStation}候车区"
        binding.btnSubmitMealOrder.isEnabled = true
        val existing = mealRepository.getByTicket(userId, ticket.id)
        binding.tvMealExisting.visibility = if (existing.isEmpty()) View.GONE else View.VISIBLE
        binding.tvMealExisting.text = existing.firstOrNull()?.let {
            "已订餐 ${money(it.totalCents)} · ${it.status} · 可继续加购"
        }.orEmpty()
    }

    private fun renderProducts() {
        binding.llMealItems.removeAllViews()
        quantityViews.clear()
        OnboardMealCatalog.products.groupBy { it.category }.forEach { (category, products) ->
            binding.llMealItems.addView(smallText(category, 20, R.color.railway_blue).apply {
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(22); bottomMargin = dp(6) }
            })
            products.forEach { product -> binding.llMealItems.addView(productCard(product)) }
        }
        renderCart()
    }

    private fun productCard(product: com.railway.ticketsystem.data.MealProduct): View {
        val card = MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(Color.WHITE)
            strokeWidth = dp(1)
            strokeColor = ContextCompat.getColor(this@MealOrderActivity, R.color.divider)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(13), dp(12), dp(13))
        }
        val textBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textBlock.addView(smallText(product.name, 17, R.color.text_primary).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        textBlock.addView(smallText(product.description, 12, R.color.text_secondary).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(3) }
        })
        content.addView(textBlock)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.END
        }
        controls.addView(smallText(money(product.priceCents), 15, R.color.railway_orange).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.END
        })
        val row = LinearLayout(this).apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)).apply { topMargin = dp(5) }
        }
        fun stepButton(symbol: String, delta: Int) = MaterialButton(
            this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = symbol
            textSize = 17f
            minWidth = 0
            insetTop = 0
            insetBottom = 0
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
            setOnClickListener {
                val value = (quantities[product.id] ?: 0) + delta
                quantities[product.id] = value.coerceAtLeast(0)
                quantityViews[product.id]?.text = quantities[product.id].toString()
                renderCart()
            }
        }
        row.addView(stepButton("−", -1))
        val amount = smallText((quantities[product.id] ?: 0).toString(), 14, R.color.text_primary).apply {
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(34))
        }
        quantityViews[product.id] = amount
        row.addView(amount)
        row.addView(stepButton("+", 1))
        controls.addView(row)
        content.addView(controls)
        card.addView(content)
        return card
    }

    private fun renderCart() {
        val total = OnboardMealCatalog.products.sumOf { it.priceCents * (quantities[it.id] ?: 0) }
        binding.tvMealTotal.text = money(total)
        binding.btnSubmitMealOrder.text = if (total > 0) "钱包支付 ${money(total)}" else "请选择餐饮商品"
        binding.btnSubmitMealOrder.isEnabled = selectedTicket != null && total > 0
    }

    private fun submitOrder() {
        val ticket = selectedTicket ?: return
        val lines = OnboardMealCatalog.products.mapNotNull { product ->
            val quantity = quantities[product.id] ?: 0
            if (quantity > 0) MealLine(product.id, product.name, quantity, product.priceCents) else null
        }
        val total = lines.sumOf { it.quantity * it.unitPriceCents }
        if (lines.isEmpty() || total <= 0) return
        val mealId = "MEAL_${UUID.randomUUID()}"
        if (!membershipRepository.payWithWallet(userId, mealId, total, "高铁订餐 · ${ticket.trainNumber}")) {
            Toast.makeText(this, "钱包余额不足，请先前往会员中心充值", Toast.LENGTH_LONG).show()
            return
        }
        val order = MealOrder(
            id = mealId,
            userId = userId,
            ticketOrderId = ticket.id,
            trainNumber = ticket.trainNumber,
            departureDate = ticket.departureDate,
            pickupStation = ticket.departureStation + "候车区配送点",
            lines = lines,
            totalCents = total,
            status = "已支付",
            createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
        )
        if (!mealRepository.save(order)) {
            membershipRepository.refundWalletPayment(userId, mealId, total)
            Toast.makeText(this, "订餐保存失败，已退回钱包余额", Toast.LENGTH_SHORT).show()
            return
        }
        messageRepository.add(
            userId,
            MessageRepository.TRAVEL,
            "高铁订餐成功 · ${ticket.trainNumber}",
            "餐品将配送至${order.pickupStation}，合计${money(total)}。",
            ticket.id,
            eventKey = "meal_order:${order.id}"
        )
        quantities.clear()
        renderProducts()
        renderTicket()
        Toast.makeText(this, "订餐成功，请留意配送通知", Toast.LENGTH_LONG).show()
    }

    private fun smallText(value: String, size: Int, colorRes: Int) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(ContextCompat.getColor(this@MealOrderActivity, colorRes))
    }

    private fun money(cents: Long) = "¥" + String.format(Locale.CHINA, "%.2f", cents / 100.0)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
