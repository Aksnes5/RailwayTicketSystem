package com.railway.ticketsystem.activity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.railway.ticketsystem.R
import com.railway.ticketsystem.data.OrderRepository
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.databinding.ActivityRailwayTravelCodeBinding

/**
 * 铁路畅行码与扶手扫码乘车服务驾驶舱
 * 模拟高铁列车座椅扶手二维码扫码体验，为旅客提供直达席位的在途餐饮、
 * 呼叫乘务、正晚点监测、微气候温度反馈等一系列智能随车服务。
 */
class RailwayTravelCodeActivity : ImmersiveActivity() {

    private lateinit var binding: ActivityRailwayTravelCodeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRailwayTravelCodeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bindCurrentTrip()
        setupClickListeners()
    }

    private fun bindCurrentTrip() {
        val userRepo = UserRepository(this)
        val user = userRepo.getCurrentUser()
        if (user != null) {
            val orderRepo = OrderRepository(this)
            val activeOrder = orderRepo.getAllOrders().firstOrNull { it.userId == user.id && it.status == "已支付" }
            if (activeOrder != null) {
                binding.tvTravelCodeTrain.text = "${activeOrder.trainNumber} 次 · 复兴号"
                val seatDisplay = if (activeOrder.carNumber.isNotBlank() && activeOrder.seatNumber.isNotBlank()) {
                    "${activeOrder.carNumber}车 ${activeOrder.seatNumber}号"
                } else activeOrder.seatNumber
                binding.tvTravelCodeSeat.text = seatDisplay
                binding.tvTravelCodeRoute.text = "${activeOrder.departureStation} ${activeOrder.departureTime} 开 → ${activeOrder.arrivalStation} ${activeOrder.arrivalTime} 到"
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnBackTravelCode.setOnClickListener { finish() }

        binding.btnSeatDining.setOnClickListener {
            startActivity(Intent(this, MealOrderActivity::class.java))
        }

        binding.btnCallAttendant.setOnClickListener {
            startActivity(Intent(this, CarriageServiceActivity::class.java))
        }

        binding.btnOnboardDelay.setOnClickListener {
            val trainNo = binding.tvTravelCodeTrain.text.toString().substringBefore(" ").trim()
            startActivity(Intent(this, TrainDelayQueryActivity::class.java).apply {
                putExtra(TrainDelayQueryActivity.EXTRA_TRAIN_NUMBER, trainNo)
            })
        }

        binding.btnTicketExtension.setOnClickListener {
            Toast.makeText(this, "正在为您检索车内就近补票与延程空闲席位...", Toast.LENGTH_LONG).show()
        }

        binding.btnCleanRequest.setOnClickListener {
            Toast.makeText(this, "保洁服务需求已发送至车厢随车保洁员，预计 5 分钟内前往清理", Toast.LENGTH_LONG).show()
        }

        binding.btnQuietCarPledge.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("🎧 静音车厢公约")
                .setMessage("请全程佩戴耳机、手机开启静音或振动、轻声细语。如需降噪耳塞可在车厢服务中向乘务员索取。")
                .setPositiveButton("我知道了", null)
                .show()
        }

        binding.btnTempCold.setOnClickListener {
            Toast.makeText(this, "已收到【偏冷】反馈，列车机械师将适度调高出风温度", Toast.LENGTH_SHORT).show()
        }
        binding.btnTempComfort.setOnClickListener {
            Toast.makeText(this, "感谢您的体感评价，当前车厢微气候保持良好", Toast.LENGTH_SHORT).show()
        }
        binding.btnTempHot.setOnClickListener {
            Toast.makeText(this, "已收到【偏热】反馈，列车机械师将加大车厢冷风循环", Toast.LENGTH_SHORT).show()
        }
    }
}
