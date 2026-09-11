package com.railway.ticketsystem.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.railway.ticketsystem.model.Order
import com.railway.ticketsystem.receiver.TravelReminderReceiver
import java.text.SimpleDateFormat
import java.util.Locale

/** One durable inbox event per departure offset; system push follows the user's switches. */
object TravelReminderScheduler {
    const val EXTRA_ORDER_ID = "travel_reminder_order_id"
    const val EXTRA_USER_ID = "travel_reminder_user_id"
    const val EXTRA_OFFSET = "travel_reminder_offset"

    private const val HOUR_MILLIS = 60L * 60L * 1000L

    /**
     * Every offset gets its own alarm.  PendingIntent matching ignores extras and only
     * compares action/data/component, so the offset has to live in the action — otherwise
     * the second reminder silently replaces the first one's alarm.  Each offset also needs
     * its own inbox event key, or MessageRepository deduplicates the second one away.
     */
    private val ADVANCE_OFFSETS = listOf(
        3L * HOUR_MILLIS,
        30L * 60L * 1000L
    )

    fun departureMillis(order: Order): Long? = runCatching {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).apply { isLenient = false }
            .parse("${order.departureDate} ${order.departureTime}")?.time
    }.getOrNull()

    fun schedule(context: Context, order: Order) {
        val departure = departureMillis(order)
        val now = System.currentTimeMillis()
        if (order.status != "已支付" || departure == null || departure <= now) {
            cancel(context, order)
            return
        }
        ADVANCE_OFFSETS.forEach { offset -> scheduleOffset(context, order, departure, offset, now) }
    }

    private fun scheduleOffset(context: Context, order: Order, departure: Long, offset: Long, now: Long) {
        val triggerAt = departure - offset
        if (triggerAt <= now) {
            deliver(context, order.id, order.userId, offset)
            return
        }
        val manager = context.applicationContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = pendingIntent(context, order, offset, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        setAlarm(manager, triggerAt, pending)
    }

    /**
     * Exact delivery is preferred so a departure reminder does not drift, but Android 12+
     * makes exact alarms a special permission the user grants in Settings, and it is denied
     * by default for apps targeting API 33+.  Fall back to an inexact alarm instead of
     * dropping the reminder.
     */
    private fun setAlarm(manager: AlarmManager, triggerAt: Long, pending: PendingIntent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                return
            } catch (_: SecurityException) {
                // Revoked between the check and the call; the inexact path below still fires.
            }
        }
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
    }

    /** Rebuild alarms after reboot/time changes and catch up reminders while still before departure. */
    fun recover(context: Context) {
        LocalNotifications.createChannels(context)
        OrderRepository(context).getAllOrders().forEach { schedule(context, it) }
    }

    fun cancel(context: Context, order: Order) {
        val manager = context.applicationContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        ADVANCE_OFFSETS.forEach { offset ->
            pendingIntent(context, order, offset, PendingIntent.FLAG_NO_CREATE)?.let {
                manager.cancel(it)
                it.cancel()
            }
        }
        MessageRepository(context).getMessages(order.userId)
            .filter { it.category == MessageRepository.TRAVEL && it.relatedOrderId == order.id }
            .forEach { LocalNotifications.dismiss(context, it.id) }
    }

    fun deliver(context: Context, orderId: String, userId: String, offset: Long) {
        val order = OrderRepository(context).getOrderById(orderId, userId) ?: return
        if (order.status != "已支付") { cancel(context, order); return }
        val departure = departureMillis(order) ?: return
        val now = System.currentTimeMillis()
        if (departure <= now) { cancel(context, order); return }
        // This offset's alarm fired early; put it back rather than notifying too soon.
        if (departure - offset > now) {
            scheduleOffset(context, order, departure, offset, now)
            return
        }
        MessageRepository(context).add(
            userId, MessageRepository.TRAVEL,
            reminderTitle(order, offset),
            reminderContent(context, order, offset),
            order.id,
            eventKey = "departure:$offset:${order.id}:${order.trainNumber}:$departure"
        )
    }

    private fun reminderTitle(order: Order, offset: Long): String =
        if (offset >= HOUR_MILLIS) "列车发车提醒 · ${order.trainNumber}" else "即将发车 · ${order.trainNumber}"

    private fun reminderContent(context: Context, order: Order, offset: Long): String {
        val lead = if (offset >= HOUR_MILLIS) "距发车约 ${offset / HOUR_MILLIS} 小时" else "即将发车"
        return "$lead：${order.departureDate} ${order.departureTime} 从${order.departureStation}出发，请提前到站。" +
            "座位：${order.seatInfo}。检票口：${TicketTravelUpdates.getGate(context, order)}，请以车站现场信息为准。"
    }

    private fun pendingIntent(context: Context, order: Order, offset: Long, flag: Int): PendingIntent? {
        val intent = Intent(context.applicationContext, TravelReminderReceiver::class.java)
            .setAction("travel:${order.userId}:${order.id}:$offset")
            .putExtra(EXTRA_ORDER_ID, order.id)
            .putExtra(EXTRA_USER_ID, order.userId)
            .putExtra(EXTRA_OFFSET, offset)
        return PendingIntent.getBroadcast(context.applicationContext, 0, intent, flag or PendingIntent.FLAG_IMMUTABLE)
    }
}
