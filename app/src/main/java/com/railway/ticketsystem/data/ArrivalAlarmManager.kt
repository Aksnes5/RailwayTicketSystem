package com.railway.ticketsystem.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.railway.ticketsystem.R
import com.railway.ticketsystem.model.Order
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 到站防踏空智能唤醒闹钟管理器
 * 支持提前 15 / 20 / 30 分钟通过系统精准闹钟、通知与强力震动提醒，
 * 避免旅客长途睡眠或夜行车次错过下车站点。
 */
class ArrivalAlarmManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("arrival_alarm_prefs", Context.MODE_PRIVATE)

    companion object {
        const val ACTION_ARRIVAL_ALARM = "com.railway.ticketsystem.ACTION_ARRIVAL_ALARM"
        const val EXTRA_ORDER_ID = "extra_order_id"
        const val EXTRA_TRAIN_NUMBER = "extra_train_number"
        const val EXTRA_STATION_NAME = "extra_station_name"
        const val EXTRA_LEAD_MINUTES = "extra_lead_minutes"
        const val CHANNEL_ID = "arrival_alarm_channel"
    }

    data class AlarmStatus(
        val isEnabled: Boolean,
        val leadMinutes: Int,
        val triggerTimeMillis: Long,
        val formattedTriggerTime: String
    )

    fun getAlarmStatus(orderId: String): AlarmStatus {
        val enabled = prefs.getBoolean("alarm_enabled_$orderId", false)
        val leadMinutes = prefs.getInt("alarm_lead_$orderId", 20)
        val triggerTime = prefs.getLong("alarm_trigger_$orderId", 0L)
        val formatted = if (triggerTime > 0) {
            SimpleDateFormat("HH:mm", Locale.CHINA).format(triggerTime)
        } else ""
        return AlarmStatus(enabled, leadMinutes, triggerTime, formatted)
    }

    /**
     * 开启/设置到站唤醒闹钟
     */
    fun scheduleAlarm(order: Order, leadMinutes: Int = 20): AlarmStatus {
        val arrivalDateStr = "${order.departureDate} ${order.arrivalTime}"
        val arrivalMillis = runCatching {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).parse(arrivalDateStr)?.time
        }.getOrNull() ?: (System.currentTimeMillis() + 3600000L)

        val triggerMillis = arrivalMillis - (leadMinutes * 60 * 1000L)
        val effectiveTrigger = if (triggerMillis > System.currentTimeMillis()) {
            triggerMillis
        } else {
            // 如果已临近到站，5秒后立即震动提醒
            System.currentTimeMillis() + 5000L
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val intent = Intent(context, ArrivalAlarmReceiver::class.java).apply {
            action = ACTION_ARRIVAL_ALARM
            putExtra(EXTRA_ORDER_ID, order.id)
            putExtra(EXTRA_TRAIN_NUMBER, order.trainNumber)
            putExtra(EXTRA_STATION_NAME, order.arrivalStation)
            putExtra(EXTRA_LEAD_MINUTES, leadMinutes)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            order.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager?.let { am ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, effectiveTrigger, pendingIntent)
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, effectiveTrigger, pendingIntent)
            }
        }

        prefs.edit()
            .putBoolean("alarm_enabled_${order.id}", true)
            .putInt("alarm_lead_${order.id}", leadMinutes)
            .putLong("alarm_trigger_${order.id}", effectiveTrigger)
            .apply()

        val formattedTime = SimpleDateFormat("HH:mm", Locale.CHINA).format(effectiveTrigger)
        return AlarmStatus(true, leadMinutes, effectiveTrigger, formattedTime)
    }

    /**
     * 取消到站闹钟
     */
    fun cancelAlarm(orderId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val intent = Intent(context, ArrivalAlarmReceiver::class.java).apply {
            action = ACTION_ARRIVAL_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            orderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager?.cancel(pendingIntent)

        prefs.edit()
            .putBoolean("alarm_enabled_$orderId", false)
            .remove("alarm_trigger_$orderId")
            .apply()
    }
}

/**
 * 接收到站闹钟并弹出强震动通知
 */
class ArrivalAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val trainNumber = intent.getStringExtra(ArrivalAlarmManager.EXTRA_TRAIN_NUMBER) ?: "本次"
        val station = intent.getStringExtra(ArrivalAlarmManager.EXTRA_STATION_NAME) ?: "目的地"
        val leadMinutes = intent.getIntExtra(ArrivalAlarmManager.EXTRA_LEAD_MINUTES, 20)

        // 强力防过站震动 (长短震动组合)
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.let { v ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 500, 200, 500, 200, 800),
                        -1
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(longArrayOf(0, 500, 200, 500, 200, 800), -1)
            }
        }

        // 弹出前台到站通知
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (notificationManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    ArrivalAlarmManager.CHANNEL_ID,
                    "列车到站智能防踏空闹钟",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "到站前强力提醒，防止坐过站"
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(context, ArrivalAlarmManager.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_railway_train_icon)
                .setContentTitle("⏰ 【防坐过站】列车即将到达 $station 站")
                .setContentText("您乘坐的 $trainNumber 次列车预计 $leadMinutes 分钟后到达 $station，请提前整理随身行李，准备下车！")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(trainNumber.hashCode(), notification)
        }
    }
}
