package com.railway.ticketsystem.activity

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.railway.ticketsystem.adapter.TrainStopScheduleAdapter
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.railway.ticketsystem.data.MessageRepository
import com.railway.ticketsystem.data.MembershipRepository
import com.railway.ticketsystem.data.PointsPolicy
import com.railway.ticketsystem.R
import com.railway.ticketsystem.data.OrderRepository
import com.railway.ticketsystem.data.PaymentLifecycle
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.data.SeatInventoryRepository
import com.railway.ticketsystem.data.TicketTravelUpdates
import com.railway.ticketsystem.data.TravelReminderScheduler
import com.railway.ticketsystem.data.TrainStopSchedule
import com.railway.ticketsystem.data.TrainSetResolver
import com.railway.ticketsystem.data.TimetableStopStatusResolver
import com.railway.ticketsystem.data.TripProgress
import com.railway.ticketsystem.data.TripLifecycle
import com.railway.ticketsystem.data.TripProgressRepository
import com.railway.ticketsystem.databinding.ActivityTripDetailBinding
import com.railway.ticketsystem.model.Order
import com.railway.ticketsystem.model.TimetableStopSnapshot
import com.railway.ticketsystem.data.TravelAssistant
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

private const val COUNTDOWN_TICK_MILLIS = 60_000L

/**
 * SimpleDateFormat is not thread-safe, so these are only safe because every use on this
 * screen happens on the UI thread. Hoisted out of the helpers so that entering the page
 * does not allocate a formatter per call.
 */
private val DATE_SOURCE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
private val MONTH_DAY_FORMAT = SimpleDateFormat("M月d日", Locale.CHINA)
private val FULL_DATE_FORMAT = SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINA)
private val CLOCK_FORMAT = SimpleDateFormat("HH:mm", Locale.getDefault())
private val DATE_TIME_FORMAT = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
private val DEPARTURE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).apply { isLenient = false }

/**
 * Rendering the voucher code costs a ~1 MB bitmap plus a 262k-iteration pixel loop, and the
 * content is deterministic per order.  This lives outside the activity on purpose: re-entering
 * the screen builds a fresh activity, so an instance field would never hit.
 */
private var cachedQrContent: String? = null
private var cachedQrBitmap: Bitmap? = null

class TripDetailActivity : ImmersiveActivity() {
    
    private lateinit var binding: ActivityTripDetailBinding
    private var order: Order? = null
    private var orderId: String = ""
    private lateinit var orderRepository: OrderRepository
    private lateinit var userRepository: UserRepository
    private lateinit var seatInventoryRepository: SeatInventoryRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var paymentLifecycle: PaymentLifecycle
    private lateinit var membershipRepository: MembershipRepository
    private lateinit var tripProgressRepository: TripProgressRepository
    private lateinit var tripLifecycle: TripLifecycle
    private var itineraryOrders: List<Order> = emptyList()
    private var intentSnapshot: Order? = null
    private var repositoriesReady = false

    /** Order currently backing the journey-status line; refreshed by [countdownTicker]. */
    private var countdownOrder: Order? = null
    private var stopTimetableAdapter: TrainStopScheduleAdapter? = null

    private val countdownTicker = object : Runnable {
        override fun run() {
            countdownOrder?.let { displayed ->
                if (repositoriesReady) {
                    tripLifecycle.archiveArrivedTrips()
                    val latest = loadLatest(showError = false) ?: displayed
                    countdownOrder = latest
                    binding.tvJourneyCountdown.text = TravelAssistant.journeyStatusText(latest)
                    renderStoredTimetable(latest)
                    renderTravelConcierge(latest)
                } else {
                    binding.tvJourneyCountdown.text = TravelAssistant.journeyStatusText(displayed)
                }
            }
            binding.root.postDelayed(this, COUNTDOWN_TICK_MILLIS)
        }
    }

