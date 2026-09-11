package com.railway.ticketsystem.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.railway.ticketsystem.R

class TestFragment : Fragment() {
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = TextView(requireContext())
        view.text = "测试Fragment - 如果看到这个说明Fragment工作正常"
        view.textSize = 18f
        view.setPadding(50, 50, 50, 50)
        return view
    }
}







