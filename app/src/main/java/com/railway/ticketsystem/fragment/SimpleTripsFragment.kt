package com.railway.ticketsystem.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.railway.ticketsystem.R
import com.railway.ticketsystem.data.OrderRepository
import com.railway.ticketsystem.data.UserRepository

class SimpleTripsFragment : Fragment() {
    
    private lateinit var orderRepository: OrderRepository
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
        title.text = "我的行程"
        title.textSize = 24f
        title.gravity = android.view.Gravity.CENTER
        layout.addView(title)
        
        val space1 = TextView(requireContext())
        space1.text = "\n"
        layout.addView(space1)
        
        orderRepository = OrderRepository(requireContext())
        userRepository = UserRepository(requireContext())
        
        val currentUser = userRepository.getCurrentUser()
        if (currentUser != null) {
            val orders = orderRepository.getOrdersByUserId(currentUser.id)
            
            if (orders.isNotEmpty()) {
                val orderCount = TextView(requireContext())
                orderCount.text = "您有 ${orders.size} 个订单"
                orderCount.textSize = 18f
                orderCount.gravity = android.view.Gravity.CENTER
                layout.addView(orderCount)
                
                val space2 = TextView(requireContext())
                space2.text = "\n"
                layout.addView(space2)
                
                // 显示最新的几个订单
                val recentOrders = orders.take(3)
                for (order in recentOrders) {
                    val orderView = createOrderView(order)
                    layout.addView(orderView)
                    
                    val space = TextView(requireContext())
                    space.text = "\n"
                    layout.addView(space)
                }
                
                if (orders.size > 3) {
                    val moreText = TextView(requireContext())
                    moreText.text = "... 还有 ${orders.size - 3} 个订单"
                    moreText.textSize = 14f
                    moreText.gravity = android.view.Gravity.CENTER
                    layout.addView(moreText)
                }
            } else {
                val noOrdersText = TextView(requireContext())
                noOrdersText.text = "暂无订单\n\n快去购买车票吧！"
                noOrdersText.textSize = 16f
                noOrdersText.gravity = android.view.Gravity.CENTER
                layout.addView(noOrdersText)
            }
        } else {
            val notLoggedInText = TextView(requireContext())
            notLoggedInText.text = "请先登录"
            notLoggedInText.textSize = 16f
            notLoggedInText.gravity = android.view.Gravity.CENTER
            layout.addView(notLoggedInText)
        }
        
        return layout
    }
    
    private fun createOrderView(order: com.railway.ticketsystem.model.Order): View {
        val orderLayout = LinearLayout(requireContext())
        orderLayout.orientation = LinearLayout.VERTICAL
        orderLayout.setPadding(20, 20, 20, 20)
        orderLayout.setBackgroundColor(android.graphics.Color.parseColor("#F5F5F5"))
        
        val trainInfo = TextView(requireContext())
        trainInfo.text = "${order.trainNumber} ${order.departureStation} → ${order.arrivalStation}"
        trainInfo.textSize = 16f
        trainInfo.setTextColor(android.graphics.Color.parseColor("#1976D2"))
        orderLayout.addView(trainInfo)
        
        val timeInfo = TextView(requireContext())
        timeInfo.text = "${order.departureDate} ${order.departureTime} - ${order.arrivalTime}"
        timeInfo.textSize = 14f
        timeInfo.setTextColor(android.graphics.Color.parseColor("#666666"))
        orderLayout.addView(timeInfo)
        
        val seatInfo = TextView(requireContext())
        seatInfo.text = "${order.seatType} ${order.seatNumber} | ${order.passengerName}"
        seatInfo.textSize = 14f
        seatInfo.setTextColor(android.graphics.Color.parseColor("#666666"))
        orderLayout.addView(seatInfo)
        
        val priceInfo = TextView(requireContext())
        priceInfo.text = "¥${order.finalPrice.toInt()} | ${order.status}"
        priceInfo.textSize = 14f
        priceInfo.setTextColor(android.graphics.Color.parseColor("#E53935"))
        orderLayout.addView(priceInfo)
        
        return orderLayout
    }
}
