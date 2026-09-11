package com.railway.ticketsystem.fragment

import android.content.Intent
import android.content.res.ColorStateList
import android.widget.LinearLayout
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.railway.ticketsystem.R
import com.railway.ticketsystem.activity.TripDetailActivity
import com.railway.ticketsystem.activity.WaitlistManageActivity
import com.railway.ticketsystem.adapter.TripAdapter
import com.railway.ticketsystem.data.OrderRepository
import com.railway.ticketsystem.data.TravelAssistant
import com.railway.ticketsystem.data.PaymentLifecycle
import com.railway.ticketsystem.data.TripLifecycle
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.data.WaitlistRepository
import com.railway.ticketsystem.databinding.FragmentTripsBinding
import com.railway.ticketsystem.model.Order
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TripsFragment : Fragment() {
    
    private var _binding: FragmentTripsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var orderRepository: OrderRepository
    private lateinit var userRepository: UserRepository
    private lateinit var tripAdapter: TripAdapter
    private lateinit var waitlistRepository: WaitlistRepository
    private lateinit var paymentLifecycle: PaymentLifecycle
    private lateinit var tripLifecycle: TripLifecycle
    private var allTrips: List<Order> = emptyList()
    private var nextTrip: Order? = null
    private var selectedDate: String? = null
    private var selectedFilter = TripFilter.ALL

    private val dateButtons = mutableMapOf<String, MaterialButton>()
    private enum class TripFilter { ALL, UPCOMING, PENDING, HISTORY }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTripsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        orderRepository = OrderRepository(requireContext())
        userRepository = UserRepository(requireContext())
        waitlistRepository = WaitlistRepository(requireContext())
        paymentLifecycle = PaymentLifecycle(requireContext())
        tripLifecycle = TripLifecycle(requireContext())
        setupDashboard()
        setupRecyclerView()
    }

    override fun onResume() {
        super.onResume()
        loadTrips()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && _binding != null) loadTrips()
    }

    /** Allows booking flows to refresh data without manually invoking a lifecycle callback. */
    fun refreshTrips() {
        if (_binding != null) loadTrips()
    }

    private fun setupDashboard() {
        binding.btnTripFilterAll.setOnClickListener { selectFilter(TripFilter.ALL) }
        binding.btnTripFilterUpcoming.setOnClickListener { selectFilter(TripFilter.UPCOMING) }
        binding.btnTripFilterPending.setOnClickListener { selectFilter(TripFilter.PENDING) }
        binding.btnTripFilterHistory.setOnClickListener { selectFilter(TripFilter.HISTORY) }
        binding.cardWaitlist.setOnClickListener {
            startActivity(Intent(requireContext(), WaitlistManageActivity::class.java))
        }
        binding.cardNextTrip.setOnClickListener {
            nextTrip?.let(::openTrip)
        }
        updateFilterButtons()
    }

    private fun selectFilter(filter: TripFilter) {
        selectedFilter = filter
        // The horizontal strip is deliberately for the next seven days only.
        // Keeping a selected future-date filter while opening history hides valid past orders.
        if (filter == TripFilter.HISTORY) {
            selectedDate = null
            dateButtons.values.forEach { styleDateButton(it, false) }
        }
        updateFilterButtons()
        renderTripList()
    }

    private fun updateFilterButtons() {
        val buttons = listOf(
            binding.btnTripFilterAll to TripFilter.ALL,
            binding.btnTripFilterUpcoming to TripFilter.UPCOMING,
            binding.btnTripFilterPending to TripFilter.PENDING,
            binding.btnTripFilterHistory to TripFilter.HISTORY
        )
        buttons.forEach { (button, filter) -> styleFilterButton(button, filter == selectedFilter) }
    }

    private fun setupRecyclerView() {
        tripAdapter = TripAdapter(::openTrip)
        binding.rvTrips.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = tripAdapter
            setHasFixedSize(true)
            itemAnimator = null
        }
    }

    private fun openTrip(order: Order) {
        val intent = Intent(requireContext(), TripDetailActivity::class.java)
        intent.putExtra("order", order)
        startActivity(intent)
    }

    private fun loadTrips() {
        paymentLifecycle.processExpiredPayments()
        tripLifecycle.archiveArrivedTrips()
        waitlistRepository.processDueRequests(orderRepository)
        val currentUser = userRepository.getCurrentUser()
        if (currentUser == null) {
            binding.tvNoTrips.text = "请先登录"
            binding.tvNoTrips.visibility = View.VISIBLE
            binding.rvTrips.visibility = View.GONE
            binding.cardNextTrip.visibility = View.GONE
            binding.cardWaitlist.visibility = View.GONE
            binding.svTripDateSelector.visibility = View.GONE
            return
        }

        allTrips = orderRepository.getOrdersByUserId(currentUser.id)
        renderWaitlist(currentUser.id)
        renderNextTrip()
        renderDateSelector()
        renderTripList()
    }

    private fun renderWaitlist(userId: String) {
        val pendingCount = waitlistRepository.getPendingCount(userId)
        binding.cardWaitlist.visibility = View.VISIBLE
        binding.tvWaitlistSummary.text = if (pendingCount == 0) {
            "暂无候补订单，座位售出时可提交候补"
        } else {
            "有 ${pendingCount} 个候补订单正在等待兑现"
        }
    }

    private fun renderNextTrip() {
        nextTrip = TravelAssistant.nextUpcoming(allTrips)
        binding.cardNextTrip.visibility = if (nextTrip == null) View.GONE else View.VISIBLE
        nextTrip?.let { order ->
            binding.tvNextTripRoute.text = "${order.departureStation} → ${order.arrivalStation}"
            binding.tvNextTripMeta.text = "${order.departureDate} ${order.departureTime} 发车 · ${order.trainNumber}"
            binding.tvNextTripCountdown.text = TravelAssistant.departureCountdown(order)
            binding.tvNextTripReminder.text = TravelAssistant.boardingReminder(order)
        }
    }

    private fun renderDateSelector() {
        binding.llTripDateSelector.removeAllViews()
        dateButtons.clear()
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        val labelFormat = SimpleDateFormat("M/d", Locale.CHINA)
        val weekdayFormat = SimpleDateFormat("EEE", Locale.CHINA)
        val tripCounts = allTrips
            .filter { !TravelAssistant.isHistory(it) }
            .groupingBy { it.departureDate }
            .eachCount()
        repeat(7) { index ->
            val date = dateFormat.format(calendar.time)
            val count = tripCounts[date] ?: 0
            val label = "${if (index == 0) "今天" else weekdayFormat.format(calendar.time)}\n${labelFormat.format(calendar.time)}"
            val dayContainer = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            }
            val button = MaterialButton(
                requireContext(),
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = label
                isAllCaps = false
                textSize = 13f
                minWidth = 0
                minHeight = 0
                minimumHeight = 0
                insetTop = 0
                insetBottom = 0
                isSingleLine = false
                gravity = Gravity.CENTER
                setLineSpacing(0f, 0.9f)
                setPadding(0, 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (52 * resources.displayMetrics.density).toInt()
                )
                setOnClickListener {
                    selectDate(date)
                }
                styleDateButton(this, selectedDate == date)
            }
            dayContainer.addView(button)
            dateButtons[date] = button
            dayContainer.addView(View(requireContext()).apply {
                setBackgroundResource(R.drawable.bg_trip_date_dot)
                visibility = if (count > 0) View.VISIBLE else View.INVISIBLE
                layoutParams = LinearLayout.LayoutParams(
                    (5 * resources.displayMetrics.density).toInt(),
                    (5 * resources.displayMetrics.density).toInt()
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = (1 * resources.displayMetrics.density).toInt()
                }
            })
            binding.llTripDateSelector.addView(dayContainer)
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    private fun selectDate(date: String) {
        val previous = selectedDate
        selectedDate = if (selectedDate == date) null else date
        previous?.let { previousDate ->
            dateButtons[previousDate]?.let { styleDateButton(it, false) }
        }
        selectedDate?.let { selected ->
            dateButtons[selected]?.let { styleDateButton(it, true) }
        }
        renderTripList()
    }

    private fun renderTripList() {
        val filtered = allTrips.filter { order ->
            val dateMatches = selectedFilter == TripFilter.HISTORY ||
                selectedDate == null || order.departureDate == selectedDate
            dateMatches && when (selectedFilter) {
                // 已出行、已完成和已取消订单只在“历史”分类中展示，
                // 不会再占用“今天”的行程位或日期提示点。
                TripFilter.ALL -> !TravelAssistant.isHistory(order)
                TripFilter.UPCOMING -> TravelAssistant.isUpcoming(order)
                TripFilter.PENDING -> order.status == "待支付"
                TripFilter.HISTORY -> TravelAssistant.isHistory(order)
            }
        }.sortedWith(
            compareBy<Order> { if (TravelAssistant.isUpcoming(it)) 0 else 1 }
                .thenBy { it.departureDate }
                .thenBy { it.departureTime }
        )

        if (filtered.isEmpty()) {
            binding.tvNoTrips.text = if (allTrips.isEmpty()) {
                "暂无行程\n\n去首页查询一趟舒适的旅程吧"
            } else {
                "当前筛选条件下暂无行程\n\n点击日期或切换分类查看其他行程"
            }
            binding.tvNoTrips.visibility = View.VISIBLE
            binding.rvTrips.visibility = View.GONE
        } else {
            tripAdapter.updateTrips(filtered)
            binding.tvNoTrips.visibility = View.GONE
            binding.rvTrips.visibility = View.VISIBLE
        }
    }

    private fun styleFilterButton(button: MaterialButton, isSelected: Boolean) {
        val color = requireContext().getColor(if (isSelected) R.color.railway_blue else R.color.surface_container)
        button.backgroundTintList = ColorStateList.valueOf(color)
        button.setTextColor(requireContext().getColor(if (isSelected) R.color.white else R.color.text_primary))
        button.setStrokeColorResource(if (isSelected) R.color.railway_blue else R.color.button_stroke)
    }

    private fun styleDateButton(button: MaterialButton, isSelected: Boolean) {
        val color = requireContext().getColor(if (isSelected) R.color.white else android.R.color.transparent)
        button.backgroundTintList = ColorStateList.valueOf(color)
        button.setTextColor(requireContext().getColor(if (isSelected) R.color.railway_blue_deep else R.color.text_secondary))
        button.setStrokeColorResource(if (isSelected) R.color.button_stroke else android.R.color.transparent)
        button.strokeWidth = if (isSelected) dp(1) else 0
        button.cornerRadius = dp(20)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
