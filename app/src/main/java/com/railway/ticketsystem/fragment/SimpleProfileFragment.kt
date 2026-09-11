package com.railway.ticketsystem.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.railway.ticketsystem.R
import com.railway.ticketsystem.activity.LoginActivity
import com.railway.ticketsystem.data.UserRepository

class SimpleProfileFragment : Fragment() {
    
    private lateinit var userRepository: UserRepository
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val layout = LinearLayout(requireContext())
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 50, 50, 50)
        
        val title = TextView(requireContext())
        title.text = "我的页面"
        title.textSize = 24f
        title.gravity = android.view.Gravity.CENTER
        layout.addView(title)
        
        val space1 = TextView(requireContext())
        space1.text = "\n"
        layout.addView(space1)
        
        userRepository = UserRepository(requireContext())
        val currentUser = userRepository.getCurrentUser()
        
        val userInfo = TextView(requireContext())
        if (currentUser != null) {
            userInfo.text = "用户名：${currentUser.username}\n真实姓名：${currentUser.realName}\n手机号：${currentUser.phone}"
        } else {
            userInfo.text = "未登录"
        }
        userInfo.textSize = 16f
        layout.addView(userInfo)
        
        val space2 = TextView(requireContext())
        space2.text = "\n"
        layout.addView(space2)
        
        val logoutBtn = Button(requireContext())
        logoutBtn.text = "退出登录"
        logoutBtn.setOnClickListener {
            userRepository.logout()
            Toast.makeText(requireContext(), "已退出登录", Toast.LENGTH_SHORT).show()
            val intent = Intent(requireContext(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            activity?.finish()
        }
        layout.addView(logoutBtn)
        
        return layout
    }
}







