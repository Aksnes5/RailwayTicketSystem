package com.railway.ticketsystem.data

import android.content.Context

/**
 * Archives trips once their scheduled arrival time has passed.
 *
 * Nothing else promotes an order to "已完成", so a finished journey used to stay in the
 * upcoming list forever and never became eligible for an invoice.  Like [PaymentLifecycle]
 * this is a sweep rather than a timer: it runs where the app already catches up on
 * background work, so a missed run simply completes on the next one.
 */
class TripLifecycle(context: Context) {
    private val appContext = context.applicationContext
    private val orders = OrderRepository(appContext)
    private val progress = TripProgressRepository(appContext)
    private val messages = MessageRepository(appContext)

    fun archiveArrivedTrips(now: Long = System.currentTimeMillis()) {
        // Keep an open trip's concierge synchronized at departure time before any eventual
        // arrival archival happens. This sweep is invoked on launch, resume and screen refresh.
        orders.getAllOrders().filter { it.status == "已支付" }.forEach { progress.synchronizeWithTimetable(it, now) }
        orders.completeArrivedOrders(now).forEach { order ->
            // Clear the departure alarms and their pending notifications before recording the
            // completion, so the new inbox entry is not swept away with the old reminders.
            TravelReminderScheduler.cancel(appContext, order)
            progress.advance(order, TripProgress.STAGE_ARRIVED)
            messages.add(
                order.userId,
                MessageRepository.TRAVEL,
                "抵达通知 · ${order.trainNumber}",
                "您乘坐的 ${order.trainNumber} 次列车已到达 ${order.arrivalStation}。感谢您的乘坐，祝您旅途愉快。",
                order.id,
                eventKey = "trip_arrived:" + order.id
            )
        }
    }
}
