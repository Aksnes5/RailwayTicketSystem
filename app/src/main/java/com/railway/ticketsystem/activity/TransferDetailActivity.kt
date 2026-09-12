package com.railway.ticketsystem.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.railway.ticketsystem.R
import com.railway.ticketsystem.adapter.TrainStopScheduleAdapter
import com.railway.ticketsystem.data.RailwayData
import com.railway.ticketsystem.data.RoutePresentationPlanner
import com.railway.ticketsystem.data.TimetableStopStatusResolver
import com.railway.ticketsystem.data.TrainSetResolver
import com.railway.ticketsystem.data.TrainStopSchedule
import com.railway.ticketsystem.data.TrainStopSchedulePlanner
import com.railway.ticketsystem.databinding.ActivityTransferDetailBinding
import com.railway.ticketsystem.databinding.ViewTransferLegDetailBinding
import com.railway.ticketsystem.model.Train
import com.railway.ticketsystem.model.TransferRisk

/**
 * Detail screen for a transfer itinerary.
 *
 * A transfer is two real services rather than a synthetic third train. Each
 * leg therefore uses the same route planner, half-arrow, train-set resolver,
 * live stop status resolver and RailwayMapActivity payload as a direct result.
 */
class TransferDetailActivity : ImmersiveActivity() {
    private lateinit var binding: ActivityTransferDetailBinding
    private lateinit var firstLeg: Train
    private lateinit var secondLeg: Train
    private var transferStation = ""
    private var transferTime = 0
    private var totalPrice = 0.0
    private var totalDuration = ""
    private var departureDate = ""
    private val presentations = mutableListOf<LegPresentation>()
    private val statusTicker = object : Runnable {
        override fun run() {
            refreshLiveStopStatuses()
            binding.root.postDelayed(this, LIVE_STATUS_REFRESH_MILLIS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransferDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val first = intent.getSerializableExtra(EXTRA_FIRST_LEG) as? Train
        val second = intent.getSerializableExtra(EXTRA_SECOND_LEG) as? Train
        if (first == null || second == null) {
            Toast.makeText(this, "中转换乘信息不完整", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        firstLeg = first
        secondLeg = second
        transferStation = intent.getStringExtra(EXTRA_TRANSFER_STATION).orEmpty()
        transferTime = intent.getIntExtra(EXTRA_TRANSFER_TIME, 0)
        totalPrice = intent.getDoubleExtra(EXTRA_TOTAL_PRICE, firstLeg.price + secondLeg.price)
        totalDuration = intent.getStringExtra(EXTRA_TOTAL_DURATION).orEmpty().ifBlank {
            calculateTotalDuration(firstLeg.duration, secondLeg.duration, transferTime)
        }
        departureDate = intent.getStringExtra(EXTRA_DEPARTURE_DATE).orEmpty().ifBlank { currentDate() }

        setupSummary()
        renderLeg(binding.llFirstLeg, "第一程", firstLeg)
        renderLeg(binding.llSecondLeg, "第二程", secondLeg)
        binding.btnBack.setOnClickListener { finish() }
        binding.btnChooseTransferSeats.setOnClickListener { openSeatSelection() }
    }

    override fun onResume() {
        super.onResume()
        binding.root.post(statusTicker)
    }

    override fun onPause() {
        binding.root.removeCallbacks(statusTicker)
        super.onPause()
    }

    private fun setupSummary() {
        binding.tvTransferRoute.text = "${firstLeg.departureStation} → ${secondLeg.arrivalStation}"
        binding.tvTransferDepartureTime.text = firstLeg.departureTime
        binding.tvTransferArrivalTime.text = secondLeg.arrivalTime
        binding.tvTransferJourneyDate.text = formatMonthDay(departureDate)
        binding.tvTransferDuration.text = "历时$totalDuration"
        binding.tvTransferPrice.text = "¥${totalPrice.toInt()}起"
        binding.tvTransferInfo.text = "在${transferStation.ifBlank { firstLeg.arrivalStation }}换乘${transferTime}分钟"
        val risk = transferRisk(transferTime)
        binding.tvTransferRisk.text = when (risk) {
            TransferRisk.STEADY -> "稳妥换乘"
            TransferRisk.TIGHT -> "时间较紧"
            TransferRisk.NOT_RECOMMENDED -> "不建议换乘"
        }
        binding.tvTransferRisk.setTextColor(getColor(when (risk) {
            TransferRisk.STEADY -> R.color.success
            TransferRisk.TIGHT -> R.color.railway_orange
            TransferRisk.NOT_RECOMMENDED -> R.color.railway_red
        }))
    }

    private fun renderLeg(container: LinearLayout, label: String, train: Train) {
        val legBinding = ViewTransferLegDetailBinding.inflate(layoutInflater, container, false)
        container.addView(legBinding.root)
        legBinding.tvLegLabel.text = label
        legBinding.tvLegTrainNumber.text = train.number
        legBinding.tvLegRoute.text = "${train.departureStation} → ${train.arrivalStation}"
        legBinding.tvLegDepartureTime.text = train.departureTime
        legBinding.tvLegArrivalTime.text = train.arrivalTime
        legBinding.tvLegJourneyDate.text = formatMonthDay(departureDate)
        legBinding.tvLegDuration.text = "历时${train.duration}"
        legBinding.tvLegPrice.text = "¥${train.price.toInt()}"
        TrainSetResolver.modelFor(train)?.let { model ->
            legBinding.layoutLegTrainSet.visibility = View.VISIBLE
            legBinding.tvLegTrainSet.text = model
        } ?: run {
            legBinding.layoutLegTrainSet.visibility = View.GONE
        }

        val fullRoute = RailwayData.getTrainRouteStations(train)
            .ifEmpty { listOf(train.departureStation, train.arrivalStation) }
        val displayPlan = RoutePresentationPlanner.plan(
            fullStations = fullRoute,
            trainNumber = train.number,
            originalDuration = train.duration,
            requiredCalls = setOf(train.departureStation, train.arrivalStation)
        )
        val timetable = TrainStopSchedulePlanner.createAnchored(
            stations = displayPlan.stations,
            trainNumber = train.number,
            queryDepartureStation = train.departureStation,
            queryArrivalStation = train.arrivalStation,
            queryDepartureTime = train.departureTime,
            queryDuration = train.duration
        ).stops
        val adapter = TrainStopScheduleAdapter(timetable)
        legBinding.rvLegStops.apply {
            layoutManager = LinearLayoutManager(this@TransferDetailActivity)
            this.adapter = adapter
            isVerticalScrollBarEnabled = true
            scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
        }
        legBinding.tvLegStationCount.text = "共 ${displayPlan.stations.size} 个经停站"
        presentations += LegPresentation(train, fullRoute, timetable, adapter)
        legBinding.btnLegRouteMap.setOnClickListener {
            openRouteMap(train, fullRoute, timetable)
        }
    }

    private fun openRouteMap(train: Train, fullRoute: List<String>, timetable: List<TrainStopSchedule>) {
        startActivity(
            RailwayMapActivity.intent(
                context = this,
                trainNumber = train.number,
                routeStations = fullRoute,
                callingStations = timetable.map { it.stationName },
                boardingStation = train.departureStation,
                alightingStation = train.arrivalStation,
                departureDate = departureDate,
                timetableStops = timetable.map {
                    RailwayMapTimetableStop(it.stationName, it.arrivalTime, it.departureTime)
                }
            )
        )
    }

    private fun refreshLiveStopStatuses() {
        presentations.forEach { presentation ->
            presentation.adapter.updateStops(
                TimetableStopStatusResolver.resolve(
                    presentation.timetable,
                    presentation.train.number,
                    departureDate
                )
            )
        }
    }

    private fun openSeatSelection() {
        startActivity(Intent(this, SeatSelectionActivity::class.java).apply {
            putExtra("isTransfer", true)
            putExtra(EXTRA_FIRST_LEG, firstLeg)
            putExtra(EXTRA_SECOND_LEG, secondLeg)
            putExtra(EXTRA_TRANSFER_STATION, transferStation)
            putExtra(EXTRA_TRANSFER_TIME, transferTime)
            putExtra(EXTRA_TOTAL_PRICE, totalPrice)
            putExtra(EXTRA_DEPARTURE_DATE, departureDate)
        })
    }

    private fun formatMonthDay(value: String): String {
        val source = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
        val target = java.text.SimpleDateFormat("M月d日", java.util.Locale.CHINA)
        return runCatching { source.parse(value) }.getOrNull()?.let(target::format) ?: value
    }

    private fun currentDate(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).format(java.util.Date())

    private fun calculateTotalDuration(first: String, second: String, transfer: Int): String {
        val totalMinutes = RoutePresentationPlanner.durationMinutes(first) +
            RoutePresentationPlanner.durationMinutes(second) + transfer
        return RoutePresentationPlanner.formatMinutes(totalMinutes)
    }

    private fun transferRisk(minutes: Int): TransferRisk = when {
        minutes >= 90 -> TransferRisk.STEADY
        minutes >= 45 -> TransferRisk.TIGHT
        else -> TransferRisk.NOT_RECOMMENDED
    }

    private data class LegPresentation(
        val train: Train,
        val fullRoute: List<String>,
        val timetable: List<TrainStopSchedule>,
        val adapter: TrainStopScheduleAdapter
    )

    companion object {
        const val EXTRA_FIRST_LEG = "firstLeg"
        const val EXTRA_SECOND_LEG = "secondLeg"
        const val EXTRA_TRANSFER_STATION = "transferStation"
        const val EXTRA_TRANSFER_TIME = "transferTime"
        const val EXTRA_TOTAL_PRICE = "totalPrice"
        const val EXTRA_TOTAL_DURATION = "totalDuration"
        const val EXTRA_DEPARTURE_DATE = "departureDate"
        private const val LIVE_STATUS_REFRESH_MILLIS = 60_000L
    }
}
