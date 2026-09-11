package com.railway.ticketsystem.activity

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.railway.ticketsystem.adapter.MessageAdapter
import com.railway.ticketsystem.data.MessageRepository
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.databinding.ActivityMessageCenterBinding
import com.railway.ticketsystem.model.AppMessage

class MessageCenterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMessageCenterBinding
    private lateinit var messageRepository: MessageRepository
    private lateinit var userRepository: UserRepository
    private lateinit var adapter: MessageAdapter
    private var userId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMessageCenterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyImmersiveSurfaceBars()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "消息中心"
        messageRepository = MessageRepository(this)
        userRepository = UserRepository(this)
        userId = userRepository.getCurrentUser()?.id.orEmpty()
        adapter = MessageAdapter(::openMessage)
        binding.rvMessages.layoutManager = LinearLayoutManager(this)
        binding.rvMessages.adapter = adapter
        binding.btnReadAll.setOnClickListener { messageRepository.markAllRead(userId); render() }
        binding.btnMessageSettings.setOnClickListener { startActivity(Intent(this, NotificationSettingsActivity::class.java)) }
    }

    private fun applyImmersiveSurfaceBars() {
        val left = binding.root.paddingLeft
        val top = binding.root.paddingTop
        val right = binding.root.paddingRight
        val bottom = binding.root.paddingBottom
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, binding.root).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(left, top + bars.top, right, bottom + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val messages = if (userId.isBlank()) emptyList() else messageRepository.getMessages(userId)
        val unread = messages.count { !it.isRead }
        binding.tvUnreadSummary.text = if (unread == 0) "全部消息已读" else "有 $unread 条未读消息"
        binding.tvEmptyMessages.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
        binding.rvMessages.visibility = if (messages.isEmpty()) View.GONE else View.VISIBLE
        adapter.submit(messages)
    }

    private fun openMessage(message: AppMessage) {
        messageRepository.markRead(message.id, userId)
        val order = message.relatedOrderId?.let { com.railway.ticketsystem.data.OrderRepository(this).getOrderById(it, userId) }
        if (order != null) {
            startActivity(Intent(this, TripDetailActivity::class.java).putExtra("order", order))
        } else if (!message.relatedWaitlistId.isNullOrBlank()) {
            startActivity(Intent(this, WaitlistManageActivity::class.java))
        } else {
            render()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