    private data class DestinationQuote(
        val stop: TimetableStopSnapshot,
        val targetIndex: Int,
        val basePrice: Double,
        val finalPrice: Double,
        val duration: String
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTripDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySurfaceSystemBars()

        intentSnapshot = runCatching { legacyOrderExtra() }.getOrNull()
        orderId = intent.getStringExtra("orderId")?.takeIf { it.isNotBlank() }
            ?: intentSnapshot?.id.orEmpty()
        if (orderId.isBlank()) {
            Toast.makeText(this, "订单参数无效", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        repositoriesReady = runCatching {
            orderRepository = OrderRepository(this)
            userRepository = UserRepository(this)
            seatInventoryRepository = SeatInventoryRepository(this)
            messageRepository = MessageRepository(this)
            paymentLifecycle = PaymentLifecycle(this)
            membershipRepository = MembershipRepository(this)
            tripProgressRepository = TripProgressRepository(this)
            tripLifecycle = TripLifecycle(this)
        }.onFailure { Log.e("TripDetail", "订单存储不可用", it) }.isSuccess

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "行程详情"
        if (repositoriesReady) {
            setupClickListeners()
        } else {
            showTripDataUnavailable()
        }
    }

    /**
     * The app theme ships a dark blue status bar with light icons, which clashes with this
     * page's light surface.  Match both bars to the page background and flip the icons to dark,
     * the same treatment the search results screens apply.
     */
    private fun applySurfaceSystemBars() {
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }

    override fun onPause() {
        binding.root.removeCallbacks(countdownTicker)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        binding.root.postDelayed(countdownTicker, COUNTDOWN_TICK_MILLIS)
        if (!repositoriesReady) {
            intentSnapshot?.let(::renderSnapshotSafely) ?: showTripDataUnavailable()
            return
        }
        runCatching { refreshScreen() }.onFailure {
            Log.e("TripDetail", "行程详情加载失败", it)
            intentSnapshot?.let(::renderSnapshotSafely) ?: showTripDataUnavailable()
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyOrderExtra(): Order? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getSerializableExtra("order", Order::class.java)
    } else {
        intent.getSerializableExtra("order") as? Order
    }

    /** Resolve every operation through the signed-in user instead of trusting an Intent snapshot. */
    private fun loadLatest(showError: Boolean = true): Order? {
        val currentUser = userRepository.getCurrentUser()
        if (currentUser == null) {
            if (showError) Toast.makeText(this, "请先登录后查看订单", Toast.LENGTH_SHORT).show()
            return null
        }
        // One read serves both lookups: every getAllOrders() call is a full Gson
        // deserialisation of the whole order file, and this method ran it twice per onResume.
        val all = orderRepository.getAllOrders()
        val latest = all.find { it.id == orderId && it.userId == currentUser.id }
        if (latest == null) {
            if (showError) Toast.makeText(this, "订单不存在或不属于当前账号", Toast.LENGTH_SHORT).show()
            return null
        }
        order = latest
        itineraryOrders = latest.itineraryId
            ?.let { id -> all.filter { it.itineraryId == id && it.userId == currentUser.id } }
            .orEmpty()
        return latest
    }

    private fun refreshScreen() {
        paymentLifecycle.processExpiredPayments()
        // A journey that ended while the app was closed must be archived before it is rendered.
        tripLifecycle.archiveArrivedTrips()
        val latest = loadLatest(showError = false)
        if (latest != null) {
            render(latest)
        } else {
            intentSnapshot?.let(::renderSnapshotSafely) ?: showTripDataUnavailable()
        }
    }

    private fun renderSnapshotSafely(snapshot: Order) {
        runCatching { render(snapshot) }.onFailure {
            Log.e("TripDetail", "订单快照渲染失败", it)
            showTripDataUnavailable()
        }
    }

    private fun showTripDataUnavailable() {
        binding.tvOrderNumber.text = "订单详情暂不可用"
        binding.tvRoute.text = "本地订单数据正在保护中"
        binding.tvItineraryInfo.visibility = View.VISIBLE
        binding.tvItineraryInfo.text = "请稍后返回重试；不会删除您的本地订单记录。"
        binding.btnChangeTicket.isEnabled = false
        binding.btnRefundTicket.isEnabled = false
        binding.btnChangeStation.isEnabled = false
        binding.btnRefundItinerary.isEnabled = false
        binding.btnOrderMeal.isEnabled = false
        binding.cardStopTimetable.visibility = View.GONE
        binding.cardTravelConcierge.visibility = View.GONE
    }

    private fun render(order: Order) {
        binding.tvOrderNumber.text = "订单号：${order.id}"
        binding.tvTravelDate.text = formatFullDate(order.departureDate)

        val itineraryText = if (order.itineraryId != null && itineraryOrders.size >= 2) {
            val legIndex = itineraryOrders.indexOfFirst { it.id == order.id }.coerceAtLeast(0) + 1
            "中转换乘行程 · 第 $legIndex/${itineraryOrders.size} 程 · 两段车票已绑定"
        } else null
        val paymentText = if (order.status == "待支付") {
            "请在 ${formatDateTime(order.paymentDeadlineMillis)} 前完成支付，倒计时不会因退出页面而重置"
        } else null
        binding.tvItineraryInfo.text = listOfNotNull(itineraryText, paymentText).joinToString("\n")
        binding.tvItineraryInfo.visibility =
            if (binding.tvItineraryInfo.text.isNullOrBlank()) View.GONE else View.VISIBLE

        binding.tvTrainNumber.text = order.trainNumber
        binding.tvRoute.text = "${order.departureStation} → ${order.arrivalStation}"
        binding.tvDepartureTime.text = order.departureTime
        binding.tvArrivalTime.text = order.arrivalTime
        binding.tvDepartureDate.text = order.departureDate
        binding.tvJourneyDate.text = formatMonthDay(order.departureDate)
        binding.tvDuration.text = "历时${order.timetableDuration
            ?: calculateDuration(order.departureTime, order.arrivalTime)}"
        binding.tvSeatType.text = order.seatInfo
        binding.tvSeatNumber.text = ""
        binding.tvCarNumber.text = ""
        val groupSize = order.groupPassengerCount.coerceAtLeast(1)
        binding.tvPassengerName.text = if (groupSize > 1) {
            "${order.passengerName}（同行订单 $groupSize 人）"
        } else order.passengerName
        binding.tvPassengerIdCard.text = order.passengerIdCard
        binding.tvTicketPrice.text = "¥${money(order.finalPrice)}"
        binding.tvOrderStatus.text = order.status
        renderJourneyCountdown(order)
        renderStoredTimetable(order)
        renderTravelConcierge(order)

        if (order.status == "已支付") {
            // A display-only gate or QR generation issue must never hide the ticket itself.
            val gate = runCatching { TicketTravelUpdates.getGate(this, order) }
                .getOrDefault("请以车站现场公告为准")
            binding.tvGateInfo.visibility = View.VISIBLE
            binding.tvGateInfo.text = "检票口：$gate，请以车站现场公告为准"
            val token = stableVoucherToken(order)
            val qrBitmap = qrBitmapFor(order, token)
            if (qrBitmap != null) {
                binding.ivQrCode.setImageBitmap(qrBitmap)
                binding.ivQrCode.visibility = View.VISIBLE
                binding.tvQrCodeInfo.text = "电子客票凭证\n凭证号：$token"
            } else {
                binding.ivQrCode.setImageDrawable(null)
                binding.ivQrCode.visibility = View.GONE
                binding.tvQrCodeInfo.text = "电子客票凭证\n凭证加载失败，请以订单信息为准"
            }
        } else {
            binding.tvGateInfo.visibility = View.GONE
            binding.ivQrCode.setImageDrawable(null)
            binding.ivQrCode.visibility = View.GONE
            binding.tvQrCodeInfo.text = when (order.status) {
                "待支付" -> "完成支付后生成本地电子客票凭证"
                "已取消" -> "订单已取消，本地电子客票凭证已失效"
                else -> "当前订单不展示电子客票凭证"
            }
        }

        setupButtonStates(order)
    }

    /** A cancelled order has no departure worth counting down to, so the line stays hidden. */
    private fun renderJourneyCountdown(order: Order) {
        countdownOrder = order
        if (order.status == "已取消") {
            binding.tvJourneyCountdown.visibility = View.GONE
            return
        }
        binding.tvJourneyCountdown.text = TravelAssistant.journeyStatusText(order)
        binding.tvJourneyCountdown.visibility = View.VISIBLE
    }

    private fun renderStoredTimetable(order: Order) {
        val stops = order.timetableStops.orEmpty()
        if (stops.isEmpty()) {
            binding.cardStopTimetable.visibility = View.GONE
            return
        }
        val plannedRows = stops.map {
            TrainStopSchedule(
                stationName = it.stationName,
                arrivalTime = it.arrivalTime,
                departureTime = it.departureTime,
                dwellLabel = it.dwellLabel
            )
        }
        val rows = TimetableStopStatusResolver.resolve(plannedRows, order.trainNumber, order.departureDate)
        // This is the passenger's itinerary: blue labels mark the ticket's boarding and
        // alighting stations, even when the timetable includes the entire through service.
        val boardingStation = order.departureStation
        val alightingStation = order.arrivalStation
        binding.tvStopTimetableCount.text = "共 ${rows.size} 个经停站"
        // Re-rendering on every onResume must not replace the layout manager: doing so discards
        // the recycler's scrap heap and re-inflates every visible row each time.
        val timetableList = binding.rvStopTimetable
        val existing = stopTimetableAdapter
        if (existing == null) {
            stopTimetableAdapter = TrainStopScheduleAdapter(rows, boardingStation, alightingStation).also {
                timetableList.layoutManager = LinearLayoutManager(this)
                timetableList.adapter = it
            }
            timetableList.isVerticalScrollBarEnabled = true
            timetableList.scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
        } else {
            existing.updateStops(rows, boardingStation, alightingStation)
        }
        TrainSetResolver.modelFor(order.trainNumber)?.let { model ->
            binding.tvStopTrainSet.text = model
            binding.layoutStopTrainSet.visibility = View.VISIBLE
        } ?: run {
            binding.layoutStopTrainSet.visibility = View.GONE
        }
        binding.cardStopTimetable.visibility = View.VISIBLE
    }

    private fun renderTravelConcierge(order: Order) {
        if (order.status !in setOf("已支付", "已完成")) {
            binding.cardTravelConcierge.visibility = View.GONE
            return
        }
        val progress = tripProgressRepository.synchronizeWithTimetable(order)
        val stage = if (order.status == "已完成") TripProgress.STAGE_ARRIVED else progress.stage
        binding.tvJourneyStage.text = when (stage) {
            TripProgress.STAGE_READY -> "当前：出发前 · 请提前到站候车"
            TripProgress.STAGE_AT_STATION -> "当前：已到车站 · 请留意检票通知"
            TripProgress.STAGE_BOARDED -> "当前：乘车途中 · 祝您旅途愉快"
            else -> "当前：已到达 ${order.arrivalStation}"
        }
        binding.tvJourneyTimeline.text = travelTimeline(order, stage, progress.updatedAt)

        val user = userRepository.getCurrentUser()
        val usageKey = loungeUsageKey(order)
        val usedLoungeCoupon = user?.let { membershipRepository.getCouponUsedForUsage(it.id, usageKey) }
        val availableLoungeCoupon = user?.let {
            membershipRepository.getAvailableCoupon(it.id, MembershipRepository.COUPON_LOUNGE)
        }
        when {
            usedLoungeCoupon != null -> {
                binding.tvLoungeBenefit.text = "贵宾候车厅服务已开通 · 凭本人有效证件及车票使用"
                binding.tvLoungeBenefit.visibility = View.VISIBLE
                binding.btnUseLoungeCoupon.visibility = View.GONE
            }
            order.status == "已支付" && availableLoungeCoupon != null -> {
                binding.tvLoungeBenefit.text = "权益包内有 1 张高铁贵宾候车厅券可用于本程"
                binding.tvLoungeBenefit.visibility = View.VISIBLE
                binding.btnUseLoungeCoupon.visibility = View.VISIBLE
            }
            else -> {
                binding.tvLoungeBenefit.visibility = View.GONE
                binding.btnUseLoungeCoupon.visibility = View.GONE
            }
        }

        // Only the first milestone is user-driven, and once it is recorded the button must not
        // linger on the card.  Later stages are no longer offered here.
        val awaitingStationConfirmation =
            order.status == "已支付" && progress.stage == TripProgress.STAGE_READY
        binding.btnJourneyAction.visibility =
            if (awaitingStationConfirmation) View.VISIBLE else View.GONE
        binding.btnJourneyAction.text = "我已到车站"
        binding.cardTravelConcierge.visibility = View.VISIBLE
    }

    private fun travelTimeline(order: Order, stage: String, updatedAt: String): String {
        val stageOrder = listOf(
            TripProgress.STAGE_READY,
            TripProgress.STAGE_AT_STATION,
            TripProgress.STAGE_BOARDED,
            TripProgress.STAGE_ARRIVED
        )
        val reached = stageOrder.indexOf(stage).coerceAtLeast(0)
        fun marker(index: Int) = if (index <= reached) "●" else "○"
        return listOf(
            "● 购票成功  " + (order.payTime ?: order.createTime),
            marker(0) + " 出发前  " + order.departureDate + " " + order.departureTime + " 从" + order.departureStation + "出发",
            marker(1) + " 到达车站  留意检票口和候车区通知",
            marker(2) + " 乘车途中  " + order.trainNumber + " 前往" + order.arrivalStation,
            marker(3) + " 到达目的地  " + if (stage == TripProgress.STAGE_ARRIVED) updatedAt else "待确认"
        ).joinToString("\n")
    }
    
    private fun setupClickListeners() {
        binding.btnChangeTicket.setOnClickListener {
            when (loadLatest()?.status) {
                "待支付" -> continuePayment()
                "已支付" -> showChangeTicketDialog()
                else -> showStaleState()
            }
        }
        binding.btnRefundTicket.setOnClickListener {
            when (loadLatest()?.status) {
                "待支付" -> showCancelPendingDialog()
                "已支付" -> showRefundTicketDialog()
                else -> showStaleState()
            }
        }
        binding.btnRefundItinerary.setOnClickListener { showRefundItineraryDialog() }
        binding.btnChangeStation.setOnClickListener { showChangeDestinationDialog() }
        binding.btnOrderMeal.setOnClickListener {
            val latest = loadLatest() ?: return@setOnClickListener
            if (latest.status != "已支付") {
                Toast.makeText(this, "仅可为已支付且未出行的车票订餐", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, MealOrderActivity::class.java).putExtra("orderId", latest.id))
        }
        binding.btnRouteMap.setOnClickListener {
            val latest = loadLatest() ?: return@setOnClickListener
            val callingStations = latest.timetableStops.orEmpty().map { it.stationName }
                .ifEmpty { listOf(latest.departureStation, latest.arrivalStation) }
            val routeStations = latest.routeStations.orEmpty().ifEmpty { callingStations }
            val mapTimetable = latest.timetableStops.orEmpty().map {
                RailwayMapTimetableStop(it.stationName, it.arrivalTime, it.departureTime)
            }
            startActivity(
                RailwayMapActivity.intent(
                    context = this,
                    trainNumber = latest.trainNumber,
                    routeStations = routeStations,
                    callingStations = callingStations,
                    boardingStation = latest.departureStation,
                    alightingStation = latest.arrivalStation,
                    departureDate = latest.departureDate,
                    timetableStops = mapTimetable
                )
            )
        }
        binding.btnJourneyAction.setOnClickListener { advanceJourney() }
        binding.btnUseLoungeCoupon.setOnClickListener { useLoungeCoupon() }
    }

    private fun advanceJourney() {
        val latest = loadLatest() ?: return
        val user = userRepository.getCurrentUser() ?: return
        if (latest.status != "已支付" || user.id != latest.userId) {
            showStaleState()
            return
        }
        val progress = tripProgressRepository.get(latest)
        val nextStage = tripProgressRepository.nextStage(progress) ?: run {
            refreshScreen()
            return
        }
        val saved = tripProgressRepository.advance(latest, nextStage)
        if (saved == null) {
            Toast.makeText(this, "出行状态保存失败，请重试", Toast.LENGTH_SHORT).show()
            return
        }
        if (nextStage == TripProgress.STAGE_ARRIVED) {
            if (!orderRepository.updateOrderStatus(latest.id, user.id, "已完成")) {
                Toast.makeText(this, "到达状态已记录，订单状态请稍后刷新", Toast.LENGTH_SHORT).show()
                refreshScreen()
                return
            }
            TravelReminderScheduler.cancel(this, latest)
            messageRepository.add(
                user.id,
                MessageRepository.TRAVEL,
                "抵达通知 · ${latest.trainNumber}",
                "您乘坐的 ${latest.trainNumber} 次列车已到达 ${latest.arrivalStation}。感谢您的乘坐，祝您旅途愉快。",
                latest.id,
                eventKey = "trip_arrived:" + latest.id
            )
            Toast.makeText(this, "已到达目的地", Toast.LENGTH_LONG).show()
        } else {
            val label = if (nextStage == TripProgress.STAGE_AT_STATION) "已到车站" else "已上车"
            messageRepository.add(
                user.id,
                MessageRepository.TRAVEL,
                "出行状态更新 · ${latest.trainNumber}",
                "本程已记录为$label。",
                latest.id,
                eventKey = "trip_progress:" + latest.id + ":" + nextStage
            )
            Toast.makeText(this, "出行状态已更新", Toast.LENGTH_SHORT).show()
        }
        refreshScreen()
    }

    private fun useLoungeCoupon() {
        val latest = loadLatest() ?: return
        val user = userRepository.getCurrentUser() ?: return
        if (latest.status != "已支付" || user.id != latest.userId) {
            showStaleState()
            return
        }
        val usageKey = loungeUsageKey(latest)
        val alreadyUsed = membershipRepository.getCouponUsedForUsage(user.id, usageKey)
        if (alreadyUsed != null) {
            Toast.makeText(this, "本程贵宾候车厅服务已开通", Toast.LENGTH_SHORT).show()
            refreshScreen()
            return
        }
        if (membershipRepository.getAvailableCoupon(user.id, MembershipRepository.COUPON_LOUNGE) == null) {
            Toast.makeText(this, "权益包内暂无高铁贵宾候车厅券", Toast.LENGTH_SHORT).show()
            refreshScreen()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("使用贵宾候车厅券")
            .setMessage("将为 ${latest.departureStation} 出发的 ${latest.trainNumber} 开通一次候车厅服务。")
            .setNegativeButton("暂不使用", null)
            .setPositiveButton("确认使用") { _, _ ->
                val coupon = membershipRepository.consumeCoupon(user.id, MembershipRepository.COUPON_LOUNGE, usageKey)
                if (coupon == null) {
                    Toast.makeText(this, "权益券状态已变化，请刷新后重试", Toast.LENGTH_SHORT).show()
                    refreshScreen()
                    return@setPositiveButton
                }
                messageRepository.add(
                    user.id,
                    MessageRepository.TRAVEL,
                    "贵宾候车厅服务已开通",
                    "${latest.trainNumber} ${latest.departureDate} 可凭本人有效证件及车票前往 ${latest.departureStation} 贵宾候车区。",
                    latest.id,
                    eventKey = "lounge_coupon:" + latest.id
                )
                Toast.makeText(this, "贵宾候车厅服务已开通", Toast.LENGTH_LONG).show()
                refreshScreen()
            }
            .show()
    }

    private fun loungeUsageKey(order: Order): String = "lounge:" + order.id
    
    private fun setupButtonStates(order: Order) {
        binding.btnOrderMeal.isEnabled = order.status == "已支付"
        binding.btnOrderMeal.visibility = if (order.status == "已取消") View.GONE else View.VISIBLE
        binding.btnRouteMap.visibility = if (order.status == "已取消") View.GONE else View.VISIBLE
        when (order.status) {
            "待支付" -> {
                binding.btnChangeTicket.isEnabled = true
                binding.btnChangeTicket.text = "继续支付"
                binding.btnRefundTicket.isEnabled = true
                binding.btnRefundTicket.text = "取消订单"
                binding.btnChangeStation.isEnabled = false
                binding.btnChangeStation.text = "变更到站"
            }
            "已支付" -> {
                // Once the train has left, the ticket can no longer be changed or refunded.
                // Grey the buttons out rather than rewriting their labels.
                val departed = hasDeparted(order)
                binding.btnChangeTicket.isEnabled = !departed
                binding.btnChangeTicket.text = "改签"
                binding.btnRefundTicket.isEnabled = !departed
                binding.btnRefundTicket.text = "退票"
                binding.btnChangeStation.isEnabled = !departed &&
                    canChangeDestination(order) && destinationQuotes(order).isNotEmpty()
                binding.btnChangeStation.text = "变更到站"
            }
            "已完成" -> {
                binding.btnChangeTicket.isEnabled = false
                binding.btnChangeTicket.text = "已完成"
                binding.btnRefundTicket.isEnabled = false
                binding.btnRefundTicket.text = "不可退票"
                binding.btnChangeStation.isEnabled = false
                binding.btnChangeStation.text = "变更到站"
            }
            else -> {
                binding.btnChangeTicket.isEnabled = false
                binding.btnChangeTicket.text = "已取消"
                binding.btnRefundTicket.isEnabled = false
                binding.btnRefundTicket.text = "不可退票"
                binding.btnChangeStation.isEnabled = false
                binding.btnChangeStation.text = "变更到站"
            }
        }
        val canRefundItinerary = order.status == "已支付" &&
            order.itineraryId != null && itineraryOrders.count { it.status == "已支付" } >= 2
        binding.btnRefundItinerary.visibility = if (canRefundItinerary) View.VISIBLE else View.GONE
        binding.btnRefundItinerary.isEnabled = canRefundItinerary
    }

    private fun continuePayment() {
        val latest = loadLatest() ?: return
        val currentUser = userRepository.getCurrentUser() ?: return
        val batchId = latest.paymentBatchId
        if (latest.status != "待支付" || batchId.isNullOrBlank()) {
            showStaleState()
            return
        }
        val batch = orderRepository.getOrdersByPaymentBatchId(batchId, currentUser.id)
        if (batch.isEmpty() || batch.any { it.status != "待支付" }) {
            showStaleState()
            return
        }
        startActivity(
            Intent(this, PaymentActivity::class.java)
                .putExtra(PaymentLifecycle.EXTRA_BATCH_ID, batchId)
        )
    }

    private fun showCancelPendingDialog() {
        val latest = loadLatest() ?: return
        val currentUser = userRepository.getCurrentUser() ?: return
        val batchId = latest.paymentBatchId
        if (latest.status != "待支付" || batchId.isNullOrBlank()) {
            showStaleState()
            return
        }
        val batchSize = orderRepository.getOrdersByPaymentBatchId(batchId, currentUser.id)
            .count { it.status == "待支付" }
        if (batchSize == 0) {
            showStaleState()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("取消待支付订单")
            .setMessage("将取消本支付单内全部 $batchSize 张车票，并释放已保留的座位。")
            .setNegativeButton("继续支付", null)
            .setPositiveButton("确认取消") { _, _ ->
                val fresh = loadLatest(false)
                val user = userRepository.getCurrentUser()
                if (fresh == null || user == null || fresh.status != "待支付" ||
                    fresh.paymentBatchId != batchId ||
                    paymentLifecycle.cancelPayment(batchId, user.id) == null
                ) {
                    showStaleState()
                } else {
                    Toast.makeText(this, "待支付订单已取消，座位已释放", Toast.LENGTH_LONG).show()
                    refreshScreen()
                }
            }
            .show()
    }

    /** Called once per stop by destinationQuotes(), so it must not allocate a formatter. */
    private fun calculateDuration(departureTime: String, arrivalTime: String): String {
        try {
            val departure = CLOCK_FORMAT.parse(departureTime)
            val arrival = CLOCK_FORMAT.parse(arrivalTime)
            
            if (departure != null && arrival != null) {
                var diff = arrival.time - departure.time
                if (diff < 0) diff += 24L * 60L * 60L * 1000L
                val hours = diff / (1000 * 60 * 60)
                val minutes = (diff % (1000 * 60 * 60)) / (1000 * 60)
                
                return if (hours > 0) {
                    "${hours}小时${minutes}分钟"
                } else {
                    "${minutes}分钟"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "未知"
    }
    
    private fun generateQrCodeContent(order: Order, token: String): String {
        return buildString {
            append("VOUCHER:").append(token)
            append("|ORD:").append(order.id)
            append("|TRAIN:").append(order.trainNumber)
            append("|FROM:").append(order.departureStation)
            append("|TO:").append(order.arrivalStation)
            append("|DATE:").append(order.departureDate)
            append("|DEPART:").append(order.departureTime)
            append("|ARRIVE:").append(order.arrivalTime)
            append("|SEAT:").append(order.seatInfo)
            append("|TOKEN:").append(token)
        }
    }

    private fun stableVoucherToken(order: Order): String {
        val raw = listOf(order.id, order.userId, order.trainNumber, order.departureDate, order.seatInfo)
            .joinToString("|")
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
            .joinToString("") { "%02X".format(Locale.CHINA, it) }
            .take(16)
    }

    private fun formatDateTime(millis: Long): String =
        if (millis <= 0L) "支付期限内" else DATE_TIME_FORMAT.format(Date(millis))

    private fun formatMonthDay(value: String): String =
        runCatching { DATE_SOURCE_FORMAT.parse(value) }.getOrNull()?.let(MONTH_DAY_FORMAT::format) ?: value

    /** "2026-09-11" → "2026年9月11日 星期五"; falls back to the raw value if unparseable. */
    private fun formatFullDate(value: String): String =
        runCatching { DATE_SOURCE_FORMAT.parse(value) }.getOrNull()?.let(FULL_DATE_FORMAT::format) ?: value

    private fun money(value: Double): String = String.format(Locale.CHINA, "%.2f", value)

    /** Reuses the last rendered code; a failure is not cached, so a retry can still succeed. */
    private fun qrBitmapFor(order: Order, token: String): Bitmap? {
        val content = generateQrCodeContent(order, token)
        cachedQrBitmap?.let { if (content == cachedQrContent) return it }
        val bitmap = runCatching { generateQrBitmap(content) }.getOrNull() ?: return null
        cachedQrContent = content
        cachedQrBitmap = bitmap
        return bitmap
    }

    private fun generateQrBitmap(content: String, size: Int = 512): Bitmap? {
        return try {
            val hints = hashMapOf<EncodeHintType, Any>(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 1
            )
            val writer = QRCodeWriter()
            // Keep ZXing's own size x size output: it scales by an integer multiple, so every
            // module lands on an equal number of pixels.  Scaling a natural-size matrix here
            // instead would mix 7px and 8px modules and risk hurting scannability.
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val row = y * width
                for (x in 0 until width) {
                    pixels[row + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                }
            }
            // One setPixels call; the setPixel loop this replaces was a JNI hop per pixel.
            Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        } catch (e: WriterException) {
            e.printStackTrace()
            null
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            null
        }
    }

    private fun showChangeTicketDialog() {
        // 检查订单状态，只有已支付的订单才能改签
        val latest = loadLatest() ?: return
        if (latest.status != "已支付") {
            showStaleState()
            return
        }
        if (latest.itineraryId != null) {
            AlertDialog.Builder(this)
                .setTitle("中转换乘改签提醒")
                .setMessage("当前仅改签本程可能影响下一程衔接。建议优先选择两程均有余票的方案，并确认换乘时间。")
                .setNegativeButton("取消", null)
                .setPositiveButton("继续改签本程") { _, _ -> openChangeTicket() }
                .show()
            return
        }
        openChangeTicket()
    }

    private fun openChangeTicket() {
        val latest = loadLatest() ?: return
        if (latest.status != "已支付") {
            Toast.makeText(this, "只有已支付的订单才能改签", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 跳转到改签选择页面
        val intent = Intent(this, ChangeTicketActivity::class.java)
        intent.putExtra("originalOrder", latest)
        startActivity(intent)
    }
    
    private fun showRefundItineraryDialog() {
        val latest = loadLatest() ?: return
        val itineraryId = latest.itineraryId ?: return
        val payableLegs = itineraryOrders.filter { it.status == "已支付" }
        if (payableLegs.isEmpty()) {
            Toast.makeText(this, "该中转行程没有可退车票", Toast.LENGTH_SHORT).show()
            return
        }
        val pointsToDeduct = PointsPolicy.fromAmount(payableLegs.sumOf { it.finalPrice })
        val currentUser = userRepository.getCurrentUser()
        if (currentUser == null || currentUser.id != latest.userId) {
            Toast.makeText(this, "用户信息校验失败", Toast.LENGTH_SHORT).show()
            return
        }
        if (currentUser.points < pointsToDeduct) {
            Toast.makeText(this, "积分不足，无法整组退票", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("确认整组退票")
            .setMessage("将同时取消 ${payableLegs.size} 段中转换乘车票，并扣除 ${pointsToDeduct} 积分。")
            .setNegativeButton("保留行程", null)
            .setPositiveButton("确认整组退票") { _, _ ->
                val cancelled = orderRepository.cancelItinerary(itineraryId, currentUser.id)
                if (cancelled == null) {
                    Toast.makeText(this, "行程状态已变化，请刷新后重试", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                cancelled.forEach { cancelledOrder ->
                    if (seatInventoryRepository.releaseSeat(cancelledOrder)) {
                        orderRepository.acknowledgeInventoryRelease(cancelledOrder.id)
                    }
                    TravelReminderScheduler.cancel(this, cancelledOrder)
                }
                userRepository.adjustPointsOnce(currentUser.id, -pointsToDeduct, "itinerary_refund:${itineraryId}")
                messageRepository.add(
                    currentUser.id,
                    MessageRepository.TICKET,
                    "中转换乘行程已退票",
                    "已取消 ${cancelled.size} 段关联车票，已释放座位并扣除 $pointsToDeduct 积分。",
                    latest.id,
                    eventKey = "itinerary_refund:${itineraryId}"
                )
                Toast.makeText(this, "中转换乘行程已整组退票", Toast.LENGTH_LONG).show()
                finish()
            }
            .show()
    }

        // 检查订单状态，只有已支付的订单才能退票
    private fun showRefundTicketDialog() {
        if (loadLatest()?.status != "已支付") {
            Toast.makeText(this, "只有已支付的订单才能退票", Toast.LENGTH_SHORT).show()
            return
        }
        
        val refundReasons = arrayOf(
            "行程变更",
            "个人原因",
            "重复购票",
            "其他原因"
        )
        
        AlertDialog.Builder(this)
            .setTitle("确认退票")
            .setMessage("您确定要退票吗？退票后将扣除相应的积分。")
            .setSingleChoiceItems(refundReasons, 0, null)
            .setPositiveButton("确认退票") { dialog, which ->
                val selectedReason = refundReasons[(dialog as AlertDialog).listView.checkedItemPosition]
                processRefund(selectedReason)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun processRefund(reason: String) {
        try {
            // 计算应扣除的积分（与购票时获得的积分相同）
            val latest = loadLatest() ?: return
            val pointsToDeduct = PointsPolicy.fromAmount(latest.finalPrice)
            
            // 获取当前用户
            val currentUser = userRepository.getCurrentUser()
            if (currentUser == null) {
                Toast.makeText(this, "用户信息获取失败", Toast.LENGTH_SHORT).show()
                return
            }
            if (currentUser.id != latest.userId) {
                Toast.makeText(this, "无权操作此订单", Toast.LENGTH_SHORT).show()
                return
            }
            
            // 检查积分是否足够
            if (currentUser.points < pointsToDeduct) {
                Toast.makeText(this, "积分不足，无法退票", Toast.LENGTH_SHORT).show()
                return
            
            }
            if (!orderRepository.updateOrderStatus(latest.id, currentUser.id, "已取消")) {
                Toast.makeText(this, "订单状态更新失败，请重试", Toast.LENGTH_SHORT).show()
                return
            }
            if (seatInventoryRepository.releaseSeat(latest)) {
                orderRepository.acknowledgeInventoryRelease(latest.id)
            }
            userRepository.adjustPointsOnce(currentUser.id, -pointsToDeduct, "ticket_refund:${latest.id}")
            TravelReminderScheduler.cancel(this, latest)
            messageRepository.add(
                currentUser.id,
                MessageRepository.TICKET,
                "退票成功 · ${latest.trainNumber}",
                "因“$reason”取消车票，已释放座位并扣除 ${pointsToDeduct} 积分。",
                latest.id,
                eventKey = "ticket_refund:${latest.id}"
            )
            
            // 显示成功消息
            Toast.makeText(this, "退票成功！已扣除${pointsToDeduct}积分", Toast.LENGTH_LONG).show()
            
            // 返回上一页
            finish()
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "退票失败，请重试", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showStaleState() {
        Toast.makeText(this, "订单状态已变化，请刷新后重试", Toast.LENGTH_SHORT).show()
        refreshScreen()
    }
    
    private fun showChangeDestinationDialog() {
        val latest = loadLatest() ?: return
        if (latest.status != "已支付") {
            showStaleState()
            return
        }
        if (!canChangeDestination(latest)) {
            Toast.makeText(this, "变更到站仅支持在开车前 48 小时以上办理", Toast.LENGTH_LONG).show()
            refreshScreen()
            return
        }
        val quotes = destinationQuotes(latest)
        if (quotes.isEmpty()) {
            Toast.makeText(this, "当前车票没有可变更的后续到站", Toast.LENGTH_LONG).show()
            return
        }
        val labels = quotes.map { quote ->
            val delta = roundMoney(quote.finalPrice - latest.finalPrice)
            val deltaLabel = when {
                delta > 0.0 -> "补 ¥${money(delta)}"
                delta < 0.0 -> "退 ¥${money(-delta)}"
                else -> "票价不变"
            }
            "${quote.stop.stationName} · ${quote.stop.arrivalTime} 到达 · ¥${money(quote.finalPrice)}（$deltaLabel）"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择变更到站")
            .setMessage("仅展示本次列车后续经停站；开车前 48 小时以上可办理。")
            .setItems(labels) { _, which -> showDestinationQuoteConfirmation(latest, quotes[which]) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDestinationQuoteConfirmation(order: Order, quote: DestinationQuote) {
        val difference = roundMoney(quote.finalPrice - order.finalPrice)
        val message = buildString {
            append("${order.arrivalStation} → ${quote.stop.stationName}\n")
            append("新票价：¥${money(quote.finalPrice)}\n")
            when {
                difference > 0.0 -> {
                    append("需补差价：¥${money(difference)}\n")
                    append("确认后从铁路钱包支付补差。")
                }
                difference < 0.0 -> {
                    val feeRate = refundFeeRate(order)
                    val refundBeforeFee = -difference
                    val fee = roundMoney(refundBeforeFee * feeRate)
                    val refund = roundMoney(refundBeforeFee - fee)
                    append("应退差额：¥${money(refundBeforeFee)}\n")
                    append("退票手续费：${(feeRate * 100).toInt()}%（¥${money(fee)}）\n")
                    append("实退：¥${money(refund)}，退至铁路钱包。")
                }
                else -> append("票价不变，无需补退款。")
            }
        }
        AlertDialog.Builder(this)
            .setTitle("确认变更到站")
            .setMessage(message)
            .setNegativeButton("取消", null)
            .setPositiveButton("确认变更") { _, _ -> processDestinationChange(quote) }
            .show()
    }

    private fun processDestinationChange(initialQuote: DestinationQuote) {
        val currentUser = userRepository.getCurrentUser() ?: run {
            Toast.makeText(this, "用户信息获取失败", Toast.LENGTH_SHORT).show()
            return
        }
        val latest = orderRepository.getOrderById(orderId, currentUser.id) ?: run {
            showStaleState()
            return
        }
        if (latest.status != "已支付" || !canChangeDestination(latest)) {
            Toast.makeText(this, "当前不满足变更到站条件", Toast.LENGTH_LONG).show()
            refreshScreen()
            return
        }
        val quote = destinationQuotes(latest).firstOrNull {
            it.stop.stationName == initialQuote.stop.stationName
        } ?: run {
            Toast.makeText(this, "到站信息已变化，请重新选择", Toast.LENGTH_SHORT).show()
            refreshScreen()
            return
        }
        val difference = roundMoney(quote.finalPrice - latest.finalPrice)
        val transactionKey = "arrival_change:${latest.id}:${quote.stop.stationName}:${money(quote.finalPrice)}"
        if (difference > 0.0 && !membershipRepository.payWithWallet(
                currentUser.id,
                transactionKey,
                toCents(difference),
                "变更到站补差"
            )
        ) {
            Toast.makeText(this, "铁路钱包余额不足，请充值后再补差价", Toast.LENGTH_LONG).show()
            return
        }

        val updatedStops = latest.timetableStops.orEmpty()
            .subList(
                updatedDepartureIndex(latest).coerceAtLeast(0),
                quote.targetIndex + 1
            )
        val updated = latest.copy(
            arrivalStation = quote.stop.stationName,
            arrivalTime = quote.stop.arrivalTime,
            basePrice = quote.basePrice,
            finalPrice = quote.finalPrice,
            timetableStops = updatedStops,
            timetableDuration = quote.duration
        )
        if (!orderRepository.replaceOrderIfSeatAvailable(
                latest.id,
                currentUser.id,
                updated,
                expectedOriginal = latest
            )
        ) {
            if (difference > 0.0) {
                membershipRepository.refundWalletPayment(currentUser.id, transactionKey, toCents(difference))
            }
            Toast.makeText(this, "订单状态已变化，请刷新后重试", Toast.LENGTH_SHORT).show()
            refreshScreen()
            return
        }

        val refund = if (difference < 0.0) {
            roundMoney((-difference) * (1.0 - refundFeeRate(latest)))
        } else 0.0
        if (refund > 0.0 && !membershipRepository.creditWallet(
                currentUser.id,
                toCents(refund),
                "变更到站退款 · ${latest.trainNumber}",
                transactionKey
            )
        ) {
            orderRepository.replaceOrderIfSeatAvailable(
                latest.id,
                currentUser.id,
                latest,
                expectedOriginal = updated
            )
            Toast.makeText(this, "退款处理失败，未变更到站", Toast.LENGTH_SHORT).show()
            refreshScreen()
            return
        }

        TravelReminderScheduler.cancel(this, latest)
        TravelReminderScheduler.schedule(this, updated)
        val settlement = when {
            difference > 0.0 -> "已从铁路钱包补差 ¥${money(difference)}。"
            difference < 0.0 -> "已退至铁路钱包 ¥${money(refund)}（已按退票规则扣除手续费）。"
            else -> "票价不变。"
        }
        messageRepository.add(
            currentUser.id,
            MessageRepository.TICKET,
            "变更到站成功 · ${updated.trainNumber}",
            "到站已由 ${latest.arrivalStation} 变更为 ${updated.arrivalStation}，${settlement}",
            updated.id,
            eventKey = "destination_change:$transactionKey"
        )
        membershipRepository.trackEvent(currentUser.id, MembershipRepository.EVENT_CHANGE_TICKET)
        Toast.makeText(this, "已变更到 ${updated.arrivalStation}", Toast.LENGTH_LONG).show()
        refreshScreen()
    }

    private fun destinationQuotes(order: Order): List<DestinationQuote> {
        val stops = order.timetableStops.orEmpty()
        val departureIndex = updatedDepartureIndex(order)
        val originalArrivalIndex = stops.indexOfFirst { it.stationName == order.arrivalStation }
        if (departureIndex < 0 || originalArrivalIndex <= departureIndex) return emptyList()
        val originalSegments = (originalArrivalIndex - departureIndex).coerceAtLeast(1)
        return stops.mapIndexedNotNull { index, stop ->
            if (index <= departureIndex || stop.stationName == order.arrivalStation || stop.arrivalTime == "—") {
                null
            } else {
                val ratio = (index - departureIndex).toDouble() / originalSegments
                DestinationQuote(
                    stop = stop,
                    targetIndex = index,
                    basePrice = roundMoney(order.basePrice * ratio),
                    finalPrice = roundMoney(order.finalPrice * ratio),
                    duration = calculateDuration(order.departureTime, stop.arrivalTime)
                )
            }
        }
    }

    private fun updatedDepartureIndex(order: Order): Int =
        order.timetableStops.orEmpty().indexOfFirst { it.stationName == order.departureStation }

    /** An unparseable departure date must not block the action, so it counts as not departed. */
    private fun hasDeparted(order: Order): Boolean =
        departureMillis(order).let { it != Long.MIN_VALUE && it <= System.currentTimeMillis() }

    private fun canChangeDestination(order: Order): Boolean =
        departureMillis(order) - System.currentTimeMillis() >= 48L * 60L * 60L * 1000L

    private fun departureMillis(order: Order): Long = runCatching {
        DEPARTURE_FORMAT.parse("${order.departureDate} ${order.departureTime}")?.time ?: Long.MIN_VALUE
    }.getOrDefault(Long.MIN_VALUE)

    private fun refundFeeRate(order: Order): Double {
        val hoursBeforeDeparture = (departureMillis(order) - System.currentTimeMillis()) / 3_600_000.0
        return when {
            hoursBeforeDeparture >= 8 * 24 -> 0.0
            hoursBeforeDeparture >= 48 -> 0.05
            hoursBeforeDeparture >= 24 -> 0.10
            else -> 0.20
        }
    }

    private fun roundMoney(value: Double): Double = kotlin.math.round(value * 100.0) / 100.0
    private fun toCents(value: Double): Long = kotlin.math.round(value * 100.0).toLong()
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}


