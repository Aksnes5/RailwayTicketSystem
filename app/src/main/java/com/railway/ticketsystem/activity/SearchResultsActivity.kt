package com.railway.ticketsystem.activity

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.railway.ticketsystem.R
import com.railway.ticketsystem.adapter.TrainResultAdapter
import com.railway.ticketsystem.data.SeatInventoryRepository
import com.railway.ticketsystem.databinding.ActivitySearchResultsBinding
import com.railway.ticketsystem.model.Train
import com.railway.ticketsystem.model.TransferTrain
import com.railway.ticketsystem.viewmodel.SearchResultsViewModel
import com.railway.ticketsystem.viewmodel.SearchSortMode
import com.railway.ticketsystem.viewmodel.SearchUiState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SearchResultsActivity : ImmersiveActivity() {
    
    private lateinit var binding: ActivitySearchResultsBinding
    private lateinit var trainAdapter: TrainResultAdapter
    private lateinit var seatInventoryRepository: SeatInventoryRepository
    private val viewModel: SearchResultsViewModel by viewModels()

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
        setupRecyclerView()
        setupSortBar()
        binding.sortBar.bringToFront()

        observeViewModel()
        searchTickets()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is SearchUiState.Loading -> {
                            binding.layoutLoadingSkeleton.visibility = View.VISIBLE
                            binding.rvTrains.visibility = View.GONE
                            binding.llEmptyState.visibility = View.GONE
                        }
                        is SearchUiState.Empty -> {
                            binding.layoutLoadingSkeleton.visibility = View.GONE
                            binding.rvTrains.visibility = View.GONE
                            binding.llEmptyState.visibility = View.VISIBLE
                            binding.tvEmptyMessage.text = state.message
                            binding.btnEmptySwitchTransfer.visibility = if (state.isDirect) View.VISIBLE else View.GONE
                        }
                        is SearchUiState.DirectSuccess -> {
                            binding.layoutLoadingSkeleton.visibility = View.GONE
                            binding.llEmptyState.visibility = View.GONE
                            binding.rvTrains.visibility = View.VISIBLE
                            currentTrains = state.displayTrains
                            trainAdapter.updateTrains(state.displayTrains)
                            highlightSort(when (state.sortMode) {
                                SearchSortMode.EARLIEST -> SortMode.EARLIEST
                                SearchSortMode.SHORTEST -> SortMode.SHORTEST
                                SearchSortMode.CHEAPEST -> SortMode.CHEAPEST
                            })
                        }
                        is SearchUiState.TransferSuccess -> {
                            binding.layoutLoadingSkeleton.visibility = View.GONE
                            binding.llEmptyState.visibility = View.GONE
                            binding.rvTrains.visibility = View.VISIBLE
                            val transferTrains = state.transferTrains.map { transferTrain ->
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
                            currentTrains = transferTrains
                            trainAdapter.updateTrains(transferTrains)
                        }
                        is SearchUiState.Error -> {
                            binding.layoutLoadingSkeleton.visibility = View.GONE
                            Toast.makeText(this@SearchResultsActivity, state.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
    
    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }
        val routeTitle = if (departureStation.isNotEmpty() && arrivalStation.isNotEmpty()) {
            "$departureStation ➔ $arrivalStation"
        } else {
            "车票查询"
        }
        binding.tvHeaderRoute.text = routeTitle
        binding.tvHeaderDate.text = formatDisplayDate(selectedDate)

        // 分段选择条：选中哪半边由 ToggleGroup 自己管，这里只跟随业务状态。
        binding.hsvQuickFilters.visibility = View.GONE
        binding.routeTypeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) selectRouteType(checkedId == R.id.btnDirect)
        }
        // 走 ToggleGroup 而不是直接调 selectRouteType，这样分段条的选中态会一起更新
        binding.btnEmptySwitchTransfer.setOnClickListener { binding.routeTypeToggle.check(R.id.btnTransfer) }
        
        // 设置初始按钮状态（默认选择直达）
        selectRouteType(true)
    }

    private fun formatDisplayDate(dateStr: String): String {
        return try {
            val date = dateFormat.parse(dateStr) ?: return dateStr
            val cal = Calendar.getInstance()
            val todayStr = dateFormat.format(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, 1)
            val tomorrowStr = dateFormat.format(cal.time)
            val dayDesc = when (dateStr) {
                todayStr -> "今天"
                tomorrowStr -> "明天"
                else -> weekDateFormat.format(date)
            }
            "${displayDateFormat.format(date)} · $dayDesc"
        } catch (_: Exception) {
            dateStr
        }
    }
    
    private fun setupSortBar() {
        binding.sortBar.isClickable = true
        binding.btnSortEarliest.isClickable = true
        binding.btnSortShortest.isClickable = true
        binding.btnSortCheapest.isClickable = true
        binding.btnSortEarliest.isEnabled = true
        binding.btnSortShortest.isEnabled = true
        binding.btnSortCheapest.isEnabled = true
        highlightSort(SortMode.EARLIEST)
        
        binding.btnSortEarliest.setOnClickListener {
            viewModel.setSortMode(SearchSortMode.EARLIEST)
            Toast.makeText(this, "已按发时最早排序", Toast.LENGTH_SHORT).show()
        }
        binding.btnSortShortest.setOnClickListener {
            viewModel.setSortMode(SearchSortMode.SHORTEST)
            Toast.makeText(this, "已按耗时最短排序", Toast.LENGTH_SHORT).show()
        }
        binding.btnSortCheapest.setOnClickListener {
            viewModel.setSortMode(SearchSortMode.CHEAPEST)
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

    // XML onClick 回调（兜底）
    fun onSortEarliest(view: android.view.View) {
        viewModel.setSortMode(SearchSortMode.EARLIEST)
        Toast.makeText(this, "已按发时最早排序", Toast.LENGTH_SHORT).show()
    }

    fun onSortShortest(view: android.view.View) {
        viewModel.setSortMode(SearchSortMode.SHORTEST)
        Toast.makeText(this, "已按耗时最短排序", Toast.LENGTH_SHORT).show()
    }

    fun onSortCheapest(view: android.view.View) {
        viewModel.setSortMode(SearchSortMode.CHEAPEST)
        Toast.makeText(this, "已按价格最低排序", Toast.LENGTH_SHORT).show()
    }
    
    private fun setupRecyclerView() {
        trainAdapter = TrainResultAdapter(seatInventoryRepository, { selectedDate }) { train ->
            val intent = Intent(this, TrainDetailActivity::class.java)
            intent.putExtra("train", train)
            intent.putExtra("departureDate", selectedDate)
            startActivity(intent)
        }
        
        binding.rvTrains.apply {
            layoutManager = LinearLayoutManager(this@SearchResultsActivity)
            adapter = trainAdapter
        }
    }
    
    private fun searchTickets() {
        viewModel.searchTickets(departureStation, arrivalStation, selectedDate, isDirectRoute)
    }

    private fun getCurrentDate(): String {
        return dateFormat.format(Date())
    }
    
    private fun selectRouteType(isDirect: Boolean) {
        isDirectRoute = isDirect
        if (isDirect) {
            binding.btnDirect.isSelected = true
            binding.btnTransfer.isSelected = false
            binding.sortBar.visibility = View.VISIBLE
        } else {
            binding.btnDirect.isSelected = false
            binding.btnTransfer.isSelected = true
            binding.sortBar.visibility = View.GONE
        }
        searchTickets()
    }
}


