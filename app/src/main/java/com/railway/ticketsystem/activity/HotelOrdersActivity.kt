package com.railway.ticketsystem.activity

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.railway.ticketsystem.R
import com.railway.ticketsystem.data.HotelReservation
import com.railway.ticketsystem.data.HotelReservationRepository
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.databinding.ActivityHotelOrdersBinding

/** The durable entry point for every hotel reservation created from the travel services page. */
class HotelOrdersActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHotelOrdersBinding
    private lateinit var reservations: HotelReservationRepository
    private lateinit var users: UserRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHotelOrdersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.statusBarColor = getColor(R.color.surface)
        window.navigationBarColor = getColor(R.color.surface)
        reservations = HotelReservationRepository(this)
        users = UserRepository(this)
        binding.btnHotelOrdersBack.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val user = users.getCurrentUser()
        binding.llHotelOrders.removeAllViews()
        if (user == null) {
            binding.tvHotelOrdersHint.text = "请先登录后查看酒店订单。"
            return
        }
        val orders = reservations.getByUser(user.id)
        binding.tvHotelOrdersHint.text = if (orders.isEmpty()) "暂无酒店订单，预订成功后会保存在这里。" else "共 ${orders.size} 笔酒店订单"
        orders.forEach { binding.llHotelOrders.addView(orderCard(it)) }
    }

    private fun orderCard(order: HotelReservation): MaterialCardView {
        val card = MaterialCardView(this).apply {
            radius = dp(24).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = ContextCompat.getColor(this@HotelOrdersActivity, R.color.divider)
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val heading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        heading.addView(text(order.hotelName, 17, R.color.text_primary, true).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        heading.addView(text("已确认", 13, R.color.green, true))
        content.addView(heading)
        content.addView(text("${order.checkInDate} 入住 · ${order.checkOutDate} 离店 · ${order.nights} 晚", 14, R.color.text_primary, false).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
        })
        content.addView(text("${order.roomName} · ${if (order.breakfastSelected) "含早餐" else "不含早餐"}", 13, R.color.text_secondary, false).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) }
        })
        order.guestName?.takeIf { it.isNotBlank() }?.let { guest ->
            content.addView(text("入住人：$guest", 13, R.color.text_secondary, false).apply {
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) }
            })
        }
        content.addView(text("订单号 ${order.id}     合计 ¥${order.totalPrice}", 13, R.color.railway_blue, true).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
        })
        card.addView(content)
        return card
    }

    private fun text(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(ContextCompat.getColor(this@HotelOrdersActivity, color))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
