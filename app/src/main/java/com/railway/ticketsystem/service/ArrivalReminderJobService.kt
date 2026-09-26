package com.railway.ticketsystem.service

import android.app.job.JobParameters
import android.app.job.JobService
import com.railway.ticketsystem.data.ArrivalReminderScheduler

/**
 * 到站提醒系统级 JobService 双保险服务
 * 当系统处于 Doze 省电模式或进程被收回时，由系统底层 JobScheduler 唤醒并执行到站提醒
 */
class ArrivalReminderJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        val orderId = params?.extras?.getString(ArrivalReminderScheduler.EXTRA_ORDER_ID)
        val userId = params?.extras?.getString(ArrivalReminderScheduler.EXTRA_USER_ID)
        if (!orderId.isNullOrEmpty() && !userId.isNullOrEmpty()) {
            ArrivalReminderScheduler.deliver(applicationContext, orderId, userId)
        }
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return false
    }
}
