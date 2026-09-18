package com.railway.ticketsystem

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import com.railway.ticketsystem.activity.AccessibleActivity
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.railway.ticketsystem.activity.LoginActivity
import com.railway.ticketsystem.activity.TestRoutesActivity
import com.railway.ticketsystem.data.LocalNotifications
import com.railway.ticketsystem.data.NotificationPermissionRequester
import com.railway.ticketsystem.data.OrderRepository
import com.railway.ticketsystem.data.PaymentLifecycle
import com.railway.ticketsystem.data.TravelReminderScheduler
import com.railway.ticketsystem.data.TripLifecycle
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.data.AccessibilityPreferences
import com.railway.ticketsystem.data.WaitlistRepository
import com.railway.ticketsystem.databinding.ActivityMainBinding
import com.railway.ticketsystem.databinding.DialogBookingBinding
import com.railway.ticketsystem.fragment.ProfileFragment
import com.railway.ticketsystem.fragment.ProfileFragmentWithAvatar
import com.railway.ticketsystem.fragment.ProfileFragmentWithSafeSave
import com.railway.ticketsystem.fragment.MembershipCenterFragment
import com.railway.ticketsystem.fragment.SafeProfileFragment
import com.railway.ticketsystem.fragment.SimpleProfileFragmentFixed
import com.railway.ticketsystem.fragment.TestProfileFragment
import com.railway.ticketsystem.fragment.TicketsFragment
import com.railway.ticketsystem.fragment.TripsFragment
import com.railway.ticketsystem.model.Ticket
import com.railway.ticketsystem.model.Train
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AccessibleActivity(), TicketsFragment.OnTicketBookListener {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var userRepository: UserRepository
    private lateinit var ticketsFragment: TicketsFragment
    private lateinit var tripsFragment: TripsFragment
    private lateinit var membershipFragment: MembershipCenterFragment
    private lateinit var profileFragment: ProfileFragmentWithSafeSave
    private var currentFragment: Fragment? = null
    private var statusBarInset = 0
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            setupImmersiveSystemBars()
            
            userRepository = UserRepository(this)
            
            // 检查是否已登录
            val currentUser = userRepository.getCurrentUser()
            if (currentUser == null) {
                Toast.makeText(this, "未登录，跳转到登录页面", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                return
            }

            recoverNotificationWork()
            NotificationPermissionRequester.requestOnFirstUse(this)
            
            setupUI()
            setupFragments()
            setupBottomNavigation()
            
            // 处理Intent参数，跳转到指定Tab
            handleIntentExtra()
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "应用启动失败: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun recoverNotificationWork() {
        runCatching {
            val appContext = applicationContext
            LocalNotifications.createChannels(appContext)
            PaymentLifecycle(appContext).recover()
            TravelReminderScheduler.recover(appContext)
            WaitlistRepository(appContext).recover(OrderRepository(appContext))
            TripLifecycle(appContext).archiveArrivedTrips()
        }.onFailure { error ->
            android.util.Log.e("MainActivity", "恢复支付、候补与行程提醒失败", error)
        }
    }

    private fun setupUI() {
        try {
            // 暂时跳过ActionBar设置，直接返回
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "设置UI失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun setupFragments() {
        try {
            ticketsFragment = TicketsFragment()
            
            switchFragment(ticketsFragment)
                
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "初始化页面失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            try {
                when (item.itemId) {
                    R.id.nav_tickets -> {
                        if (::ticketsFragment.isInitialized) {
                            switchFragment(ticketsFragment)
                        } else {
                            ticketsFragment = TicketsFragment()
                            switchFragment(ticketsFragment)
                        }
                        true
                    }
                    R.id.nav_trips -> {
                        if (::tripsFragment.isInitialized) {
                            switchFragment(tripsFragment)
                        } else {
                            tripsFragment = TripsFragment()
                            switchFragment(tripsFragment)
                        }
                        true
                    }
                    R.id.nav_membership -> {
                        if (::membershipFragment.isInitialized) {
                            switchFragment(membershipFragment)
                        } else {
                            membershipFragment = MembershipCenterFragment()
                            switchFragment(membershipFragment)
                        }
                        true
                    }
                    R.id.nav_profile -> {
                        try {
                            if (!::profileFragment.isInitialized) {
                                profileFragment = ProfileFragmentWithSafeSave()
                            }
                            switchFragment(profileFragment)
                            true
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(this, "打开我的页面失败: ${e.message}", Toast.LENGTH_LONG).show()
                            false
                        }
                    }
                    else -> false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "切换页面失败: ${e.message}", Toast.LENGTH_LONG).show()
                false
            }
        }
    }
    
    private fun switchFragment(fragment: Fragment) {
        try {
            if (currentFragment === fragment) return
            supportFragmentManager.beginTransaction().apply {
                setCustomAnimations(R.anim.fragment_liquid_enter, R.anim.fragment_liquid_exit)
                currentFragment?.takeIf { it.isAdded }?.let(::hide)
                if (fragment.isAdded) {
                    show(fragment)
                } else {
                    add(R.id.fragment_container, fragment)
                }
                commitNow()
            }
            currentFragment = fragment
            updateStatusBarFor(fragment)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "切换Fragment失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupImmersiveSystemBars() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        AccessibilityPreferences.applySystemBarAppearance(this)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navigationInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            binding.bottomNavigation.updatePadding(bottom = navigationInset)
            applyContentInsets()
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun applyContentInsets() {
        // 购票页的插画从状态栏下方延展；其余白色页面避开状态栏内容区。
        binding.fragmentContainer.updatePadding(top = if (currentFragment is TicketsFragment) 0 else statusBarInset)
    }

    private fun updateStatusBarFor(fragment: Fragment) {
        window.statusBarColor = Color.TRANSPARENT
        AccessibilityPreferences.applySystemBarAppearance(this)
        applyContentInsets()
    }
    
    override fun onTicketBook(train: Train) {
        showBookingDialog(train)
    }
    
    private fun showBookingDialog(train: Train) {
        val dialogBinding = DialogBookingBinding.inflate(LayoutInflater.from(this))
        
        // 设置车票信息
        dialogBinding.tvDialogTrainNumber.text = train.number
        dialogBinding.tvDialogRoute.text = "${train.departureStation} → ${train.arrivalStation}"
        dialogBinding.tvDialogDepartureTime.text = train.departureTime
        dialogBinding.tvDialogArrivalTime.text = train.arrivalTime
        dialogBinding.tvDialogPrice.text = "¥${train.price.toInt()}"
        
        // 生成座位号
        val seatNumber = generateSeatNumber()
        dialogBinding.tvDialogSeatNumber.text = seatNumber
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()
        
        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        dialogBinding.btnConfirmBooking.setOnClickListener {
            val passengerName = dialogBinding.etPassengerName.text.toString().trim()
            
            if (passengerName.isEmpty()) {
                Toast.makeText(this, getString(R.string.enter_passenger_name), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // 生成车厢号和座位号
            val carNumber = (1..16).random()
            val generatedSeatNumber = generateSeatNumber()
            
            // 创建车票记录
            val ticketId = "${train.number}_${System.currentTimeMillis()}"
            val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            
            val ticket = Ticket(
                id = ticketId,
                trainNumber = train.number,
                departureStation = train.departureStation,
                arrivalStation = train.arrivalStation,
                departureTime = train.departureTime,
                arrivalTime = train.arrivalTime,
                price = train.price,
                seatNumber = generatedSeatNumber,
                carNumber = carNumber.toString(),
                passengerName = passengerName,
                bookingTime = currentTime
            )
            
            // 保存车票到本地存储
            val ticketRepository = com.railway.ticketsystem.data.TicketRepository(this)
            ticketRepository.saveTicket(ticket)
            
            // 更新用户积分
            try {
                val userRepository = UserRepository(this)
                val currentUser = userRepository.getCurrentUser()
                if (currentUser != null) {
                    val pointsEarned = (train.price * 10).toInt()
                    val oldPoints = currentUser.points
                    val updatedUser = currentUser.copy(points = currentUser.points + pointsEarned)
                    userRepository.updateUser(updatedUser)
                    
                    // 调试日志
                    android.util.Log.d("MainActivity", "积分更新: 旧积分=$oldPoints, 获得积分=$pointsEarned, 新积分=${updatedUser.points}")
                    
                    // 验证更新是否成功
                    val verifyUser = userRepository.getCurrentUser()
                    android.util.Log.d("MainActivity", "验证积分: 当前用户积分=${verifyUser?.points}")
                    
                    Toast.makeText(this, "购票成功！获得${pointsEarned}积分", Toast.LENGTH_LONG).show()
                } else {
                    android.util.Log.w("MainActivity", "用户未登录，无法更新积分")
                    Toast.makeText(this, getString(R.string.booking_success), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                android.util.Log.e("MainActivity", "积分更新失败", e)
                Toast.makeText(this, getString(R.string.booking_success), Toast.LENGTH_SHORT).show()
            }
            
            dialog.dismiss()
            
            if (::tripsFragment.isInitialized) tripsFragment.refreshTrips()
        }
        
        dialog.show()
    }
    
    private fun generateSeatNumber(): String {
        val seatLetter = listOf("A", "B", "C", "D", "F").random()
        val seatNumber = (1..20).random()
        return "${String.format("%02d", seatNumber)}$seatLetter"
    }
    
    private fun handleIntentExtra() {
        val selectedTab = intent.getIntExtra("selectedTab", -1)
        if (selectedTab != -1) {
            when (selectedTab) {
                0 -> {
                    // 跳转到购票页面
                    binding.bottomNavigation.selectedItemId = R.id.nav_tickets
                }
                1 -> {
                    // 跳转到行程页面
                    binding.bottomNavigation.selectedItemId = R.id.nav_trips
                    if (::tripsFragment.isInitialized) {
                        switchFragment(tripsFragment)
                    } else {
                        tripsFragment = TripsFragment()
                        switchFragment(tripsFragment)
                    }
                }
                2 -> {
                    // 跳转到个人资料页面
                    binding.bottomNavigation.selectedItemId = R.id.nav_profile
                    if (!::profileFragment.isInitialized) {
                        profileFragment = ProfileFragmentWithSafeSave()
                    }
                    switchFragment(profileFragment)
                }
            }
        }
    }
    
}
