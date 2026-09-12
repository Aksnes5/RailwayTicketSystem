package com.railway.ticketsystem.activity

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.railway.ticketsystem.R
import com.railway.ticketsystem.adapter.TrainStopScheduleAdapter
import com.railway.ticketsystem.data.RailwayData
import com.railway.ticketsystem.data.RoutePresentationPlanner
import com.railway.ticketsystem.data.TrainSetResolver
import com.railway.ticketsystem.data.TrainStopSchedulePlanner
import com.railway.ticketsystem.data.TimetableStopStatusResolver
import com.railway.ticketsystem.data.TrainStopSchedule
import com.railway.ticketsystem.databinding.ActivityTrainDetailBinding
import com.railway.ticketsystem.model.Train

class TrainDetailActivity : ImmersiveActivity() {
    
    private lateinit var binding: ActivityTrainDetailBinding
    private lateinit var train: Train
    private lateinit var stationAdapter: TrainStopScheduleAdapter
    private var departureDate: String = ""
    private var publishedTimetable: List<TrainStopSchedule> = emptyList()
    private val statusTicker = object : Runnable {
        override fun run() {
            refreshLiveStopStatuses()
            binding.root.postDelayed(this, 60_000L)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrainDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 获取传递的车次信息
        val trainData = intent.getSerializableExtra("train") as? Train
        if (trainData == null) {
            android.util.Log.e("TrainDetailActivity", "无法获取车次信息，Activity将关闭")
            Toast.makeText(this, "无法获取车次信息", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        train = trainData
        android.util.Log.d("TrainDetailActivity", "成功获取车次信息: ${train.number}")
        departureDate = intent.getStringExtra("departureDate") ?: getCurrentDate()
        
        setupUI()
        setupStationList()
    }

    override fun onResume() {
        super.onResume()
        binding.root.post(statusTicker)
    }

    override fun onPause() {
        binding.root.removeCallbacks(statusTicker)
        super.onPause()
    }
    
    private fun setupUI() {
        // 设置返回按钮
        binding.btnBack.setOnClickListener {
            finish()
        }
        
        // 设置车次信息
        binding.tvTrainNumber.text = train.number
        binding.tvRoute.text = "${train.departureStation} → ${train.arrivalStation}"
        binding.tvDepartureTime.text = train.departureTime
        binding.tvArrivalTime.text = train.arrivalTime
        binding.tvJourneyDate.text = formatMonthDay(departureDate)
        binding.tvDuration.text = "历时${train.duration}"
        binding.tvPrice.text = "¥${train.price.toInt()}"
        
        // 设置订票按钮
        binding.btnBookTicket.setOnClickListener {
            // 跳转到座位选择页面
            val intent = android.content.Intent(this, SeatSelectionActivity::class.java)
            intent.putExtra("selectedTrain", train)
            intent.putExtra("departureDate", departureDate)
            startActivity(intent)
        }

        binding.btnRouteMap.setOnClickListener {
            val routeStations = RailwayData.getTrainRouteStations(train)
                .ifEmpty {
                    listOf(train.departureStation, train.arrivalStation)
                }
            val callingStations = publishedTimetable.map { it.stationName }
                .ifEmpty { routeStations }
            val mapTimetable = publishedTimetable.map {
                RailwayMapTimetableStop(it.stationName, it.arrivalTime, it.departureTime)
            }
            startActivity(
                RailwayMapActivity.intent(
                    context = this,
                    trainNumber = train.number,
                    routeStations = routeStations,
                    callingStations = callingStations,
                    boardingStation = train.departureStation,
                    alightingStation = train.arrivalStation,
                    departureDate = departureDate,
                    timetableStops = mapTimetable
                )
            )
        }
        
        // 添加测试按钮（临时调试用）
        binding.btnBookTicket.setOnLongClickListener {
            testRouteStations()
            true
        }
    }
    
    private fun setupStationList() {
        // The detail page is the first and only place where calls and timetable
        // are calculated. Search results stay as light-weight train summaries.
        val fullRouteStations = RailwayData.getTrainRouteStations(train)
            .ifEmpty { listOf(train.departureStation, train.arrivalStation) }
        // The search row remains authoritative for the selected passenger
        // section. The planner may expose a longer operating route, but cannot
        // skip either query station.
        val displayPlan = RoutePresentationPlanner.plan(
            fullStations = fullRouteStations,
            trainNumber = train.number,
            originalDuration = train.duration,
            requiredCalls = setOf(train.departureStation, train.arrivalStation)
        )
        val routeStations = displayPlan.stations
        TrainSetResolver.modelFor(train)?.let { model ->
            binding.tvTrainSet.text = model
            binding.layoutTrainSet.visibility = android.view.View.VISIBLE
        } ?: run {
            binding.layoutTrainSet.visibility = android.view.View.GONE
        }
        
        // 添加调试信息
        android.util.Log.d("TrainDetailActivity", "车次: ${train.number}")
        android.util.Log.d("TrainDetailActivity", "起点: ${train.departureStation}")
        android.util.Log.d("TrainDetailActivity", "终点: ${train.arrivalStation}")
        android.util.Log.d("TrainDetailActivity", "完整途径车站数量: ${fullRouteStations.size}")
        android.util.Log.d("TrainDetailActivity", "展示途径车站: ${routeStations.joinToString(" -> ")}")
        android.util.Log.d("TrainDetailActivity", "省会枢纽保留，折叠站数: ${displayPlan.skippedStationCount}")
        
        if (routeStations.isNotEmpty()) {
            val timetable = TrainStopSchedulePlanner.createAnchored(
                stations = routeStations,
                trainNumber = train.number,
                queryDepartureStation = train.departureStation,
                queryArrivalStation = train.arrivalStation,
                queryDepartureTime = train.departureTime,
                queryDuration = train.duration
            )
            publishedTimetable = timetable.stops
            stationAdapter = TrainStopScheduleAdapter(timetable.stops)
            binding.rvStations.apply {
                layoutManager = LinearLayoutManager(this@TrainDetailActivity)
                adapter = stationAdapter
                isVerticalScrollBarEnabled = true
                scrollBarStyle = android.view.View.SCROLLBARS_OUTSIDE_OVERLAY
                visibility = android.view.View.VISIBLE
            }
            
            val stationCountText = if (routeStations.size > 5) {
                stationCountMessage(routeStations, routeStations.size, "向下滑动查看完整时刻")
            } else {
                stationCountMessage(routeStations, routeStations.size)
            }
            binding.tvStationCount.text = stationCountText
            refreshLiveStopStatuses()
            
            // 添加滚动监听器来显示滚动提示
            binding.rvStations.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    updateScrollHint()
                }
            })
        } else {
            binding.tvStationCount.text = "暂无经停站信息"
            binding.rvStations.visibility = android.view.View.GONE
        }
    }
    
