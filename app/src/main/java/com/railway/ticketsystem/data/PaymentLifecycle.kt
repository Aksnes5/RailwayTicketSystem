package com.railway.ticketsystem.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.railway.ticketsystem.model.Order
import com.railway.ticketsystem.receiver.PaymentTimeoutReceiver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Owns the persistent 15-minute checkout hold and its retry-safe side effects. */
class PaymentLifecycle(context: Context) {
    private val appContext = context.applicationContext
    private val orders = OrderRepository(appContext)
    private val inventory = SeatInventoryRepository(appContext)
    private val messages = MessageRepository(appContext)
    private val users = UserRepository(appContext)

    companion object {
        const val PAYMENT_WINDOW_MILLIS = 15L * 60L * 1_000L
        const val EXTRA_BATCH_ID = "payment_batch_id"

        fun formatRemaining(deadline: Long, now: Long = System.currentTimeMillis()): String {
            val remaining = (deadline - now).coerceAtLeast(0L)
            return "%02d:%02d".format(Locale.CHINA, remaining / 60_000L, (remaining / 1_000L) % 60L)
        }
    }

    fun registerPendingBatch(pendingOrders: List<Order>) {
        val first = pendingOrders.firstOrNull() ?: return
        val batchId = first.paymentBatchId ?: return
        scheduleTimeout(batchId, first.paymentDeadlineMillis)
        messages.add(
            first.userId,
            MessageRepository.PAYMENT,
            "待支付订单已生成",
            "已为 ${pendingOrders.size} 张车票保留座位，请在 15 分钟内完成支付。",
            first.id,
            eventKey = "payment_created:$batchId"
        )
    }

    fun getPendingBatch(batchId: String, userId: String): List<Order> {
        processExpiredPayments()
        return orders.getOrdersByPaymentBatchId(batchId, userId).filter { it.status == "待支付" }
    }

    fun completePayment(batchId: String, userId: String): List<Order>? {
        processExpiredPayments()
        val paid = orders.payPendingPaymentBatch(batchId, userId, nowText()) ?: return null
        cancelTimeout(batchId)
        recoverPendingEffects()
        return paid
    }

    fun cancelPayment(batchId: String, userId: String, timedOut: Boolean = false): List<Order>? {
        val reason = if (timedOut) "支付超时" else "用户取消"
        val cancelled = orders.cancelPendingPaymentBatch(batchId, userId, reason) ?: return null
        cancelTimeout(batchId)
        recoverPendingEffects()
        return cancelled
    }

    fun expirePaymentBatch(batchId: String) {
        processExpiredPayments()
        orders.getOrdersByPaymentBatchId(batchId).firstOrNull { it.status == "待支付" }?.let {
            scheduleTimeout(batchId, it.paymentDeadlineMillis)
        }
    }

    fun processExpiredPayments() {
        orders.expirePendingPayments()
        recoverPendingEffects()
    }

    /** Called after app launch and reboot: overdue orders remain cancelled and future timers are rebuilt. */
    fun recover() {
        processExpiredPayments()
        orders.getAllOrders()
            .filter { it.status == "待支付" && !it.paymentBatchId.isNullOrBlank() }
            .groupBy { it.paymentBatchId!! }
            .forEach { (batchId, batch) -> scheduleTimeout(batchId, batch.minOf { it.paymentDeadlineMillis }) }
    }

    private fun recoverPendingEffects() {
        val stored = orders.getAllOrders()
        stored.filter { it.status == "已取消" && it.inventoryReleasePending }.forEach { cancelled ->
            if (inventory.releaseSeat(cancelled)) orders.acknowledgeInventoryRelease(cancelled.id)
        }

        stored.filter {
            it.status == "已取消" && it.payTime.isNullOrBlank() && !it.paymentBatchId.isNullOrBlank()
        }.groupBy { it.userId to it.paymentBatchId!! }.forEach { (_, batch) ->
            val first = batch.first()
            val timedOut = first.cancelReason == "支付超时"
            messages.add(
                first.userId,
                MessageRepository.PAYMENT,
                if (timedOut) "支付超时，订单已取消" else "待支付订单已取消",
                if (timedOut) "15 分钟内未完成支付，订单已自动取消，保留座位已释放。" else "订单已取消，保留座位已释放。",
                first.id,
                eventKey = "payment_cancelled:${first.paymentBatchId}"
            )
        }

        stored.filter {
            it.status == "已支付" && it.paymentEffectsPending && !it.paymentBatchId.isNullOrBlank()
        }.groupBy { it.userId to it.paymentBatchId!! }.forEach { (_, batch) ->
            val first = batch.first()
            val batchId = first.paymentBatchId!!
            val amount = batch.sumOf { it.finalPrice }
            users.adjustPointsOnce(first.userId, PointsPolicy.fromAmount(amount), "payment:$batchId")
            val itinerary = batch.joinToString("；") {
                "${it.trainNumber} ${it.departureDate} ${it.departureTime} ${it.departureStation}→${it.arrivalStation}"
            }
            messages.add(
                first.userId,
                MessageRepository.PAYMENT,
                "购票成功",
                "${batch.size} 张车票已支付成功，共 ¥${amount.toInt()}。${itinerary}；电子客票凭证已生成。",
                first.id,
                eventKey = "payment_paid:$batchId"
            )
            batch.forEach { TravelReminderScheduler.schedule(appContext, it) }
            orders.acknowledgePaymentEffects(batchId, first.userId)
        }
    }

    private fun scheduleTimeout(batchId: String, deadline: Long) {
        if (deadline <= System.currentTimeMillis()) return
        val manager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = pendingIntent(batchId, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, pending)
    }

    private fun cancelTimeout(batchId: String) {
        val manager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = pendingIntent(batchId, PendingIntent.FLAG_NO_CREATE) ?: return
        manager.cancel(pending)
        pending.cancel()
    }

    private fun pendingIntent(batchId: String, flag: Int): PendingIntent? {
        val intent = Intent(appContext, PaymentTimeoutReceiver::class.java)
            .setAction("payment_timeout:$batchId")
            .putExtra(EXTRA_BATCH_ID, batchId)
        return PendingIntent.getBroadcast(appContext, 0, intent, flag or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun nowText(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())
}
