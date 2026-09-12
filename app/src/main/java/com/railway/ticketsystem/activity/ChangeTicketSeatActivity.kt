package com.railway.ticketsystem.activity

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.railway.ticketsystem.data.PointsPolicy
import com.railway.ticketsystem.R
import com.railway.ticketsystem.data.OrderRepository
import com.railway.ticketsystem.data.OrderTimetableFactory
import com.railway.ticketsystem.data.MessageRepository
import com.railway.ticketsystem.data.MembershipRepository
import com.railway.ticketsystem.data.SeatInventoryRepository
import com.railway.ticketsystem.data.TravelReminderScheduler
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.databinding.ActivityChangeTicketSeatBinding
import com.railway.ticketsystem.model.Order
import com.railway.ticketsystem.model.SeatType
import com.railway.ticketsystem.model.SeatTypes
import com.railway.ticketsystem.model.Train
import java.text.SimpleDateFormat
import java.util.*

class ChangeTicketSeatActivity : ImmersiveActivity() {
    
    private lateinit var binding: ActivityChangeTicketSeatBinding
    private lateinit var originalOrder: Order
    private lateinit var newTrain: Train
    private lateinit var departureDate: String
    private lateinit var orderRepository: OrderRepository
    private lateinit var seatInventoryRepository: SeatInventoryRepository
    private lateinit var messageRepository: MessageRepository
    private var selectedCarNumber = ""
    private lateinit var userRepository: UserRepository
    private lateinit var membershipRepository: MembershipRepository
    
    private var selectedSeatType = SeatTypes.SECOND_CLASS
    private var selectedSeatNumber = ""
    private var basePrice = 0.0
    private var finalPrice = 0.0
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChangeTicketSeatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 获取传递的信息
        originalOrder = intent.getSerializableExtra("originalOrder") as Order
        newTrain = intent.getSerializableExtra("selectedTrain") as Train
        departureDate = intent.getStringExtra("departureDate") ?: ""
        
        // 初始化数据仓库
        orderRepository = OrderRepository(this)
        userRepository = UserRepository(this)
        membershipRepository = MembershipRepository(this)
        seatInventoryRepository = SeatInventoryRepository(this)
        messageRepository = MessageRepository(this)
        
