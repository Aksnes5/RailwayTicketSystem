package com.railway.ticketsystem.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.railway.ticketsystem.data.PaymentLifecycle

class PaymentTimeoutReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra(PaymentLifecycle.EXTRA_BATCH_ID)?.let { batchId ->
            PaymentLifecycle(context.applicationContext).expirePaymentBatch(batchId)
        }
    }
}
