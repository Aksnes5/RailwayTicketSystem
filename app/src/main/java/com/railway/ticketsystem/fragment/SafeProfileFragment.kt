package com.railway.ticketsystem.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.railway.ticketsystem.R
import com.railway.ticketsystem.activity.EditProfileActivity
import com.railway.ticketsystem.activity.LoginActivity
import com.railway.ticketsystem.activity.PassengerManageActivity
import com.railway.ticketsystem.data.PassengerRepository
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.databinding.FragmentProfileBinding

class SafeProfileFragment : Fragment() {
    
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var userRepository: UserRepository
    private lateinit var passengerRepository: PassengerRepository
    
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
            
            setupUI()
            loadUserInfo()
            loadPassengerCount()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
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
            
            // 暂时禁用头像功能
            binding.cardAvatar.setOnClickListener {
                Toast.makeText(requireContext(), "头像功能开发中", Toast.LENGTH_SHORT).show()
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
                
                // 使用默认头像
                binding.ivAvatar.setImageResource(R.drawable.ic_default_avatar)
            } else {
                binding.tvUsername.text = "未登录"
                binding.tvUserEmail.text = "请先登录"
                binding.tvUserInfo.text = "未登录"
                binding.tvUserPhone.text = "未登录"
                binding.tvRegisterTime.text = "未登录"
                binding.ivAvatar.setImageResource(R.drawable.ic_default_avatar)
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
    
    override fun onResume() {
        super.onResume()
        try {
            loadUserInfo()
            loadPassengerCount()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
