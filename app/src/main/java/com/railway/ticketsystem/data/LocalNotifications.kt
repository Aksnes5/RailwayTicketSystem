package com.railway.ticketsystem.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.railway.ticketsystem.R
import com.railway.ticketsystem.activity.MessageCenterActivity
import com.railway.ticketsystem.model.AppMessage

/** Android notification delivery; the inbox is persisted by MessageRepository first. */
object LocalNotifications {
    const val EXTRA_MESSAGE_ID = "message_id"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        MessageRepository.CATEGORIES.forEach { category ->
            manager.createNotificationChannel(NotificationChannel(
                channelId(category), "${MessageRepository.categoryLabel(category)}提醒", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "购票系统的${MessageRepository.categoryLabel(category)}动态" })
        }
    }

    fun isSystemEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun post(context: Context, message: AppMessage) {
        // Background business processing can touch other accounts; do not expose their data.
        if (UserRepository(context).getCurrentUser()?.id != message.userId) return
        if (!MessageRepository(context).isReminderEnabled(message.userId, message.category)) return
        createChannels(context)
        if (!isSystemEnabled(context)) return
        val intent = Intent(context, MessageCenterActivity::class.java)
            .setAction("message:${message.id}")
            .putExtra(EXTRA_MESSAGE_ID, message.id)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, message.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channelId(message.category))
            .setSmallIcon(R.drawable.ic_notification_train)
            .setColor(ContextCompat.getColor(context, R.color.railway_blue))
            .setContentTitle(message.title)
            .setContentText(message.content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.content))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(message.id, 0, notification)
        } catch (_: SecurityException) {
            // Permission may be revoked between the check and notify; the inbox remains available.
        }
    }

    fun dismiss(context: Context, messageId: String) {
        NotificationManagerCompat.from(context).cancel(messageId, 0)
    }

    fun dismissAll(context: Context) { NotificationManagerCompat.from(context).cancelAll() }

    private fun channelId(category: String): String = "railway_$category"
}