    private fun updateScrollHint() {
        val layoutManager = binding.rvStations.layoutManager as? LinearLayoutManager
        if (layoutManager != null) {
            val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
            val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
            val totalItemCount = layoutManager.itemCount
            
            val hintText = when {
                firstVisiblePosition == 0 && lastVisiblePosition == totalItemCount - 1 -> {
                    // 所有项目都可见
                    stationCountMessage(publishedTimetable.map { it.stationName }, totalItemCount)
                }
                firstVisiblePosition == 0 -> {
                    // 在顶部
                    stationCountMessage(publishedTimetable.map { it.stationName }, totalItemCount, "向下滑动查看完整时刻")
                }
                lastVisiblePosition == totalItemCount - 1 -> {
                    // 在底部
                    stationCountMessage(publishedTimetable.map { it.stationName }, totalItemCount, "向上滑动查看完整时刻")
                }
                else -> {
                    // 在中间
                    stationCountMessage(publishedTimetable.map { it.stationName }, totalItemCount, "可上下滑动查看完整时刻")
                }
            }
            binding.tvStationCount.text = hintText
        }
    }
    
    private fun getCurrentDate(): String {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return dateFormat.format(java.util.Date())
    }

    private fun serviceRouteText(stations: List<String>): String = when {
        stations.size < 2 -> ""
        stations.first() == train.departureStation && stations.last() == train.arrivalStation -> ""
        else -> "${stations.first()}始发 · ${stations.last()}终到"
    }.trim().trim('·').trim()

    private fun stationCountMessage(stations: List<String>, count: Int, hint: String = ""): String {
        val countText = buildString {
            append("共 ${count} 个经停站")
            if (hint.isNotBlank()) append(" · $hint")
        }
        return listOfNotNull(serviceRouteText(stations).takeIf { it.isNotBlank() }, countText)
            .joinToString(" · ")
    }

    private fun refreshLiveStopStatuses() {
        if (!::stationAdapter.isInitialized || publishedTimetable.isEmpty()) return
        stationAdapter.updateStops(
            TimetableStopStatusResolver.resolve(publishedTimetable, train.number, departureDate)
        )
    }

    private fun formatMonthDay(value: String): String {
        val source = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val target = java.text.SimpleDateFormat("M月d日", java.util.Locale.CHINA)
        return runCatching { source.parse(value) }.getOrNull()?.let(target::format) ?: value
    }
    
    private fun testRouteStations() {
        android.util.Log.d("TrainDetailActivity", "=== 开始测试途径车站功能 ===")
        
        // 测试当前车次的途径车站
        val currentRouteStations = RailwayData.getTrainRouteStations(train)
        android.util.Log.d("TrainDetailActivity", "当前车次途径车站数量: ${currentRouteStations.size}")
        android.util.Log.d("TrainDetailActivity", "当前车次途径车站: ${currentRouteStations.joinToString(" -> ")}")
        
        // 测试一些已知的线路
        val testRoutes = listOf(
            "南京南" to "成都东",
            "北京丰台" to "香港西九龙",
            "南昌西" to "福州",
            "厦门北" to "深圳北",
            "南京" to "上海"
        )
        
        testRoutes.forEach { (from, to) ->
            val stations = RailwayData.getRouteStations(from, to)
            android.util.Log.d("TrainDetailActivity", "$from -> $to: ${stations.size}个站点")
            if (stations.isNotEmpty()) {
                android.util.Log.d("TrainDetailActivity", "途径车站: ${stations.joinToString(" -> ")}")
            } else {
                android.util.Log.w("TrainDetailActivity", "警告: $from -> $to 没有找到途径车站")
            }
        }
        
        android.util.Log.d("TrainDetailActivity", "=== 途径车站功能测试完成 ===")
        
        // 显示测试结果
        val message = if (currentRouteStations.isNotEmpty()) {
            "途径车站功能正常\n当前车次有${currentRouteStations.size}个途径车站"
        } else {
            "途径车站功能可能有问题\n当前车次没有找到途径车站"
        }
        
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
