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
import com.railway.ticketsystem.data.LatestRailwayNetwork
import com.railway.ticketsystem.data.RailwayData
import com.railway.ticketsystem.databinding.ActivitySearchResultsBinding
import com.railway.ticketsystem.data.SeatInventoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.railway.ticketsystem.model.Train
import com.railway.ticketsystem.model.TransferTrain
import com.railway.ticketsystem.model.TransferRisk

class AdvancedSearchResultsActivity : ImmersiveActivity() {
    
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
        // 分段选择条：选中哪半边由 ToggleGroup 自己管，这里只跟随业务状态。
        binding.routeTypeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) selectRouteType(checkedId == R.id.btnDirect)
        }
        // 走 ToggleGroup 而不是直接调 selectRouteType，这样分段条的选中态会一起更新
        binding.btnEmptySwitchTransfer.setOnClickListener { binding.routeTypeToggle.check(R.id.btnTransfer) }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "$departureStation → $arrivalStation"
        
        // 选中态由 ToggleGroup 的监听器统一处理，这里不再重复挂点击，否则一次点击会跑两遍查询
        
        // 设置初始按钮状态（默认选择直达）。调试构建可以用一个 intent extra 直接进中转：
        // 真机上 MIUI 关掉了 adb 的输入注入（input tap 报 INJECT_EVENTS 拒绝），
        // 没法用坐标点按钮，只能这样复现。release 构建里这个分支恒为 false。
        if (forceTransferSearch) {
            binding.routeTypeToggle.check(R.id.btnTransfer)
        } else {
            selectRouteType(true)
        }
    }

    /** 见 setupUI：只在 debug 构建且显式传参时为 true。 */
    private val forceTransferSearch: Boolean
        get() = intent.getBooleanExtra("forceTransferSearch", false) &&
            (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

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
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
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
            // 中转换乘先进入两段服务的详情页。每一程在那里复用直达
            // 车次的线路图、经停时刻、动态状态和担当车型，最后再选择席别。
            val intent = Intent(this, TransferDetailActivity::class.java)
            intent.putExtra(TransferDetailActivity.EXTRA_FIRST_LEG, transferTrain.firstLeg)
            intent.putExtra(TransferDetailActivity.EXTRA_SECOND_LEG, transferTrain.secondLeg)
            intent.putExtra(TransferDetailActivity.EXTRA_TRANSFER_STATION, transferTrain.transferStation)
            intent.putExtra(TransferDetailActivity.EXTRA_TRANSFER_TIME, transferTrain.transferTime)
            intent.putExtra(TransferDetailActivity.EXTRA_TOTAL_PRICE, transferTrain.totalPrice)
            intent.putExtra(TransferDetailActivity.EXTRA_TOTAL_DURATION, transferTrain.totalDuration)
            intent.putExtra(TransferDetailActivity.EXTRA_DEPARTURE_DATE, departureDate)
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
    
    private var searchJob: Job? = null

    /**
     * 上一次算出来的中转方案。
     *
     * 切回直达时不清掉：中转那一趟要跑生成和排序，来回切一次就白算一次。
     * 本页的出发站/到达站/日期在实例生命周期内不变，所以这份缓存不会失效。
     */
    private var cachedTransferTrains: List<TransferTrain>? = null

    /** 换乘搜索要对每个候选站生成两段车次，不设上限会指数级放大耗时。 */
    private val MAX_TRANSFER_CANDIDATES = 12

    /**
     * 换乘方案要在铁路图上做 BFS，实测单次可达 5 秒以上。这段原本跑在主线程，
     * 超时直接触发 ANR（输入事件 5 秒未响应）导致闪退 —— 所以只有换乘分支被挪到
     * 后台线程；直达搜索很快，且会现场生成车次，线程安全性未经确认，保持原样。
     */
    private fun searchTickets() {
        searchJob?.cancel()
        val direct = isDirectRoute
        android.util.Log.w("TransferDebug", "searchTickets: 启动协程 direct=$direct")
        searchJob = CoroutineScope(Dispatchers.Main).launch {
        try {
            android.util.Log.d("AdvancedSearchResults", "开始搜索车次: $departureStation -> $arrivalStation, 直达: $direct")
            
            if (direct) {
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
                
                // 判空要看过滤后的 currentTrains，不是原始的 trains：当天车次全部发车之后
                // trains 仍然非空，只看它就会把空列表当成"有结果"，屏幕上什么都不显示。
                if (currentTrains.isEmpty()) {
                    showEmptyState(isDirect = true)
                } else {
                    hideEmptyState()
                }
            } else {
                // 中转车次：需要中转的车次组合
                val cached = cachedTransferTrains
                if (cached != null) {
                    // 切回中转时直接用上次的结果，不再跑一遍生成与排序。
                    renderTransferTrains(cached)
                } else {
                    android.util.Log.d("AdvancedSearchResults", "搜索中转车次")
                    // 先把直达的列表收起来。中转要算一会儿，留着上一模式的车次会让人以为没切过去。
                    showLoadingState()
                    val transferTrains = withContext(Dispatchers.Default) { findTransferRoutes() }
                    // 计算期间用户可能已经退出或切走，这时不要再碰 UI。
                    if (isFinishing || isDestroyed) return@launch
                    android.util.Log.d("AdvancedSearchResults", "找到中转车次: ${transferTrains.size}个")
                    cachedTransferTrains = transferTrains
                    renderTransferTrains(transferTrains)
                }
            }
            
            android.util.Log.d("AdvancedSearchResults", "搜索完成")
        } catch (e: CancellationException) {
            android.util.Log.w("TransferDebug", "searchTickets: 协程被取消 direct=$direct")
            throw e   // 取消不是错误，不能当成查询失败弹提示
        } catch (e: Exception) {
            if (isFinishing || isDestroyed) return@launch
            android.util.Log.e("AdvancedSearchResults", "搜索车次时出错: ${e.message}")
            e.printStackTrace()
            // 中转失败时屏幕上还停着"正在查询中转…"，得换成正常的空状态，否则会一直挂着。
            showEmptyState(isDirect = direct)
            Toast.makeText(this@AdvancedSearchResultsActivity, "查询失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
        }
    }
    
    /**
     * 空状态。模式由调用方传入而不是读 isDirectRoute 字段：搜索在协程里跑，用户可能
     * 已经切走，那时字段和这次查询的模式对不上，文案就串了。
     */
    private fun showEmptyState(isDirect: Boolean) {
        // 常驻空状态而不是 Toast：这段文案要读完，其中"改用中转换乘"还要引导操作。
        binding.tvEmptyMessage.text = if (isDirect) {
            "很抱歉，按您的查询条件，当前未找到从${departureStation}到${arrivalStation}的列车。" +
                "您可使用中转换乘功能，查询途中换乘一次的部分列车余票情况。"
        } else {
            "很抱歉，按您的查询条件，当前未找到从${departureStation}到${arrivalStation}的中转列车。"
        }
        // 只有直达查不到时，换成中转才有意义。
        binding.btnEmptySwitchTransfer.visibility = if (isDirect) View.VISIBLE else View.GONE
        binding.rvTrains.visibility = View.GONE
        binding.llEmptyState.visibility = View.VISIBLE
    }

    /**
     * 把中转方案显示出来。首次算出和切回来复用缓存走同一条路径，免得两处渲染逻辑跑偏。
     */
    private fun renderTransferTrains(transferTrains: List<TransferTrain>) {
        binding.rvTrains.adapter = transferTrainAdapter
        // A transfer itinerary is sellable only while its first leg still is.
        transferTrainAdapter.updateTransferTrains(
            transferTrains.filter {
                DepartureTimingPolicy.isBookable(departureDate, it.firstLeg.departureTime)
            }
        )
        if (transferTrains.isEmpty()) showEmptyState(isDirect = false) else hideEmptyState()
    }

    /**
     * 计算期间顶掉上一模式的列表。中转要跑生成和排序，直达的车次留在屏幕上会让人
     * 以为没切过去。
     */
    private fun showLoadingState() {
        binding.tvEmptyMessage.text = "正在查询中转换乘方案…"
        binding.btnEmptySwitchTransfer.visibility = View.GONE
        binding.rvTrains.visibility = View.GONE
        binding.llEmptyState.visibility = View.VISIBLE
    }

    private fun hideEmptyState() {
        binding.llEmptyState.visibility = View.GONE
        binding.rvTrains.visibility = View.VISIBLE
    }
    
    private fun selectRouteType(isDirect: Boolean) {
        try {
            android.util.Log.d("AdvancedSearchResults", "selectRouteType被调用: isDirect=$isDirect")
            isDirectRoute = isDirect
            
            if (isDirect) {
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

        // 临时诊断：中转搜索慢，但每轮改都是靠推测定位，先把四段实测出来再动手。
        var phaseStart = System.nanoTime()
        fun phase(label: String, extra: String = "") {
            val now = System.nanoTime()
            android.util.Log.w("TransferDebug", "阶段 $label: ${(now - phaseStart) / 1_000_000}ms $extra")
            phaseStart = now
        }

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
            
            // 候选中转站可能上百个，而每个都要跑两次 getTrainsSortedByTime —— 那会现场
            // 生成 40~60 趟车并各跑两遍图 BFS。全量遍历实测 5 秒以上，直接触发 ANR。
            // 这里按"是否为大枢纽"排序后限量：换乘本来就该发生在枢纽，小站作为中转点
            // 既不合理也没必要算。
            val rankedTransferStations = possibleTransferStations
                .filter { it != departureStation && it != arrivalStation }
                .sortedByDescending { LatestRailwayNetwork.isMajorHubStation(it) }
                .take(MAX_TRANSFER_CANDIDATES)
            phase("选候选站", "共 ${possibleTransferStations.size} 个，取 ${rankedTransferStations.size} 个")

            // 为每个可能的中转站查找车次组合
            for (transferStation in rankedTransferStations) {
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
            
            phase("生成两段车次并组合", "组合 ${transferTrains.size} 条")

            // 优先展示两段二等座均可售且换乘更稳妥的方案，再比较总耗时和价格。
            //
            // 排序键必须只算一次。getAvailability 每调用一次都要把整份库存反序列化，遇到
            // 新记录还要整份写回并同步落盘；放进比较器里就是 n·log n 次——汉口→庐山约 4 万条
            // 组合，实测上百万次查询，跑三分钟都出不来，界面看着就像卡死。
            // 罚分只取决于单趟车次，按车次号缓存；耗时也先解析一次，别在比较器里反复拆字符串。
            val penaltyByTrain = HashMap<String, Int>()
            fun availabilityPenalty(train: Train): Int = penaltyByTrain.getOrPut(train.number) {
                if (requiresWaitlist(train)) 1 else 0
            }
            return transferTrains
                .map { transfer ->
                    RankedTransfer(
                        transfer = transfer,
                        availabilityPenalty = availabilityPenalty(transfer.firstLeg) or
                            availabilityPenalty(transfer.secondLeg),
                        riskRank = transferRiskRank(transfer),
                        minutes = parseDurationToMinutes(transfer.totalDuration)
                    )
                }
                .sorted()
                .take(20)
                .map { it.transfer }
                .also { phase("算排序键并排序", "查库存 ${penaltyByTrain.size} 个车次") }
            
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
    /**
     * 中转方案的排序键，四个字段的先后就是原来的比较顺序。
     * 单独拎出来是为了每个方案只算一次：放在比较器里会被调用 n·log n 次，
     * 而其中查库存那一步的开销极大（见 findTransferRoutes 里的说明）。
     */
    private data class RankedTransfer(
        val transfer: TransferTrain,
        val availabilityPenalty: Int,
        val riskRank: Int,
        val minutes: Int
    ) : Comparable<RankedTransfer> {
        override fun compareTo(other: RankedTransfer): Int = compareValuesBy(
            this, other,
            { it.availabilityPenalty }, { it.riskRank }, { it.minutes }, { it.transfer.totalPrice }
        )
    }

    /** 单趟车次二等座是否需要候补。排序时按车次号缓存后调用，见 findTransferRoutes。 */
    private fun requiresWaitlist(train: Train): Boolean =
        seatInventoryRepository.getAvailability(train, departureDate, "二等座").requiresWaitlist

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
