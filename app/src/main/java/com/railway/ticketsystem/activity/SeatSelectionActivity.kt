package com.railway.ticketsystem.activity

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.railway.ticketsystem.R
import com.railway.ticketsystem.adapter.PassengerAdapter
import com.railway.ticketsystem.data.PassengerRepository
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.data.SeatInventoryRepository
import com.railway.ticketsystem.data.SeatAvailability
import com.railway.ticketsystem.data.FamilyAccountRepository
import com.railway.ticketsystem.data.FamilySeatAllocator
import com.railway.ticketsystem.data.CarriageCrowdData
import com.railway.ticketsystem.data.SplitTicketingPlanner
import com.railway.ticketsystem.databinding.ActivitySeatSelectionBinding
import com.railway.ticketsystem.databinding.DialogPassengerSelectionBinding
import com.railway.ticketsystem.model.Passenger
import com.railway.ticketsystem.model.SeatTypes
import com.railway.ticketsystem.model.Train
import com.railway.ticketsystem.model.TransferTrain
import com.railway.ticketsystem.viewmodel.SeatSelectionViewModel
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SeatSelectionActivity : ImmersiveActivity() {
    
    private lateinit var binding: ActivitySeatSelectionBinding
    private lateinit var train: Train
    private lateinit var departureDate: String
    private val viewModel: SeatSelectionViewModel by viewModels()
    private var selectedSeatType = SeatTypes.SECOND_CLASS
    private var selectedSeatNumber = ""
    private var selectedSeatNumberFirst = ""
    private var selectedSeatNumberSecond = ""
    private var basePrice = 0.0
    private var selectedSleeperPreference = "下铺"
    
    // 中转车次相关
    private var isTransfer = false
    private var firstLeg: Train? = null
    private var secondLeg: Train? = null
    private var transferStation: String? = null
    private var transferTime: Int = 0
    private var totalPrice: Double = 0.0
    
    // 乘客相关
    private lateinit var passengerRepository: PassengerRepository
    private lateinit var userRepository: UserRepository
    private var selectedPassengers: List<Passenger> = emptyList()
    private lateinit var seatInventoryRepository: SeatInventoryRepository
    private lateinit var familyAccountRepository: FamilyAccountRepository
    private var isManualInput = true
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySeatSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 获取传递的车次信息
        isTransfer = intent.getBooleanExtra("isTransfer", false)
        departureDate = intent.getStringExtra("departureDate") ?: ""
        
        if (isTransfer) {
            // 处理中转车次
            firstLeg = intent.getSerializableExtra("firstLeg") as? Train
            secondLeg = intent.getSerializableExtra("secondLeg") as? Train
            transferStation = intent.getStringExtra("transferStation")
            transferTime = intent.getIntExtra("transferTime", 0)
            totalPrice = intent.getDoubleExtra("totalPrice", 0.0)
            
            // 创建一个虚拟的Train对象用于显示
            if (firstLeg != null && secondLeg != null) {
                train = Train(
                    number = "中转${firstLeg!!.number}→${secondLeg!!.number}",
                    departureStation = firstLeg!!.departureStation,
                    arrivalStation = secondLeg!!.arrivalStation,
                    departureTime = firstLeg!!.departureTime,
                    arrivalTime = secondLeg!!.arrivalTime,
                    duration = calculateTotalDuration(firstLeg!!.duration, secondLeg!!.duration),
                    price = totalPrice,
                    availableSeats = minOf(firstLeg!!.availableSeats, secondLeg!!.availableSeats),
                    routeType = firstLeg!!.routeType
                )
            } else {
                throw IllegalArgumentException("中转车次信息不完整")
            }
        } else {
            // 处理直达车次
            train = intent.getSerializableExtra("selectedTrain") as Train
        }
        selectedSeatType = SeatTypes.forTrain(train).first()
        
        // 初始化数据仓库
        passengerRepository = PassengerRepository(this)
        userRepository = UserRepository(this)
        
        seatInventoryRepository = SeatInventoryRepository(this)
        familyAccountRepository = FamilyAccountRepository(this)
        setupUI()
        setupSeatTypeSelection()
        setupSeatNumberSelection()
        setupPassengerSelection()
        setupSleeperPreference()
        updateContiguousSeatingUI()
        updatePrice()

        viewModel.initialize(train, departureDate, isTransfer, seatInventoryRepository)
        observeViewModel()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.totalAmount > 0) {
                        binding.tvTotalPrice.text = "¥${state.totalAmount.toInt()}"
                    }
                    if (state.requiresWaitlist) {
                        binding.btnSubmitOrder.text = "提交候补"
                    }
                }
            }
        }
    }
    
    private fun setupUI() {
        // 设置车次信息
        if (isTransfer) {
            // 中转车次显示
            binding.tvTrainInfo.text = "${train.number} ${train.departureStation} → ${train.arrivalStation}"
            binding.tvTimeInfo.text = "${train.departureTime} - ${train.arrivalTime} | ${train.duration}"
            
            // 显示中转信息
            val transferInfo = "在${transferStation}中转${transferTime}分钟"
            // 这里可以添加一个TextView来显示中转信息，暂时用Toast显示
            Toast.makeText(this, transferInfo, Toast.LENGTH_LONG).show()

            binding.groupDirectSeatSelection.visibility = View.GONE
            binding.groupTransferSeatSelection.visibility = View.VISIBLE
            binding.tvFirstLegLabel.text = "第一程 ${firstLeg?.number ?: ""}"
            binding.tvSecondLegLabel.text = "第二程 ${secondLeg?.number ?: ""}"
        } else {
            // 直达车次显示
            binding.tvTrainInfo.text = "${train.number} ${train.departureStation} → ${train.arrivalStation}"
            binding.tvTimeInfo.text = "${train.departureTime} - ${train.arrivalTime} | ${train.duration}"

            binding.groupDirectSeatSelection.visibility = View.VISIBLE
            binding.groupTransferSeatSelection.visibility = View.GONE
        }
        
        // 静音车厢优选开关（高铁 G 字头专享）
        val isGTrain = (!isTransfer && train.number.startsWith("G")) || (isTransfer && (firstLeg?.number?.startsWith("G") == true || secondLeg?.number?.startsWith("G") == true))
        if (isGTrain) {
            binding.cardQuietCar.visibility = View.VISIBLE
            binding.switchQuietCar.setOnCheckedChangeListener { buttonView, isChecked ->
                buttonView.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                if (isChecked) {
                    Toast.makeText(this, "已为您优选 03 号静音车厢！请在车厢内保持安静、佩戴耳机。", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            binding.cardQuietCar.visibility = View.GONE
        }

        basePrice = train.price
        binding.tvBasePrice.text = "¥${basePrice.toInt()}"

        setupCarriageHeatmap()
        setupSplitTicketing()
        
        // 设置提交按钮
        binding.btnSubmitOrder.setOnClickListener {
            submitOrder()
        }
    }
    
    private fun calculateTotalDuration(firstDuration: String, secondDuration: String): String {
        val firstMinutes = parseDurationToMinutes(firstDuration)
        val secondMinutes = parseDurationToMinutes(secondDuration)
        val transferMinutes = transferTime
        val totalMinutes = firstMinutes + secondMinutes + transferMinutes
        
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        
        return if (hours > 0) {
            "${hours}小时${minutes}分"
        } else {
            "${minutes}分"
        }
    }
    
    private fun parseDurationToMinutes(duration: String): Int {
        return try {
            if (duration.contains("小时")) {
                val parts = duration.split("小时")
                val hours = parts[0].toInt()
                val minutes = if (parts.size > 1) {
                    parts[1].replace("分钟", "").trim().toIntOrNull() ?: 0
                } else 0
                hours * 60 + minutes
            } else {
                duration.replace("分钟", "").trim().toIntOrNull() ?: 0
            }
        } catch (e: Exception) {
            0
        }
    }
    
    private fun setupSeatTypeSelection() {
        binding.llSeatTypeButtons.removeAllViews()
        val seatTypes = SeatTypes.forTrain(train).map { it to it.name }
        for ((seatType, displayName) in seatTypes) {
            val seatTypeButton = com.google.android.material.button.MaterialButton(this)
            val availabilityLabel = if (isTransfer) {
                val stocks = transferSeatAvailability(seatType.name)
                if (stocks == null) "" else {
                    " · ${transferStockLabel(stocks.first)}/${transferStockLabel(stocks.second)}"
                }
            } else {
                val availability = seatInventoryRepository.getAvailability(train, departureDate, seatType.name)
                " · ${availability.displayLabel}"
            }
            seatTypeButton.text = "$displayName$availabilityLabel"
            seatTypeButton.textSize = 12f
            seatTypeButton.minWidth = 0
            seatTypeButton.height = 60
            seatTypeButton.insetTop = 0
            seatTypeButton.insetBottom = 0
            seatTypeButton.setPadding(dp(8), dp(8), dp(8), dp(8))
            val layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                weight = 1f
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            seatTypeButton.layoutParams = layoutParams
            styleGlassChoice(seatTypeButton, seatType == selectedSeatType)
            seatTypeButton.setOnClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                for (i in 0 until binding.llSeatTypeButtons.childCount) {
                    val button = binding.llSeatTypeButtons.getChildAt(i) as com.google.android.material.button.MaterialButton
                    styleGlassChoice(button, false)
                }
                styleGlassChoice(seatTypeButton, true)
                selectedSeatType = seatType
                resetSeatSelections()
                updateSeatNumbers()
                updatePrice()
                setupSleeperPreference()
                updateContiguousSeatingUI()
            }
            
            binding.llSeatTypeButtons.addView(seatTypeButton)
        }
    }
    
    private fun setupSeatNumberSelection() {
        updateSeatNumbers()
    }
    private fun selectedAvailability() = seatInventoryRepository.getAvailability(
        train,
        departureDate,
        selectedSeatType.name
    )

    
    private fun transferSeatAvailability(seatType: String): Pair<SeatAvailability, SeatAvailability>? {
        val first = firstLeg ?: return null
        val second = secondLeg ?: return null
        return seatInventoryRepository.getAvailability(first, departureDate, seatType) to
            seatInventoryRepository.getAvailability(second, departureDate, seatType)
    }

    private fun transferStockLabel(stock: SeatAvailability): String = stock.displayLabel

    private fun updateSeatNumbers() {
        if (isTransfer) {
            val stocks = transferSeatAvailability(selectedSeatType.name)
            if (stocks == null) return
            if (stocks.first.requiresWaitlist || stocks.second.requiresWaitlist) {
                val message = "该席别两程需同时有余票；当前 ${transferStockLabel(stocks.first)} / ${transferStockLabel(stocks.second)}，请换方案或席别"
                listOf(binding.llFirstLegSeatButtons, binding.llSecondLegSeatButtons).forEach { container ->
                    container.removeAllViews()
                    android.widget.TextView(this).apply {
                        text = message
                        setTextColor(getColor(R.color.railway_orange))
                        textSize = 13f
                        setPadding(0, 8, 0, 8)
                        container.addView(this)
                    }
                }
                binding.btnSubmitOrder.text = "中转方案不可出票"
            } else {
                binding.btnSubmitOrder.text = "提交订单"
                populateSeatButtons(binding.llFirstLegSeatButtons, selectedSeatNumberFirst) { seat ->
                    selectedSeatNumberFirst = seat
                }
                populateSeatButtons(binding.llSecondLegSeatButtons, selectedSeatNumberSecond) { seat ->
                    selectedSeatNumberSecond = seat
                }
            }
        } else if (selectedAvailability().requiresWaitlist) {
            binding.llSeatButtons.removeAllViews()
            android.widget.TextView(this).apply {
                text = "该席别暂无余票，提交后将进入候补流程"
                setTextColor(getColor(R.color.railway_orange))
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 12, 0, 12)
                binding.llSeatButtons.addView(this)
            }
            binding.btnSubmitOrder.text = "提交候补"
        } else {
            binding.btnSubmitOrder.text = "提交订单"
            populateSeatButtons(binding.llSeatButtons, selectedSeatNumber) { seat ->
                selectedSeatNumber = seat
            }
        }

        // 无座空座智能指引
        if (selectedSeatType.name.contains("无座")) {
            binding.cardUnreservedSeatGuide.visibility = View.VISIBLE
            val dep = train.departureStation
            val arr = train.arrivalStation
            binding.tvUnreservedGuideContent.text = "① 03车 11A：$dep 站发车后空闲（预计可坐约 35 分钟）\n② 05车 08F：中途停靠站间空闲（预计可坐约 40 分钟）\n温馨提示：后续车站持对号座车票乘客上车时，请主动礼貌让座。"
        } else {
            binding.cardUnreservedSeatGuide.visibility = View.GONE
        }

        val isWaitlist = if (isTransfer) {
            val stocks = transferSeatAvailability(selectedSeatType.name)
            stocks != null && (stocks.first.requiresWaitlist || stocks.second.requiresWaitlist)
        } else {
            selectedAvailability().requiresWaitlist
        }
        binding.cardWaitlistOptions.visibility = if (isWaitlist) View.VISIBLE else View.GONE
    }

    private fun populateSeatButtons(
        container: LinearLayout,
        preselectedSeat: String,
        onSeatSelected: (String) -> Unit
    ) {
        container.removeAllViews()

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
            seatButton.isSelected = seatLetter == preselectedSeat
            styleGlassChoice(seatButton, seatButton.isSelected)

            seatButton.setOnClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                for (i in 0 until container.childCount) {
                    val child = container.getChildAt(i) as? com.google.android.material.button.MaterialButton
                    child?.isSelected = false
                    child?.let { styleGlassChoice(it, false) }
                }
                seatButton.isSelected = true
                styleGlassChoice(seatButton, true)
                onSeatSelected(seatLetter)
                updateContiguousSeatingUI()
            }

            container.addView(seatButton)
        }
    }

    private fun seatPositionLabel(seatLetter: String): String = when (seatLetter) {
        "A", "F" -> "靠窗"
        "C", "D" -> "走廊"
        "上" -> "上铺"
        "中" -> "中铺"
        "下" -> "下铺"
        else -> "中间"
    }

    private fun setupSleeperPreference() {
        val isSleeper = selectedSeatType.name.contains("卧")
        if (isSleeper) {
            binding.cardSleeperPreference.visibility = View.VISIBLE
            updateSleeperButtonsUI()
            binding.btnPrefLowerBerth.setOnClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                selectedSleeperPreference = "下铺"
                updateSleeperButtonsUI()
            }
            binding.btnPrefMiddleBerth.setOnClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                selectedSleeperPreference = "中铺"
                updateSleeperButtonsUI()
            }
            binding.btnPrefUpperBerth.setOnClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                selectedSleeperPreference = "上铺"
                updateSleeperButtonsUI()
            }
            if (selectedSeatType.name.contains("软卧")) {
                binding.btnPrefMiddleBerth.visibility = View.GONE
            } else {
                binding.btnPrefMiddleBerth.visibility = View.VISIBLE
            }
        } else {
            binding.cardSleeperPreference.visibility = View.GONE
        }
    }

    private fun updateSleeperButtonsUI() {
        styleGlassChoice(binding.btnPrefLowerBerth, selectedSleeperPreference == "下铺")
        styleGlassChoice(binding.btnPrefMiddleBerth, selectedSleeperPreference == "中铺")
        styleGlassChoice(binding.btnPrefUpperBerth, selectedSleeperPreference == "上铺")
        binding.tvSleeperSelectedFeedback.text = "✓ 已优先为您锁定【$selectedSleeperPreference】偏好（若无余票将自动顺配其他铺位）"
    }

    private fun updateContiguousSeatingUI() {
        val count = if (isManualInput) 1 else selectedPassengers.size
        if (count >= 2 && !selectedSeatType.name.contains("无座")) {
            binding.cardContiguousSeating.visibility = View.VISIBLE
            val prefLetter = selectedSeatNumber.ifEmpty { "A" }
            val plan = FamilySeatAllocator.plan(selectedSeatType.name, prefLetter, 5, count)
            binding.tvContiguousBadge.text = plan.label
            val passengers = getPassengersForOrder()
            val seatPairs = passengers.mapIndexed { idx, p ->
                val seat = plan.seatNumbers.getOrElse(idx) { "05A" }
                "${p.name} ($seat)"
            }
            binding.tvContiguousSeatsPreview.text = "智能连座预选：${seatPairs.joinToString(" · ")}"
        } else {
            binding.cardContiguousSeating.visibility = View.GONE
        }
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

    private fun resetSeatSelections() {
        selectedSeatNumber = ""
        selectedSeatNumberFirst = ""
        selectedSeatNumberSecond = ""
    }
    
    private fun updatePrice() {
        val perPassengerPrice = basePrice * selectedSeatType.multiplier
        val passengerCount = if (isManualInput) 1 else selectedPassengers.size.coerceAtLeast(1)
        val totalPrice = perPassengerPrice * passengerCount
        
        binding.tvSeatTypePrice.text = "${selectedSeatType.name} × ${selectedSeatType.multiplier}"
        binding.tvTotalPrice.text = "¥${totalPrice.toInt()}"
    }
    
    private fun submitOrder() {
        val passengers = getPassengersForOrder()
        val primaryPassenger = passengers.firstOrNull()
        val (passengerName, passengerIdCard, passengerPhone) = primaryPassenger?.let { Triple(it.name, it.idCard, it.phone) }
            ?: Triple("", "", "")
        
        if (passengerName.isEmpty() || passengerIdCard.isEmpty()) {
            Toast.makeText(this, "请填写完整的乘客信息", Toast.LENGTH_SHORT).show()
            return
        }
        if (passengers.size > 5) {
            Toast.makeText(this, "单次最多购买 5 张同行车票", Toast.LENGTH_SHORT).show()
            return
        }
        if (isTransfer && passengers.size > 1) {
            Toast.makeText(this, "中转换乘暂仅支持单人购票，请分开下单", Toast.LENGTH_LONG).show()
            return
        }
        val requiresWaitlist = !isTransfer && selectedAvailability().requiresWaitlist
        if (requiresWaitlist && passengers.size > 1) {
            Toast.makeText(this, "当前席别暂无余票，暂仅支持单人候补", Toast.LENGTH_LONG).show()
            return
        }
        val transferStocks = if (isTransfer) transferSeatAvailability(selectedSeatType.name) else null
        if (transferStocks != null && (transferStocks.first.requiresWaitlist || transferStocks.second.requiresWaitlist)) {
            Toast.makeText(this, "中转换乘必须两程均有余票；请更换席别或其他方案", Toast.LENGTH_LONG).show()
            return
        }
        val availableTickets = if (isTransfer) {
            minOf(transferStocks?.first?.availableSeats ?: 0, transferStocks?.second?.availableSeats ?: 0)
        } else {
            selectedAvailability().availableSeats
        }
        if (!requiresWaitlist && availableTickets < passengers.size) {
            Toast.makeText(this, "余票仅剩 ${availableTickets} 张，不足 ${passengers.size} 人，整单无法出票", Toast.LENGTH_LONG).show()
            return
        }
        if (!isTransfer && !requiresWaitlist && passengers.size > 5) {
            Toast.makeText(this, "单次最多安排 5 人同行", Toast.LENGTH_LONG).show()
            return
        }
        
        if (isTransfer) {
            if (selectedSeatNumberFirst.isEmpty() || selectedSeatNumberSecond.isEmpty()) {
                Toast.makeText(this, "请分别选择两段车次的座位", Toast.LENGTH_SHORT).show()
                return
            }
        } else {
            if (!requiresWaitlist && selectedSeatNumber.isEmpty()) {
                Toast.makeText(this, "请选择座位", Toast.LENGTH_SHORT).show()
                return
            }
        }
        
        // 验证身份证格式
        if (passengerIdCard.length != 18) {
            Toast.makeText(this, "身份证号格式不正确", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 如果是手动输入且填写了手机号，验证手机号格式
        if (isManualInput && passengerPhone.isNotEmpty() && passengerPhone.length != 11) {
            Toast.makeText(this, "手机号格式不正确", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 保存新建的乘客信息
        saveNewPassenger(passengerName, passengerIdCard, passengerPhone)
        
        val finalPrice = basePrice * selectedSeatType.multiplier
        
        // 跳转到订单确认页面
        val intent = Intent(this, OrderConfirmActivity::class.java)
        
        if (isTransfer) {
            // 中转车票：传递中转信息
            intent.putExtra("isTransfer", true)
            intent.putExtra("firstLeg", firstLeg)
            intent.putExtra("secondLeg", secondLeg)
            intent.putExtra("transferStation", transferStation)
            intent.putExtra("transferTime", transferTime)
            intent.putExtra("totalPrice", totalPrice)
            intent.putExtra("seatNumber", selectedSeatNumberFirst)
            intent.putExtra("firstLegSeatLetter", selectedSeatNumberFirst)
            intent.putExtra("secondLegSeatLetter", selectedSeatNumberSecond)
        } else {
            // 直达车票：传递普通信息
            intent.putExtra("isTransfer", false)
            intent.putExtra("train", train)
            intent.putExtra("seatNumber", if (requiresWaitlist) selectedSeatType.availableSeats.first() else selectedSeatNumber)
        }
        
        intent.putExtra("departureDate", departureDate)
        intent.putExtra("seatType", selectedSeatType.name)
        intent.putExtra("passengerName", passengerName)
        intent.putExtra("passengerIdCard", passengerIdCard)
        intent.putExtra("passengerPhone", passengerPhone)
        intent.putExtra("groupPassengers", ArrayList(passengers))
        intent.putExtra("basePrice", basePrice)
        intent.putExtra("finalPrice", finalPrice)
        intent.putExtra("requiresWaitlist", requiresWaitlist)
        intent.putExtra("isQuietCar", binding.switchQuietCar.isChecked)
        intent.putExtra("sleeperPreference", selectedSleeperPreference)
        if (requiresWaitlist) {
            intent.putExtra("crossClassWaitlist", binding.switchCrossClassWaitlist.isChecked)
            intent.putExtra("unreservedWaitlist", binding.switchUnreservedWaitlist.isChecked)
            val deadlineText = when {
                binding.rbDeadline6h.isChecked -> "发车前6小时"
                binding.rbDeadline24h.isChecked -> "发车前24小时"
                else -> "发车前2小时"
            }
            intent.putExtra("waitlistDeadline", deadlineText)
        }
        startActivity(intent)
    }
    
    /**
     * 设置乘客选择功能
     */
    private fun setupPassengerSelection() {
        binding.btnSelectPassenger.setOnClickListener {
            showPassengerSelectionDialog()
        }
        
        // 默认显示手动输入
        showManualInput()
    }
    
    /**
     * 显示乘客选择对话框
     */
    private fun showPassengerSelectionDialog() {
        val currentUser = userRepository.getCurrentUser()
        if (currentUser == null) {
            Toast.makeText(this, "用户未登录", Toast.LENGTH_SHORT).show()
            return
        }
        val passengers = passengerRepository.getPassengersByUserId(currentUser.id)
        if (passengers.isEmpty()) {
            Toast.makeText(this, "暂无保存的乘车人，请手动输入", Toast.LENGTH_SHORT).show()
            return
        }
        val checked = passengers.map { it.id in selectedPassengers.map(Passenger::id) }.toBooleanArray()
        val dialogBinding = DialogPassengerSelectionBinding.inflate(layoutInflater)
        passengers.forEachIndexed { index, passenger ->
            val row = com.google.android.material.button.MaterialButton(this).apply {
                minWidth = 0
                height = dp(58)
                insetTop = 0
                insetBottom = 0
                gravity = android.view.Gravity.CENTER_VERTICAL
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                setPadding(dp(16), dp(6), dp(12), dp(6))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
                renderPassengerChoice(passenger, checked[index])
                setOnClickListener {
                    checked[index] = !checked[index]
                    renderPassengerChoice(passenger, checked[index])
                }
            }
            dialogBinding.llPassengerChoices.addView(row)
        }
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        }
        dialogBinding.btnManualInput.setOnClickListener {
            dialog.dismiss()
            showManualInput()
            updatePrice()
        }
        dialogBinding.btnConfirmPassengers.setOnClickListener {
            val selected = passengers.filterIndexed { index, _ -> checked[index] }
            when {
                selected.isEmpty() -> Toast.makeText(this, "请至少选择一位乘车人", Toast.LENGTH_SHORT).show()
                selected.size > 5 -> Toast.makeText(this, "单次最多选择 5 位同行乘车人", Toast.LENGTH_SHORT).show()
                else -> {
                    selectPassengers(selected)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun com.google.android.material.button.MaterialButton.renderPassengerChoice(
        passenger: Passenger,
        selected: Boolean
    ) {
        val family = userRepository.getCurrentUser()?.let { user -> familyAccountRepository.memberForPassenger(user.id, passenger.id) }
        val detail = family?.let { "${it.relation} · ${it.travelerType} · ${it.benefitLabel}" }
            ?: "身份证尾号 ${passenger.idCard.takeLast(4)}"
        text = if (selected) {
            "✓  ${passenger.name}\n    $detail"
        } else {
            "    ${passenger.name}\n    $detail"
        }
        styleGlassChoice(this, selected)
    }

    private fun selectPassengers(passengers: List<Passenger>) {
        selectedPassengers = passengers
        isManualInput = false
        binding.llSelectedPassenger.visibility = View.VISIBLE
        binding.llManualInput.visibility = View.GONE
        if (passengers.size == 1) {
            val p = passengers.first()
            val idMasked = if (p.idCard.length >= 8) {
                "${p.idCard.take(4)}********${p.idCard.takeLast(4)}"
            } else {
                p.idCard
            }
            binding.tvSelectedPassengerName.text = p.name
            binding.tvSelectedPassengerIdCard.text = "居民身份证 $idMasked\n${FamilySeatAllocator.describe(selectedSeatType.name, passengers.size)}"
        } else {
            binding.tvSelectedPassengerName.text = "已选择 ${passengers.size} 位同行乘车人"
            binding.tvSelectedPassengerIdCard.text = passengers.joinToString("、") { it.name } +
                "\n" + FamilySeatAllocator.describe(selectedSeatType.name, passengers.size)
        }
        binding.etPassengerName.setText("")
        binding.etPassengerIdCard.setText("")
        binding.etPassengerPhone.setText("")
        updatePrice()
        updateContiguousSeatingUI()
    }
    
    /**
     * 显示手动输入
     */
    private fun showManualInput() {
        selectedPassengers = emptyList()
        isManualInput = true
        
        binding.llSelectedPassenger.visibility = View.GONE
        binding.llManualInput.visibility = View.VISIBLE
        updateContiguousSeatingUI()
    }
    
    /**
     * 获取乘客信息
     */
    private fun getPassengerInfo(): Triple<String, String, String> = getPassengersForOrder().firstOrNull()?.let {
        Triple(it.name, it.idCard, it.phone)
    } ?: Triple("", "", "")

    private fun getPassengersForOrder(): List<Passenger> {
        if (!isManualInput) return selectedPassengers
        val name = binding.etPassengerName.text.toString().trim()
        val idCard = binding.etPassengerIdCard.text.toString().trim()
        val phone = binding.etPassengerPhone.text.toString().trim()
        if (name.isEmpty() || idCard.isEmpty()) return emptyList()
        return listOf(
            Passenger(
                id = "MANUAL_${System.currentTimeMillis()}",
                userId = userRepository.getCurrentUser()?.id.orEmpty(),
                name = name,
                idCard = idCard,
                phone = phone
            )
        )
    }
    
    /**
     * 保存新建的乘客信息
     */
    private fun saveNewPassenger(name: String, idCard: String, phone: String) {
        if (isManualInput && name.isNotEmpty() && idCard.isNotEmpty() && phone.isNotEmpty()) {
            val currentUser = userRepository.getCurrentUser()
            if (currentUser != null) {
                val passenger = Passenger(
                    id = "PASSENGER_${System.currentTimeMillis()}",
                    userId = currentUser.id,
                    name = name,
                    idCard = idCard,
                    phone = phone,
                    addTime = System.currentTimeMillis().toString()
                )
                passengerRepository.addPassenger(passenger)
            }
        }
    }

    private var selectedCarriageIndex = 1 // 默认推荐 02 车厢

    private fun setupCarriageHeatmap() {
        val trainNumber = if (isTransfer) (firstLeg?.number ?: "G1") else train.number
        val carriages = CarriageCrowdData.getCarriages(trainNumber)
        binding.llCarriageList.removeAllViews()

        carriages.forEachIndexed { index, car ->
            val carBtn = com.google.android.material.button.MaterialButton(this).apply {
                minWidth = 0
                width = dp(76)
                height = dp(62)
                insetTop = 0
                insetBottom = 0
                setPadding(dp(4), dp(4), dp(4), dp(4))
                layoutParams = LinearLayout.LayoutParams(dp(76), dp(62)).apply {
                    marginEnd = dp(8)
                }
                renderCarriageButton(this, car, index == selectedCarriageIndex)
                setOnClickListener {
                    it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    selectedCarriageIndex = index
                    for (i in 0 until binding.llCarriageList.childCount) {
                        val child = binding.llCarriageList.getChildAt(i) as? com.google.android.material.button.MaterialButton
                        val item = carriages.getOrNull(i)
                        if (child != null && item != null) {
                            renderCarriageButton(child, item, i == selectedCarriageIndex)
                        }
                    }
                    updateCarriageSummary(car)
                }
            }
            binding.llCarriageList.addView(carBtn)
        }
        carriages.getOrNull(selectedCarriageIndex)?.let { updateCarriageSummary(it) }
    }

    private fun renderCarriageButton(
        btn: com.google.android.material.button.MaterialButton,
        car: CarriageCrowdData.CarriageInfo,
        isSelected: Boolean
    ) {
        val crowdDot = when (car.crowd) {
            CarriageCrowdData.CrowdLevel.SPACIOUS -> "🟢"
            CarriageCrowdData.CrowdLevel.MODERATE -> "🟡"
            CarriageCrowdData.CrowdLevel.BUSY -> "🟠"
        }
        val specialTag = when {
            car.isQuietCar -> "🔕"
            car.isDiningCar -> "☕"
            car.hasLuggageRack -> "🧳"
            else -> ""
        }
        btn.text = "${car.carNumber}车 $specialTag\n${car.carType}\n$crowdDot ${car.crowd.label}"
        btn.textSize = 10.5f
        btn.cornerRadius = dp(14)
        btn.strokeWidth = dp(1)
        btn.strokeColor = android.content.res.ColorStateList.valueOf(
            android.graphics.Color.parseColor(if (isSelected) "#007AFF" else "#B8FFFFFF")
        )
        btn.backgroundTintList = android.content.res.ColorStateList.valueOf(
            android.graphics.Color.parseColor(if (isSelected) "#E1F0FF" else "#99FFFFFF")
        )
        btn.setTextColor(getColor(if (isSelected) R.color.railway_blue_deep else R.color.text_primary))
        btn.elevation = 0f
    }

    private fun updateCarriageSummary(car: CarriageCrowdData.CarriageInfo) {
        binding.tvSelectedCarriageSummary.text = "${car.carNumber}车 · ${car.carType} · ${car.crowd.label}"
        val facility = buildString {
            append("当前车厢配备：")
            if (car.hasLuggageRack) append("🧳大件行李架 · ")
            if (car.isQuietCar) append("🔕静音车厢 · ")
            if (car.isDiningCar) append("☕餐吧吧台 · ")
            if (car.hasAccessibleToilet) append("♿无障碍洗手间 · ")
            append("🔌全列AC/USB电源 · 🚰冷热饮用水")
        }
        binding.tvCarriageFacilityDetail.text = facility
    }

    private fun setupSplitTicketing() {
        if (isTransfer) {
            binding.cardSplitTicketing.visibility = View.GONE
            return
        }
        val plan = SplitTicketingPlanner.findSameTrainSplitPlan(train, departureDate)
        if (plan != null) {
            binding.cardSplitTicketing.visibility = View.VISIBLE
            binding.tvSplitPlanDesc.text = "前段：${plan.firstLeg.fromStation} ➔ ${plan.firstLeg.toStation} (${plan.firstLeg.carriage} ${plan.firstLeg.seatType} · ${plan.firstLeg.statusText})\n后段：${plan.secondLeg.fromStation} ➔ ${plan.secondLeg.toStation} (${plan.secondLeg.carriage} · ${plan.secondLeg.statusText})\n💡 ${plan.tip}"
            binding.tvSplitPlanPrice.text = "分段总计：¥${plan.totalPrice.toInt()} (同趟车免下车)"
            binding.btnAdoptSplitPlan.setOnClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                Toast.makeText(
                    this,
                    "已采纳同车分段方案！前段 ${plan.firstLeg.seatNumber}，后段到 ${plan.intermediateStation} 车内续乘",
                    Toast.LENGTH_LONG
                ).show()
                binding.btnAdoptSplitPlan.text = "✓ 已采纳分段方案"
                binding.btnAdoptSplitPlan.isEnabled = false
            }
        } else {
            binding.cardSplitTicketing.visibility = View.GONE
        }
    }
}
