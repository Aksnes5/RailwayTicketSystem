package com.railway.ticketsystem.activity

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.view.WindowCompat
import com.railway.ticketsystem.MainActivity
import com.railway.ticketsystem.R
import com.railway.ticketsystem.data.RailwayData
import com.railway.ticketsystem.data.RailwayGraphManager
import com.railway.ticketsystem.data.RealRailwayRoutes
import com.railway.ticketsystem.data.TrainGenerator
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.data.AsyncDataLoader
import com.railway.ticketsystem.data.AccessibilityPreferences
import com.railway.ticketsystem.databinding.ActivityLoadingBinding
import kotlinx.coroutines.*

/**
 * 启动加载Activity
 * 负责在应用启动时预加载所有数据，显示加载进度
 */
class LoadingActivity : AccessibleActivity() {
    
    private lateinit var binding: ActivityLoadingBinding
    private var loadingJob: Job? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        AccessibilityPreferences.applySystemBarAppearance(this)
        binding = ActivityLoadingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 隐藏ActionBar
        supportActionBar?.hide()
        
        // 开始数据加载
        startDataLoading()
    }
    
    private fun startDataLoading() {
        loadingJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                updateLoadingStatus("正在初始化数据...", 0)
                
                // 步骤1: 检查登录状态
                updateLoadingStatus("检查用户登录状态...", 5)
                val userRepository = UserRepository(this@LoadingActivity)
                val currentUser = userRepository.getCurrentUser()
                delay(100) // 减少延迟
                
                if (currentUser == null) {
                    // 未登录，跳转到登录页面
                    updateLoadingStatus("未登录，跳转到登录页面...", 100)
                    delay(300)
                    navigateToLoginActivity()
                    return@launch
                }
                
                // 步骤2: 使用异步数据加载器
                updateLoadingStatus("开始异步加载数据...", 10)
                
                // 使用异步数据加载器
                AsyncDataLoader.initializeAsync(this@LoadingActivity) { status, progress ->
                    updateLoadingStatus(status, progress)
                }
                
                // 步骤3: 预热查询功能（在后台进行）
                updateLoadingStatus("优化查询性能...", 95)
                withContext(Dispatchers.IO) {
                    // 预热一些常用查询
                    RailwayData.searchStations("北京")
                    RailwayData.searchStations("上海")
                    RailwayData.searchStations("广州")
                }
                delay(100)
                
                // 步骤4: 完成加载
                updateLoadingStatus("加载完成", 100)
                delay(300)
                
                // 跳转到主界面
                navigateToMainActivity()
                
            } catch (e: Exception) {
                Log.e("LoadingActivity", "数据加载失败", e)
                updateLoadingStatus("加载失败，请重试", 0)
                
                // 即使加载失败也跳转到主界面，让用户可以使用基本功能
                Handler(Looper.getMainLooper()).postDelayed({
                    navigateToMainActivity()
                }, 2000)
            }
        }
    }
    
    private fun updateLoadingStatus(status: String, progress: Int) {
        runOnUiThread {
            binding.tvLoadingStatus.text = status
            binding.tvProgressPercent.text = "$progress%"
            binding.progressBar.progress = progress
            
            // 更新加载详情
            val detail = when (progress) {
                in 0..10 -> "正在准备数据..."
                in 11..25 -> "加载${RealRailwayRoutes.getAllRoutes().size}条铁路线路..."
                in 26..50 -> "构建${RailwayData.stations.size}个车站的路线图..."
                in 51..75 -> "生成列车时刻表数据..."
                in 76..90 -> "优化查询性能..."
                else -> "准备就绪"
            }
            binding.tvLoadingDetail.text = detail
        }
    }
    
    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    private fun navigateToLoginActivity() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        loadingJob?.cancel()
    }
    
    override fun onBackPressed() {
        // 禁用返回键，防止用户在加载过程中退出
        // 可以显示提示信息
    }
}
