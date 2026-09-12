package com.railway.ticketsystem.activity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.railway.ticketsystem.R
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.databinding.ActivityLoginBinding
import com.railway.ticketsystem.model.User
import java.text.SimpleDateFormat
import java.util.*

class LoginActivity : ImmersiveActivity() {
    
    private lateinit var binding: ActivityLoginBinding
    private lateinit var userRepository: UserRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        userRepository = UserRepository(this)
        
        // 检查是否已登录
        if (userRepository.getCurrentUser() != null) {
            startMainActivity()
            return
        }
        
        setupUI()
    }
    
    private fun setupUI() {
        binding.btnLogin.setOnClickListener {
            login()
        }
        
        binding.btnRegister.setOnClickListener {
            showRegisterDialog()
        }
    }
    
    private fun login() {
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        
        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "请输入用户名和密码", Toast.LENGTH_SHORT).show()
            return
        }
        
        val user = userRepository.loginUser(username, password)
        if (user != null) {
            Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show()
            startMainActivity()
        } else {
            Toast.makeText(this, "用户名或密码错误", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showRegisterDialog() {
        val dialogBinding = com.railway.ticketsystem.databinding.DialogRegisterBinding.inflate(layoutInflater)
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()
        
        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        dialogBinding.btnConfirmRegister.setOnClickListener {
            val username = dialogBinding.etUsername.text.toString().trim()
            val password = dialogBinding.etPassword.text.toString().trim()
            val realName = dialogBinding.etRealName.text.toString().trim()
            val idCard = dialogBinding.etIdCard.text.toString().trim()
            val phone = dialogBinding.etPhone.text.toString().trim()
            val email = dialogBinding.etEmail.text.toString().trim()
            
            if (username.isEmpty() || password.isEmpty() || realName.isEmpty() || idCard.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (password.length < 6) {
                Toast.makeText(this, "密码至少6位", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val userId = "user_${System.currentTimeMillis()}"
            val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            
            val user = User(
                id = userId,
                username = username,
                password = password,
                realName = realName,
                idCard = idCard,
                phone = phone,
                email = email,
                createTime = currentTime
            )
            
            if (userRepository.registerUser(user)) {
                Toast.makeText(this, "注册成功", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                // 注册成功后自动登录
                startMainActivity()
            } else {
                Toast.makeText(this, "用户名已存在", Toast.LENGTH_SHORT).show()
            }
        }
        
        dialog.show()
    }
    
    private fun startMainActivity() {
        val intent = Intent(this, com.railway.ticketsystem.MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
