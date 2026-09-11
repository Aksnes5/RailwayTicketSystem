package com.railway.ticketsystem.activity

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.View
import android.graphics.Color
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.railway.ticketsystem.R
import com.railway.ticketsystem.adapter.TrainResultAdapter
import com.railway.ticketsystem.data.DepartureTimingPolicy
import com.railway.ticketsystem.data.RailwayData
import com.railway.ticketsystem.data.SeatInventoryRepository
import com.railway.ticketsystem.databinding.ActivitySearchResultsBinding
import com.railway.ticketsystem.model.Train
import com.railway.ticketsystem.model.TransferTrain
import java.text.SimpleDateFormat
import java.util.*

class SearchResultsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySearchResultsBinding
    private lateinit var trainAdapter: TrainResultAdapter
    private lateinit var seatInventoryRepository: SeatInventoryRepository
    private var departureStation = ""
    private var arrivalStation = ""
    private var selectedDate = ""
    private var isDirectRoute = true // 默认选择直达
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("MM.dd", Locale.getDefault())
    private val weekDateFormat = SimpleDateFormat("E", Locale.getDefault())

    private enum class SortMode { EARLIEST, SHORTEST, CHEAPEST }
    private var sortMode: SortMode = SortMode.EARLIEST
    private var currentTrains: List<Train> = emptyList()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchResultsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySurfaceSystemBars()
        
        // 获取传递的查询参数
        departureStation = intent.getStringExtra("departureStation") ?: ""
        arrivalStation = intent.getStringExtra("arrivalStation") ?: ""
        selectedDate = intent.getStringExtra("departureDate") ?: getCurrentDate()
        seatInventoryRepository = SeatInventoryRepository(this)
        
        setupUI()
        // 日期选择器已删除
        setupRecyclerView()
        setupSortBar()
        // 确保排序栏在最上层并可点击
        binding.sortBar.bringToFront()

        // 兜底：通过 findViewById 再次绑定点击，避免绑定异常导致不触发
        try {
            val btnEarliest = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSortEarliest)
            val btnShortest = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSortShortest)
            val btnCheapest = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSortCheapest)
            val bar = findViewById<android.view.View>(R.id.sortBar)
            bar?.setOnClickListener {
                android.util.Log.d("SearchResults", "点击 SortBar 容器")
            }
            btnEarliest?.setOnClickListener {
                android.util.Log.d("SearchResults", "[fallback] 点击 发时最早")
                Toast.makeText(this, "已按发时最早排序", Toast.LENGTH_SHORT).show()
                sortMode = SortMode.EARLIEST
                highlightSort(sortMode)
                applySort()
            }
            btnShortest?.setOnClickListener {
                android.util.Log.d("SearchResults", "[fallback] 点击 耗时最短")
                Toast.makeText(this, "已按耗时最短排序", Toast.LENGTH_SHORT).show()
                sortMode = SortMode.SHORTEST
                highlightSort(sortMode)
                applySort()
            }
            btnCheapest?.setOnClickListener {
                android.util.Log.d("SearchResults", "[fallback] 点击 价格最低")
                Toast.makeText(this, "已按价格最低排序", Toast.LENGTH_SHORT).show()
                sortMode = SortMode.CHEAPEST
                highlightSort(sortMode)
                applySort()
            }
        } catch (_: Exception) { }
        searchTickets()
    }
    
    private fun setupUI() {
        // 顶部导航栏已删除
        
        // 设置路线类型选择
        binding.btnDirect.setOnClickListener {
            android.util.Log.d("SearchResults", "直达按钮被点击")
            Toast.makeText(this, "直达按钮被点击", Toast.LENGTH_SHORT).show()
            selectRouteType(true)
        }
        
        binding.btnTransfer.setOnClickListener {
            android.util.Log.d("SearchResults", "中转按钮被点击")
            Toast.makeText(this, "中转按钮被点击", Toast.LENGTH_SHORT).show()
            selectRouteType(false)
        }
        
        // 设置初始按钮状态（默认选择直达）
        selectRouteType(true)
    }
    
    private fun setupSortBar() {
        android.util.Log.d("SearchResults", "setupSortBar 开始")
        // 确保可点击
        binding.sortBar.isClickable = true
        binding.btnSortEarliest.isClickable = true
        binding.btnSortShortest.isClickable = true
        binding.btnSortCheapest.isClickable = true
        binding.btnSortEarliest.isEnabled = true
        binding.btnSortShortest.isEnabled = true
        binding.btnSortCheapest.isEnabled = true
        android.util.Log.d("SearchResults", "sortBar clickable=${binding.sortBar.isClickable}")
        // 默认选中“发时最早”
        highlightSort(SortMode.EARLIEST)
        
        binding.btnSortEarliest.setOnClickListener {
            android.util.Log.d("SearchResults", "点击 发时最早")
            Toast.makeText(this, "已按发时最早排序", Toast.LENGTH_SHORT).show()
            sortMode = SortMode.EARLIEST
            highlightSort(sortMode)
            applySort()
        }
        binding.btnSortShortest.setOnClickListener {
            android.util.Log.d("SearchResults", "点击 耗时最短")
            Toast.makeText(this, "已按耗时最短排序", Toast.LENGTH_SHORT).show()
            sortMode = SortMode.SHORTEST
            highlightSort(sortMode)
            applySort()
        }
        binding.btnSortCheapest.setOnClickListener {
            android.util.Log.d("SearchResults", "点击 价格最低")
            Toast.makeText(this, "已按价格最低排序", Toast.LENGTH_SHORT).show()
            sortMode = SortMode.CHEAPEST
            highlightSort(sortMode)
            applySort()
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
        if (!isDirectRoute) return // 仅直达使用底部排序栏
        val sorted = when (sortMode) {
            SortMode.EARLIEST -> currentTrains.sortedBy { parseTimeToMinutes(it.departureTime) }
            SortMode.SHORTEST -> currentTrains.sortedBy { parseDurationToMinutes(it.duration) }
            SortMode.CHEAPEST -> currentTrains.sortedBy { it.price } // 价格最低优先（从小到大）
        }
        trainAdapter.updateTrains(sorted)
        // 排序后回到顶部，方便用户观察变化
        binding.rvTrains.scrollToPosition(0)
    }

    // XML onClick 回调（兜底）
    fun onSortEarliest(view: android.view.View) {
        android.util.Log.d("SearchResults", "[xml] 点击 发时最早")
        Toast.makeText(this, "已按发时最早排序", Toast.LENGTH_SHORT).show()
        sortMode = SortMode.EARLIEST
        highlightSort(sortMode)
        applySort()
    }

    fun onSortShortest(view: android.view.View) {
        android.util.Log.d("SearchResults", "[xml] 点击 耗时最短")
        Toast.makeText(this, "已按耗时最短排序", Toast.LENGTH_SHORT).show()
        sortMode = SortMode.SHORTEST
        highlightSort(sortMode)
        applySort()
    }

    fun onSortCheapest(view: android.view.View) {
        android.util.Log.d("SearchResults", "[xml] 点击 价格最低")
        Toast.makeText(this, "已按价格最低排序", Toast.LENGTH_SHORT).show()
        sortMode = SortMode.CHEAPEST
        highlightSort(sortMode)
        applySort()
    }
    
    // 日期选择器相关方法已删除
    
    private fun formatSelectedDate(dateStr: String): String {
        try {
            val date = dateFormat.parse(dateStr)
            val calendar = Calendar.getInstance()
            calendar.time = date ?: Date()
            
            val weekDay = when (calendar.get(Calendar.DAY_OF_WEEK)) {
                Calendar.SUNDAY -> "周日"
                Calendar.MONDAY -> "周一"
                Calendar.TUESDAY -> "周二"
                Calendar.WEDNESDAY -> "周三"
                Calendar.THURSDAY -> "周四"
                Calendar.FRIDAY -> "周五"
                Calendar.SATURDAY -> "周六"
                else -> ""
            }
            
            return "$weekDay ${displayDateFormat.format(date)}"
        } catch (e: Exception) {
            return "今天"
        }
    }
    
    private fun setupRecyclerView() {
        trainAdapter = TrainResultAdapter(seatInventoryRepository, { selectedDate }) { train ->
            // 跳转到车次详情页面显示沿途车站
            android.util.Log.d("SearchResultsActivity", "点击车次: ${train.number}")
            android.util.Log.d("SearchResultsActivity", "起点: ${train.departureStation}, 终点: ${train.arrivalStation}")
            
            val intent = Intent(this, TrainDetailActivity::class.java)
            intent.putExtra("train", train)
            intent.putExtra("departureDate", selectedDate)
            startActivity(intent)
            
            android.util.Log.d("SearchResultsActivity", "已启动TrainDetailActivity")
        }
        
        binding.rvTrains.apply {
            layoutManager = LinearLayoutManager(this@SearchResultsActivity)
            adapter = trainAdapter
        }
    }
    
    private fun searchTickets() {
        try {
            val trains = if (isDirectRoute) {
                // 直达车次：直接从出发站到到达站（按时间排序）
                RailwayData.getTrainsSortedByTime(departureStation, arrivalStation)
            } else {
                // 中转车次：需要中转的车次组合
                findTransferRoutes()
            }

            // Trains that already left are not sellable; the generated timetable does not
            // look at the clock, so a same-day search otherwise lists them all day.
            currentTrains = trains.filter {
                DepartureTimingPolicy.isBookable(selectedDate, it.departureTime)
            }
            if (isDirectRoute) {
                applySort()
            } else {
                trainAdapter.updateTrains(trains)
            }
            
            // 如果没有车次，显示空状态提示
            if (trains.isEmpty()) {
                showEmptyState()
            }
        } catch (e: Exception) {
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
    
    private fun findTransferRoutes(): List<Train> {
        val transferTrains = mutableListOf<TransferTrain>()
        
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
                    val combinations = findTransferCombinations(firstLegTrains, secondLegTrains, transferStation)
                    transferTrains.addAll(combinations)
                }
            }
        }
        
        // 按总时间排序，返回前20个最佳方案，并转换为Train对象
        return transferTrains.sortedBy { parseDurationToMinutes(it.totalDuration) }.take(20).map { transferTrain ->
            // 将TransferTrain转换为Train对象用于显示
            Train(
                number = transferTrain.displayNumber,
                departureStation = transferTrain.departureStation,
                arrivalStation = transferTrain.arrivalStation,
                departureTime = transferTrain.departureTime,
                arrivalTime = transferTrain.arrivalTime,
                duration = transferTrain.totalDuration,
                price = transferTrain.totalPrice,
                availableSeats = transferTrain.availableSeats
            )
        }
    }
    
    private fun findTransferCombinations(
        firstLegTrains: List<Train>, 
        secondLegTrains: List<Train>,
        transferStation: String
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
                        transferStation = transferStation,
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
    
    
    private fun getCurrentDate(): String {
        return dateFormat.format(Date())
    }
    
    private fun selectRouteType(isDirect: Boolean) {
        android.util.Log.d("SearchResults", "selectRouteType被调用: isDirect=$isDirect")
        isDirectRoute = isDirect
        
        if (isDirect) {
            // 使用Material Design的方式设置按钮状态
            binding.btnDirect.isSelected = true
            binding.btnTransfer.isSelected = false
            android.util.Log.d("SearchResults", "设置直达按钮为选中状态")
            binding.sortBar.visibility = android.view.View.VISIBLE
        } else {
            binding.btnDirect.isSelected = false
            binding.btnTransfer.isSelected = true
            android.util.Log.d("SearchResults", "设置中转按钮为选中状态")
            binding.sortBar.visibility = android.view.View.GONE
        }
        
        // 重新搜索车次
        android.util.Log.d("SearchResults", "开始重新搜索车次")
        searchTickets()
    }
}


