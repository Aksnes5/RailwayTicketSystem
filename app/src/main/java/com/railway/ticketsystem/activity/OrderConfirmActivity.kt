package com.railway.ticketsystem.activity

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.railway.ticketsystem.R
import com.railway.ticketsystem.MainActivity
import com.railway.ticketsystem.data.DepartureTimingPolicy
import com.railway.ticketsystem.data.SeatInventoryRepository
import com.railway.ticketsystem.data.OrderRepository
import com.railway.ticketsystem.data.OrderTimetableFactory
import com.railway.ticketsystem.data.PaymentLifecycle
import com.railway.ticketsystem.data.PointsPolicy
import com.railway.ticketsystem.data.TravelAssistant
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.data.WaitlistRepository
import com.railway.ticketsystem.databinding.ActivityOrderConfirmBinding
import com.railway.ticketsystem.model.Order
import com.railway.ticketsystem.model.Passenger
import com.railway.ticketsystem.model.Train
import java.text.SimpleDateFormat
import java.util.*

class OrderConfirmActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityOrderConfirmBinding
    private lateinit var train: Train
    private lateinit var departureDate: String
    private lateinit var seatType: String
    private var seatNumber: String = ""
    private var carNumber: String = ""
    private var selectedSeatLetter: String = ""
    private lateinit var passengerName: String
    private lateinit var passengerIdCard: String
    private lateinit var passengerPhone: String
    private var groupPassengers: List<Passenger> = emptyList()
    private var groupSeatNumbers: List<String> = emptyList()
    private var basePrice = 0.0
    private var finalPrice = 0.0
    
    private var requiresWaitlist = false
    // 中转车票相关
    private var isTransfer = false
    private var firstLeg: Train? = null
    private var secondLeg: Train? = null
    private var transferStation: String? = null
    private var transferTime: Int = 0
    private var totalPrice: Double = 0.0
    private var firstLegSeatLetter: String? = null
    private var secondLegSeatLetter: String? = null
    private var firstLegSeatNumber: String? = null
    private var secondLegSeatNumber: String? = null
    private var firstLegCarNumber: String? = null
    private var secondLegCarNumber: String? = null
    
    private lateinit var orderRepository: OrderRepository
    private lateinit var userRepository: UserRepository
    private lateinit var waitlistRepository: WaitlistRepository
    private lateinit var seatInventoryRepository: SeatInventoryRepository
    private lateinit var paymentLifecycle: PaymentLifecycle
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderConfirmBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 获取传递的订单信息
        isTransfer = intent.getBooleanExtra("isTransfer", false)
        departureDate = intent.getStringExtra("departureDate") ?: ""
        seatType = intent.getStringExtra("seatType") ?: ""
        selectedSeatLetter = intent.getStringExtra("seatNumber") ?: ""
        carNumber = intent.getStringExtra("carNumber") ?: ""
        passengerName = intent.getStringExtra("passengerName") ?: ""
        passengerIdCard = intent.getStringExtra("passengerIdCard") ?: ""
        passengerPhone = intent.getStringExtra("passengerPhone") ?: ""
        basePrice = intent.getDoubleExtra("basePrice", 0.0)
        finalPrice = intent.getDoubleExtra("finalPrice", 0.0)
        requiresWaitlist = intent.getBooleanExtra("requiresWaitlist", false)
        @Suppress("DEPRECATION")
        groupPassengers = (intent.getSerializableExtra("groupPassengers") as? ArrayList<*>)
            ?.filterIsInstance<Passenger>()
            .orEmpty()
        if (groupPassengers.isEmpty() && passengerName.isNotBlank()) {
            groupPassengers = listOf(Passenger("single", "", passengerName, passengerIdCard, passengerPhone))
        }
        
        if (isTransfer) {
            // 处理中转车票
            firstLeg = intent.getSerializableExtra("firstLeg") as? Train
            secondLeg = intent.getSerializableExtra("secondLeg") as? Train
            transferStation = intent.getStringExtra("transferStation")
            transferTime = intent.getIntExtra("transferTime", 0)
            totalPrice = intent.getDoubleExtra("totalPrice", 0.0)
            firstLegSeatLetter = intent.getStringExtra("firstLegSeatLetter") ?: selectedSeatLetter
            secondLegSeatLetter = intent.getStringExtra("secondLegSeatLetter") ?: selectedSeatLetter
            
            // 创建虚拟的Train对象用于显示
            if (firstLeg != null && secondLeg != null) {
                train = Train(
                    number = "中转${firstLeg!!.number}→${secondLeg!!.number}",
                    departureStation = firstLeg!!.departureStation,
                    arrivalStation = secondLeg!!.arrivalStation,
                    departureTime = firstLeg!!.departureTime,
                    arrivalTime = secondLeg!!.arrivalTime,
                    duration = calculateTotalDuration(firstLeg!!.duration, secondLeg!!.duration),
                    price = totalPrice,
                    availableSeats = minOf(firstLeg!!.availableSeats, secondLeg!!.availableSeats)
                )
            } else {
                throw IllegalArgumentException("中转车次信息不完整")
            }
        } else {
            // 处理直达车票
            train = intent.getSerializableExtra("train") as Train
        }
        
        orderRepository = OrderRepository(this)
        userRepository = UserRepository(this)
        waitlistRepository = WaitlistRepository(this)
        seatInventoryRepository = SeatInventoryRepository(this)
        paymentLifecycle = PaymentLifecycle(this)
        
        setupUI()
    }
    
    private fun setupUI() {
        // 设置订单信息
        binding.tvOrderTrainInfo.text = "${train.number} ${train.departureStation} → ${train.arrivalStation}"
        binding.tvOrderTimeInfo.text = "$departureDate ${train.departureTime} - ${train.arrivalTime} | ${train.duration}"
        
        binding.tvOrderSeatType.text = seatType

        if (isTransfer) {
            setupTransferSeatInfo()
        } else {
            setupDirectSeatInfo()
        }
        
        val passengers = passengersForOrder()
        binding.tvOrderPassengerName.text = if (passengers.size == 1) passengerName else {
            "同行 ${passengers.size} 人：${passengers.joinToString("、") { it.name }}"
        }
        binding.tvOrderPassengerIdCard.text = if (passengers.size == 1) passengerIdCard else {
            "同车厢相邻座位 · 共 ${passengers.size} 张车票"
        }
        
        binding.tvOrderBasePrice.text = "¥${basePrice.toInt()}"
        binding.tvOrderSeatTypePrice.text = "$seatType × ${getSeatTypeMultiplier(seatType)}"
        binding.tvOrderTotalPrice.text = "¥${(finalPrice * passengers.size).toInt()}"
        
        // 设置按钮事件
        binding.btnPay.text = if (requiresWaitlist) "提交候补" else "提交订单"
        binding.btnPay.setOnClickListener {
            processPayment()
        }
        
        binding.btnBackOrder.setOnClickListener { finish() }
        binding.btnCancel.setOnClickListener {
            finish()
        }
    }
    
    private fun setupDirectSeatInfo() {
        if (requiresWaitlist) {
            carNumber = "候补"
            seatNumber = "候补"
            groupSeatNumbers = listOf(seatNumber)
            binding.tvOrderSeatNumber.text = "候补 $seatType（兑现后自动分配座位）"
            return
        }

        carNumber = generateCarNumber()
        val passengers = passengersForOrder()
        val row = (1..20).random()
        groupSeatNumbers = seatLettersForType(selectedSeatLetter, passengers.size).map { letter ->
            "${String.format("%02d", row)}$letter"
        }
        seatNumber = groupSeatNumbers.firstOrNull().orEmpty()
        binding.tvOrderSeatNumber.text = if (passengers.size == 1) {
            "${carNumber}车$seatNumber"
        } else {
            "${carNumber}车同排：${groupSeatNumbers.joinToString("、")}" 
        }
    }

    private fun passengersForOrder(): List<Passenger> = groupPassengers.ifEmpty {
        listOf(Passenger("single", "", passengerName, passengerIdCard, passengerPhone))
    }

    private fun seatLettersForType(preferred: String, count: Int): List<String> {
        val letters = when (seatType) {
            "一等座" -> listOf("A", "B", "D", "F")
            "商务座" -> listOf("A", "F")
            else -> listOf("A", "B", "C", "D", "F")
        }
        val start = letters.indexOf(preferred).takeIf { it >= 0 } ?: 0
        return (0 until count).map { letters[(start + it) % letters.size] }
    }

    private fun setupTransferSeatInfo() {
        firstLegCarNumber = generateCarNumber()
        secondLegCarNumber = generateCarNumber()
        firstLegSeatNumber = generateSeatNumber(firstLegSeatLetter)
        secondLegSeatNumber = generateSeatNumber(secondLegSeatLetter)

        val firstLegLabel = firstLeg?.number ?: ""
        val secondLegLabel = secondLeg?.number ?: ""
        val firstSeatInfo = "${firstLegCarNumber}车${firstLegSeatNumber}"
        val secondSeatInfo = "${secondLegCarNumber}车${secondLegSeatNumber}"

        binding.tvOrderSeatNumber.text = "第一程 $firstLegLabel：$firstSeatInfo\n第二程 $secondLegLabel：$secondSeatInfo"
    }

    private fun generateCarNumber(): String {
        return (1..16).random().toString()
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
    
    private fun generateSeatNumber(preferredSeatLetter: String? = null): String {
        val seatLetter = preferredSeatLetter?.takeIf { it.isNotEmpty() } ?: listOf("A", "B", "C", "D", "F").random()
        val seatNumber = (1..20).random()
        return "${String.format("%02d", seatNumber)}$seatLetter"
    }
    
    private fun getSeatTypeMultiplier(seatType: String): Double {
        return when (seatType) {
            "二等座" -> 1.0
            "一等座" -> 1.6
            "商务座" -> 3.0
            else -> 1.0
        }
    }
    
    private fun processPayment() {
        // 显示支付确认对话框
        if (requiresWaitlist) {
            submitWaitlistRequest()
            return
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("提交订单")
            .setMessage("将为您保留座位 15 分钟，随后进入支付页面。应付 ¥${(finalPrice * passengersForOrder().size).toInt()}。")
            .setPositiveButton("提交订单") { _, _ ->
                createOrder()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun createOrder() {
        try {
            binding.btnPay.isEnabled = false
            val currentUser = userRepository.getCurrentUser()
            if (currentUser == null) {
                Toast.makeText(this, "用户未登录", Toast.LENGTH_SHORT).show()
                binding.btnPay.isEnabled = true
                return
            }

            // Defence in depth: the list already hides departed trains, but a stale screen
            // or a re-entered intent must not be able to sell one either.
            val departedTrain = if (isTransfer) firstLeg else train
            if (departedTrain != null && !DepartureTimingPolicy.isBookable(departureDate, departedTrain.departureTime)) {
                Toast.makeText(this, "该车次已发车，无法购票，请重新查询", Toast.LENGTH_LONG).show()
                binding.btnPay.isEnabled = true
                return
            }

            val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val paymentDeadline = System.currentTimeMillis() + PaymentLifecycle.PAYMENT_WINDOW_MILLIS
            
            if (isTransfer && firstLeg != null && secondLeg != null) {
                val firstStock = seatInventoryRepository.getAvailability(firstLeg!!, departureDate, seatType)
                val secondStock = seatInventoryRepository.getAvailability(secondLeg!!, departureDate, seatType)
                if (firstStock.requiresWaitlist || secondStock.requiresWaitlist) {
                    val unavailableLegs = buildList {
                        if (firstStock.requiresWaitlist) add("第一程")
                        if (secondStock.requiresWaitlist) add("第二程")
                    }.joinToString("、")
                    Toast.makeText(this, "$unavailableLegs 暂无余票，中转方案不可部分出票", Toast.LENGTH_LONG).show()
                    binding.btnPay.isEnabled = true
                    return
                }
                val firstTimetable = OrderTimetableFactory.capture(firstLeg!!)
                val secondTimetable = OrderTimetableFactory.capture(secondLeg!!)

                // 两段车票作为同一中转换乘行程保存。
                val baseOrderId = "ORDER_${System.currentTimeMillis()}"
                val itineraryId = "ITINERARY_${System.currentTimeMillis()}"
                val paymentBatchId = "PAY_${System.currentTimeMillis()}"
                
                // 第一段车票
                val finalFirstCarNumber = firstLegCarNumber ?: generateCarNumber()
                val finalSecondCarNumber = secondLegCarNumber ?: generateCarNumber()
                val finalFirstSeatNumber = firstLegSeatNumber ?: generateSeatNumber(firstLegSeatLetter)
                val finalSecondSeatNumber = secondLegSeatNumber ?: generateSeatNumber(secondLegSeatLetter)

                val firstLegOrder = Order(
                    "${baseOrderId}_1",
                    currentUser.id,
                    firstLeg!!.number,
                    firstLeg!!.departureStation,
                    firstLeg!!.arrivalStation,
                    firstLeg!!.departureTime,
                    firstTimetable.arrivalTime,
                    departureDate,
                    seatType,
                    finalFirstSeatNumber,
                    finalFirstCarNumber,
                    passengerName,
                    passengerIdCard,
                    passengerPhone,
                    firstLeg!!.price,
                    firstLeg!!.price * getSeatTypeMultiplier(seatType),
                    "待支付",
                    currentTime,
                    null,
                    itineraryId = itineraryId,
                    paymentBatchId = paymentBatchId,
                    paymentDeadlineMillis = paymentDeadline,
                    timetableStops = firstTimetable.stops,
                    timetableDuration = firstTimetable.duration,
                    routeStations = firstTimetable.routeStations,
                    arrivalDate = TravelAssistant.arrivalDateFor(
                        departureDate, firstLeg!!.departureTime, firstTimetable.arrivalTime
                    )
                )
                
                // 第二段车票
                val secondLegOrder = Order(
                    "${baseOrderId}_2",
                    currentUser.id,
                    secondLeg!!.number,
                    secondLeg!!.departureStation,
                    secondLeg!!.arrivalStation,
                    secondLeg!!.departureTime,
                    secondTimetable.arrivalTime,
                    departureDate,
                    seatType,
                    finalSecondSeatNumber,
                    finalSecondCarNumber,
                    passengerName,
                    passengerIdCard,
                    passengerPhone,
                    secondLeg!!.price,
                    secondLeg!!.price * getSeatTypeMultiplier(seatType),
                    "待支付",
                    currentTime,
                    null,
                    itineraryId = itineraryId,
                    paymentBatchId = paymentBatchId,
                    paymentDeadlineMillis = paymentDeadline,
                    timetableStops = secondTimetable.stops,
                    timetableDuration = secondTimetable.duration,
                    routeStations = secondTimetable.routeStations,
                    arrivalDate = TravelAssistant.arrivalDateFor(
                        departureDate, secondLeg!!.departureTime, secondTimetable.arrivalTime
                    )
                )
                
                if (!seatInventoryRepository.reserveSeats(firstLeg!!, departureDate, seatType, 1)) {
                    Toast.makeText(this, "第一程余票已变化，请重新选择方案", Toast.LENGTH_LONG).show()
                    binding.btnPay.isEnabled = true
                    return
                }
                if (!seatInventoryRepository.reserveSeats(secondLeg!!, departureDate, seatType, 1)) {
                    seatInventoryRepository.releaseSeat(firstLegOrder)
                    Toast.makeText(this, "第二程余票已变化，已取消整单出票", Toast.LENGTH_LONG).show()
                    binding.btnPay.isEnabled = true
                    return
                }
                if (!orderRepository.saveOrdersIfSeatsAvailable(listOf(firstLegOrder, secondLegOrder))) {
                    seatInventoryRepository.releaseSeat(firstLegOrder)
                    seatInventoryRepository.releaseSeat(secondLegOrder)
                    Toast.makeText(this, "所选座位刚刚售出，请返回重新选择", Toast.LENGTH_LONG).show()
                    binding.btnPay.isEnabled = true
                    return
                }

                paymentLifecycle.registerPendingBatch(listOf(firstLegOrder, secondLegOrder))
                Toast.makeText(this, "中转订单已提交，座位保留 15 分钟", Toast.LENGTH_LONG).show()
                startActivity(Intent(this, PaymentActivity::class.java).apply {
                    putExtra(PaymentLifecycle.EXTRA_BATCH_ID, paymentBatchId)
                })
                finish()
                
            } else {
                val passengers = passengersForOrder()
                val availability = seatInventoryRepository.getAvailability(train, departureDate, seatType)
                if (requiresWaitlist || availability.requiresWaitlist) {
                    if (passengers.size == 1) {
                        submitWaitlistRequest()
                    } else {
                        Toast.makeText(this, "当前席别暂无余票，暂仅支持单人候补", Toast.LENGTH_LONG).show()
                        binding.btnPay.isEnabled = true
                    }
                    return
                }
                if (passengers.isEmpty() || passengers.size > 5 || availability.availableSeats < passengers.size) {
                    Toast.makeText(this, "余票不足 ${passengers.size} 人同行，整单未出票", Toast.LENGTH_LONG).show()
                    binding.btnPay.isEnabled = true
                    return
                }
                if (groupSeatNumbers.size != passengers.size) {
                    Toast.makeText(this, "同行座位分配失败，请重新选择席别", Toast.LENGTH_LONG).show()
                    binding.btnPay.isEnabled = true
                    return
                }

                val baseOrderId = "ORDER_${System.currentTimeMillis()}"
                val groupId = if (passengers.size > 1) "GROUP_${System.currentTimeMillis()}" else null
                val paymentBatchId = "PAY_${System.currentTimeMillis()}"
                val timetable = OrderTimetableFactory.capture(train)
                val orders = passengers.mapIndexed { index, passenger ->
                    Order(
                        id = if (groupId == null) baseOrderId else "${baseOrderId}_${index + 1}",
                        userId = currentUser.id,
                        trainNumber = train.number,
                        departureStation = train.departureStation,
                        arrivalStation = train.arrivalStation,
                        departureTime = train.departureTime,
                        arrivalTime = timetable.arrivalTime,
                        departureDate = departureDate,
                        seatType = seatType,
                        seatNumber = groupSeatNumbers[index],
                        carNumber = carNumber,
                        passengerName = passenger.name,
                        passengerIdCard = passenger.idCard,
                        passengerPhone = passenger.phone,
                        basePrice = basePrice,
                        finalPrice = finalPrice,
                        status = "待支付",
                        createTime = currentTime,
                        payTime = null,
                        groupId = groupId,
                        groupPassengerCount = passengers.size,
                        paymentBatchId = paymentBatchId,
                        paymentDeadlineMillis = paymentDeadline,
                        timetableStops = timetable.stops,
                        timetableDuration = timetable.duration,
                        routeStations = timetable.routeStations,
                        arrivalDate = TravelAssistant.arrivalDateFor(
                            departureDate, train.departureTime, timetable.arrivalTime
                        )
                    )
                }
                if (orders.any { waitlistRepository.hasPendingRequestFor(it) }) {
                    if (orders.size == 1) {
                        showWaitlistOffer()
                    } else {
                        Toast.makeText(this, "同行订单包含候补席别，整单未出票", Toast.LENGTH_LONG).show()
                    }
                    binding.btnPay.isEnabled = true
                    return
                }
                if (!seatInventoryRepository.reserveSeats(train, departureDate, seatType, orders.size)) {
                    if (orders.size == 1) showWaitlistOffer()
                    else Toast.makeText(this, "余票已变化，不足 ${orders.size} 人同行，整单未出票", Toast.LENGTH_LONG).show()
                    binding.btnPay.isEnabled = true
                    return
                }
                if (!orderRepository.saveOrdersIfSeatsAvailable(orders)) {
                    orders.forEach { seatInventoryRepository.releaseSeat(it) }
                    Toast.makeText(this, "座位已被占用，请重新选择座位", Toast.LENGTH_LONG).show()
                    binding.btnPay.isEnabled = true
                    return
                }

                paymentLifecycle.registerPendingBatch(orders)
                Toast.makeText(this, "订单已提交，座位保留 15 分钟", Toast.LENGTH_LONG).show()
                startActivity(Intent(this, PaymentActivity::class.java).apply {
                    putExtra(PaymentLifecycle.EXTRA_BATCH_ID, paymentBatchId)
                })
                finish()
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
            binding.btnPay.isEnabled = true
            Toast.makeText(this, "创建订单失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showWaitlistOffer() {
        requiresWaitlist = true
        binding.btnPay.text = "提交候补"
        AlertDialog.Builder(this)
            .setTitle("所选席别已进入候补")
            .setMessage(
                "当前 " + seatType + " 暂无余票，可提交候补。系统将展示兑现概率，并在发车前依据候补规则及席位释放情况处理。"
            )
            .setNegativeButton("返回重选", null)
            .setPositiveButton("提交候补") { _, _ ->
                submitWaitlistRequest()
            }
            .show()
    }

    private fun submitWaitlistRequest() {
        val currentUser = userRepository.getCurrentUser()
        val passenger = passengersForOrder().singleOrNull()
        if (currentUser == null || passenger == null) {
            Toast.makeText(this, "候补信息不完整，请返回重试", Toast.LENGTH_SHORT).show()
            binding.btnPay.isEnabled = true
            return
        }
        // A waitlist closes earlier than sales: there has to be time to actually clear it.
        if (!DepartureTimingPolicy.isWaitlistOpen(departureDate, train.departureTime)) {
            Toast.makeText(this, "距发车不足 30 分钟，无法提交候补，请重新查询", Toast.LENGTH_LONG).show()
            binding.btnPay.isEnabled = true
            return
        }
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val timetable = OrderTimetableFactory.capture(train)
        val requestedOrder = Order(
            id = "WAIT_TEMPLATE_${System.currentTimeMillis()}",
            userId = currentUser.id,
            trainNumber = train.number,
            departureStation = train.departureStation,
            arrivalStation = train.arrivalStation,
            departureTime = train.departureTime,
            arrivalTime = timetable.arrivalTime,
            departureDate = departureDate,
            seatType = seatType,
            seatNumber = "候补",
            carNumber = "候补",
            passengerName = passenger.name,
            passengerIdCard = passenger.idCard,
            passengerPhone = passenger.phone,
            basePrice = basePrice,
            finalPrice = finalPrice,
            status = "候补中",
            createTime = now,
            payTime = "",
            timetableStops = timetable.stops,
            timetableDuration = timetable.duration,
            routeStations = timetable.routeStations,
            arrivalDate = TravelAssistant.arrivalDateFor(
                departureDate, train.departureTime, timetable.arrivalTime
            )
        )
        val request = waitlistRepository.submit(requestedOrder)
        if (request == null) {
            Toast.makeText(this, "该席别已有候补订单，请勿重复提交", Toast.LENGTH_LONG).show()
            binding.btnPay.isEnabled = true
            return
        }
        AlertDialog.Builder(this)
            .setTitle("候补已提交")
            .setMessage(seatType + " 兑现概率 " + request.successProbability + "%\n系统将在发车前完成候补处理，请以实际兑现结果为准。")
            .setPositiveButton("查看候补") { _, _ ->
                startActivity(Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("selectedTab", 1)
                })
                finish()
            }
            .show()
    }
}
