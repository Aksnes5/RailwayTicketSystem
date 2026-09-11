package com.railway.ticketsystem.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.railway.ticketsystem.data.LocalNotifications
import com.railway.ticketsystem.data.OrderRepository
import com.railway.ticketsystem.data.PaymentLifecycle
import com.railway.ticketsystem.data.TravelReminderScheduler
import com.railway.ticketsystem.data.TripLifecycle
import com.railway.ticketsystem.data.WaitlistRepository

/** Restores persisted deadlines and reminder alarms after boot, package updates, or time changes. */
class NotificationRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                val appContext = context.applicationContext
                LocalNotifications.createChannels(appContext)
                PaymentLifecycle(appContext).recover()
                TravelReminderScheduler.recover(appContext)
                WaitlistRepository(appContext).recover(OrderRepository(appContext))
                TripLifecycle(appContext).archiveArrivedTrips()
            } finally {
                pending.finish()
            }
        }.start()
    }
}
