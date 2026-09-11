package com.railway.ticketsystem.activity

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import com.railway.ticketsystem.data.ExactAlarmPermissionRequester
import com.railway.ticketsystem.data.LocalNotifications
import com.railway.ticketsystem.data.MessageRepository
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.databinding.ActivityNotificationSettingsBinding

class NotificationSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNotificationSettingsBinding
    private lateinit var messages: MessageRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "行程提醒设置"
        messages = MessageRepository(this)
        binding.switchPaymentReminder.isChecked = messages.isReminderEnabled(MessageRepository.PAYMENT)
        binding.switchWaitlistReminder.isChecked = messages.isReminderEnabled(MessageRepository.WAITLIST)
        binding.switchTravelReminder.isChecked = messages.isReminderEnabled(MessageRepository.TRAVEL)
        binding.switchTicketReminder.isChecked = messages.isReminderEnabled(MessageRepository.TICKET)
        binding.switchPaymentReminder.setOnCheckedChangeListener { _, checked -> saveToggle(MessageRepository.PAYMENT, checked) }
        binding.switchWaitlistReminder.setOnCheckedChangeListener { _, checked -> saveToggle(MessageRepository.WAITLIST, checked) }
        binding.switchTravelReminder.setOnCheckedChangeListener { _, checked -> saveToggle(MessageRepository.TRAVEL, checked) }
        binding.switchTicketReminder.setOnCheckedChangeListener { _, checked -> saveToggle(MessageRepository.TICKET, checked) }
        binding.btnTestNotification.setOnClickListener { sendTestNotification() }
    }

    private fun saveToggle(category: String, enabled: Boolean) {
        messages.setReminderEnabled(category, enabled)
        if (!enabled) return
        requestNotificationPermissionIfNeeded()
        // Turning the departure reminder on is the natural moment to ask for exact alarms;
        // asking at app launch would be far more intrusive.
        if (category == MessageRepository.TRAVEL) ExactAlarmPermissionRequester.requestIfNeeded(this)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) return
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_NOTIFICATION_PERMISSION
        )
    }

    private fun sendTestNotification() {
        requestNotificationPermissionIfNeeded()
        if (!LocalNotifications.isSystemEnabled(this)) {
            android.widget.Toast.makeText(this, "请先允许系统通知，再点击发送测试通知", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        if (!messages.isReminderEnabled(MessageRepository.TRAVEL)) {
            android.widget.Toast.makeText(this, "请先打开“发车前 3 小时和 30 分钟提醒”", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val userId = UserRepository(this).getCurrentUser()?.id
        if (userId.isNullOrBlank()) return
        MessageRepository(this).add(
            userId,
            MessageRepository.TRAVEL,
            "通知已开启",
            "系统通知可正常发送。购票成功、支付超时、候补结果和发车前提醒会显示在这里。",
            eventKey = "notification_test:${System.currentTimeMillis()}"
        )
        android.widget.Toast.makeText(this, "测试通知已发送", android.widget.Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val REQUEST_NOTIFICATION_PERMISSION = 301
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
