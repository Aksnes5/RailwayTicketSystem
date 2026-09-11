package com.railway.ticketsystem.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.railway.ticketsystem.data.OrderRepository
import com.railway.ticketsystem.data.WaitlistRepository

/** Receives the locally scheduled candidate-result time, including after the app was closed. */
class WaitlistEvaluationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.getStringExtra("waitlist_request_id") ?: return
        val appContext = context.applicationContext
        WaitlistRepository(appContext).processRequest(requestId, OrderRepository(appContext))
    }
}
