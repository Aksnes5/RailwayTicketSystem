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
import com.railway.ticketsystem.databinding.ActivitySeatSelectionBinding
import com.railway.ticketsystem.databinding.DialogPassengerSelectionBinding
import com.railway.ticketsystem.model.Passenger
import com.railway.ticketsystem.model.SeatTypes
import com.railway.ticketsystem.model.Train
import com.railway.ticketsystem.model.TransferTrain
import java.text.SimpleDateFormat
import java.util.*

class SeatSelectionActivity : ImmersiveActivity() {
    
    private lateinit var binding: ActivitySeatSelectionBinding
    private lateinit var train: Train
    private lateinit var departureDate: String
    private var selectedSeatType = SeatTypes.SECOND_CLASS
    private var selectedSeatNumber = ""
    private var selectedSeatNumberFirst = ""
    private var selectedSeatNumberSecond = ""
    private var basePrice = 0.0
    
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
        setupUI()
        setupSeatTypeSelection()
        setupSeatNumberSelection()
        setupPassengerSelection()
        updatePrice()
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
        
        basePrice = train.price
        binding.tvBasePrice.text = "¥${basePrice.toInt()}"
        
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
                for (i in 0 until binding.llSeatTypeButtons.childCount) {
                    val button = binding.llSeatTypeButtons.getChildAt(i) as com.google.android.material.button.MaterialButton
                    styleGlassChoice(button, false)
                }
                styleGlassChoice(seatTypeButton, true)
                selectedSeatType = seatType
                resetSeatSelections()
                updateSeatNumbers()
                updatePrice()
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
                for (i in 0 until container.childCount) {
                    val child = container.getChildAt(i) as? com.google.android.material.button.MaterialButton
                    child?.isSelected = false
                    child?.let { styleGlassChoice(it, false) }
                }
                seatButton.isSelected = true
                styleGlassChoice(seatButton, true)
                onSeatSelected(seatLetter)
            }

            container.addView(seatButton)
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
        if (!isTransfer && !requiresWaitlist && passengers.size > selectedSeatType.availableSeats.size) {
            Toast.makeText(this, "当前席别无法安排 ${passengers.size} 人同排座位，请更换席别", Toast.LENGTH_LONG).show()
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
        text = if (selected) {
            "✓  ${passenger.name}\n    身份证尾号 ${passenger.idCard.takeLast(4)}"
        } else {
            "    ${passenger.name}\n    身份证尾号 ${passenger.idCard.takeLast(4)}"
        }
        styleGlassChoice(this, selected)
    }

    private fun selectPassengers(passengers: List<Passenger>) {
        selectedPassengers = passengers
        isManualInput = false
        binding.llSelectedPassenger.visibility = View.VISIBLE
        binding.llManualInput.visibility = View.GONE
        binding.tvSelectedPassengerName.text = if (passengers.size == 1) {
            passengers.first().name
        } else {
            "已选择 ${passengers.size} 位同行乘车人"
        }
        binding.tvSelectedPassengerIdCard.text = passengers.joinToString("、") { it.name }
        binding.etPassengerName.setText("")
        binding.etPassengerIdCard.setText("")
        binding.etPassengerPhone.setText("")
        updatePrice()
    }
    
    /**
     * 显示手动输入
     */
    private fun showManualInput() {
        selectedPassengers = emptyList()
        isManualInput = true
        
        binding.llSelectedPassenger.visibility = View.GONE
        binding.llManualInput.visibility = View.VISIBLE
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
}
