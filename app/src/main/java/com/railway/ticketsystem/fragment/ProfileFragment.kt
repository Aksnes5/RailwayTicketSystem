package com.railway.ticketsystem.fragment

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.railway.ticketsystem.R
import com.railway.ticketsystem.activity.LoginActivity
import com.railway.ticketsystem.activity.PassengerManageActivity
import com.railway.ticketsystem.activity.EditProfileActivity
import com.railway.ticketsystem.data.PassengerRepository
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.databinding.FragmentProfileBinding
import com.railway.ticketsystem.utils.AvatarManager

class ProfileFragment : Fragment() {
    
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var userRepository: UserRepository
    private lateinit var passengerRepository: PassengerRepository
    private lateinit var avatarManager: AvatarManager
    
    // 头像选择相关
    private var galleryLauncher: androidx.activity.result.ActivityResultLauncher<String>? = null
    private var cameraLauncher: androidx.activity.result.ActivityResultLauncher<Void?>? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        userRepository = UserRepository(requireContext())
        passengerRepository = PassengerRepository(requireContext())
        avatarManager = AvatarManager(requireContext())
        
        // 初始化ActivityResultLauncher
        try {
            galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                uri?.let { loadAndSaveAvatar(it) }
            }
            
            cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
                bitmap?.let { saveAvatarFromCamera(it) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "初始化头像功能失败", Toast.LENGTH_SHORT).show()
        }
        
        setupUI()
        loadUserInfo()
        loadPassengerCount()
    }
    
    private fun setupUI() {
        binding.btnLogout.setOnClickListener {
            logout()
        }
        
        binding.btnManagePassengers.setOnClickListener {
            startActivity(Intent(requireContext(), PassengerManageActivity::class.java))
        }
        
        binding.btnEditProfile.setOnClickListener {
            startActivity(Intent(requireContext(), EditProfileActivity::class.java))
        }
        
        binding.cardAvatar.setOnClickListener {
            showAvatarSelectionDialog()
        }
        
    }
    
    private fun loadUserInfo() {
        val currentUser = userRepository.getCurrentUser()
        if (currentUser != null) {
            binding.tvUsername.text = currentUser.username
            binding.tvUserEmail.text = currentUser.email
            binding.tvUserInfo.text = currentUser.realName
            binding.tvUserPhone.text = maskPhone(currentUser.phone)
            binding.tvRegisterTime.text = currentUser.createTime.substring(0, 10) // 只显示日期部分
            android.util.Log.d("ProfileFragment", "加载账户资料")
            android.util.Log.d("ProfileFragment", "用户ID: ${currentUser.id}, 用户名: ${currentUser.username}")
            
            // 加载头像
            loadAvatar(currentUser.avatarPath)
        } else {
            android.util.Log.w("ProfileFragment", "用户未登录")
            binding.tvUsername.text = "未登录"
            binding.tvUserEmail.text = "请先登录"
            binding.tvUserInfo.text = "未登录"
            binding.tvUserPhone.text = "未登录"
            binding.tvRegisterTime.text = "未登录"
            binding.ivAvatar.setImageResource(R.drawable.ic_default_avatar)
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
        userRepository.logout()
        Toast.makeText(requireContext(), "已退出登录", Toast.LENGTH_SHORT).show()
        
        // 跳转到登录页面
        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        activity?.finish()
    }
    
    override fun onResume() {
        super.onResume()
        loadUserInfo() // 刷新用户信息
        loadPassengerCount() // 刷新乘客数量
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
            val bitmap = avatarManager.loadBitmapFromUri(uri)
            if (bitmap != null) {
                saveAvatar(bitmap)
            } else {
                Toast.makeText(requireContext(), "图片加载失败", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "加载图片失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 从相机保存头像
     */
    private fun saveAvatarFromCamera(bitmap: Bitmap) {
        try {
            saveAvatar(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "保存头像失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 保存头像并更新用户信息
     */
    private fun saveAvatar(bitmap: Bitmap) {
        try {
            val currentUser = userRepository.getCurrentUser()
            if (currentUser != null) {
                // 删除旧头像
                if (currentUser.avatarPath.isNotEmpty()) {
                    avatarManager.deleteOldAvatar(currentUser.avatarPath)
                }
                
                // 保存新头像
                val newAvatarPath = avatarManager.saveAvatar(bitmap, currentUser.id)
                if (newAvatarPath != null) {
                    // 更新用户信息
                    val updatedUser = currentUser.copy(avatarPath = newAvatarPath)
                    userRepository.updateUser(updatedUser)
                    
                    // 更新UI
                    binding.ivAvatar.setImageBitmap(bitmap)
                    Toast.makeText(requireContext(), "头像更新成功", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "头像保存失败", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "用户未登录", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "保存头像失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 加载头像
     */
    private fun loadAvatar(avatarPath: String) {
        try {
            if (avatarPath.isNotEmpty()) {
                val bitmap = avatarManager.loadAvatar(avatarPath)
                if (bitmap != null) {
                    binding.ivAvatar.setImageBitmap(bitmap)
                } else {
                    binding.ivAvatar.setImageResource(R.drawable.ic_default_avatar)
                }
            } else {
                binding.ivAvatar.setImageResource(R.drawable.ic_default_avatar)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            binding.ivAvatar.setImageResource(R.drawable.ic_default_avatar)
        }
    }
    
    /**
     * 加载乘客数量
     */
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
    
    /**
     * 根据积分计算会员等级
     * @param points 用户积分
     * @return 会员等级字符串
     */
    private fun calculateMemberLevel(points: Int): String {
        return when {
            points >= 100000 -> "五星会员"
            points >= 75000 -> "四星会员"
            points >= 50000 -> "三星会员"
            points >= 20000 -> "二星会员"
            else -> "一星会员"
        }
    }
}
