package com.railway.ticketsystem.activity

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.railway.ticketsystem.R
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.databinding.ActivityEditProfileBinding
import com.railway.ticketsystem.model.User
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class EditProfileActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityEditProfileBinding
    private lateinit var userRepository: UserRepository
    private var currentUser: User? = null
    private var avatarBitmap: Bitmap? = null
    
    companion object {
        private const val PICK_IMAGE_REQUEST = 1001
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 设置标题栏
        supportActionBar?.setTitle("编辑资料")
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        userRepository = UserRepository(this)
        currentUser = userRepository.getCurrentUser()
        
        setupUI()
        loadUserData()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    
    private fun setupUI() {
        // 设置头像点击事件
        binding.cardAvatar.setOnClickListener {
            showImageSelectionDialog()
        }
        
        // 保存按钮
        binding.btnSaveProfile.setOnClickListener {
            saveProfile()
        }
    }
    
    private fun loadUserData() {
        currentUser?.let { user ->
            binding.etUsername.setText(user.username)
            binding.etRealName.setText(user.realName)
            binding.etIdCard.setText(user.idCard)
            binding.etPhone.setText(user.phone)
            binding.etEmail.setText(user.email)
            
            // 加载头像
            if (user.avatar != null) {
                val bitmap = BitmapFactory.decodeByteArray(user.avatar, 0, user.avatar!!.size)
                binding.ivAvatar.setImageBitmap(bitmap)
                avatarBitmap = bitmap
            }
        }
    }
    
    private fun showImageSelectionDialog() {
        val options = arrayOf("拍照", "从相册选择")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("选择头像")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openCamera()
                    1 -> openGallery()
                }
            }
            .show()
    }
    
    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }
    
    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            try {
                val bitmap = if (data.extras?.get("data") != null) {
                    // 拍照返回的缩略图
                    data.extras?.get("data") as Bitmap
                } else {
                    // 相册选择的图片
                    val selectedImageUri: Uri? = data.data
                    val inputStream = contentResolver.openInputStream(selectedImageUri!!)
                    BitmapFactory.decodeStream(inputStream)
                }
                
                // 压缩图片
                val compressedBitmap = compressBitmap(bitmap)
                binding.ivAvatar.setImageBitmap(compressedBitmap)
                avatarBitmap = compressedBitmap
                
            } catch (e: Exception) {
                Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun compressBitmap(bitmap: Bitmap): Bitmap {
        val maxSize = 200 // 最大尺寸
        val width = bitmap.width
        val height = bitmap.height
        
        val scale = if (width > height) {
            maxSize.toFloat() / width
        } else {
            maxSize.toFloat() / height
        }
        
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    private fun saveProfile() {
        val username = binding.etUsername.text.toString().trim()
        val realName = binding.etRealName.text.toString().trim()
        val idCard = binding.etIdCard.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        
        // 验证用户名
        if (username.isEmpty()) {
            Toast.makeText(this, "请输入用户名", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 验证真实姓名
        if (realName.isEmpty()) {
            Toast.makeText(this, "请输入真实姓名", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 验证身份证号
        if (idCard.isEmpty()) {
            Toast.makeText(this, "请输入身份证号", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!isValidIdCard(idCard)) {
            Toast.makeText(this, "请输入正确的身份证号", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 验证手机号
        if (phone.isEmpty()) {
            Toast.makeText(this, "请输入手机号", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!isValidPhone(phone)) {
            Toast.makeText(this, "请输入正确的手机号", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 验证邮箱
        if (email.isEmpty()) {
            Toast.makeText(this, "请输入邮箱", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!isValidEmail(email)) {
            Toast.makeText(this, "请输入正确的邮箱", Toast.LENGTH_SHORT).show()
            return
        }
        
        currentUser?.let { user ->
            val updatedUser = user.copy(
                username = username,
                realName = realName,
                idCard = idCard,
                phone = phone,
                email = email,
                avatar = avatarBitmap?.let { bitmapToByteArray(it) }
            )
            
            userRepository.updateUser(updatedUser)
            Toast.makeText(this, "资料保存成功", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    private fun isValidPhone(phone: String): Boolean {
        return phone.matches(Regex("^1[3-9]\\d{9}$"))
    }
    
    private fun isValidEmail(email: String): Boolean {
        return email.matches(Regex("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$"))
    }
    
    private fun isValidIdCard(idCard: String): Boolean {
        // 简单的身份证号验证：18位，最后一位可能是X
        return idCard.matches(Regex("^[1-9]\\d{5}(18|19|20)\\d{2}((0[1-9])|(1[0-2]))(([0-2][1-9])|10|20|30|31)\\d{3}[0-9Xx]$"))
    }
    
    private fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }
}
