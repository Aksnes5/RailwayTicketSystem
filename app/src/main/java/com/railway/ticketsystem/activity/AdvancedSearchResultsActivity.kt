package com.railway.ticketsystem.activity

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.railway.ticketsystem.R
import com.railway.ticketsystem.adapter.TrainResultAdapter
import com.railway.ticketsystem.adapter.TransferTrainAdapter
import com.railway.ticketsystem.data.DepartureTimingPolicy
import com.railway.ticketsystem.data.RailwayData
import com.railway.ticketsystem.databinding.ActivitySearchResultsBinding
import com.railway.ticketsystem.data.SeatInventoryRepository
import com.railway.ticketsystem.model.Train
import com.railway.ticketsystem.model.TransferTrain
import com.railway.ticketsystem.model.TransferRisk

class AdvancedSearchResultsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySearchResultsBinding
    private lateinit var trainAdapter: TrainResultAdapter
    private lateinit var transferTrainAdapter: TransferTrainAdapter
    private lateinit var seatInventoryRepository: SeatInventoryRepository
    private var departureStation: String = ""
    private var arrivalStation: String = ""
    private var departureDate: String = ""
    private var isDirectRoute = true // 默认选择直达
    private enum class SortMode { EARLIEST, SHORTEST, CHEAPEST }
    private var sortMode: SortMode = SortMode.EARLIEST
    private var currentTrains: List<Train> = emptyList()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchResultsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySurfaceSystemBars()
        
        // 获取传递的参数
        departureStation = intent.getStringExtra("departureStation") ?: ""
        arrivalStation = intent.getStringExtra("arrivalStation") ?: ""
        departureDate = intent.getStringExtra("departureDate") ?: ""
        
        seatInventoryRepository = SeatInventoryRepository(this)
        setupRecyclerView()
        setupUI()
        setupSortBar()
        loadSearchResults()
    }
    
    private fun setupUI() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "$departureStation → $arrivalStation"
        
        // 设置路线类型选择
        binding.btnDirect.setOnClickListener {
            selectRouteType(true)
        }
        
        binding.btnTransfer.setOnClickListener {
            selectRouteType(false)
        }
        
        // 设置初始按钮状态（默认选择直达）
        selectRouteType(true)
    }

    private fun setupSortBar() {
        // 默认选中“发时最早”
        highlightSort(SortMode.EARLIEST)
        // bring to front 防遮挡
        binding.sortBar.bringToFront()
        // 显式可点
        binding.sortBar.isClickable = true
        binding.btnSortEarliest.isClickable = true
        binding.btnSortShortest.isClickable = true
        binding.btnSortCheapest.isClickable = true
        binding.btnSortEarliest.isEnabled = true
        binding.btnSortShortest.isEnabled = true
        binding.btnSortCheapest.isEnabled = true

        // 绑定代码监听（即使XML onClick也可冗余保障）
        binding.btnSortEarliest.setOnClickListener {
            sortMode = SortMode.EARLIEST
            highlightSort(sortMode)
            applySort()
            Toast.makeText(this, "已按发时最早排序", Toast.LENGTH_SHORT).show()
        }
        binding.btnSortShortest.setOnClickListener {
            sortMode = SortMode.SHORTEST
            highlightSort(sortMode)
            applySort()
            Toast.makeText(this, "已按耗时最短排序", Toast.LENGTH_SHORT).show()
        }
        binding.btnSortCheapest.setOnClickListener {
            sortMode = SortMode.CHEAPEST
            highlightSort(sortMode)
            applySort()
            Toast.makeText(this, "已按价格最低排序", Toast.LENGTH_SHORT).show()
        }
    }

    private fun highlightSort(mode: SortMode) {
        val selected = when (mode) {
            SortMode.EARLIEST -> binding.btnSortEarliest
            SortMode.SHORTEST -> binding.btnSortShortest
            SortMode.CHEAPEST -> binding.btnSortCheapest
        }
        listOf(binding.btnSortEarliest, binding.btnSortShortest, binding.btnSortCheapest).forEach { button ->
            val color = if (button == selected) R.color.railway_blue else R.color.text_secondary
            button.setTextColor(getColor(color))
            button.iconTint = ColorStateList.valueOf(getColor(color))
        }
    }

    private fun applySurfaceSystemBars() {
        window.statusBarColor = getColor(R.color.surface)
        window.navigationBarColor = getColor(R.color.surface)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

    private fun applySort() {
        if (!isDirectRoute) return
        val sorted = when (sortMode) {
            SortMode.EARLIEST -> currentTrains.sortedBy { parseTimeToMinutes(it.departureTime) }
            SortMode.SHORTEST -> currentTrains.sortedBy { parseDurationToMinutes(it.duration) }
            SortMode.CHEAPEST -> currentTrains.sortedBy { it.price }
        }
        trainAdapter.updateTrains(sorted)
        binding.rvTrains.scrollToPosition(0)
    }
    
    private fun setupRecyclerView() {
        trainAdapter = TrainResultAdapter(seatInventoryRepository, { departureDate }) { train ->
            // 点击车次，跳转到车次详情页面显示沿途车站
            val intent = Intent(this, TrainDetailActivity::class.java)
            intent.putExtra("train", train)
            intent.putExtra("departureDate", departureDate)
            startActivity(intent)
        }
        
        transferTrainAdapter = TransferTrainAdapter(seatInventoryRepository, { departureDate }) { transferTrain ->
            // 点击中转车次，跳转到座位选择页面（传递两段车次信息）
            val intent = Intent(this, SeatSelectionActivity::class.java)
            intent.putExtra("isTransfer", true)
            intent.putExtra("firstLeg", transferTrain.firstLeg)
            intent.putExtra("secondLeg", transferTrain.secondLeg)
            intent.putExtra("transferStation", transferTrain.transferStation)
            intent.putExtra("transferTime", transferTrain.transferTime)
            intent.putExtra("totalPrice", transferTrain.totalPrice)
            intent.putExtra("departureDate", departureDate)
            startActivity(intent)
        }
        
        binding.rvTrains.apply {
            layoutManager = LinearLayoutManager(this@AdvancedSearchResultsActivity)
            adapter = trainAdapter // 默认使用直达适配器
        }
    }
    
    private fun loadSearchResults() {
        searchTickets()
    }
    
    private fun searchTickets() {
        try {
            android.util.Log.d("AdvancedSearchResults", "开始搜索车次: $departureStation -> $arrivalStation, 直达: $isDirectRoute")
            
            if (isDirectRoute) {
                // 直达车次：直接从出发站到到达站（按时间排序）
                android.util.Log.d("AdvancedSearchResults", "搜索直达车次")
                val trains = RailwayData.getTrainsSortedByTime(departureStation, arrivalStation)
                android.util.Log.d("AdvancedSearchResults", "找到直达车次: ${trains.size}个")
                
                // 切换到直达适配器
                binding.rvTrains.adapter = trainAdapter
                // Trains that already left are not sellable; the generated timetable does
                // not look at the clock, so a same-day search otherwise lists them all day.
                currentTrains = trains.filter {
                    DepartureTimingPolicy.isBookable(departureDate, it.departureTime)
                }
                applySort()
                
                if (trains.isEmpty()) {
                    showEmptyState()
                }
            } else {
                // 中转车次：需要中转的车次组合
                android.util.Log.d("AdvancedSearchResults", "搜索中转车次")
                val transferTrains = findTransferRoutes()
                android.util.Log.d("AdvancedSearchResults", "找到中转车次: ${transferTrains.size}个")
                
                // 切换到中转适配器
                binding.rvTrains.adapter = transferTrainAdapter
                // A transfer itinerary is sellable only while its first leg still is.
                transferTrainAdapter.updateTransferTrains(
                    transferTrains.filter {
                        DepartureTimingPolicy.isBookable(departureDate, it.firstLeg.departureTime)
                    }
                )
                
                if (transferTrains.isEmpty()) {
                    showEmptyState()
                }
            }
            
            android.util.Log.d("AdvancedSearchResults", "搜索完成")
        } catch (e: Exception) {
            android.util.Log.e("AdvancedSearchResults", "搜索车次时出错: ${e.message}")
            e.printStackTrace()
            Toast.makeText(this, "查询失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun showEmptyState() {
        val message = if (isDirectRoute) {
            "暂无直达车次\n建议选择中转方案"
        } else {
            "暂无中转车次\n建议选择直达方案"
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun selectRouteType(isDirect: Boolean) {
        try {
            android.util.Log.d("AdvancedSearchResults", "selectRouteType被调用: isDirect=$isDirect")
            isDirectRoute = isDirect
            
            if (isDirect) {
                // 使用Material Design的方式设置按钮状态
                binding.btnDirect.isSelected = true
                binding.btnTransfer.isSelected = false
                android.util.Log.d("AdvancedSearchResults", "设置直达按钮为选中状态")
                binding.sortBar.visibility = android.view.View.VISIBLE
            } else {
                binding.btnDirect.isSelected = false
                binding.btnTransfer.isSelected = true
                android.util.Log.d("AdvancedSearchResults", "设置中转按钮为选中状态")
                binding.sortBar.visibility = android.view.View.GONE
            }
            
            // 重新搜索车次
            android.util.Log.d("AdvancedSearchResults", "开始重新搜索车次")
            searchTickets()
        } catch (e: Exception) {
            android.util.Log.e("AdvancedSearchResults", "selectRouteType出错: ${e.message}")
            e.printStackTrace()
            Toast.makeText(this, "切换路线类型失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun findTransferRoutes(): List<TransferTrain> {
        val transferTrains = mutableListOf<TransferTrain>()
        
        try {
            // 获取所有真实线路，查找可能的中转站
            val allRoutes = com.railway.ticketsystem.data.RealRailwayRoutes.getAllRoutes()
            val possibleTransferStations = mutableSetOf<String>()
            
            // 从所有线路中收集可能的中转站
            allRoutes.forEach { route ->
                val stationNames = route.stations.map { it.name }
                if (stationNames.contains(departureStation) && stationNames.contains(arrivalStation)) {
                    // 如果线路同时包含出发站和到达站，查找中间站作为中转站
                    val departureIndex = stationNames.indexOf(departureStation)
                    val arrivalIndex = stationNames.indexOf(arrivalStation)
                    
                    if (departureIndex != -1 && arrivalIndex != -1) {
                        val startIndex = minOf(departureIndex, arrivalIndex)
                        val endIndex = maxOf(departureIndex, arrivalIndex)
                        
                        // 添加中间站作为可能的中转站
                        for (i in startIndex + 1 until endIndex) {
                            possibleTransferStations.add(stationNames[i])
                        }
                    }
                } else if (stationNames.contains(departureStation)) {
                    // 如果线路包含出发站，添加后续站作为可能的中转站
                    val departureIndex = stationNames.indexOf(departureStation)
                    for (i in departureIndex + 1 until stationNames.size) {
                        possibleTransferStations.add(stationNames[i])
                    }
                } else if (stationNames.contains(arrivalStation)) {
                    // 如果线路包含到达站，添加前面的站作为可能的中转站
                    val arrivalIndex = stationNames.indexOf(arrivalStation)
                    for (i in 0 until arrivalIndex) {
                        possibleTransferStations.add(stationNames[i])
                    }
                }
            }
            
            // 为每个可能的中转站查找车次组合
            for (transferStation in possibleTransferStations) {
                if (transferStation != departureStation && transferStation != arrivalStation) {
                    // 查找出发站到中转站的车次
                    val firstLegTrains = RailwayData.getTrainsSortedByTime(departureStation, transferStation)
                    
                    // 查找中转站到到达站的车次
                    val secondLegTrains = RailwayData.getTrainsSortedByTime(transferStation, arrivalStation)
                    
                    // 如果两段都有车次，则组合成中转路线
                    if (firstLegTrains.isNotEmpty() && secondLegTrains.isNotEmpty()) {
                        // 为每个中转站生成多个合理的车次组合
                        val combinations = findTransferCombinations(firstLegTrains, secondLegTrains)
                        transferTrains.addAll(combinations)
                    }
                }
            }
            
            // 优先展示两段二等座均可售且换乘更稳妥的方案，再比较总耗时和价格。
            return transferTrains.sortedWith(
                compareBy<TransferTrain> { transferAvailabilityPenalty(it) }
                    .thenBy { transferRiskRank(it) }
                    .thenBy { parseDurationToMinutes(it.totalDuration) }
                    .thenBy { it.totalPrice }
            ).take(20)
            
        } catch (e: Exception) {
            android.util.Log.e("TransferDebug", "查找中转车次时出错: ${e.message}")
            return emptyList()
        }
    }
    
    private fun findTransferCombinations(
        firstLegTrains: List<Train>, 
        secondLegTrains: List<Train>
    ): List<TransferTrain> {
        val combinations = mutableListOf<TransferTrain>()
        
        for (firstTrain in firstLegTrains) {
            for (secondTrain in secondLegTrains) {
                // 计算实际的中转时间
                val transferTime = calculateTransferTime(firstTrain, secondTrain)
                
                // 检查中转时间是否合理（20分钟以上，最多6小时）
                if (transferTime >= 20 && transferTime <= 360) {
                    val totalPrice = firstTrain.price + secondTrain.price
                    val totalDuration = calculateTotalDuration(firstTrain.duration, secondTrain.duration, transferTime)
                    
                    val transferTrain = TransferTrain(
                        firstLeg = firstTrain,
                        secondLeg = secondTrain,
                        transferStation = firstTrain.arrivalStation,
                        transferTime = transferTime,
                        totalPrice = totalPrice,
                        totalDuration = totalDuration
                    )
                    combinations.add(transferTrain)
                }
            }
        }
        
        return combinations
    }
    
    private fun calculateTransferTime(firstTrain: Train, secondTrain: Train): Int {
        // 解析第一段车次的到达时间
        val firstArrivalTime = parseTimeToMinutes(firstTrain.arrivalTime)
        // 解析第二段车次的出发时间
        val secondDepartureTime = parseTimeToMinutes(secondTrain.departureTime)
        
        // 计算中转时间（分钟）
        var transferTime = secondDepartureTime - firstArrivalTime
        
        // 如果第二段车次是第二天，需要加上24小时
        if (transferTime < 0) {
            transferTime += 24 * 60
        }
        
        return transferTime
    }
    private fun transferAvailabilityPenalty(transferTrain: TransferTrain): Int {
        val firstStock = seatInventoryRepository.getAvailability(transferTrain.firstLeg, departureDate, "二等座")
        val secondStock = seatInventoryRepository.getAvailability(transferTrain.secondLeg, departureDate, "二等座")
        return if (firstStock.requiresWaitlist || secondStock.requiresWaitlist) 1 else 0
    }

    private fun transferRiskRank(transferTrain: TransferTrain): Int = when (transferTrain.risk) {
        TransferRisk.STEADY -> 0
        TransferRisk.TIGHT -> 1
        TransferRisk.NOT_RECOMMENDED -> 2
    }

    
    private fun parseTimeToMinutes(time: String): Int {
        val parts = time.split(":")
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()
        return hour * 60 + minute
    }
    
    private fun calculateTotalDuration(firstDuration: String, secondDuration: String, transferMinutes: Int): String {
        val firstMinutes = parseDurationToMinutes(firstDuration)
        val secondMinutes = parseDurationToMinutes(secondDuration)
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

    // XML onClick 兜底
    fun onSortEarliest(view: android.view.View) {
        sortMode = SortMode.EARLIEST
        highlightSort(sortMode)
        applySort()
        Toast.makeText(this, "已按发时最早排序", Toast.LENGTH_SHORT).show()
    }
    fun onSortShortest(view: android.view.View) {
        sortMode = SortMode.SHORTEST
        highlightSort(sortMode)
        applySort()
        Toast.makeText(this, "已按耗时最短排序", Toast.LENGTH_SHORT).show()
    }
    fun onSortCheapest(view: android.view.View) {
        sortMode = SortMode.CHEAPEST
        highlightSort(sortMode)
        applySort()
        Toast.makeText(this, "已按价格最低排序", Toast.LENGTH_SHORT).show()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
