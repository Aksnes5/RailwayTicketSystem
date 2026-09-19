package com.railway.ticketsystem.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.railway.ticketsystem.data.ArrivalReminderScheduler
import com.railway.ticketsystem.data.TravelReminderScheduler

class TravelReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action?.startsWith("arrival:") == true) {
            val orderId = intent.getStringExtra(ArrivalReminderScheduler.EXTRA_ORDER_ID) ?: return
            val userId = intent.getStringExtra(ArrivalReminderScheduler.EXTRA_USER_ID) ?: return
            ArrivalReminderScheduler.deliver(context.applicationContext, orderId, userId)
            return
        }
        val orderId = intent.getStringExtra(TravelReminderScheduler.EXTRA_ORDER_ID) ?: return
        val userId = intent.getStringExtra(TravelReminderScheduler.EXTRA_USER_ID) ?: return
        // Absent only for alarms scheduled by an older build; those are simply ignored.
        val offset = intent.getLongExtra(TravelReminderScheduler.EXTRA_OFFSET, -1L)
        if (offset <= 0L) return
        TravelReminderScheduler.deliver(context.applicationContext, orderId, userId, offset)
    }
}
