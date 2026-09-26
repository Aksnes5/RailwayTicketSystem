package com.railway.ticketsystem.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 候补购票自动兑现引擎 (Waitlist Fulfillment Engine)
 * 当有用户退票、取消订单或余票释放时，后台自动扫描待兑现的候补订单并完成出票，触发真实系统通知。
 */
object WaitlistFulfillmentEngine {

    fun onSeatReleased(
        context: Context,
        trainNumber: String,
        departureDate: String,
        seatType: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val waitlistRepo = WaitlistRepository(appContext)
                val orderRepo = OrderRepository(appContext)
                val candidates = waitlistRepo.getPendingRequestsFor(trainNumber, departureDate, seatType)
                if (candidates.isNotEmpty()) {
                    val candidate = candidates.first()
                    android.util.Log.d("WaitlistEngine", "扫描到待兑现候补订单: ${candidate.id}, 车次: $trainNumber, 座位: $seatType")
                    val fulfilled = waitlistRepo.fulfillDirectly(candidate, orderRepo)
                    if (fulfilled != null) {
                        android.util.Log.d("WaitlistEngine", "候补订单 ${candidate.id} 自动兑现成功！")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("WaitlistEngine", "自动兑现异常: ${e.message}")
            }
        }
    }
}
