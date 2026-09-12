package com.railway.ticketsystem.activity

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.railway.ticketsystem.R
import com.railway.ticketsystem.data.MealOrderRepository
import com.railway.ticketsystem.data.MembershipRepository
import com.railway.ticketsystem.data.MessageRepository
import com.railway.ticketsystem.data.MultiRidePass
import com.railway.ticketsystem.data.MultiRidePassPricing
import com.railway.ticketsystem.data.MultiRidePassRepository
import com.railway.ticketsystem.data.MultiRideQuote
import com.railway.ticketsystem.data.RailwayData
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.data.plusDaysDate
import com.railway.ticketsystem.data.todayDate
import com.railway.ticketsystem.databinding.ActivityMultiRidePassBinding
import com.railway.ticketsystem.model.RouteType
import com.railway.ticketsystem.model.Station
import java.util.Locale
import java.util.UUID

class MultiRidePassActivity : ImmersiveActivity() {
    private enum class StationField { DEPARTURE, ARRIVAL }

    private lateinit var binding: ActivityMultiRidePassBinding
    private lateinit var userRepository: UserRepository
    private lateinit var membershipRepository: MembershipRepository
    private lateinit var passRepository: MultiRidePassRepository
    private lateinit var messageRepository: MessageRepository
    private var stationNames: List<String> = emptyList()
    private var referencePriceCents = 0L
    private var validityDays = 30
    private var rides = 10
    private var pickingField = StationField.DEPARTURE
    private val stationPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val station = result.data?.getSerializableExtra("selectedStation") as? Station
        if (result.resultCode == RESULT_OK && station != null) {
            when (pickingField) {
                StationField.DEPARTURE -> binding.actPassDeparture.setText(station.name)
                StationField.ARRIVAL -> binding.actPassArrival.setText(station.name)
            }
            referencePriceCents = 0L
            binding.tvPassReference.text = "已选择区间，请查询参考票价"
            updateQuote()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMultiRidePassBinding.inflate(layoutInflater)
        setContentView(binding.root)
        userRepository = UserRepository(this)
        membershipRepository = MembershipRepository(this)
        passRepository = MultiRidePassRepository(this)
        messageRepository = MessageRepository(this)
        binding.btnBack.setOnClickListener { finish() }
        stationNames = RailwayData.stations.map { it.name }.distinct().sorted()
        binding.actPassDeparture.setText(stationNames.firstOrNull { it == "北京南" } ?: stationNames.firstOrNull().orEmpty())
        binding.actPassArrival.setText(stationNames.firstOrNull { it == "上海虹桥" } ?: stationNames.drop(1).firstOrNull().orEmpty())
        binding.actPassDeparture.setOnClickListener { openStationSelection(StationField.DEPARTURE) }
        binding.actPassArrival.setOnClickListener { openStationSelection(StationField.ARRIVAL) }
        binding.btnPassCalculate.setOnClickListener { calculateReferencePrice() }
        binding.btnPass30Days.setOnClickListener { validityDays = 30; updateQuote() }
        binding.btnPass90Days.setOnClickListener { validityDays = 90; updateQuote() }
        binding.btnPass10Rides.setOnClickListener { rides = 10; updateQuote() }
        binding.btnPass20Rides.setOnClickListener { rides = 20; updateQuote() }
        binding.btnPass30Rides.setOnClickListener { rides = 30; updateQuote() }
        binding.btnBuyPass.setOnClickListener { buyPass() }
        updateQuote()
        renderPassRecords()
    }

    private fun openStationSelection(field: StationField) {
        pickingField = field
        stationPicker.launch(Intent(this, StationSelectionActivity::class.java))
    }

    private fun calculateReferencePrice() {
        val from = binding.actPassDeparture.text?.toString()?.trim().orEmpty()
        val to = binding.actPassArrival.text?.toString()?.trim().orEmpty()
        if (from !in stationNames || to !in stationNames || from == to) {
            Toast.makeText(this, "请选择不同的有效出发站和到达站", Toast.LENGTH_SHORT).show()
            return
        }
        // 两套网络分别取价，避免普速区间落到高铁默认价格或兜底价格。
        val loadedPrice = listOf(
            RailwayData.getPriceBetweenStations(from, to, RouteType.HIGH_SPEED),
            RailwayData.getPriceBetweenStations(from, to, RouteType.CONVENTIONAL)
        ).firstOrNull { it > 0.0 } ?: 0.0
        val storedPrice = RailwayData.trains.asSequence()
            .filter { it.departureStation == from && it.arrivalStation == to }
            .map { it.price }
            .minOrNull()
        referencePriceCents = when {
            storedPrice != null && storedPrice > 0.0 -> (storedPrice * 100).toLong()
            loadedPrice > 0.0 -> (loadedPrice * 100).toLong()
            else -> MultiRidePassPricing.fallbackPriceCents(from, to)
        }
        binding.tvPassReference.text = "${from} → ${to} · 参考单程 ${money(referencePriceCents)}"
        updateQuote()
    }

