package com.railway.ticketsystem.fragment

import android.app.Dialog
import android.animation.ObjectAnimator
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.ArrayAdapter
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.core.content.ContextCompat
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
    private var liquidSheenAnimator: ObjectAnimator? = null
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
            applyQueryGlassBlur()
            startLiquidMotion()
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

    /** A slow, low-alpha light sweep makes the hero feel like a live glass surface. */
    private fun applyQueryGlassBlur() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            binding.ivQueryGlassBackdrop.setRenderEffect(
                RenderEffect.createBlurEffect(28f, 28f, Shader.TileMode.CLAMP)
            )
        }
    }

    private fun startLiquidMotion() {
        liquidSheenAnimator?.cancel()
        val width = resources.displayMetrics.widthPixels.toFloat()
        liquidSheenAnimator = ObjectAnimator.ofFloat(
            binding.vLiquidSheen,
            View.TRANSLATION_X,
            -dp(180).toFloat(),
            width + dp(180).toFloat()
        ).apply {
            duration = 6_800L
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
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
        binding.rowDepartureDate.setOnClickListener {
            showDatePicker()
        }
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
        val context = requireContext()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = Calendar.getInstance().startOfDay()
        val lastAvailableDay = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 15) }
        var selectedDate = runCatching {
            Calendar.getInstance().apply {
                time = dateFormat.parse(binding.etDepartureDate.text.toString()) ?: today.time
                startOfDayInPlace()
            }
        }.getOrElse { today.clone() as Calendar }
        if (selectedDate.before(today) || selectedDate.after(lastAvailableDay)) {
            selectedDate = today.clone() as Calendar
        }
        var displayedMonth = (selectedDate.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            startOfDayInPlace()
        }

        val dialog = Dialog(context)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        val sheet = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(20))
            background = roundedBackground(Color.WHITE, 28)
        }
        sheet.addView(TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(14)
            }
            background = roundedBackground(Color.rgb(214, 224, 235), 3)
        })
        sheet.addView(TextView(context).apply {
            text = "选择出发日期"
            textSize = 20f
            setTextColor(Color.rgb(27, 39, 56))
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        sheet.addView(TextView(context).apply {
            text = "可选未来 15 天内的车票"
            textSize = 13f
            setTextColor(Color.rgb(124, 142, 160))
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, dp(16))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val selectionSummary = TextView(context).apply {
            textSize = 15f
            setTextColor(ContextCompat.getColor(context, R.color.railway_blue_deep))
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedBackground(Color.rgb(238, 247, 255), 16)
        }
        sheet.addView(selectionSummary, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(14)
        })

        val monthBar = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        val previousMonth = calendarNavButton("‹", "查看上月")
        val monthLabel = TextView(context).apply {
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(27, 39, 56))
            setTypeface(typeface, Typeface.BOLD)
        }
        val nextMonth = calendarNavButton("›", "查看下月")
        monthBar.addView(previousMonth, LinearLayout.LayoutParams(dp(42), dp(42)))
        monthBar.addView(monthLabel, LinearLayout.LayoutParams(0, dp(42), 1f))
        monthBar.addView(nextMonth, LinearLayout.LayoutParams(dp(42), dp(42)))
        sheet.addView(monthBar)

        val weekdayGrid = GridLayout(context).apply {
            columnCount = 7
            rowCount = 1
            useDefaultMargins = false
        }
        listOf("日", "一", "二", "三", "四", "五", "六").forEachIndexed { index, day ->
            weekdayGrid.addView(TextView(context).apply {
                text = day
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(if (index == 0 || index == 6) Color.rgb(138, 155, 173) else Color.rgb(89, 108, 128))
            }, calendarGridParams(0, index, dp(28)))
        }
        sheet.addView(weekdayGrid)

        val dayGrid = GridLayout(context).apply {
            columnCount = 7
            rowCount = 6
            useDefaultMargins = false
            alignmentMode = GridLayout.ALIGN_BOUNDS
        }
        sheet.addView(dayGrid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(276)))

        val actions = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(14), 0, 0)
        }
        val todayButton = calendarActionButton("今天", false)
        val confirmButton = calendarActionButton("确定", true)
        actions.addView(todayButton, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(10) })
        actions.addView(confirmButton, LinearLayout.LayoutParams(0, dp(46), 1f))
        sheet.addView(actions)

        fun refreshSelectionSummary() {
            val weekday = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")[selectedDate.get(Calendar.DAY_OF_WEEK) - 1]
            val prefix = when {
                selectedDate.isSameDay(today) -> "今天"
                selectedDate.isSameDay((today.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }) -> "明天"
                else -> weekday
            }
            selectionSummary.text = "$prefix · ${selectedDate.get(Calendar.MONTH) + 1}月${selectedDate.get(Calendar.DAY_OF_MONTH)}日"
        }

        fun refreshMonthControls() {
            val firstSelectableMonth = (today.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
            val lastSelectableMonth = (lastAvailableDay.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
            previousMonth.isEnabled = displayedMonth.after(firstSelectableMonth)
            nextMonth.isEnabled = displayedMonth.before(lastSelectableMonth)
            previousMonth.alpha = if (previousMonth.isEnabled) 1f else 0.35f
            nextMonth.alpha = if (nextMonth.isEnabled) 1f else 0.35f
            monthLabel.text = "${displayedMonth.get(Calendar.YEAR)}年${displayedMonth.get(Calendar.MONTH) + 1}月"
        }

        fun renderDays() {
            dayGrid.removeAllViews()
            val firstOfMonth = (displayedMonth.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
            val leadingBlankCount = firstOfMonth.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
            val daysInMonth = firstOfMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
            repeat(leadingBlankCount) { blankIndex ->
                dayGrid.addView(View(context), calendarGridParams(blankIndex / 7, blankIndex % 7, dp(46)))
            }
            for (day in 1..daysInMonth) {
                val date = (firstOfMonth.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, day) }
                val index = leadingBlankCount + day - 1
                val isAvailable = !date.before(today) && !date.after(lastAvailableDay)
                val isSelected = date.isSameDay(selectedDate)
                dayGrid.addView(TextView(context).apply {
                    text = day.toString()
                    textSize = 16f
                    gravity = Gravity.CENTER
                    isClickable = isAvailable
                    isFocusable = isAvailable
                    when {
                        isSelected -> {
                            setTextColor(Color.WHITE)
                            setTypeface(typeface, Typeface.BOLD)
                            background = roundedBackground(ContextCompat.getColor(context, R.color.railway_blue), 18)
                        }
                        isAvailable -> {
                            setTextColor(Color.rgb(35, 48, 65))
                            background = roundedBackground(Color.TRANSPARENT, 18)
                        }
                        else -> {
                            setTextColor(Color.rgb(196, 206, 217))
                            background = roundedBackground(Color.TRANSPARENT, 18)
                        }
                    }
                    if (isAvailable) {
                        contentDescription = "选择${date.get(Calendar.MONTH) + 1}月${day}日"
                        setOnClickListener {
                            selectedDate = date
                            refreshSelectionSummary()
                            renderDays()
                        }
                    }
                }, calendarGridParams(index / 7, index % 7, dp(46)))
            }
        }

        previousMonth.setOnClickListener {
            if (previousMonth.isEnabled) {
                displayedMonth.add(Calendar.MONTH, -1)
                refreshMonthControls()
                renderDays()
            }
        }
        nextMonth.setOnClickListener {
            if (nextMonth.isEnabled) {
                displayedMonth.add(Calendar.MONTH, 1)
                refreshMonthControls()
                renderDays()
            }
        }
        todayButton.setOnClickListener {
            selectedDate = today.clone() as Calendar
            displayedMonth = (today.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
            refreshSelectionSummary()
            refreshMonthControls()
            renderDays()
        }
        confirmButton.setOnClickListener {
            binding.etDepartureDate.setText(dateFormat.format(selectedDate.time))
            dialog.dismiss()
        }

        refreshSelectionSummary()
        refreshMonthControls()
        renderDays()
        dialog.setContentView(sheet)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            attributes = attributes.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                dimAmount = 0.3f
            }
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun calendarNavButton(label: String, description: String): TextView = TextView(requireContext()).apply {
        text = label
        textSize = 30f
        gravity = Gravity.CENTER
        setTextColor(ContextCompat.getColor(requireContext(), R.color.railway_blue_deep))
        contentDescription = description
        background = roundedBackground(Color.rgb(238, 247, 255), 16)
        isClickable = true
        isFocusable = true
    }

    private fun calendarActionButton(label: String, primary: Boolean): TextView = TextView(requireContext()).apply {
        text = label
        textSize = 16f
        gravity = Gravity.CENTER
        setTypeface(typeface, Typeface.BOLD)
        val blue = ContextCompat.getColor(requireContext(), R.color.railway_blue)
        setTextColor(if (primary) Color.WHITE else blue)
        background = if (primary) {
            roundedBackground(blue, 18)
        } else {
            roundedBackground(Color.rgb(238, 247, 255), 18, blue)
        }
        isClickable = true
        isFocusable = true
    }

    private fun calendarGridParams(row: Int, column: Int, height: Int): GridLayout.LayoutParams {
        return GridLayout.LayoutParams(
            GridLayout.spec(row, 1, 1f),
            GridLayout.spec(column, 1, 1f)
        ).apply {
            width = 0
            this.height = height
            setMargins(dp(2), dp(1), dp(2), dp(1))
        }
    }

    private fun roundedBackground(fillColor: Int, radiusDp: Int, strokeColor: Int? = null): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fillColor)
            strokeColor?.let { setStroke(dp(1), it) }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun Calendar.startOfDay(): Calendar = (clone() as Calendar).apply { startOfDayInPlace() }

    private fun Calendar.startOfDayInPlace() {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun Calendar.isSameDay(other: Calendar): Boolean =
        get(Calendar.YEAR) == other.get(Calendar.YEAR) && get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)
    
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
        liquidSheenAnimator?.cancel()
        liquidSheenAnimator = null
        super.onDestroyView()
        _binding = null
    }
}
