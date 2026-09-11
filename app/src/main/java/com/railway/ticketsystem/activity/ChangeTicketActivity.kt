package com.railway.ticketsystem.activity

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.railway.ticketsystem.R
import com.railway.ticketsystem.adapter.TrainResultAdapter
import com.railway.ticketsystem.data.DepartureTimingPolicy
import com.railway.ticketsystem.data.RailwayData
import com.railway.ticketsystem.databinding.ActivityChangeTicketBinding
import com.railway.ticketsystem.data.SeatInventoryRepository
import com.railway.ticketsystem.model.Order
import com.railway.ticketsystem.model.Train
import java.text.SimpleDateFormat
import java.util.*

class ChangeTicketActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityChangeTicketBinding
    private lateinit var originalOrder: Order
    private lateinit var trainAdapter: TrainResultAdapter
    private var selectedDate = ""
    private lateinit var seatInventoryRepository: SeatInventoryRepository
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("MM月dd日 E", Locale.getDefault())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChangeTicketBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 获取原订单信息
        originalOrder = intent.getSerializableExtra("originalOrder") as Order
        
        setupUI()
        seatInventoryRepository = SeatInventoryRepository(this)
        setupRecyclerView()
        setupDateSelection()
        
        // 默认选择今天
        selectedDate = getCurrentDate()
        updateDateDisplay()
        searchTrains()
    }
    
    private fun setupUI() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "改签车票"
        
        // 显示原订单信息
        binding.tvOriginalTrainInfo.text = "${originalOrder.trainNumber} ${originalOrder.departureStation} → ${originalOrder.arrivalStation}"
        binding.tvOriginalTimeInfo.text = "${originalOrder.departureDate} ${originalOrder.departureTime} - ${originalOrder.arrivalTime}"
        binding.tvOriginalSeatInfo.text = originalOrder.seatInfo
    }
    
    private fun setupRecyclerView() {
        trainAdapter = TrainResultAdapter(seatInventoryRepository, { selectedDate }) { train ->
            if (seatInventoryRepository.getAvailability(train, selectedDate, originalOrder.seatType).requiresWaitlist) {
                Toast.makeText(this, "该席别暂无余票，暂不支持候补改签", Toast.LENGTH_LONG).show()
                return@TrainResultAdapter
            }
            // 点击车次，跳转到改签座位选择页面
            val intent = Intent(this, ChangeTicketSeatActivity::class.java)
            intent.putExtra("originalOrder", originalOrder)
            intent.putExtra("selectedTrain", train)
            intent.putExtra("departureDate", selectedDate)
            startActivity(intent)
        }
        
        binding.rvTrains.apply {
            layoutManager = LinearLayoutManager(this@ChangeTicketActivity)
            adapter = trainAdapter
        }
    }
    
    private fun setupDateSelection() {
        binding.btnSelectDate.setOnClickListener {
            showDatePicker()
        }
    }
    
    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val today = Calendar.getInstance()
        
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(year, month, dayOfMonth)
                selectedDate = dateFormat.format(selectedCalendar.time)
                updateDateDisplay()
                searchTrains()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        
        // 设置日期范围：今天到15天后
        val maxDate = Calendar.getInstance()
        maxDate.add(Calendar.DAY_OF_MONTH, 15)
        datePickerDialog.datePicker.maxDate = maxDate.timeInMillis
        datePickerDialog.datePicker.minDate = today.timeInMillis
        
        datePickerDialog.show()
    }
    
    private fun updateDateDisplay() {
        try {
            val date = dateFormat.parse(selectedDate)
            if (date != null) {
                val displayText = if (isToday(selectedDate)) {
                    "今天 (${displayDateFormat.format(date)})"
                } else {
                    displayDateFormat.format(date)
                }
                binding.btnSelectDate.text = displayText
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun searchTrains() {
        try {
            val trains = RailwayData.getTrainsSortedByTime(
                originalOrder.departureStation, 
                originalOrder.arrivalStation
            )
            
            // Same sales window as buying: nothing that has left, or is about to, can be
            // changed onto.
            trainAdapter.updateTrains(
                trains.filter { DepartureTimingPolicy.isBookable(selectedDate, it.departureTime) }
            )
            
            if (trains.isEmpty()) {
                showEmptyState()
            } else {
                hideEmptyState()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "查询失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun showEmptyState() {
        binding.tvEmptyState.visibility = android.view.View.VISIBLE
        binding.rvTrains.visibility = android.view.View.GONE
    }
    
    private fun hideEmptyState() {
        binding.tvEmptyState.visibility = android.view.View.GONE
        binding.rvTrains.visibility = android.view.View.VISIBLE
    }
    
    private fun getCurrentDate(): String {
        return dateFormat.format(Date())
    }
    
    private fun isToday(dateString: String): Boolean {
        return dateString == getCurrentDate()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
