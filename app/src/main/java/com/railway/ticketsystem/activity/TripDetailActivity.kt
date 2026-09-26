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
import com.railway.ticketsystem.data.RouteWeatherService
import com.railway.ticketsystem.data.StationGroundTransferCatalog
import com.railway.ticketsystem.data.TripChecklistRepository
import com.railway.ticketsystem.data.SpecialAssistanceRepository
import com.railway.ticketsystem.data.ArrivalAlarmManager
import com.railway.ticketsystem.data.ChildDeclarationRepository
import com.railway.ticketsystem.dialog.BaggageCheckBottomSheet
import com.railway.ticketsystem.databinding.DialogSpecialAssistanceBinding
import com.railway.ticketsystem.databinding.DialogAddChecklistItemBinding
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.EditText
import android.widget.TextView
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
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
        binding.btnIndoorNavigation.isEnabled = false
        binding.btnCarriageService.isEnabled = false
        binding.btnStationService.isEnabled = false
        binding.btnViewPassbook.isEnabled = false
        binding.btnSyncCalendar.isEnabled = false
        binding.btnConcourseMap.isEnabled = false
        binding.cardUnreservedTripGuide.visibility = View.GONE
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
        renderWeatherTimeline(order)
        renderStationExitGuide(order)
        renderLuggageChecklist(order)
        renderSpecialAssistance(order)
        renderArrivalAlarm(order)
        renderChildDeclaration(order)

        if (order.status == "已支付") {
            // A display-only gate or QR generation issue must never hide the ticket itself.
            val gate = runCatching { TicketTravelUpdates.getGate(this, order) }
                .getOrDefault("请以车站现场公告为准")
            binding.tvGateInfo.visibility = View.VISIBLE
            binding.tvGateInfo.text = "检票口：$gate · 查看候车大厅导览图 ›"
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

            if (order.seatType.contains("无座") || order.seatNumber == "无座") {
                binding.cardUnreservedTripGuide.visibility = View.VISIBLE
                binding.tvUnreservedTripGuideContent.text = buildUnreservedSeatGuide(order)
            } else {
                binding.cardUnreservedTripGuide.visibility = View.GONE
            }
        } else {
            binding.tvGateInfo.visibility = View.GONE
            binding.cardUnreservedTripGuide.visibility = View.GONE
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
        binding.tvJourneyGuidance.text = smartJourneyGuidance(order, stage)

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

    /** Compact operational guidance shared with the live position map; refreshed by the minute ticker. */
    private fun smartJourneyGuidance(order: Order, stage: String): String {
        val walkingMinutes = 8 + ((order.id + order.departureStation).hashCode() and Int.MAX_VALUE) % 11
        val gate = runCatching { TicketTravelUpdates.getGate(this, order) }.getOrDefault("请以现场公告为准")
        val transferHint = nextTransferHint(order)
        return when (stage) {
            TripProgress.STAGE_READY -> buildString {
                append("到站倒计时 · ").append(TravelAssistant.departureCountdown(order))
                append("\n站内步行 · 预计 ").append(walkingMinutes).append(" 分钟到达检票口 ").append(gate)
                append("\n").append(transferHint)
            }
            TripProgress.STAGE_AT_STATION -> buildString {
                append("检票指引 · 请前往 ").append(gate).append("，预计步行 ").append(walkingMinutes).append(" 分钟")
                append("\n出发倒计时 · ").append(TravelAssistant.departureCountdown(order))
                append("\n").append(transferHint)
            }
            TripProgress.STAGE_BOARDED -> "实时位置 · ${runningSection(order)}\n${operationalHint(order)}\n$transferHint"
            else -> "到达服务 · 已抵达 ${order.arrivalStation}，预计步行 ${walkingMinutes.coerceAtLeast(6)} 分钟至出站口\n$transferHint"
        }
    }

    private fun runningSection(order: Order): String {
        val stops = order.timetableStops.orEmpty()
        if (stops.size < 2) return "列车正驶向 ${order.arrivalStation}"
        val departure = TravelAssistant.departureMillis(order) ?: return "列车正驶向 ${order.arrivalStation}"
        val arrival = TravelAssistant.arrivalMillis(order) ?: return "列车正驶向 ${order.arrivalStation}"
        val total = (arrival - departure).coerceAtLeast(1L)
        val fraction = ((System.currentTimeMillis() - departure).toDouble() / total).coerceIn(0.0, 0.999)
        val index = (fraction * (stops.size - 1)).toInt().coerceIn(0, stops.size - 2)
        return "${stops[index].stationName}—${stops[index + 1].stationName} 区间 · 行程约 ${(fraction * 100).toInt()}%"
    }

    private fun operationalHint(order: Order): String {
        val rows = TimetableStopStatusResolver.resolve(
            order.timetableStops.orEmpty().map {
                TrainStopSchedule(it.stationName, it.arrivalTime, it.departureTime, it.dwellLabel)
            },
            order.trainNumber,
            order.departureDate
        )
        val latest = rows.lastOrNull { it.operationalStatus.isNotBlank() && it.operationalStatus != "--" }
            ?: return "运行提示 · 当前区段运行平稳，请留意车内广播"
        return when {
            latest.operationalStatus.startsWith("晚点") -> "运行提示 · ${latest.stationName}${latest.operationalStatus}，到达预期已同步更新"
            latest.operationalStatus.startsWith("早点") -> "运行提示 · ${latest.stationName}${latest.operationalStatus}，请提前做好下车准备"
            else -> "运行提示 · ${latest.stationName}正点通过"
        }
    }

    private fun nextTransferHint(order: Order): String {
        if (itineraryOrders.size < 2) return "到站建议 · 如需接送、行李托运或同城送达，可在车站服务中预约"
        val current = itineraryOrders.indexOfFirst { it.id == order.id }
        val next = itineraryOrders.getOrNull(current + 1)
        return if (next == null) {
            "换乘完成后将抵达终到站 ${order.arrivalStation}"
        } else {
            "换乘建议 · 到达 ${order.arrivalStation} 后前往 ${next.departureStation} 候车，建议预留 25 分钟"
        }
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
        val departure = TravelAssistant.departureMillis(order)
        val stationScale = 10 + ((order.departureStation + order.id).hashCode() and Int.MAX_VALUE) % 11
        fun planTime(minutesBefore: Int): String = departure?.let { CLOCK_FORMAT.format(Date(it - minutesBefore * 60_000L)) } ?: "请以车票时间为准"
        return listOf(
            "● 购票成功  " + (order.payTime ?: order.createTime),
            marker(0) + " 建议出门  " + planTime(75 + stationScale) + " · 预留路程与进站时间",
            marker(1) + " 到站安检  " + planTime(45 + stationScale) + " · 前往候车区",
            marker(2) + " 开始检票  " + planTime(20) + " · 请留意检票口变更",
            marker(2) + " 列车发车  " + order.departureDate + " " + order.departureTime + " 从" + order.departureStation + "出发",
            marker(3) + " 到达目的地  " + if (stage == TripProgress.STAGE_ARRIVED) updatedAt else "预计 " + order.arrivalTime + " 抵达" + order.arrivalStation
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

        binding.btnIndoorNavigation.setOnClickListener {
            val latest = loadLatest() ?: return@setOnClickListener
            if (latest.status == "已取消") {
                Toast.makeText(this, "已取消订单不可关联站内导航", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, IndoorNavigationActivity::class.java)
                .putExtra(IndoorNavigationActivity.EXTRA_STATION, latest.departureStation)
                .putExtra(IndoorNavigationActivity.EXTRA_TICKET_ORDER_ID, latest.id))
        }
        binding.btnCarriageService.setOnClickListener {
            val latest = loadLatest() ?: return@setOnClickListener
            if (latest.status != "已支付") {
                Toast.makeText(this, "仅可为已支付且未出行的车票使用车厢服务", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, CarriageServiceActivity::class.java)
                .putExtra(CarriageServiceActivity.EXTRA_TICKET_ORDER_ID, latest.id))
        }
        binding.btnStationService.setOnClickListener {
            val latest = loadLatest() ?: return@setOnClickListener
            if (latest.status == "已取消") {
                Toast.makeText(this, "已取消订单不可关联车站服务", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, StationServiceActivity::class.java)
                .putExtra(StationServiceActivity.EXTRA_STATION, latest.departureStation)
                .putExtra(StationServiceActivity.EXTRA_TICKET_ORDER_ID, latest.id))
        }
        binding.btnRouteMap.setOnClickListener {
            val latest = loadLatest() ?: return@setOnClickListener
            val callingStations = latest.timetableStops.orEmpty().map { it.stationName }
                .ifEmpty { listOf(latest.departureStation, latest.arrivalStation) }
            val routeStations = latest.routeStations.orEmpty().ifEmpty { callingStations }
            val mapTimetable = latest.timetableStops.orEmpty().map {
                RailwayMapTimetableStop(it.stationName, it.arrivalTime, it.departureTime)
            }
            val seatDisplay = if (latest.carNumber.isNotBlank() && latest.seatNumber.isNotBlank()) {
                "${latest.carNumber}车${latest.seatNumber}"
            } else latest.seatNumber
            startActivity(
                RailwayMapActivity.intent(
                    context = this,
                    trainNumber = latest.trainNumber,
                    routeStations = routeStations,
                    callingStations = callingStations,
                    boardingStation = latest.departureStation,
                    alightingStation = latest.arrivalStation,
                    departureDate = latest.departureDate,
                    timetableStops = mapTimetable,
                    seatNumber = seatDisplay
                )
            )
        }
        binding.btnJourneyAction.setOnClickListener { advanceJourney() }
        binding.btnUseLoungeCoupon.setOnClickListener { useLoungeCoupon() }
        binding.tvGateInfo.setOnClickListener {
            val latest = loadLatest() ?: return@setOnClickListener
            openConcourseMap(latest)
        }
        binding.btnConcourseMap.setOnClickListener {
            val latest = loadLatest() ?: return@setOnClickListener
            openConcourseMap(latest)
        }
        binding.btnViewPassbook.setOnClickListener {
            val latest = loadLatest() ?: return@setOnClickListener
            val gate = runCatching { TicketTravelUpdates.getGate(this, latest) }.getOrDefault("现场公告")
            showPassbookTicketDialog(latest, gate)
        }
        binding.btnSimulateGatePass.setOnClickListener {
            val latest = loadLatest() ?: return@setOnClickListener
            val gate = runCatching { TicketTravelUpdates.getGate(this, latest) }.getOrDefault("02A")
            showTurnstileGateSimulationDialog(latest, gate)
        }
        binding.btnRailwayTravelCode.setOnClickListener {
            startActivity(Intent(this, RailwayTravelCodeActivity::class.java))
        }
        binding.btnSyncCalendar.setOnClickListener {
            val latest = loadLatest() ?: return@setOnClickListener
            syncTripToCalendar(latest)
        }
        binding.btnAddChecklistItem.setOnClickListener {
            val latest = loadLatest() ?: return@setOnClickListener
            showAddChecklistItemDialog(latest)
        }
        binding.btnBookSpecialAssistance.setOnClickListener {
            val latest = loadLatest() ?: return@setOnClickListener
            showSpecialAssistanceDialog(latest)
        }
        binding.btnOpenBaggageCheck.setOnClickListener {
            BaggageCheckBottomSheet(this).show()
        }
        binding.btnDeclareChild.setOnClickListener {
            val latest = loadLatest() ?: return@setOnClickListener
            showChildDeclarationDialog(latest)
        }
        binding.btnMutePhone.setOnClickListener {
            mutePhoneQuietMode()
        }
        binding.btnRequestEarplugs.setOnClickListener {
            val latest = loadLatest() ?: return@setOnClickListener
            requestNoiseCancellingEarplugs(latest)
        }
        binding.btnLead15m.setOnClickListener {
            val latest = loadLatest() ?: return@setOnClickListener
            updateAlarmLeadTime(latest, 15)
        }
        binding.btnLead20m.setOnClickListener {
            val latest = loadLatest() ?: return@setOnClickListener
            updateAlarmLeadTime(latest, 20)
        }
        binding.btnLead30m.setOnClickListener {
            val latest = loadLatest() ?: return@setOnClickListener
            updateAlarmLeadTime(latest, 30)
        }
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

        binding.btnIndoorNavigation.isEnabled = order.status != "已取消"
        binding.btnIndoorNavigation.visibility = if (order.status == "已取消") View.GONE else View.VISIBLE
        binding.btnCarriageService.isEnabled = order.status == "已支付"
        binding.btnCarriageService.visibility = if (order.status == "已取消") View.GONE else View.VISIBLE
        binding.btnStationService.isEnabled = order.status != "已取消"
        binding.btnStationService.visibility = if (order.status == "已取消") View.GONE else View.VISIBLE
        binding.btnRouteMap.visibility = if (order.status == "已取消") View.GONE else View.VISIBLE
        binding.btnViewPassbook.isEnabled = order.status != "已取消"
        binding.btnViewPassbook.visibility = if (order.status == "已取消") View.GONE else View.VISIBLE
        binding.btnSyncCalendar.isEnabled = order.status == "已支付"
        binding.btnSyncCalendar.visibility = if (order.status == "已支付") View.VISIBLE else View.GONE
        binding.btnConcourseMap.isEnabled = order.status != "已取消"
        binding.btnConcourseMap.visibility = if (order.status == "已取消") View.GONE else View.VISIBLE
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
        val latest = loadLatest()
        if (latest?.status != "已支付") {
            Toast.makeText(this, "只有已支付的订单才能退票", Toast.LENGTH_SHORT).show()
            return
        }

        val breakdown = com.railway.ticketsystem.data.RefundFeePolicy.calculateRefund(latest)
        if (!breakdown.isRefundable) {
            Toast.makeText(this, breakdown.policyExplanation, Toast.LENGTH_LONG).show()
            return
        }
        
        val refundReasons = arrayOf(
            "行程变更",
            "个人原因",
            "重复购票",
            "其他原因"
        )

        val feeDesc = if (breakdown.feeRatePercent == 0) "免手续费" else "${breakdown.feeRatePercent}%（¥${String.format(Locale.CHINA, "%.2f", breakdown.feeAmount)}）"
        val messageText = "【退票明细】\n" +
                "票面金额：¥${String.format(Locale.CHINA, "%.2f", breakdown.ticketPrice)}\n" +
                "发车时间：${latest.departureDate} ${latest.departureTime}\n" +
                "退票费率：$feeDesc\n" +
                "应退金额：¥${String.format(Locale.CHINA, "%.2f", breakdown.refundAmount)}\n" +
                "政策说明：${breakdown.policyExplanation}\n\n" +
                "请选择退票原因并确认："
        
        AlertDialog.Builder(this)
            .setTitle("确认退票")
            .setMessage(messageText)
            .setSingleChoiceItems(refundReasons, 0, null)
            .setPositiveButton("确认退票 (退¥${String.format(Locale.CHINA, "%.2f", breakdown.refundAmount)})") { dialog, _ ->
                val selectedReason = refundReasons[(dialog as AlertDialog).listView.checkedItemPosition]
                processRefund(selectedReason, breakdown)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun processRefund(reason: String, breakdown: com.railway.ticketsystem.data.RefundBreakdown? = null) {
        try {
            val latest = loadLatest() ?: return
            val pointsToDeduct = PointsPolicy.fromAmount(latest.finalPrice)
            val refundInfo = breakdown ?: com.railway.ticketsystem.data.RefundFeePolicy.calculateRefund(latest)
            
            val currentUser = userRepository.getCurrentUser()
            if (currentUser == null) {
                Toast.makeText(this, "用户信息获取失败", Toast.LENGTH_SHORT).show()
                return
            }
            if (currentUser.id != latest.userId) {
                Toast.makeText(this, "无权操作此订单", Toast.LENGTH_SHORT).show()
                return
            }
            
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
                "因“$reason”取消车票。票面 ¥${String.format(Locale.CHINA, "%.2f", refundInfo.ticketPrice)}，核收手续费(${refundInfo.feeRatePercent}%) ¥${String.format(Locale.CHINA, "%.2f", refundInfo.feeAmount)}，实退 ¥${String.format(Locale.CHINA, "%.2f", refundInfo.refundAmount)}，扣除 ${pointsToDeduct} 积分。",
                latest.id,
                eventKey = "ticket_refund:${latest.id}"
            )
            
            Toast.makeText(
                this,
                "退票成功！实退 ¥${String.format(Locale.CHINA, "%.2f", refundInfo.refundAmount)}（手续费 ¥${String.format(Locale.CHINA, "%.2f", refundInfo.feeAmount)}）",
                Toast.LENGTH_LONG
            ).show()
            
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

    private fun buildUnreservedSeatGuide(order: Order): String {
        val stops = order.timetableStops.orEmpty()
        val nextStation = if (stops.size >= 2) stops[1].stationName else order.arrivalStation
        return "• 03车 12F：${order.departureStation} 至 $nextStation 区间当前空闲，可优先暂坐\n" +
               "• 05车 08D：餐吧车厢设有多组就座休闲席位，旅途中全程开放\n" +
               "• 07车 03A：中途站客流平稳区间空闲\n" +
               "💡 乘车提示：车厢座位上方电子指示灯绿灯为空闲，红灯为已售有人。"
    }

    private fun openConcourseMap(order: Order) {
        val gate = runCatching { TicketTravelUpdates.getGate(this, order) }.getOrDefault("检票口")
        startActivity(
            Intent(this, StationGuideMapActivity::class.java).apply {
                putExtra(StationGuideMapActivity.EXTRA_TICKET_ORDER_ID, order.id)
                putExtra(StationGuideMapActivity.EXTRA_STATION, order.departureStation)
                putExtra(StationGuideMapActivity.EXTRA_TARGET, "$gate 检票口")
            }
        )
    }

    private fun syncTripToCalendar(order: Order) {
        try {
            val startMillis = runCatching {
                DEPARTURE_FORMAT.parse("${order.departureDate} ${order.departureTime}")?.time
            }.getOrNull() ?: System.currentTimeMillis()
            val endMillis = runCatching {
                DEPARTURE_FORMAT.parse("${order.departureDate} ${order.arrivalTime}")?.time
            }.getOrNull() ?: (startMillis + 7200000L)
            val gate = runCatching { TicketTravelUpdates.getGate(this, order) }.getOrDefault("车站大屏")

            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = android.provider.CalendarContract.Events.CONTENT_URI
                putExtra(android.provider.CalendarContract.Events.TITLE, "🚆 ${order.trainNumber}次 ${order.departureStation} → ${order.arrivalStation}")
                putExtra(android.provider.CalendarContract.Events.EVENT_LOCATION, "${order.departureStation} ($gate 检票口)")
                putExtra(
                    android.provider.CalendarContract.Events.DESCRIPTION,
                    "车次：${order.trainNumber}\n" +
                    "席位：${order.seatInfo}\n" +
                    "检票口：$gate 检票口\n" +
                    "乘车人：${order.passengerName}\n" +
                    "订单号：${order.id}\n" +
                    "温馨提示：请提前到达车站候车，发车前5分钟停止检票。"
                )
                putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, if (endMillis > startMillis) endMillis else startMillis + 3600000L)
                putExtra(android.provider.CalendarContract.Events.AVAILABILITY, android.provider.CalendarContract.Events.AVAILABILITY_BUSY)
                putExtra(android.provider.CalendarContract.Events.HAS_ALARM, 1)
            }
            startActivity(intent)
            Toast.makeText(this, "正在调起系统日历，保存即可建立智能行程提醒", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("TripDetail", "日历同步失败", e)
            Toast.makeText(this, "未找到可用系统日历应用", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPassbookTicketDialog(order: Order, gate: String) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_passbook_ticket, null)
        dialog.setContentView(view)
        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)

        val cardFront = view.findViewById<View>(R.id.cardTicketFront)
        val cardBack = view.findViewById<View>(R.id.cardTicketBack)
        val tvTrainNumber = view.findViewById<android.widget.TextView>(R.id.tvPassbookTrainNumber)
        val tvDepStation = view.findViewById<android.widget.TextView>(R.id.tvPassbookDepStation)
        val tvDepPinyin = view.findViewById<android.widget.TextView>(R.id.tvPassbookDepPinyin)
        val tvArrStation = view.findViewById<android.widget.TextView>(R.id.tvPassbookArrStation)
        val tvArrPinyin = view.findViewById<android.widget.TextView>(R.id.tvPassbookArrPinyin)
        val tvDepTime = view.findViewById<android.widget.TextView>(R.id.tvPassbookDepTime)
        val tvGate = view.findViewById<android.widget.TextView>(R.id.tvPassbookGate)
        val tvSeatInfo = view.findViewById<android.widget.TextView>(R.id.tvPassbookSeatInfo)
        val tvPrice = view.findViewById<android.widget.TextView>(R.id.tvPassbookPrice)
        val tvPassenger = view.findViewById<android.widget.TextView>(R.id.tvPassbookPassenger)
        val tvTicketCode = view.findViewById<android.widget.TextView>(R.id.tvPassbookTicketCode)
        val ivMiniQr = view.findViewById<android.widget.ImageView>(R.id.ivPassbookQrMini)
        val btnFlip = view.findViewById<android.widget.Button>(R.id.btnFlipTicket)
        val btnSave = view.findViewById<android.widget.Button>(R.id.btnSaveTicket)
        val btnClose = view.findViewById<android.widget.Button>(R.id.btnClosePassbook)

        tvTrainNumber?.text = "${order.trainNumber} 次"
        tvDepStation?.text = "${order.departureStation}站"
        tvDepPinyin?.text = com.railway.ticketsystem.data.StationPinyinResolver.getPinyin(order.departureStation)
        tvArrStation?.text = "${order.arrivalStation}站"
        tvArrPinyin?.text = com.railway.ticketsystem.data.StationPinyinResolver.getPinyin(order.arrivalStation)
        tvDepTime?.text = "${formatFullDate(order.departureDate)} ${order.departureTime} 开"
        tvGate?.text = "检票口 $gate"
        tvSeatInfo?.text = order.seatInfo
        tvPrice?.text = "¥${money(order.finalPrice)}元"
        tvPassenger?.text = "${desensitizeId(order.passengerIdCard)} ${desensitizeName(order.passengerName)}"

        val serial = "W" + (Math.abs(order.id.hashCode()) % 900000 + 100000) + " " +
                order.departureDate.replace("-", "").takeLast(4) + " " +
                order.departureTime.replace(":", "") + " " +
                (Math.abs((order.id + order.passengerIdCard).hashCode().toLong()) % 9000000000000000L + 1000000000000000L)
        tvTicketCode?.text = serial

        val token = stableVoucherToken(order)
        val qrBitmap = qrBitmapFor(order, token)
        if (qrBitmap != null) {
            ivMiniQr?.setImageBitmap(qrBitmap)
        }

        var isShowingFront = true
        btnFlip?.setOnClickListener {
            val visibleCard = if (isShowingFront) cardFront else cardBack
            val hiddenCard = if (isShowingFront) cardBack else cardFront

            val anim1 = android.animation.ObjectAnimator.ofFloat(visibleCard, "rotationY", 0f, 90f).apply {
                duration = 200
            }
            val anim2 = android.animation.ObjectAnimator.ofFloat(hiddenCard, "rotationY", -90f, 0f).apply {
                duration = 200
            }
            anim1.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    visibleCard?.visibility = View.GONE
                    hiddenCard?.visibility = View.VISIBLE
                    hiddenCard?.rotationY = -90f
                    anim2.start()
                    isShowingFront = !isShowingFront
                    btnFlip.text = "翻转票面"
                }
            })
            anim1.start()
        }

        btnSave?.setOnClickListener {
            Toast.makeText(this, "电子磁介质客票已保存至本地相册", Toast.LENGTH_SHORT).show()
        }

        btnClose?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showTurnstileGateSimulationDialog(order: Order, gate: String) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_turnstile_gate_simulation, null)
        dialog.setContentView(view)
        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)

        val tvChannelBadge = view.findViewById<android.widget.TextView>(R.id.tvGateChannelBadge)
        val tvStatusLed = view.findViewById<android.widget.TextView>(R.id.tvGateStatusLed)
        val tvStatusText = view.findViewById<android.widget.TextView>(R.id.tvGateStatusText)
        val tvPassPrompt = view.findViewById<android.widget.TextView>(R.id.tvGatePassPrompt)
        val tvCameraPrompt = view.findViewById<android.widget.TextView>(R.id.tvFaceCameraPrompt)
        val tvPassengerId = view.findViewById<android.widget.TextView>(R.id.tvGatePassengerIdName)
        val layoutTap = view.findViewById<View>(R.id.layoutTapIdCard)
        val layoutResult = view.findViewById<View>(R.id.layoutVerificationResult)
        val tvVerifiedTrip = view.findViewById<android.widget.TextView>(R.id.tvGateVerifiedTripInfo)
        val tvVerifiedStation = view.findViewById<android.widget.TextView>(R.id.tvGateVerifiedStationGuide)
        val viewLaser = view.findViewById<View>(R.id.viewGateScanLaser)
        val btnClose = view.findViewById<View>(R.id.btnCloseGateDialog)

        val channelName = if (gate.isNotBlank()) "南 $gate 检票通道" else "南 02A 检票通道"
        tvChannelBadge?.text = channelName
        tvPassengerId?.text = "${order.passengerName} (身份证: ${desensitizeId(order.passengerIdCard)})"
        val platformNumber = (Math.abs(order.trainNumber.hashCode()) % 8 + 1)
        val seatDisplay = if (order.carNumber.isNotBlank() && order.seatNumber.isNotBlank()) {
            "${order.carNumber}车 ${order.seatNumber}号"
        } else order.seatInfo
        tvVerifiedTrip?.text = "车次：${order.trainNumber} 次 · $seatDisplay"
        tvVerifiedStation?.text = "乘车站：${order.departureStation} · 检票口：$gate · 请前往 ${platformNumber} 站台乘车"

        var isVerified = false
        layoutTap?.setOnClickListener {
            if (isVerified) {
                Toast.makeText(this, "该证件已完成进站核验", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            tvStatusLed?.text = "🟡"
            tvStatusText?.text = "已感应身份证，正在比对动态人脸活体..."
            tvPassPrompt?.text = "请平视前方摄像头并保持面部无遮挡..."
            tvCameraPrompt?.text = "正在采集面部特征点进行活体校验..."

            // 扫描激光上下往返微动
            android.animation.ObjectAnimator.ofFloat(viewLaser, "translationY", 0f, 320f).apply {
                duration = 600
                repeatCount = 1
                repeatMode = android.animation.ValueAnimator.REVERSE
                start()
            }

            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (isFinishing || isDestroyed) return@postDelayed
                isVerified = true
                window?.decorView?.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                tvStatusLed?.text = "🟢"
                tvStatusText?.text = "核验成功 · 票证人一致"
                tvPassPrompt?.text = "闸翼通道开启 · 请抓紧通行"
                tvCameraPrompt?.text = "✓ 动态活体比对 100% 吻合"
                layoutResult?.visibility = View.VISIBLE
                Toast.makeText(this, "核验通过，闸机已开，祝您旅途愉快！", Toast.LENGTH_SHORT).show()
            }, 1200L)
        }

        btnClose?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun desensitizeId(id: String): String {
        if (id.length < 8) return id
        return id.take(4) + "********" + id.takeLast(4)
    }

    private fun desensitizeName(name: String): String {
        if (name.length <= 1) return name
        return name.first() + "*".repeat(name.length - 1)
    }

    private fun renderWeatherTimeline(order: Order) {
        val stopNames = order.timetableStops?.map { it.stationName }?.ifEmpty { null }
            ?: listOf(order.departureStation, order.arrivalStation)
        val weather = RouteWeatherService.getWeatherTimeline(order.departureStation, order.arrivalStation, stopNames)
        binding.llWeatherStops.removeAllViews()

        weather.stops.forEachIndexed { _, stop ->
            val stopView = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setBackgroundResource(R.drawable.bg_passenger_selected)
                layoutParams = LinearLayout.LayoutParams(dp(76), dp(82)).apply {
                    marginEnd = dp(8)
                }

                val tvIcon = android.widget.TextView(context).apply {
                    text = stop.icon
                    textSize = 18f
                    gravity = android.view.Gravity.CENTER
                }
                val tvName = android.widget.TextView(context).apply {
                    text = stop.stationName
                    textSize = 12f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(getColor(R.color.railway_blue_deep))
                    gravity = android.view.Gravity.CENTER
                    maxLines = 1
                }
                val tvTemp = android.widget.TextView(context).apply {
                    text = "${stop.temperature}°C · ${stop.condition}"
                    textSize = 10f
                    setTextColor(getColor(R.color.text_secondary))
                    gravity = android.view.Gravity.CENTER
                }
                addView(tvIcon)
                addView(tvName)
                addView(tvTemp)
            }
            binding.llWeatherStops.addView(stopView)
        }

        binding.tvWeatherAlertBadge.text = weather.alertLevel ?: "宜出行 · 晴好"
        binding.tvWeatherAdvisory.text = "💡 ${weather.advisory}"
    }

    private fun renderStationExitGuide(order: Order) {
        val guide = StationGroundTransferCatalog.getGuide(order.arrivalStation)
        binding.tvExitGuideTitle.text = "🚇 到达【${guide.stationName}】地面接驳指引"
        binding.tvExitMetroLines.text = "🚇 地铁直达接驳：${guide.metroLines}"
        binding.tvExitMetroGuide.text = guide.metroGuide
        binding.tvExitTaxiGuide.text = "🚕 网约车与出租车：${guide.taxiRideHailLocation}"
        binding.tvExitBusGuide.text = "🚌 公交与机场大巴：${guide.busAirportShuttle}"
        binding.tvExitFastTip.text = "💡 快捷通行建议：${guide.fastExitTip}"
    }

    private fun renderLuggageChecklist(order: Order) {
        val checklistRepo = TripChecklistRepository(this)
        val tripKey = "${order.trainNumber}_${order.departureDate}"
        val items = checklistRepo.getItems(tripKey)
        binding.llChecklistContainer.removeAllViews()

        items.forEach { item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(4), dp(4), dp(4))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(4) }

                val checkBox = CheckBox(context).apply {
                    isChecked = item.isChecked
                    text = "${item.text} · ${item.category}"
                    textSize = 13f
                    setTextColor(getColor(if (item.isChecked) R.color.gray_medium else R.color.text_primary))
                    if (item.isChecked) {
                        paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                    }
                    setOnCheckedChangeListener { buttonView, isChecked ->
                        buttonView.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        item.isChecked = isChecked
                        checklistRepo.saveItems(tripKey, items)
                        setTextColor(getColor(if (isChecked) R.color.gray_medium else R.color.text_primary))
                        if (isChecked) {
                            paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                        } else {
                            paintFlags = paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                        }
                    }
                }
                addView(checkBox)
            }
            binding.llChecklistContainer.addView(row)
        }
    }

    private fun showAddChecklistItemDialog(order: Order) {
        val tripKey = "${order.trainNumber}_${order.departureDate}"
        val dialogBinding = DialogAddChecklistItemBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        }
        dialogBinding.btnCancelChecklist.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnConfirmChecklist.setOnClickListener {
            val text = dialogBinding.etNewChecklistText.text?.toString()?.trim().orEmpty()
            if (text.isNotBlank()) {
                val repo = TripChecklistRepository(this)
                repo.addItem(tripKey, text)
                renderLuggageChecklist(order)
                Toast.makeText(this, "已添加备忘项：$text", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } else {
                Toast.makeText(this, "请输入待办内容", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun renderSpecialAssistance(order: Order) {
        val assistanceRepo = SpecialAssistanceRepository(this)
        val tripKey = "${order.trainNumber}_${order.departureDate}"
        val record = assistanceRepo.getBooking(tripKey)

        if (record != null) {
            binding.tvAssistanceStatusBadge.text = "🟢 已预约"
            binding.tvAssistanceStatusBadge.setTextColor(getColor(R.color.success))
            binding.tvAssistancePrompt.text = "已为您成功预约【${record.serviceType}】服务。\n联系人：${record.passengerName}（${record.contactPhone}）\n${if (record.note.isNotBlank()) "备注需求：${record.note}\n" else ""}提示：${record.statusText}"
            binding.btnBookSpecialAssistance.text = "管理 / 取消爱心协助预约"
        } else {
            binding.tvAssistanceStatusBadge.text = "未预约"
            binding.tvAssistanceStatusBadge.setTextColor(getColor(R.color.text_secondary))
            binding.tvAssistancePrompt.text = "提供老幼病残孕重点旅客站台轮椅推车接送、视力协助引导与绿色进出站通道预约"
            binding.btnBookSpecialAssistance.text = "预约站台爱心接送协助"
        }
    }

    private fun showSpecialAssistanceDialog(order: Order) {
        val assistanceRepo = SpecialAssistanceRepository(this)
        val tripKey = "${order.trainNumber}_${order.departureDate}"
        val existing = assistanceRepo.getBooking(tripKey)

        if (existing != null) {
            AlertDialog.Builder(this)
                .setTitle("重点旅客协助预约管理")
                .setMessage("当前已预约：${existing.serviceType}\n联系电话：${existing.contactPhone}\n\n如需取消，请点击【取消本次预约】。")
                .setNegativeButton("保留预约", null)
                .setPositiveButton("取消本次预约") { _, _ ->
                    assistanceRepo.cancelBooking(tripKey)
                    renderSpecialAssistance(order)
                    Toast.makeText(this, "已取消爱心协助预约", Toast.LENGTH_SHORT).show()
                }
                .show()
            return
        }

        val dialogBinding = DialogSpecialAssistanceBinding.inflate(layoutInflater)
        dialogBinding.tvAssistanceTripSummary.text = "${order.trainNumber} ${order.departureStation} ➔ ${order.arrivalStation} · ${order.seatInfo} · ${order.passengerName}"
        dialogBinding.etAssistancePhone.setText(order.passengerPhone)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        }

        dialogBinding.btnCancelAssistance.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnConfirmAssistance.setOnClickListener {
            val phone = dialogBinding.etAssistancePhone.text?.toString()?.trim().orEmpty()
            if (phone.isBlank()) {
                Toast.makeText(this, "请填写联系人电话", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val serviceType = when (dialogBinding.rgAssistanceTypes.checkedRadioButtonId) {
                R.id.rbWheelchair -> "轮椅推车站台无障碍接驳"
                R.id.rbVisionGuide -> "视力障碍专人引导乘车"
                R.id.rbMaternalInfant -> "母婴/老幼特需绿色通道优先检票"
                R.id.rbLuggageHelp -> "重特大件行李站台便民搬运协助"
                else -> "重点旅客关怀协助"
            }
            val note = dialogBinding.etAssistanceNote.text?.toString()?.trim().orEmpty()
            val record = SpecialAssistanceRepository.AssistanceRecord(
                tripKey = tripKey,
                trainNumber = order.trainNumber,
                passengerName = order.passengerName,
                contactPhone = phone,
                serviceType = serviceType,
                note = note,
                bookedAt = System.currentTimeMillis(),
                statusText = "车站爱心服务专区工作人员将在发车前30分钟电话确认进站通道与接驳站台"
            )
            assistanceRepo.saveBooking(record)
            renderSpecialAssistance(order)
            Toast.makeText(this, "预约提交成功！车站专员将在发车前致电接洽", Toast.LENGTH_LONG).show()
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun renderArrivalAlarm(order: Order) {
        val alarmManager = ArrivalAlarmManager(this)
        val status = alarmManager.getAlarmStatus(order.id)

        binding.switchArrivalAlarm.setOnCheckedChangeListener(null)
        binding.switchArrivalAlarm.isChecked = status.isEnabled
        binding.switchArrivalAlarm.setOnCheckedChangeListener { _, isChecked ->
            toggleArrivalAlarm(order, isChecked)
        }

        updateLeadPillsUI(status.leadMinutes)

        if (status.isEnabled) {
            binding.tvArrivalAlarmStatus.text = "🟢 已开启 · 预计 ${status.formattedTriggerTime}（到站前 ${status.leadMinutes} 分钟）强力震动提醒"
            binding.tvArrivalAlarmStatus.setTextColor(getColor(R.color.success))
        } else {
            binding.tvArrivalAlarmStatus.text = "未开启 · 开启后将在到站前 ${status.leadMinutes} 分钟强震动提醒，避免坐过站"
            binding.tvArrivalAlarmStatus.setTextColor(getColor(R.color.text_secondary))
        }
    }

    private fun updateLeadPillsUI(leadMinutes: Int) {
        val activeBg = ContextCompat.getDrawable(this, R.drawable.bg_quick_date_pill_active)
        val inactiveBg = ContextCompat.getDrawable(this, R.drawable.bg_quick_date_pill)
        val activeColor = ContextCompat.getColor(this, R.color.railway_blue)
        val inactiveColor = ContextCompat.getColor(this, R.color.text_secondary)

        binding.btnLead15m.background = if (leadMinutes == 15) activeBg else inactiveBg
        binding.btnLead15m.setTextColor(if (leadMinutes == 15) activeColor else inactiveColor)
        binding.btnLead15m.setTypeface(null, if (leadMinutes == 15) Typeface.BOLD else Typeface.NORMAL)

        binding.btnLead20m.background = if (leadMinutes == 20) activeBg else inactiveBg
        binding.btnLead20m.setTextColor(if (leadMinutes == 20) activeColor else inactiveColor)
        binding.btnLead20m.setTypeface(null, if (leadMinutes == 20) Typeface.BOLD else Typeface.NORMAL)

        binding.btnLead30m.background = if (leadMinutes == 30) activeBg else inactiveBg
        binding.btnLead30m.setTextColor(if (leadMinutes == 30) activeColor else inactiveColor)
        binding.btnLead30m.setTypeface(null, if (leadMinutes == 30) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun updateAlarmLeadTime(order: Order, leadMinutes: Int) {
        val alarmManager = ArrivalAlarmManager(this)
        val current = alarmManager.getAlarmStatus(order.id)
        if (current.isEnabled) {
            val updated = alarmManager.scheduleAlarm(order, leadMinutes)
            Toast.makeText(this, "已更新为到站前 $leadMinutes 分钟唤醒（${updated.formattedTriggerTime}）", Toast.LENGTH_SHORT).show()
        } else {
            getSharedPreferences("arrival_alarm_prefs", MODE_PRIVATE).edit()
                .putInt("alarm_lead_${order.id}", leadMinutes)
                .apply()
        }
        renderArrivalAlarm(order)
    }

    private fun toggleArrivalAlarm(order: Order, isChecked: Boolean) {
        val alarmManager = ArrivalAlarmManager(this)
        val lead = alarmManager.getAlarmStatus(order.id).leadMinutes
        if (isChecked) {
            val status = alarmManager.scheduleAlarm(order, lead)
            Toast.makeText(this, "到站防踏空闹钟已开启！将在 ${status.formattedTriggerTime} 震动提醒", Toast.LENGTH_SHORT).show()
        } else {
            alarmManager.cancelAlarm(order.id)
            Toast.makeText(this, "到站唤醒闹钟已关闭", Toast.LENGTH_SHORT).show()
        }
        renderArrivalAlarm(order)
    }

    private fun renderChildDeclaration(order: Order) {
        val repo = ChildDeclarationRepository(this)
        val tripKey = "${order.trainNumber}_${order.departureDate}_${order.passengerName}"
        val record = repo.getDeclaration(tripKey)

        if (record != null) {
            binding.tvChildDeclarationBadge.text = "🟢 已申报"
            binding.tvChildDeclarationBadge.setTextColor(getColor(R.color.success))
            binding.tvChildDeclarationPrompt.text = "已绑定免票同行儿童：${record.childName}（证件号：${record.idNumber}）\n通行凭证号：${record.certificateCode}\n申报时间：${record.declaredAt}\n提示：进出站请随主乘车人一同经人脸/人工闸机直接核验通行。"
            binding.btnDeclareChild.text = "管理 / 取消免费儿童申报"
        } else {
            binding.tvChildDeclarationBadge.text = "未申报"
            binding.tvChildDeclarationBadge.setTextColor(getColor(R.color.text_secondary))
            binding.tvChildDeclarationPrompt.text = "依规定：每名持票成人可带一名未满6周岁且不占座儿童，需在线提前申报以便闸机直接放行"
            binding.btnDeclareChild.text = "申报免费乘车儿童"
        }
    }

    private fun showChildDeclarationDialog(order: Order) {
        val repo = ChildDeclarationRepository(this)
        val tripKey = "${order.trainNumber}_${order.departureDate}_${order.passengerName}"
        val existing = repo.getDeclaration(tripKey)

        if (existing != null) {
            android.app.AlertDialog.Builder(this)
                .setTitle("免费乘车儿童申报管理")
                .setMessage("当前已绑定免票儿童：${existing.childName}\n证件号码：${existing.idNumber}\n凭证编号：${existing.certificateCode}\n同行成人：${existing.adultPassenger}\n\n如需取消绑定，请点击【取消本次申报】。")
                .setPositiveButton("保留申报", null)
                .setNegativeButton("取消本次申报") { _, _ ->
                    repo.cancelDeclaration(tripKey)
                    Toast.makeText(this, "已取消该儿童免费乘车申报", Toast.LENGTH_SHORT).show()
                    renderChildDeclaration(order)
                }
                .show()
            return
        }

        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_declare_child, null)
        dialog.setContentView(view)

        val etChildName = view.findViewById<EditText>(R.id.etChildName)
        val etChildId = view.findViewById<EditText>(R.id.etChildIdNumber)
        val tvAdultInfo = view.findViewById<TextView>(R.id.tvAdultPassengerInfo)
        val btnCancel = view.findViewById<View>(R.id.btnCancelDeclareChild)
        val btnSubmit = view.findViewById<View>(R.id.btnSubmitDeclareChild)

        tvAdultInfo.text = "同行成年人：${order.passengerName}（${order.trainNumber}次 ${order.departureStation} → ${order.arrivalStation}）"

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSubmit.setOnClickListener {
            val name = etChildName.text.toString().trim()
            val idNum = etChildId.text.toString().trim()
            if (name.isEmpty() || idNum.isEmpty()) {
                Toast.makeText(this, "请完整填写儿童姓名与证件号", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            repo.saveDeclaration(tripKey, name, idNum, order.passengerName)
            Toast.makeText(this, "免费乘车儿童申报成功！已生成进站电子凭证", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            renderChildDeclaration(order)
        }

        dialog.show()
    }

    private fun mutePhoneQuietMode() {
        val audioManager = getSystemService(AUDIO_SERVICE) as? android.media.AudioManager
        try {
            audioManager?.ringerMode = android.media.AudioManager.RINGER_MODE_VIBRATE
            Toast.makeText(this, "🔕 已为您将手机切换至静音/振动模式，共同守护静音车厢", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "请在系统控制中心开启手机静音模式，践行静音车厢公约", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestNoiseCancellingEarplugs(order: Order) {
        val car = if (order.carNumber.isNotBlank()) "${order.carNumber}车" else "您所在车厢"
        val seat = if (order.seatNumber.isNotBlank()) order.seatNumber else "您的席位"
        android.app.AlertDialog.Builder(this)
            .setTitle("索取静音降噪耳塞")
            .setMessage("将为您呼叫 $car 乘务员免费送达一套高分子慢回弹降噪耳塞至 $seat。\n\n请确认是否呼叫乘务员送达？")
            .setPositiveButton("立即索取") { _, _ ->
                Toast.makeText(this, "乘务组已受理！乘务员将在5分钟内将降噪耳塞送达您的席位", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}


