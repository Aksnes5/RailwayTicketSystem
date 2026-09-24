package com.railway.ticketsystem.fragment

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.railway.ticketsystem.R
import com.railway.ticketsystem.activity.EditProfileActivity
import com.railway.ticketsystem.activity.LoginActivity
import com.railway.ticketsystem.activity.MessageCenterActivity
import com.railway.ticketsystem.activity.NotificationSettingsActivity
import com.railway.ticketsystem.activity.PassengerManageActivity
import com.railway.ticketsystem.activity.TravelExpenseCenterActivity
import com.railway.ticketsystem.activity.FamilyAccountActivity
import com.railway.ticketsystem.activity.OfflineTravelPackActivity
import com.railway.ticketsystem.activity.AccessibilitySettingsActivity
import com.railway.ticketsystem.activity.OperationsCenterActivity
import com.railway.ticketsystem.activity.TravelProtectionActivity
import com.railway.ticketsystem.data.MessageRepository
import com.railway.ticketsystem.data.PassengerRepository
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.databinding.FragmentProfileBinding
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ProfileFragmentWithSafeSave : Fragment() {
    
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var userRepository: UserRepository
    private lateinit var passengerRepository: PassengerRepository
    private lateinit var messageRepository: MessageRepository
    
    // 头像选择相关
    private var galleryLauncher: androidx.activity.result.ActivityResultLauncher<String>? = null
    private var cameraLauncher: androidx.activity.result.ActivityResultLauncher<Void?>? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        try {
            _binding = FragmentProfileBinding.inflate(inflater, container, false)
            return binding.root
        } catch (e: Exception) {
            e.printStackTrace()
            // 如果DataBinding失败，返回简单布局
            return createSimpleLayout(inflater, container)
        }
    }
    
    private fun createSimpleLayout(inflater: LayoutInflater, container: ViewGroup?): View {
        val layout = android.widget.LinearLayout(requireContext())
        layout.orientation = android.widget.LinearLayout.VERTICAL
        layout.setPadding(50, 50, 50, 50)
        
        val title = android.widget.TextView(requireContext())
        title.text = "我的页面"
        title.textSize = 24f
        title.gravity = android.view.Gravity.CENTER
        layout.addView(title)
        
        val space1 = android.widget.TextView(requireContext())
        space1.text = "\n"
        layout.addView(space1)
        
        try {
            userRepository = UserRepository(requireContext())
            val currentUser = userRepository.getCurrentUser()
            
            val userInfo = android.widget.TextView(requireContext())
            if (currentUser != null) {
                userInfo.text = "用户名：${currentUser.username}\n真实姓名：${currentUser.realName}\n手机号：${currentUser.phone}"
            } else {
                userInfo.text = "未登录"
            }
            userInfo.textSize = 16f
            layout.addView(userInfo)
            
            val space2 = android.widget.TextView(requireContext())
            space2.text = "\n"
            layout.addView(space2)
            
            val managePassengersBtn = android.widget.Button(requireContext())
            managePassengersBtn.text = "管理乘客信息"
            managePassengersBtn.setOnClickListener {
                try {
                    startActivity(Intent(requireContext(), PassengerManageActivity::class.java))
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(requireContext(), "打开乘客管理失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            layout.addView(managePassengersBtn)
            
            val space3 = android.widget.TextView(requireContext())
            space3.text = "\n"
            layout.addView(space3)
            
            val logoutBtn = android.widget.Button(requireContext())
            logoutBtn.text = "退出登录"
            logoutBtn.setOnClickListener {
                try {
                    userRepository.logout()
                    Toast.makeText(requireContext(), "已退出登录", Toast.LENGTH_SHORT).show()
                    val intent = Intent(requireContext(), LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    activity?.finish()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(requireContext(), "退出登录失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            layout.addView(logoutBtn)
            
        } catch (e: Exception) {
            e.printStackTrace()
            val errorText = android.widget.TextView(requireContext())
            errorText.text = "加载用户信息失败: ${e.message}"
            errorText.textSize = 16f
            errorText.setTextColor(android.graphics.Color.RED)
            layout.addView(errorText)
        }
        
        return layout
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            userRepository = UserRepository(requireContext())
            passengerRepository = PassengerRepository(requireContext())
            messageRepository = MessageRepository(requireContext())
            
            // 初始化ActivityResultLauncher
            initLaunchers()
            
            setupUI()
            loadUserInfo()
            loadPassengerCount()
            loadMessageSummary()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun initLaunchers() {
        try {
            galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                try {
                    uri?.let { 
                        Log.d("ProfileFragment", "选择了图片: $uri")
                        loadAndSaveAvatar(it)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.e("ProfileFragment", "处理图片失败", e)
                    Toast.makeText(requireContext(), "处理图片失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            
            cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
                try {
                    bitmap?.let { 
                        Log.d("ProfileFragment", "拍摄了照片")
                        saveAvatarFromCamera(it)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.e("ProfileFragment", "处理照片失败", e)
                    Toast.makeText(requireContext(), "处理照片失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "初始化头像功能失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupUI() {
        try {
            binding.btnLogout.setOnClickListener {
                logout()
            }
            
            binding.btnManagePassengers.setOnClickListener {
                startActivity(Intent(requireContext(), PassengerManageActivity::class.java))
            }
            
            binding.btnEditProfile.setOnClickListener {
                startActivity(Intent(requireContext(), EditProfileActivity::class.java))
            }

            binding.btnMessageCenter.setOnClickListener {
                startActivity(Intent(requireContext(), MessageCenterActivity::class.java))
            }

            binding.btnNotificationSettings.setOnClickListener {
                startActivity(Intent(requireContext(), NotificationSettingsActivity::class.java))
            }

            binding.btnExpenseCenter.setOnClickListener {
                startActivity(Intent(requireContext(), TravelExpenseCenterActivity::class.java))
            }

            binding.btnFamilyAccount.setOnClickListener {
                startActivity(Intent(requireContext(), FamilyAccountActivity::class.java))
            }

            binding.btnOfflineTravelPack.setOnClickListener {
                startActivity(Intent(requireContext(), OfflineTravelPackActivity::class.java))
            }

            binding.btnAccessibilitySettings.setOnClickListener {
                startActivity(Intent(requireContext(), AccessibilitySettingsActivity::class.java))
            }

            binding.btnOperationsCenter.setOnClickListener {
                startActivity(Intent(requireContext(), OperationsCenterActivity::class.java))
            }

            binding.btnTravelProtection.setOnClickListener {
                startActivity(Intent(requireContext(), TravelProtectionActivity::class.java))
            }
            // 头像选择功能
            binding.cardAvatar.setOnClickListener {
                showAvatarSelectionDialog()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun loadUserInfo() {
        try {
            val currentUser = userRepository.getCurrentUser()
            if (currentUser != null) {
                binding.tvUsername.text = currentUser.username
                binding.tvUserEmail.text = currentUser.email
                binding.tvUserInfo.text = currentUser.realName
                binding.tvUserPhone.text = maskPhone(currentUser.phone)
                binding.tvRegisterTime.text = currentUser.createTime.substring(0, 10)
                
                // 加载头像
                loadAvatar(currentUser.avatarPath)
            } else {
                Log.w("ProfileFragmentWithSafeSave", "用户未登录")
                binding.tvUsername.text = "未登录"
                binding.tvUserEmail.text = "请先登录"
                binding.tvUserInfo.text = "未登录"
                binding.tvUserPhone.text = "未登录"
                binding.tvRegisterTime.text = "未登录"
                binding.ivAvatar.setImageResource(R.drawable.ic_default_avatar)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("ProfileFragmentWithSafeSave", "加载用户信息失败", e)
        }
    }
    
    private fun loadPassengerCount() {
        try {
            val currentUser = userRepository.getCurrentUser()
            if (currentUser != null) {
                val passengers = passengerRepository.getPassengersByUserId(currentUser.id)
                binding.tvPassengerCount.text = "${passengers.size}位乘客"
            } else {
                binding.tvPassengerCount.text = "0位乘客"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            binding.tvPassengerCount.text = "0位乘客"
        }
    }

    private fun loadMessageSummary() {
        try {
            val currentUser = userRepository.getCurrentUser()
            val unreadCount = currentUser?.let { messageRepository.unreadCount(it.id) } ?: 0
            binding.tvMessageSummary.text = if (unreadCount > 0) {
                unreadCount.toString() + " 条未读"
            } else {
                "全部已读"
            }
        } catch (e: Exception) {
            Log.e("ProfileFragmentWithSafeSave", "加载消息未读数失败", e)
            binding.tvMessageSummary.text = "消息中心"
        }
    }
    
    private fun maskPhone(phone: String): String {
        return if (phone.length >= 11) {
            "${phone.substring(0, 3)}****${phone.substring(7)}"
        } else {
            phone
        }
    }
    
    private fun logout() {
        try {
            userRepository.logout()
            Toast.makeText(requireContext(), "已退出登录", Toast.LENGTH_SHORT).show()
            
            val intent = Intent(requireContext(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            activity?.finish()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "退出登录失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 显示头像选择对话框
     */
    private fun showAvatarSelectionDialog() {
        try {
            val options = arrayOf("拍照", "从相册选择")
            AlertDialog.Builder(requireContext())
                .setTitle("选择头像")
                .setItems(options) { _, which ->
                    try {
                        when (which) {
                            0 -> {
                                if (cameraLauncher != null) {
                                    cameraLauncher?.launch(null)
                                } else {
                                    Toast.makeText(requireContext(), "相机功能暂不可用", Toast.LENGTH_SHORT).show()
                                }
                            }
                            1 -> {
                                if (galleryLauncher != null) {
                                    galleryLauncher?.launch("image/*")
                                } else {
                                    Toast.makeText(requireContext(), "相册功能暂不可用", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(requireContext(), "操作失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "显示对话框失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 从相册加载并保存头像
     */
    private fun loadAndSaveAvatar(uri: Uri) {
        try {
            Log.d("ProfileFragment", "开始从相册加载头像")
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                
                if (bitmap != null) {
                    Log.d("ProfileFragment", "图片加载成功，开始保存")
                    saveAvatar(bitmap)
                } else {
                    Log.e("ProfileFragment", "图片解码失败")
                    Toast.makeText(requireContext(), "图片加载失败", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.e("ProfileFragment", "无法打开输入流")
                Toast.makeText(requireContext(), "无法读取图片", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("ProfileFragment", "从相册加载头像失败", e)
            Toast.makeText(requireContext(), "加载图片失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 从相机保存头像
     */
    private fun saveAvatarFromCamera(bitmap: Bitmap) {
        try {
            Log.d("ProfileFragment", "开始保存相机拍摄的头像")
            saveAvatar(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("ProfileFragment", "保存相机头像失败", e)
            Toast.makeText(requireContext(), "保存头像失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 保存头像并更新用户信息
     */
    private fun saveAvatar(bitmap: Bitmap) {
        try {
            Log.d("ProfileFragment", "开始保存头像")
            val currentUser = userRepository.getCurrentUser()
            if (currentUser != null) {
                // 删除旧头像
                if (currentUser.avatarPath.isNotEmpty()) {
                    deleteOldAvatar(currentUser.avatarPath)
                }
                
                // 保存新头像
                val newAvatarPath = saveAvatarToFile(bitmap, currentUser.id)
                if (newAvatarPath != null) {
                    Log.d("ProfileFragment", "头像保存成功: $newAvatarPath")
                    
                    // 更新用户信息
                    val updatedUser = currentUser.copy(avatarPath = newAvatarPath)
                    userRepository.updateUser(updatedUser)
                    
                    // 更新UI
                    binding.ivAvatar.setImageBitmap(bitmap)
                    Toast.makeText(requireContext(), "头像更新成功", Toast.LENGTH_SHORT).show()
                } else {
                    Log.e("ProfileFragment", "头像保存失败")
                    Toast.makeText(requireContext(), "头像保存失败", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.e("ProfileFragment", "用户未登录")
                Toast.makeText(requireContext(), "用户未登录", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("ProfileFragment", "保存头像失败", e)
            Toast.makeText(requireContext(), "保存头像失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 保存头像到文件
     */
    private fun saveAvatarToFile(bitmap: Bitmap, userId: String): String? {
        return try {
            Log.d("ProfileFragment", "开始保存头像到文件")
            
            // 创建头像目录
            val avatarDir = File(requireContext().getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "avatars")
            if (!avatarDir.exists()) {
                val created = avatarDir.mkdirs()
                Log.d("ProfileFragment", "创建头像目录: $created, 路径: ${avatarDir.absolutePath}")
            }
            
            val fileName = "avatar_${userId}_${System.currentTimeMillis()}.jpg"
            val file = File(avatarDir, fileName)
            Log.d("ProfileFragment", "头像文件路径: ${file.absolutePath}")
            
            // 保存文件
            val outputStream = FileOutputStream(file)
            try {
                val success = bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, outputStream)
                Log.d("ProfileFragment", "图片压缩结果: $success")
                
                if (success) {
                    outputStream.flush()
                    Log.d("ProfileFragment", "头像保存成功")
                    file.absolutePath
                } else {
                    Log.e("ProfileFragment", "图片压缩失败")
                    null
                }
            } finally {
                outputStream.close()
                Log.d("ProfileFragment", "输出流已关闭")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("ProfileFragment", "保存头像到文件失败", e)
            null
        }
    }
    
    /**
     * 删除旧的头像文件
     */
    private fun deleteOldAvatar(avatarPath: String) {
        try {
            Log.d("ProfileFragment", "删除旧头像: $avatarPath")
            if (avatarPath.isNotEmpty()) {
                val file = File(avatarPath)
                if (file.exists()) {
                    val deleted = file.delete()
                    Log.d("ProfileFragment", "删除结果: $deleted")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("ProfileFragment", "删除旧头像失败", e)
        }
    }
    
    /**
     * 加载头像
     */
    private fun loadAvatar(avatarPath: String) {
        try {
            Log.d("ProfileFragment", "加载头像: $avatarPath")
            if (avatarPath.isNotEmpty()) {
                val bitmap = android.graphics.BitmapFactory.decodeFile(avatarPath)
                if (bitmap != null) {
                    binding.ivAvatar.setImageBitmap(bitmap)
                    Log.d("ProfileFragment", "头像加载成功")
                } else {
                    binding.ivAvatar.setImageResource(R.drawable.ic_default_avatar)
                    Log.d("ProfileFragment", "头像文件不存在或损坏，使用默认头像")
                }
            } else {
                binding.ivAvatar.setImageResource(R.drawable.ic_default_avatar)
                Log.d("ProfileFragment", "使用默认头像")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("ProfileFragment", "加载头像失败", e)
            binding.ivAvatar.setImageResource(R.drawable.ic_default_avatar)
        }
    }
    
    override fun onResume() {
        super.onResume()
        try {
            loadUserInfo()
            loadPassengerCount()
            loadMessageSummary()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