    private fun updateQuote() {
        val quote = if (referencePriceCents > 0) {
            MultiRidePassPricing.quote(referencePriceCents, validityDays, rides)
        } else null
        binding.tvPassQuote.text = quote?.let {
            "${it.validityDays}天 · ${it.rides}次\n${money(it.totalCents)}  ·  单次约${money(it.perRideCents)}\n较参考票价节省 ${money(it.savingCents)}"
        } ?: "选择区间后显示总价与单次优惠"
        binding.btnBuyPass.isEnabled = quote != null
        binding.btnBuyPass.text = quote?.let { "钱包支付 ${money(it.totalCents)}" } ?: "购买计次票"
        toggle(binding.btnPass30Days, validityDays == 30)
        toggle(binding.btnPass90Days, validityDays == 90)
        toggle(binding.btnPass10Rides, rides == 10)
        toggle(binding.btnPass20Rides, rides == 20)
        toggle(binding.btnPass30Rides, rides == 30)
    }

    private fun buyPass() {
        val user = userRepository.getCurrentUser() ?: run {
            Toast.makeText(this, "请先登录后购买计次票", Toast.LENGTH_SHORT).show()
            return
        }
        val from = binding.actPassDeparture.text?.toString()?.trim().orEmpty()
        val to = binding.actPassArrival.text?.toString()?.trim().orEmpty()
        if (referencePriceCents <= 0 || from !in stationNames || to !in stationNames || from == to) {
            Toast.makeText(this, "请先查询有效区间", Toast.LENGTH_SHORT).show()
            return
        }
        val quote = MultiRidePassPricing.quote(referencePriceCents, validityDays, rides)
        val passId = "PASS_${UUID.randomUUID()}"
        if (!membershipRepository.payWithWallet(user.id, passId, quote.totalCents, "购买计次票 · $from→$to")) {
            Toast.makeText(this, "钱包余额不足，请先前往会员中心充值", Toast.LENGTH_LONG).show()
            return
        }
        val pass = MultiRidePass(
            id = passId,
            userId = user.id,
            departureStation = from,
            arrivalStation = to,
            validityDays = validityDays,
            totalRides = rides,
            usedRides = 0,
            referencePriceCents = referencePriceCents,
            paidCents = quote.totalCents,
            validFrom = todayDate(),
            validUntil = plusDaysDate(validityDays)
        )
        if (!passRepository.save(pass)) {
            membershipRepository.refundWalletPayment(user.id, passId, quote.totalCents)
            Toast.makeText(this, "计次票保存失败，已退回钱包余额", Toast.LENGTH_SHORT).show()
            return
        }
        messageRepository.add(
            user.id,
            MessageRepository.TICKET,
            "计次票购买成功",
            "${from}至${to} ${validityDays}天${rides}次，${pass.validUntil}前有效。",
            eventKey = "multi_ride_pass:${pass.id}"
        )
        Toast.makeText(this, "计次票已存入账户", Toast.LENGTH_LONG).show()
        renderPassRecords()
    }

    private fun renderPassRecords() {
        val user = userRepository.getCurrentUser()
        if (user == null) {
            binding.tvPassRecords.text = "登录后可查看已购买的计次票。"
            return
        }
        val records = passRepository.getByUser(user.id).take(4)
        binding.tvPassRecords.text = if (records.isEmpty()) {
            "暂无计次票。30天产品总价更轻，90天产品适合更长的通勤周期；同一有效期内次数越多，单次越优惠。"
        } else {
            "我的计次票\n" + records.joinToString("\n\n") {
                "${it.departureStation} → ${it.arrivalStation}\n${it.validityDays}天 ${it.usedRides}/${it.totalRides}次 · 有效至${it.validUntil} · ${it.status}"
            }
        }
    }

    private fun toggle(button: MaterialButton, selected: Boolean) {
        button.backgroundTintList = ColorStateList.valueOf(
            android.graphics.Color.parseColor(if (selected) "#D5D9F0FF" else "#B8FFFFFF")
        )
        button.strokeColor = ColorStateList.valueOf(
            android.graphics.Color.parseColor(if (selected) "#B077BDF4" else "#A8FFFFFF")
        )
        button.strokeWidth = (resources.displayMetrics.density).toInt()
        button.cornerRadius = (20 * resources.displayMetrics.density).toInt()
        button.rippleColor = ColorStateList.valueOf(android.graphics.Color.parseColor("#260677D7"))
        button.setTextColor(ContextCompat.getColor(this, if (selected) R.color.railway_blue_deep else R.color.text_primary))
        button.elevation = 0f
    }

    private fun money(cents: Long) = "¥" + String.format(Locale.CHINA, "%.2f", cents / 100.0)
}
