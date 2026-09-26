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
import android.view.HapticFeedbackConstants
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
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
import com.railway.ticketsystem.data.DataSourceModePreferences
import com.railway.ticketsystem.data.TrainDataSourceMode
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
            updateLiveJourneyCapsule()
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
        binding.btnBaggageCompliance.setOnClickListener {
            com.railway.ticketsystem.dialog.BaggageCheckBottomSheet(requireContext()).show()
        }
        binding.btnChildDeclarationHome.setOnClickListener {
            showChildDeclarationPolicyDialog()
        }
        binding.btnQuietCarriageHome.setOnClickListener {
            showQuietCarriagePledgeDialog()
        }
        binding.btnDelayQueryHome.setOnClickListener {
            val depStation = binding.etDepartureStation.text?.toString()?.trim().orEmpty().ifEmpty { "汉口" }
            startActivity(android.content.Intent(requireContext(), com.railway.ticketsystem.activity.TrainDelayQueryActivity::class.java).apply {
                putExtra(com.railway.ticketsystem.activity.TrainDelayQueryActivity.EXTRA_STATION_NAME, depStation)
            })
        }
        binding.btnTempIdHome.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), com.railway.ticketsystem.activity.TemporaryIdCertificateActivity::class.java))
        }
        binding.btnTravelCodeHome.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), com.railway.ticketsystem.activity.RailwayTravelCodeActivity::class.java))
        }

        setupDataSourceModeCapsule()
    }

    private fun showChildDeclarationPolicyDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("👶 免费乘车儿童线上申明须知")
            .setMessage("依国家铁路局新规：\n\n1. 每名持票成年人旅客可免费携带一名未满 6 周岁且不单独占用席位的儿童乘车。\n\n2. 超过一名时，超过人数需购买儿童优惠票。\n\n3. 携带免费乘车儿童必须在购票后提前在线申报申明，申报成功后儿童即可随成年人一同直接刷闸机进出站。\n\n您可在【行程详情】中随时为已购车票一键办理免费儿童申报。")
            .setPositiveButton("我知道了", null)
            .show()
    }

    private fun showQuietCarriagePledgeDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("🎧 铁路静音车厢公约")
            .setMessage("共同守护舒适静谧的出行环境：\n\n1. 请将手机及其他电子设备调至静音或震动模式。\n2. 使用电子设备收听音频请全程佩戴耳机且不外音泄露。\n3. 在车厢内轻声细语交流，接打电话请前往连接处。\n4. 请照看好随行儿童，避免喧哗嬉闹。\n\n💡 列车上如需休息或感到嘈杂，可在【车厢服务】或【行程详情】中向乘务员免费索取降噪耳塞。")
            .setPositiveButton("践行静音公约", null)
            .show()
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
        binding.btnSwapStations.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

        // 1. Rotate the swap button with an elastic overshoot spring
        binding.btnSwapStations.animate()
            .rotationBy(180f)
            .setDuration(360L)
            .setInterpolator(OvershootInterpolator(2.2f))
            .start()

        // 2. Animate departure and arrival text with smooth cross-fade slide
        val depText = binding.etDepartureStation.text?.toString().orEmpty()
        val arrText = binding.etArrivalStation.text?.toString().orEmpty()

        binding.etDepartureStation.animate()
            .translationX(40f)
            .alpha(0f)
            .setDuration(130L)
            .withEndAction {
                binding.etDepartureStation.setText(arrText)
                binding.etDepartureStation.translationX = -40f
                binding.etDepartureStation.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(180L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            .start()

        binding.etArrivalStation.animate()
            .translationX(-40f)
            .alpha(0f)
            .setDuration(130L)
            .withEndAction {
                binding.etArrivalStation.setText(depText)
                binding.etArrivalStation.translationX = 40f
                binding.etArrivalStation.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(180L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            .start()
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

        binding.btnQuickToday.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            setDepartureDate(0)
        }
        binding.btnQuickTomorrow.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            setDepartureDate(1)
        }
        
        // 设置默认日期为今天
        setDepartureDate(0)
    }

    private fun updateQuickDatePills(daysFromToday: Int) {
        val activeBg = ContextCompat.getDrawable(requireContext(), R.drawable.bg_quick_date_pill_active)
        val inactiveBg = ContextCompat.getDrawable(requireContext(), R.drawable.bg_quick_date_pill)
        val activeColor = ContextCompat.getColor(requireContext(), R.color.railway_blue)
        val inactiveColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)

        binding.btnQuickToday.background = if (daysFromToday == 0) activeBg else inactiveBg
        binding.btnQuickToday.setTextColor(if (daysFromToday == 0) activeColor else inactiveColor)
        binding.btnQuickToday.setTypeface(null, if (daysFromToday == 0) Typeface.BOLD else Typeface.NORMAL)

        binding.btnQuickTomorrow.background = if (daysFromToday == 1) activeBg else inactiveBg
        binding.btnQuickTomorrow.setTextColor(if (daysFromToday == 1) activeColor else inactiveColor)
        binding.btnQuickTomorrow.setTypeface(null, if (daysFromToday == 1) Typeface.BOLD else Typeface.NORMAL)
    }
    
    private fun setDepartureDate(daysFromToday: Int) {
        val date = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, daysFromToday) }
        binding.etDepartureDate.setText(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date.time))
        updateQuickDatePills(daysFromToday)
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
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            binding.etDepartureDate.setText(dateFormat.format(selectedDate.time))
            val diffDays = ((selectedDate.timeInMillis - today.timeInMillis) / (24 * 60 * 60 * 1000)).toInt()
            updateQuickDatePills(diffDays)
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
            binding.llRecentRoutesContainer.visibility = View.GONE
            binding.tvRecentRoutes.visibility = View.GONE
            binding.svRecentRoutes.visibility = View.GONE
            emptyList()
        }
        binding.llRecentRoutes.removeAllViews()
        val showRoutes = routes.isNotEmpty()
        binding.llRecentRoutesContainer.visibility = if (showRoutes) View.VISIBLE else View.GONE
        binding.tvRecentRoutes.visibility = if (showRoutes) View.VISIBLE else View.GONE
        binding.svRecentRoutes.visibility = if (showRoutes) View.VISIBLE else View.GONE

        routes.forEach { route ->
            val button = TextView(requireContext()).apply {
                text = "${route.departure} → ${route.arrival}"
                textSize = 15f
                setTextColor(resources.getColor(R.color.railway_blue, null))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = android.view.Gravity.CENTER_VERTICAL
                val verticalPadding = (7 * resources.displayMetrics.density).toInt()
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

    private fun setupDataSourceModeCapsule() {
        updateModeCapsuleUi()
        binding.capsuleModeToggle.setOnClickListener {
            val newMode = DataSourceModePreferences.toggleMode(requireContext())
            updateModeCapsuleUi()
            val toastText = if (newMode == TrainDataSourceMode.REAL) {
                "已切换为 12306 官方实盘车次"
            } else {
                "已切换为 算法智能推算模式"
            }
            Toast.makeText(requireContext(), toastText, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateModeCapsuleUi() {
        if (_binding == null) return
        val ctx = context ?: return
        val mode = DataSourceModePreferences.getMode(ctx)
        if (mode == TrainDataSourceMode.REAL) {
            binding.tvHomeModeBadge.text = "官方实盘"
            binding.vHomeModeDot.setBackgroundResource(R.drawable.bg_dot_live_green)
        } else {
            binding.tvHomeModeBadge.text = "智能推算"
            binding.vHomeModeDot.setBackgroundResource(R.drawable.bg_dot_mode_blue)
        }
    }
    
    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            renderRecentRoutes()
            updateLiveJourneyCapsule()
            updateModeCapsuleUi()
        }
    }

    private fun updateLiveJourneyCapsule() {
        if (_binding == null) return
        val ctx = context ?: return
        try {
            val userRepo = com.railway.ticketsystem.data.UserRepository(ctx)
            val user = userRepo.getCurrentUser()
            if (user == null) {
                binding.cardLiveJourneyCapsule.visibility = View.GONE
                return
            }
            val orderRepo = com.railway.ticketsystem.data.OrderRepository(ctx)
            val activeOrders = orderRepo.getAllOrders()
                .filter { it.userId == user.id && it.status == "已支付" }
                .sortedBy { "${it.departureDate} ${it.departureTime}" }

            val upcomingTrip = activeOrders.firstOrNull()
            if (upcomingTrip == null) {
                binding.cardLiveJourneyCapsule.visibility = View.GONE
                return
            }

            binding.cardLiveJourneyCapsule.visibility = View.VISIBLE
            binding.tvCapsuleTrainNumber.text = upcomingTrip.trainNumber
            binding.tvCapsuleRoute.text = "${upcomingTrip.departureStation} → ${upcomingTrip.arrivalStation}"
            binding.tvCapsuleSeat.text = if (upcomingTrip.carNumber.isNotBlank() && upcomingTrip.seatNumber.isNotBlank()) {
                "${upcomingTrip.carNumber}车 ${upcomingTrip.seatNumber}号"
            } else upcomingTrip.seatNumber

            val gate = runCatching {
                com.railway.ticketsystem.data.TicketTravelUpdates.getGate(ctx, upcomingTrip)
            }.getOrDefault("现场公告")
            binding.tvCapsuleStatusBadge.text = "检票口 $gate"
            val statusText = com.railway.ticketsystem.data.TravelAssistant.journeyStatusText(upcomingTrip)
            binding.tvCapsuleCountdown.text = "乘车日 ${upcomingTrip.departureDate} ${upcomingTrip.departureTime} 开 · $statusText"

            binding.cardLiveJourneyCapsule.setOnClickListener {
                val intent = android.content.Intent(ctx, com.railway.ticketsystem.activity.TripDetailActivity::class.java).apply {
                    putExtra("orderId", upcomingTrip.id)
                }
                startActivity(intent)
            }
        } catch (e: Exception) {
            android.util.Log.e("TicketsFragment", "更新行程胶囊卡片失败", e)
            binding.cardLiveJourneyCapsule.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        liquidSheenAnimator?.cancel()
        liquidSheenAnimator = null
        super.onDestroyView()
        _binding = null
    }
}
