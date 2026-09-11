package com.railway.ticketsystem.fragment

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.railway.ticketsystem.R
import com.railway.ticketsystem.adapter.TicketAdapter
import com.railway.ticketsystem.data.MembershipRepository
import com.railway.ticketsystem.data.RailwayData
import com.railway.ticketsystem.data.SecurePreferences
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.databinding.FragmentTicketsBinding
import com.railway.ticketsystem.model.Train
import java.text.SimpleDateFormat
import java.util.*

class TicketsFragment : Fragment() {
    
    private var _binding: FragmentTicketsBinding? = null
    private val binding get() = _binding!!
    private lateinit var ticketAdapter: TicketAdapter
    private val recentRoutesPrefs by lazy {
        SecurePreferences.open(requireContext(), "secure_search_history", "search_history")
    }

    private data class RecentRoute(val departure: String, val arrival: String)

    private companion object {
        const val RECENT_ROUTES_KEY = "recent_routes"
        const val ROUTE_SEPARATOR = "\u001F"
        const val MAX_RECENT_ROUTES = 5
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTicketsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            android.util.Log.d("TicketsFragment", "onViewCreated开始")
            setupUI()
            android.util.Log.d("TicketsFragment", "setupUI完成")
            setupRecyclerView()
            android.util.Log.d("TicketsFragment", "setupRecyclerView完成")
            setupStationDropdowns()
            android.util.Log.d("TicketsFragment", "setupStationDropdowns完成")
            setupDatePicker()
            android.util.Log.d("TicketsFragment", "setupDatePicker完成")
            setupRecentRoutes()
            android.util.Log.d("TicketsFragment", "setupRecentRoutes完成")
            android.util.Log.d("TicketsFragment", "onViewCreated完成")
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("TicketsFragment", "onViewCreated错误: ${e.message}")
        }
    }
    
    private fun setupUI() {
        binding.btnSearchTickets.setOnClickListener {
            searchTickets()
        }
        binding.btnSwapStations.setOnClickListener {
            swapStations()
        }
        binding.btnRailMeal.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), com.railway.ticketsystem.activity.MealOrderActivity::class.java))
        }
        binding.btnStationBoard.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), com.railway.ticketsystem.activity.StationBoardActivity::class.java))
        }
        binding.btnMultiRidePass.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), com.railway.ticketsystem.activity.MultiRidePassActivity::class.java))
        }
        binding.btnElectronicInvoice.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), com.railway.ticketsystem.activity.ElectronicInvoiceActivity::class.java))
        }
        binding.btnHotelBooking.setOnClickListener {
            val selectedStation = binding.etDepartureStation.text?.toString()?.trim().orEmpty()
            startActivity(android.content.Intent(requireContext(), com.railway.ticketsystem.activity.HotelBookingActivity::class.java).apply {
                putExtra(com.railway.ticketsystem.activity.HotelBookingActivity.EXTRA_STATION, selectedStation)
            })
        }
        binding.btnStationService.setOnClickListener {
            val selectedStation = binding.etDepartureStation.text?.toString()?.trim().orEmpty()
            startActivity(android.content.Intent(requireContext(), com.railway.ticketsystem.activity.StationServiceActivity::class.java).apply {
                putExtra(com.railway.ticketsystem.activity.StationServiceActivity.EXTRA_STATION, selectedStation)
            })
        }
    }

    private fun swapStations() {
        val departureStation = binding.etDepartureStation.text.toString()
        binding.etDepartureStation.setText(binding.etArrivalStation.text.toString())
        binding.etArrivalStation.setText(departureStation)
    }
    
    private fun setupRecyclerView() {
        // 不再需要RecyclerView，因为查询结果会跳转到新页面
        // 这个方法保留但为空，避免编译错误
    }
    
    private fun setupStationDropdowns() {
        // 设置车站选择点击事件
        binding.etDepartureStation.setOnClickListener {
            openStationSelection("departure")
        }
        
        binding.etArrivalStation.setOnClickListener {
            openStationSelection("arrival")
        }
    }
    
    private fun openStationSelection(type: String) {
        val intent = android.content.Intent(requireContext(), com.railway.ticketsystem.activity.StationSelectionActivity::class.java)
        startActivityForResult(intent, if (type == "departure") 1001 else 1002)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (resultCode == android.app.Activity.RESULT_OK && data != null) {
            val selectedStation = data.getSerializableExtra("selectedStation") as? com.railway.ticketsystem.model.Station
            selectedStation?.let { station ->
                when (requestCode) {
                    1001 -> binding.etDepartureStation.setText(station.name)
                    1002 -> binding.etArrivalStation.setText(station.name)
                }
            }
        }
    }
    
    private fun setupDatePicker() {
        binding.etDepartureDate.setOnClickListener {
            showDatePicker()
        }
        
        // 设置默认日期为今天
        val today = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        binding.etDepartureDate.setText(dateFormat.format(today.time))
    }
    
    private fun setDepartureDate(daysFromToday: Int) {
        val date = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, daysFromToday) }
        binding.etDepartureDate.setText(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date.time))
    }

    private fun showDatePicker() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val selectedDepartureDate = runCatching { dateFormat.parse(binding.etDepartureDate.text.toString()) }.getOrNull()
        val calendar = Calendar.getInstance()
        if (selectedDepartureDate != null) calendar.time = selectedDepartureDate
        val today = Calendar.getInstance()
        val maxDate = Calendar.getInstance()
        maxDate.add(Calendar.DAY_OF_MONTH, 15) // 最多选择15天后
        
        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selectedDate = Calendar.getInstance()
                selectedDate.set(year, month, dayOfMonth)
                binding.etDepartureDate.setText(dateFormat.format(selectedDate.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        
        datePickerDialog.datePicker.minDate = today.timeInMillis
        datePickerDialog.datePicker.maxDate = maxDate.timeInMillis
        datePickerDialog.show()
    }
    
    private fun searchTickets() {
        val departureStation = binding.etDepartureStation.text.toString().trim()
        val arrivalStation = binding.etArrivalStation.text.toString().trim()
        val departureDate = binding.etDepartureDate.text.toString().trim()
        
        if (departureStation.isEmpty() || arrivalStation.isEmpty() || departureDate.isEmpty()) {
            Toast.makeText(requireContext(), "请完整填写查询信息", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (departureStation == arrivalStation) {
            Toast.makeText(requireContext(), "出发站和到达站不能相同", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 验证车站名称是否在系统中存在
        val validStations = RailwayData.stations.map { it.name }
        if (!validStations.contains(departureStation)) {
            Toast.makeText(requireContext(), "出发站不存在，请选择正确的车站", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!validStations.contains(arrivalStation)) {
            Toast.makeText(requireContext(), "到达站不存在，请选择正确的车站", Toast.LENGTH_SHORT).show()
            return
        }
        
        saveRecentRoute(departureStation, arrivalStation)
        UserRepository(requireContext()).getCurrentUser()?.let { user ->
            MembershipRepository(requireContext()).trackEvent(user.id, MembershipRepository.EVENT_SEARCH)
        }
        // 跳转到高级查询结果页面（支持路线推荐）
        val intent = android.content.Intent(requireContext(), com.railway.ticketsystem.activity.AdvancedSearchResultsActivity::class.java)
        intent.putExtra("departureStation", departureStation)
        intent.putExtra("arrivalStation", arrivalStation)
        intent.putExtra("departureDate", departureDate)
        startActivity(intent)
    }
    
    private fun setupRecentRoutes() {
        renderRecentRoutes()
    }

    private fun renderRecentRoutes() {
        val routes = runCatching { loadRecentRoutes() }.getOrElse {
            binding.llRecentRoutes.removeAllViews()
            binding.tvRecentRoutes.visibility = View.GONE
            binding.svRecentRoutes.visibility = View.GONE
            emptyList()
        }
        binding.llRecentRoutes.removeAllViews()
        val showRoutes = routes.isNotEmpty()
        binding.tvRecentRoutes.visibility = if (showRoutes) View.VISIBLE else View.GONE
        binding.svRecentRoutes.visibility = if (showRoutes) View.VISIBLE else View.GONE

        routes.forEach { route ->
            val button = TextView(requireContext()).apply {
                text = "${route.departure} → ${route.arrival}"
                textSize = 13f
                setTextColor(resources.getColor(R.color.railway_blue, null))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = android.view.Gravity.CENTER_VERTICAL
                val verticalPadding = (6 * resources.displayMetrics.density).toInt()
                setPadding(0, verticalPadding, 0, verticalPadding)
                contentDescription = "查询${route.departure}到${route.arrival}"
                setOnClickListener {
                    binding.etDepartureStation.setText(route.departure)
                    binding.etArrivalStation.setText(route.arrival)
                }
                val margin = (8 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = margin }
            }
            binding.llRecentRoutes.addView(button)
        }
    }

    private fun loadRecentRoutes(): List<RecentRoute> {
        return recentRoutesPrefs.getString(RECENT_ROUTES_KEY, "").orEmpty()
            .lineSequence()
            .mapNotNull { entry ->
                val separatorIndex = entry.indexOf(ROUTE_SEPARATOR)
                if (separatorIndex <= 0 || separatorIndex == entry.lastIndex) {
                    null
                } else {
                    RecentRoute(
                        entry.substring(0, separatorIndex),
                        entry.substring(separatorIndex + ROUTE_SEPARATOR.length)
                    )
                }
            }
            .distinct()
            .take(MAX_RECENT_ROUTES)
            .toList()
    }

    private fun saveRecentRoute(departure: String, arrival: String) {
        runCatching {
            val newRoute = RecentRoute(departure, arrival)
            val routes = (listOf(newRoute) + loadRecentRoutes())
                .distinct()
                .take(MAX_RECENT_ROUTES)
            val value = routes.joinToString("\n") { "${it.departure}$ROUTE_SEPARATOR${it.arrival}" }
            recentRoutesPrefs.edit().putString(RECENT_ROUTES_KEY, value).apply()
        }
    }

    interface OnTicketBookListener {
        fun onTicketBook(train: Train)
    }
    
    override fun onResume() {
        super.onResume()
        if (_binding != null) renderRecentRoutes()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
