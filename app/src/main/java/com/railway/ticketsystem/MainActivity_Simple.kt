package com.railway.ticketsystem

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.railway.ticketsystem.activity.LoginActivity
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.databinding.ActivityMainBinding

class MainActivity_Simple : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var userRepository: UserRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        userRepository = UserRepository(this)
        
        // 检查是否已登录
        val currentUser = userRepository.getCurrentUser()
        if (currentUser == null) {
            Toast.makeText(this, "未登录，跳转到登录页面", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        
        
        // 简单的测试界面
        // 标题栏已删除
    }
}




