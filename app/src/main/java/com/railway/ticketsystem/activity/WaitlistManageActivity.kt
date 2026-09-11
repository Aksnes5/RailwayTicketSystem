package com.railway.ticketsystem.activity

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.railway.ticketsystem.adapter.WaitlistAdapter
import com.railway.ticketsystem.data.OrderRepository
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.data.WaitlistRepository
import com.railway.ticketsystem.databinding.ActivityWaitlistManageBinding
import com.railway.ticketsystem.model.WaitlistRequest

class WaitlistManageActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWaitlistManageBinding
    private lateinit var waitlistRepository: WaitlistRepository
    private lateinit var userRepository: UserRepository
    private lateinit var orderRepository: OrderRepository
    private lateinit var adapter: WaitlistAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWaitlistManageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyImmersiveSurfaceBars()
        waitlistRepository = WaitlistRepository(this)
        userRepository = UserRepository(this)
        orderRepository = OrderRepository(this)
        adapter = WaitlistAdapter(::confirmCancel)
        binding.rvWaitlist.layoutManager = LinearLayoutManager(this)
        binding.rvWaitlist.adapter = adapter
        binding.btnBackWaitlist.setOnClickListener { finish() }
    }

    private fun applyImmersiveSurfaceBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, binding.root).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    override fun onResume() {
        super.onResume()
        loadRequests()
    }

    private fun loadRequests() {
        waitlistRepository.processDueRequests(orderRepository)
        val user = userRepository.getCurrentUser()
        if (user == null) {
            binding.tvNoWaitlist.text = "请先登录后查看候补订单"
            binding.tvNoWaitlist.visibility = View.VISIBLE
            binding.rvWaitlist.visibility = View.GONE
            return
        }
        val requests = waitlistRepository.getRequestsByUserId(user.id)
        binding.tvWaitlistSubtitle.text = if (requests.isEmpty()) "暂未提交候补订单" else "共 ${requests.size} 条候补记录"
        adapter.updateRequests(requests)
        binding.tvNoWaitlist.visibility = if (requests.isEmpty()) View.VISIBLE else View.GONE
        binding.rvWaitlist.visibility = if (requests.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun confirmCancel(request: WaitlistRequest) {
        AlertDialog.Builder(this)
            .setTitle("终止候补")
            .setMessage("终止后，该座位释放时将不再为您自动兑现。")
            .setNegativeButton("保留候补", null)
            .setPositiveButton("确认终止") { _, _ ->
                if (waitlistRepository.cancel(request.id, request.userId)) {
                    Toast.makeText(this, "候补已终止", Toast.LENGTH_SHORT).show()
                    loadRequests()
                }
            }
            .show()
    }
}
