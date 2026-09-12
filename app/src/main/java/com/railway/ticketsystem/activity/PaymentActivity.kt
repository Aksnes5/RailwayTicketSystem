package com.railway.ticketsystem.activity

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.railway.ticketsystem.MainActivity
import com.railway.ticketsystem.data.OrderRepository
import com.railway.ticketsystem.data.MembershipRepository
import com.railway.ticketsystem.data.PaymentLifecycle
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.databinding.ActivityPaymentBinding
import com.railway.ticketsystem.model.Order
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PaymentActivity : ImmersiveActivity() {
    private lateinit var binding: ActivityPaymentBinding
    private lateinit var paymentLifecycle: PaymentLifecycle
    private lateinit var orderRepository: OrderRepository
    private lateinit var userRepository: UserRepository
    private var batchId = ""
    private lateinit var membershipRepository: MembershipRepository
    private var displayedCashCouponCents = 0L
    private var orders: List<Order> = emptyList()
    private var timer: CountDownTimer? = null
    private var resumed = false
    private var leaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "订单支付"
        paymentLifecycle = PaymentLifecycle(this)
        orderRepository = OrderRepository(this)
        userRepository = UserRepository(this)
        batchId = intent.getStringExtra(PaymentLifecycle.EXTRA_BATCH_ID).orEmpty()
        membershipRepository = MembershipRepository(this)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (!leaving) refreshBatch()
    }

    override fun onPause() {
        resumed = false
        timer?.cancel()
        super.onPause()
    }

    private fun refreshBatch() {
        timer?.cancel()
        val user = userRepository.getCurrentUser()
        if (user == null || batchId.isBlank()) {
            Toast.makeText(this, "未找到当前账号的待支付订单", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        paymentLifecycle.processExpiredPayments()
        orders = orderRepository.getOrdersByPaymentBatchId(batchId, user.id)
        if (orders.isEmpty()) {
            Toast.makeText(this, "订单不存在或不属于当前账号", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (orders.all { it.status == "已支付" || it.status == "已完成" }) {
            openSuccess()
            return
        }
        val pending = orders.all { it.status == "待支付" }
        val first = orders.first()
        binding.tvPaymentTitle.text = if (pending) "确认订单并完成支付" else "订单状态已更新"
        binding.tvPaymentRoute.text = if (orders.size == 1) {
            "${first.departureStation} → ${first.arrivalStation}"
        } else "本次支付 · ${orders.size} 张车票"
        binding.tvPaymentTicketCount.text = orders.joinToString("\n\n") {
            "${it.trainNumber}  ${it.departureStation} → ${it.arrivalStation}\n" +
                "${it.departureDate} ${it.departureTime}  ${it.passengerName}\n" +
                "${it.seatInfo}  ·  ¥${money(it.finalPrice)}  ·  ${it.status}"
        }
        val totalCents = amountCents(orders.sumOf { it.finalPrice })
        val cashCoupon = if (pending) {
            membershipRepository.getAvailableCoupon(user.id, MembershipRepository.COUPON_CASH)
        } else null
        displayedCashCouponCents = if (cashCoupon == null) 0L else minOf(2_000L, totalCents)
        val payableCents = (totalCents - displayedCashCouponCents).coerceAtLeast(0L)
        binding.tvPaymentAmount.text = "¥" + money(payableCents / 100.0)
        binding.tvCouponPayHint.text = if (displayedCashCouponCents > 0L) {
            "已自动选用 ¥20 购票抵扣券，本单立减 ¥" + money(displayedCashCouponCents / 100.0) + "。"
        } else {
            "暂无可用购票抵扣券；可在会员中心使用积分兑换。"
        }
        binding.tvPaymentBatchInfo.text = "支付单号：$batchId\n创建时间：${first.createTime}"
        binding.btnConfirmPayment.isEnabled = true
        binding.tvWalletPayBalance.text = "可用余额 " + membershipRepository.getWalletBalanceText(user.id)
        binding.btnWalletRecharge.setOnClickListener { showWalletRecharge() }
        if (pending) {
            val deadline = orders.minOf { it.paymentDeadlineMillis }
            binding.tvPaymentHint.text = "请在支付期限内完成支付。座位保留至 ${dateTime(deadline)}。返回后可在行程中继续支付，倒计时不会重置。"
            binding.btnConfirmPayment.text = "确认支付"
            val canUseWallet = membershipRepository.getWalletBalanceCents(user.id) >= totalCents
            binding.btnConfirmPayment.text = if (canUseWallet) "使用钱包支付" else "确认支付"
            if (canUseWallet) {
                binding.tvPaymentHint.text = "钱包余额充足，确认后将使用铁路钱包支付。座位保留至 " + dateTime(deadline) + "。"
            } else {
                binding.tvPaymentHint.text = "钱包余额不足时仍可支付；可先充值后使用铁路钱包购票。座位保留至 " + dateTime(deadline) + "。"
            }
            binding.btnConfirmPayment.setOnClickListener { completePayment() }
            binding.btnCancelPayment.text = "取消订单并释放座位"
            binding.btnCancelPayment.setOnClickListener { confirmCancel() }
            startCountdown(deadline)
        } else {
            binding.tvPaymentCountdown.text = if (orders.all { it.status == "已取消" }) {
                if (orders.any { it.cancelReason == "支付超时" }) "支付超时 · 订单已自动取消" else "订单已取消"
            } else "请查看各张车票的最新状态"
            binding.tvPaymentHint.text = "本次支付未继续进行。已取消车票的保留座位已释放。"
            binding.btnConfirmPayment.text = "查看订单明细"
            binding.btnConfirmPayment.setOnClickListener {
                startActivity(Intent(this, TripDetailActivity::class.java).putExtra("orderId", first.id))
                finish()
            }
            binding.btnCancelPayment.text = "返回行程"
            binding.btnCancelPayment.setOnClickListener { openTrips() }
        }
    }

    private fun startCountdown(deadline: Long) {
        binding.tvPaymentCountdown.text = "支付剩余时间  ${PaymentLifecycle.formatRemaining(deadline)}"
        val remaining = deadline - System.currentTimeMillis()
        if (remaining <= 0L) {
            binding.btnConfirmPayment.isEnabled = false
            binding.tvPaymentCountdown.text = "支付期限已到，请刷新订单状态"
            return
        }
        timer = object : CountDownTimer(remaining, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                if (resumed && !leaving) {
                    binding.tvPaymentCountdown.text = "支付剩余时间  ${PaymentLifecycle.formatRemaining(deadline)}"
                }
            }
            override fun onFinish() {
                if (resumed && !leaving && !isFinishing && !isDestroyed) refreshBatch()
            }
        }.start()
    }

    private fun completePayment() {
        val user = userRepository.getCurrentUser() ?: return
        binding.btnConfirmPayment.isEnabled = false
        val totalCents = amountCents(orders.sumOf { it.finalPrice })
        val couponUsageKey = "cash_payment:" + batchId
        val cashCoupon = if (displayedCashCouponCents > 0L) {
            membershipRepository.consumeCoupon(user.id, MembershipRepository.COUPON_CASH, couponUsageKey)
        } else null
        val couponCents = if (cashCoupon == null) 0L else minOf(2_000L, totalCents)
        val cents = (totalCents - couponCents).coerceAtLeast(0L)
        val useWallet = membershipRepository.getWalletBalanceCents(user.id) >= cents
        if (useWallet && !membershipRepository.payWithWallet(user.id, batchId, cents)) {
            if (cashCoupon != null) membershipRepository.restoreCoupon(user.id, cashCoupon.id, couponUsageKey)
            Toast.makeText(this, "钱包余额已变化，请刷新后重试", Toast.LENGTH_SHORT).show()
            refreshBatch()
            return
        }
        val paid = paymentLifecycle.completePayment(batchId, user.id)
        if (paid.isNullOrEmpty()) {
            if (useWallet) membershipRepository.refundWalletPayment(user.id, batchId, cents)
            if (cashCoupon != null) membershipRepository.restoreCoupon(user.id, cashCoupon.id, couponUsageKey)
            Toast.makeText(this, "支付未完成，请查看订单当前状态", Toast.LENGTH_LONG).show()
            refreshBatch()
            return
        }
        orders = paid
        val resultMessage = when {
            useWallet && cashCoupon != null -> "已使用铁路钱包支付，并抵扣 ¥" + money(couponCents / 100.0)
            useWallet -> "已使用铁路钱包支付"
            cashCoupon != null -> "支付成功，已抵扣 ¥" + money(couponCents / 100.0)
            else -> null
        }
        if (resultMessage != null) Toast.makeText(this, resultMessage, Toast.LENGTH_SHORT).show()
        openSuccess()
    }

    private fun confirmCancel() {
        AlertDialog.Builder(this)
            .setTitle("取消本次待支付订单")
            .setMessage("将取消本支付单内全部 ${orders.size} 张车票，并释放保留的座位。")
            .setNegativeButton("保留订单", null)
            .setPositiveButton("确认取消") { _, _ ->
                val user = userRepository.getCurrentUser() ?: return@setPositiveButton
                if (paymentLifecycle.cancelPayment(batchId, user.id) == null) {
                    Toast.makeText(this, "订单状态已变化", Toast.LENGTH_SHORT).show()
                }
                refreshBatch()
            }.show()
    }

    private fun showWalletRecharge() {
        val choices = arrayOf("充值 ¥20", "充值 ¥50", "充值 ¥100")
        val cents = longArrayOf(2_000L, 5_000L, 10_000L)
        AlertDialog.Builder(this)
            .setTitle("充值铁路钱包")
            .setMessage("请选择充值金额。")
            .setItems(choices) { _, index ->
                val user = userRepository.getCurrentUser() ?: return@setItems
                if (membershipRepository.rechargeWallet(user.id, cents[index])) {
                    Toast.makeText(this, "充值成功，已可用于本订单支付", Toast.LENGTH_SHORT).show()
                    refreshBatch()
                }
            }.show()
    }

    private fun openSuccess() {
        if (leaving || !resumed) return
        leaving = true
        timer?.cancel()
        startActivity(Intent(this, PaymentSuccessActivity::class.java).apply {
            putExtra("orderId", orders.first().id)
            putExtra("paymentBatchId", batchId)
        })
        finish()
    }

    private fun openTrips() {
        leaving = true
        timer?.cancel()
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("selectedTab", 1)
        })
        finish()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
    override fun onDestroy() { timer?.cancel(); super.onDestroy() }

    private fun amountCents(value: Double): Long = (value * 100.0).toLong()
    private fun money(value: Double) = String.format(Locale.CHINA, "%.2f", value)
    private fun dateTime(millis: Long) =
        SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(Date(millis))
}
