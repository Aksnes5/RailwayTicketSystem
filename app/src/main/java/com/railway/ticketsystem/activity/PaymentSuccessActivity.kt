package com.railway.ticketsystem.activity

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.railway.ticketsystem.MainActivity
import com.railway.ticketsystem.R
import com.railway.ticketsystem.data.OrderRepository
import com.railway.ticketsystem.data.PaymentLifecycle
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.data.OfflineTravelRepository
import com.railway.ticketsystem.databinding.ActivityPaymentSuccessBinding
import com.railway.ticketsystem.model.Order
import java.util.Locale

class PaymentSuccessActivity : ImmersiveActivity() {
    private lateinit var binding: ActivityPaymentSuccessBinding
    private lateinit var orderRepository: OrderRepository
    private lateinit var userRepository: UserRepository
    private var orders: List<Order> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentSuccessBinding.inflate(layoutInflater)
        setContentView(binding.root)
        orderRepository = OrderRepository(this)
        userRepository = UserRepository(this)
        binding.btnViewTrips.setOnClickListener { openMain(1) }
        binding.btnBackHome.setOnClickListener { openMain(0) }
        binding.btnViewDetails.setOnClickListener { chooseTicket() }
    }

    override fun onResume() {
        super.onResume()
        PaymentLifecycle(this).processExpiredPayments()
        val user = userRepository.getCurrentUser()
        val first = user?.let { orderRepository.getOrderById(intent.getStringExtra("orderId").orEmpty(), it.id) }
        orders = if (first == null) emptyList() else when {
            !first.paymentBatchId.isNullOrBlank() ->
                orderRepository.getOrdersByPaymentBatchId(first.paymentBatchId!!, first.userId)
            !first.itineraryId.isNullOrBlank() ->
                orderRepository.getOrdersByItineraryId(first.itineraryId!!, first.userId)
            !first.groupId.isNullOrBlank() ->
                orderRepository.getOrdersByUserId(first.userId).filter { it.groupId == first.groupId }
            else -> listOf(first)
        }
        // Persist the trip details off the UI thread. The optional national map remains an
        // explicit user download because it can use significantly more storage.
        if (user != null && orders.any { it.status == "已支付" || it.status == "已完成" }) {
            Thread { OfflineTravelRepository(applicationContext).autoPrepare(user.id) }.start()
        }
        render()
    }

    private fun render() {
        val paid = orders.isNotEmpty() && orders.all { it.status == "已支付" || it.status == "已完成" }
        binding.tvResultIcon.text = if (paid) "✓" else "·"
        binding.tvResultIcon.setTextColor(getColor(if (paid) R.color.success else R.color.text_secondary))
        binding.tvResultTitle.text = when {
            paid -> "支付成功"
            orders.isEmpty() -> "无法查看此订单"
            orders.all { it.status == "已取消" } -> "订单已取消"
            orders.any { it.status == "待支付" } -> "订单尚未支付"
            else -> "订单状态已更新"
        }
        binding.tvResultHint.text = if (paid) {
            "支付已完成。\n可查看各张车票的订单明细与电子凭证。"
        } else "请以订单明细中的最新状态为准。"
        val first = orders.firstOrNull()
        binding.tvOrderId.text = when {
            first == null -> "订单不存在或不属于当前登录账号"
            !first.paymentBatchId.isNullOrBlank() -> "支付单号：${first.paymentBatchId}"
            else -> "订单号：${first.id}"
        }
        binding.tvPaymentTime.text = first?.let { "支付时间：${it.payTime ?: "尚未支付"}" }.orEmpty()
        binding.tvPaymentTotal.text = if (orders.isEmpty()) "" else
            "共 ${orders.size} 张车票  ·  ¥${String.format(Locale.CHINA, "%.2f", orders.sumOf { it.finalPrice })}"
        binding.tvTicketList.text = orders.joinToString("\n\n") {
            "${it.trainNumber}  ${it.departureStation} → ${it.arrivalStation}\n" +
                "${it.departureDate} ${it.departureTime}  ·  ${it.passengerName}\n" +
                "${it.seatInfo}  ·  ${it.status}"
        }
        binding.btnViewDetails.visibility = if (orders.isEmpty()) View.GONE else View.VISIBLE
        binding.btnViewDetails.text = if (paid) "查看明细与电子凭证" else "查看订单明细"
    }

    private fun chooseTicket() {
        if (orders.size == 1) {
            openDetail(orders.first())
        } else if (orders.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("选择车票")
                .setItems(orders.map { "${it.trainNumber} · ${it.passengerName} · ${it.departureStation} → ${it.arrivalStation}" }.toTypedArray()) { _, index ->
                    openDetail(orders[index])
                }.setNegativeButton("取消", null).show()
        }
    }

    private fun openDetail(order: Order) =
        startActivity(Intent(this, TripDetailActivity::class.java).putExtra("orderId", order.id))

    private fun openMain(tab: Int) {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("selectedTab", tab)
        })
        finish()
    }
}