        setupUI()
        setupSeatTypeSelection()
        setupSeatNumberSelection()
        setupPriceCalculation()
        setupConfirmButton()
    }
    
    private fun setupUI() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "改签座位选择"
        
        // 显示原订单信息
        binding.tvOriginalOrderInfo.text = "${originalOrder.trainNumber} ${originalOrder.departureStation} → ${originalOrder.arrivalStation}"
        binding.tvOriginalSeatInfo.text = originalOrder.seatInfo
        
        // 显示新车次信息
        binding.tvNewTrainInfo.text = "${newTrain.number} ${newTrain.departureStation} → ${newTrain.arrivalStation}"
        binding.tvNewTimeInfo.text = "${newTrain.departureTime} - ${newTrain.arrivalTime} | ${newTrain.duration}"
        
        // 设置原订单价格
        binding.tvOriginalPrice.text = "¥${originalOrder.finalPrice.toInt()}"
    }
    
    private fun setupSeatTypeSelection() {
        val seatTypes = SeatTypes.forTrain(newTrain)
        
        binding.llSeatTypeButtons.removeAllViews()
        
        seatTypes.forEach { seatType ->
            val button = com.google.android.material.button.MaterialButton(this)
            button.tag = seatType.name
            button.text = seatType.name
            button.textSize = 13f
            button.minWidth = 0
            button.height = 52
            button.insetTop = 0
            button.insetBottom = 0
            
            val layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                weight = 1f
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            button.layoutParams = layoutParams
            styleGlassChoice(button, seatType == selectedSeatType)
            
            button.setOnClickListener {
                selectSeatType(seatType)
            }
            
            binding.llSeatTypeButtons.addView(button)
        }
        
        // 初始化价格
        updatePrice()
    }
    
    private fun selectSeatType(seatType: SeatType) {
        selectedSeatType = seatType
        selectedSeatNumber = ""
        
        // 更新按钮状态
        for (i in 0 until binding.llSeatTypeButtons.childCount) {
            val button = binding.llSeatTypeButtons.getChildAt(i) as com.google.android.material.button.MaterialButton
            styleGlassChoice(button, button.tag == seatType.name)
        }
        
        updateSeatNumbers()
        updatePrice()
    }
    
    private fun setupSeatNumberSelection() {
        updateSeatNumbers()
    }
    
    private fun updateSeatNumbers() {
        binding.llSeatButtons.removeAllViews()
        
        for (seatLetter in selectedSeatType.availableSeats) {
            val seatButton = com.google.android.material.button.MaterialButton(this)
            seatButton.tag = seatLetter
            seatButton.text = "$seatLetter\n${seatPositionLabel(seatLetter)}"
            seatButton.textSize = 12f
            seatButton.minWidth = 0
            seatButton.height = 64
            seatButton.insetTop = 0
            seatButton.insetBottom = 0
            seatButton.setPadding(dp(4), dp(6), dp(4), dp(6))
            
            val layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                weight = 1f
                val aisleGap = dp(11)
                val start = if (seatLetter == "D") aisleGap else dp(4)
                val end = if (seatLetter == "C") aisleGap else dp(4)
                setMargins(start, dp(4), end, dp(4))
            }
            seatButton.layoutParams = layoutParams
            styleGlassChoice(seatButton, false)
            
            seatButton.setOnClickListener {
                selectSeatNumber(seatLetter)
            }
            
            binding.llSeatButtons.addView(seatButton)
        }
    }
    
    private fun selectSeatNumber(seatLetter: String) {
        // 生成完整的座位号，包含车厢号和座位号
        selectedCarNumber = (1..8).random().toString()
        selectedSeatNumber = "$selectedCarNumber$seatLetter"
        
        // 更新按钮状态
        for (i in 0 until binding.llSeatButtons.childCount) {
            val button = binding.llSeatButtons.getChildAt(i) as com.google.android.material.button.MaterialButton
            styleGlassChoice(button, button.tag == seatLetter)
        }
    }

    private fun seatPositionLabel(seatLetter: String): String = when (seatLetter) {
        "A", "F" -> "靠窗"
        "C", "D" -> "走廊"
        else -> "中间"
    }

    private fun styleGlassChoice(
        button: com.google.android.material.button.MaterialButton,
        selected: Boolean
    ) {
        button.backgroundTintList = android.content.res.ColorStateList.valueOf(
            android.graphics.Color.parseColor(if (selected) "#D5D9F0FF" else "#B8FFFFFF")
        )
        button.strokeColor = android.content.res.ColorStateList.valueOf(
            android.graphics.Color.parseColor(if (selected) "#B077BDF4" else "#A8FFFFFF")
        )
        button.strokeWidth = dp(1)
        button.cornerRadius = dp(20)
        button.rippleColor = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#260677D7"))
        button.setTextColor(getColor(if (selected) R.color.railway_blue_deep else R.color.text_primary))
        button.elevation = 0f
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    
    private fun setupPriceCalculation() {
        updatePrice()
    }
    
    private fun updatePrice() {
        basePrice = newTrain.price
        finalPrice = basePrice * selectedSeatType.multiplier
        
        binding.tvNewPrice.text = "¥${finalPrice.toInt()}"
        
        val priceDifference = finalPrice - originalOrder.finalPrice
        binding.tvPriceDifference.text = "¥${priceDifference.toInt()}"
        
        if (priceDifference > 0) {
            binding.tvPriceDifference.setTextColor(resources.getColor(R.color.railway_red))
            val userId = userRepository.getCurrentUser()?.id
            val coupon = userId?.let { availableChangeCoupon(it, originalOrder.seatType, priceDifference) }
            binding.tvPriceDifferenceNote.text = if (coupon == null) {
                "本次改签需扣除积分 ${PointsPolicy.fromAmount(priceDifference)} 分"
            } else {
                "可自动使用「" + coupon.second + "」，免扣本次积分补差"
            }
        } else if (priceDifference < 0) {
            binding.tvPriceDifference.setTextColor(resources.getColor(R.color.green))
            binding.tvPriceDifferenceNote.text = "将返还积分 ${PointsPolicy.fromAmount(-priceDifference)} 分"
        } else {
            binding.tvPriceDifference.setTextColor(resources.getColor(R.color.gray_medium))
            binding.tvPriceDifferenceNote.text = "价格相同，无需补差价"
        }
    }
    
    private fun setupConfirmButton() {
        binding.btnConfirmChange.setOnClickListener {
            if (selectedSeatNumber.isEmpty()) {
                Toast.makeText(this, "请选择座位", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            showConfirmDialog()
        }
    }
    
    private fun showConfirmDialog() {
        val priceDifference = finalPrice - originalOrder.finalPrice
        val message = if (priceDifference > 0) {
            confirmChangeMessage(priceDifference)
        } else if (priceDifference < 0) {
            "确认改签？\n\n将返还积分：${PointsPolicy.fromAmount(-priceDifference)} 分"
        } else {
            "确认改签？\n\n价格相同，无需补差价"
        }
        
        AlertDialog.Builder(this)
            .setTitle("确认改签")
            .setMessage(message)
            .setPositiveButton("确认改签") { _, _ ->
                processChangeTicket()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun processChangeTicket() {
        binding.btnConfirmChange.isEnabled = false
        var reservedNewInventory = false
        try {
            val currentUser = userRepository.getCurrentUser()
            if (currentUser == null) {
                Toast.makeText(this, "用户信息获取失败", Toast.LENGTH_SHORT).show()
                binding.btnConfirmChange.isEnabled = true
                return
            }
            val latestOrder = orderRepository.getOrderById(originalOrder.id, currentUser.id)
            if (latestOrder == null || originalOrder.userId != currentUser.id) {
                Toast.makeText(this, "无权修改此订单", Toast.LENGTH_SHORT).show()
                binding.btnConfirmChange.isEnabled = true
                return
            }
            if (latestOrder.status != "已支付") {
                Toast.makeText(this, "订单状态已变化，当前不可改签", Toast.LENGTH_SHORT).show()
                binding.btnConfirmChange.isEnabled = true
                return
            }
            
            val priceDifference = finalPrice - latestOrder.finalPrice
            val couponOffer = availableChangeCoupon(currentUser.id, latestOrder.seatType, priceDifference)
            val pointsDelta = when {
                couponOffer != null -> 0
                priceDifference > 0 -> -PointsPolicy.fromAmount(priceDifference)
                priceDifference < 0 -> PointsPolicy.fromAmount(-priceDifference)
                else -> 0
            }
            
            if (pointsDelta < 0) {
                if (currentUser.points < -pointsDelta) {
                    Toast.makeText(this, "积分不足，无法改签", Toast.LENGTH_SHORT).show()
                    binding.btnConfirmChange.isEnabled = true
                    return
                }
            }

            val availability = seatInventoryRepository.getAvailability(newTrain, departureDate, selectedSeatType.name)
            if (availability.availableSeats < 1) {
                Toast.makeText(this, "所选席别暂无余票，请选择其他席别或车次", Toast.LENGTH_SHORT).show()
                binding.btnConfirmChange.isEnabled = true
                return
            }
            if (!seatInventoryRepository.reserveSeats(newTrain, departureDate, selectedSeatType.name, 1)) {
                Toast.makeText(this, "所选席别刚刚售罄，请重新选择", Toast.LENGTH_SHORT).show()
                binding.btnConfirmChange.isEnabled = true
                return
            }
            reservedNewInventory = true

            val timetable = OrderTimetableFactory.capture(newTrain)
            val updatedOrder = latestOrder.copy(
                trainNumber = newTrain.number,
                departureTime = newTrain.departureTime,
                arrivalTime = timetable.arrivalTime,
                departureDate = departureDate,
                seatType = selectedSeatType.name,
                departureStation = newTrain.departureStation,
                arrivalStation = newTrain.arrivalStation,
                seatNumber = selectedSeatNumber,
                carNumber = selectedCarNumber,
                basePrice = basePrice,
                finalPrice = finalPrice,
                status = "已支付",
                timetableStops = timetable.stops,
                timetableDuration = timetable.duration,
                routeStations = timetable.routeStations
            )
            
            if (!orderRepository.replaceOrderIfSeatAvailable(
                    latestOrder.id,
                    currentUser.id,
                    updatedOrder,
                    expectedOriginal = latestOrder
                )) {
                seatInventoryRepository.rollbackReservedSeats(newTrain, departureDate, selectedSeatType.name)
                reservedNewInventory = false
                Toast.makeText(this, "所选座位刚刚售出，请重新选择", Toast.LENGTH_SHORT).show()
                binding.btnConfirmChange.isEnabled = true
                return
            }
            reservedNewInventory = false

            val changeFingerprint = listOf(
                latestOrder.id,
                latestOrder.trainNumber,
                latestOrder.departureDate,
                latestOrder.seatType,
                updatedOrder.trainNumber,
                updatedOrder.departureDate,
                updatedOrder.seatType,
                updatedOrder.carNumber,
                updatedOrder.seatNumber
            ).joinToString(":")
            val couponUsageKey = "change_coupon:" + changeFingerprint
            val consumedCoupon = couponOffer?.let {
                membershipRepository.consumeCoupon(currentUser.id, it.first, couponUsageKey)
            }
            if (couponOffer != null && consumedCoupon == null) {
                val orderRolledBack = orderRepository.replaceOrderIfSeatAvailable(
                    updatedOrder.id,
                    currentUser.id,
                    latestOrder,
                    expectedOriginal = updatedOrder
                )
                if (orderRolledBack) {
                    seatInventoryRepository.rollbackReservedSeats(
                        newTrain,
                        departureDate,
                        selectedSeatType.name
                    )
                }
                Toast.makeText(this, "权益券状态已变化，改签未完成，请重试", Toast.LENGTH_LONG).show()
                binding.btnConfirmChange.isEnabled = true
                return
            }
            if (pointsDelta != 0 && !userRepository.adjustPointsOnce(
                    currentUser.id,
                    pointsDelta,
                    "change_ticket_points:$changeFingerprint"
                )) {
                val orderRolledBack = orderRepository.replaceOrderIfSeatAvailable(
                    updatedOrder.id,
                    currentUser.id,
                    latestOrder,
                    expectedOriginal = updatedOrder
                )
                if (orderRolledBack) {
                    seatInventoryRepository.rollbackReservedSeats(
                        newTrain,
                        departureDate,
                        selectedSeatType.name
                    )
                }
                if (consumedCoupon != null) membershipRepository.restoreCoupon(currentUser.id, consumedCoupon.id, couponUsageKey)
                Toast.makeText(this, "积分处理失败，改签未完成，请重试", Toast.LENGTH_LONG).show()
                binding.btnConfirmChange.isEnabled = true
                return
            }

            if (seatInventoryRepository.releaseSeat(latestOrder)) {
                orderRepository.acknowledgeInventoryRelease(latestOrder.id)
            }

            TravelReminderScheduler.cancel(this, latestOrder)
            TravelReminderScheduler.schedule(this, updatedOrder)
            messageRepository.add(
                currentUser.id,
                MessageRepository.TICKET,
                "改签成功 · ${updatedOrder.trainNumber}",
                "已由 ${latestOrder.trainNumber} ${latestOrder.departureDate} 改签至 ${updatedOrder.trainNumber} ${updatedOrder.departureDate} ${updatedOrder.departureTime}。",
                updatedOrder.id,
                eventKey = "change_ticket_result:$changeFingerprint"
            )
            messageRepository.add(
                currentUser.id,
                MessageRepository.TICKET,
                "座位已变更",
                "原座位 ${latestOrder.seatInfo}，新座位 ${updatedOrder.seatInfo}，请按新票面乘车。",
                updatedOrder.id,
                eventKey = "change_ticket_seat:$changeFingerprint"
            )
            membershipRepository.trackEvent(currentUser.id, MembershipRepository.EVENT_CHANGE_TICKET)
            
            val successMessage = if (consumedCoupon != null) {
                "改签成功！已使用" + consumedCoupon.title + "，免扣积分补差"
            } else if (priceDifference > 0) {
                "改签成功！已扣除积分 ${PointsPolicy.fromAmount(priceDifference)}"
            } else if (priceDifference < 0) {
                "改签成功！已返还积分 ${PointsPolicy.fromAmount(-priceDifference)}"
            } else {
                "改签成功！"
            }
            
            Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show()
            
            // 返回主界面，行程列表会自动刷新
            val intent = Intent(this, com.railway.ticketsystem.MainActivity::class.java)
            intent.putExtra("selectedTab", 1) // 跳转到行程页面
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
            
        } catch (e: Exception) {
            if (reservedNewInventory) {
                seatInventoryRepository.rollbackReservedSeats(newTrain, departureDate, selectedSeatType.name)
            }
            e.printStackTrace()
            Toast.makeText(this, "改签失败，请重试", Toast.LENGTH_SHORT).show()
            binding.btnConfirmChange.isEnabled = true
        }
    }
    
    private fun availableChangeCoupon(
        userId: String,
        sourceSeatType: String,
        priceDifference: Double
    ): Pair<String, String>? {
        if (priceDifference <= 0.0) return null
        val isUpgrade = seatLevel(selectedSeatType.name) > seatLevel(sourceSeatType)
        if (isUpgrade && membershipRepository.getAvailableCoupon(userId, MembershipRepository.COUPON_UPGRADE) != null) {
            return MembershipRepository.COUPON_UPGRADE to "升座体验券"
        }
        if (membershipRepository.getAvailableCoupon(userId, MembershipRepository.COUPON_CHANGE) != null) {
            return MembershipRepository.COUPON_CHANGE to "免费改签券"
        }
        return null
    }

    private fun confirmChangeMessage(priceDifference: Double): String {
        if (priceDifference <= 0.0) return "确认改签？\n\n将返还积分 " + PointsPolicy.fromAmount(-priceDifference) + " 分"
        val userId = userRepository.getCurrentUser()?.id
        val coupon = userId?.let { availableChangeCoupon(it, originalOrder.seatType, priceDifference) }
        return if (coupon == null) {
            "确认改签？\n\n本次需扣除积分 " + PointsPolicy.fromAmount(priceDifference) + " 分"
        } else {
            "确认改签？\n\n将自动使用「" + coupon.second + "」，免扣本次积分补差。"
        }
    }

    private fun seatLevel(seatType: String): Int = when (seatType) {
        "商务座" -> 3
        "一等座" -> 2
        else -> 1
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
