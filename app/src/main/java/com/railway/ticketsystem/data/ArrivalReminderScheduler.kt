package com.railway.ticketsystem.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.railway.ticketsystem.model.Order
import com.railway.ticketsystem.receiver.TravelReminderReceiver

/** Optional arrival reminder enabled from the carriage-service center. */
object ArrivalReminderScheduler {
    private const val PREFS = "arrival_reminder_preferences"
    private const val OFFSET_MILLIS = 20L * 60L * 1000L
    const val EXTRA_ORDER_ID = "arrival_reminder_order_id"
    const val EXTRA_USER_ID = "arrival_reminder_user_id"

    fun isEnabled(context: Context, order: Order): Boolean = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(key(order), false)

    fun setEnabled(context: Context, order: Order, enabled: Boolean): Boolean {
        val saved = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(key(order), enabled).commit()
        if (saved) {
            if (enabled) schedule(context, order) else cancel(context, order)
        }
        return saved
    }

    fun schedule(context: Context, order: Order) {
        if (!isEnabled(context, order) || order.status != "已支付") return
        val arrival = TravelAssistant.arrivalMillis(order) ?: return
        val now = System.currentTimeMillis()
        if (arrival <= now) { cancel(context, order); return }
        val triggerAt = arrival - OFFSET_MILLIS
        if (triggerAt <= now) {
            deliver(context, order.id, order.userId)
            return
        }
        val manager = context.applicationContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = pendingIntent(context, order, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                return
            } catch (_: SecurityException) { /* fall through to inexact scheduling */ }
        }
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
    }

    fun deliver(context: Context, orderId: String, userId: String) {
        val order = OrderRepository(context).getOrderById(orderId, userId) ?: return
        if (!isEnabled(context, order) || order.status != "已支付") return
        val arrival = TravelAssistant.arrivalMillis(order) ?: return
        val now = System.currentTimeMillis()
        if (arrival <= now) { cancel(context, order); return }
        if (arrival - OFFSET_MILLIS > now) { schedule(context, order); return }
        MessageRepository(context).add(
            userId, MessageRepository.TRAVEL,
            "即将到达 · ${order.trainNumber}",
            "预计 ${order.arrivalTime} 到达${order.arrivalStation}，请提前整理随身物品，留意车内广播。",
            order.id,
            eventKey = "arrival_reminder:${order.id}:$arrival"
        )
    }

    fun recover(context: Context) {
        OrderRepository(context).getAllOrders().filter { it.status == "已支付" && isEnabled(context, it) }
            .forEach { schedule(context, it) }
    }

    fun cancel(context: Context, order: Order) {
        val manager = context.applicationContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        pendingIntent(context, order, PendingIntent.FLAG_NO_CREATE)?.let {
            manager.cancel(it)
            it.cancel()
        }
    }

    private fun pendingIntent(context: Context, order: Order, flag: Int): PendingIntent? = PendingIntent.getBroadcast(
        context.applicationContext,
        0,
        Intent(context.applicationContext, TravelReminderReceiver::class.java)
            .setAction("arrival:${order.userId}:${order.id}")
            .putExtra(EXTRA_ORDER_ID, order.id)
            .putExtra(EXTRA_USER_ID, order.userId),
        flag or PendingIntent.FLAG_IMMUTABLE
    )

    private fun key(order: Order) = "enabled_${order.userId}_${order.id}"
}
