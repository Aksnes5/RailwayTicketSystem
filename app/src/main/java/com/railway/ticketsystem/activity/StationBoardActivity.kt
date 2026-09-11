package com.railway.ticketsystem.activity

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.railway.ticketsystem.R
import com.railway.ticketsystem.data.RailwayData
import com.railway.ticketsystem.data.StationBoardGenerator
import com.railway.ticketsystem.data.todayDate
import com.railway.ticketsystem.databinding.ActivityStationBoardBinding
import com.railway.ticketsystem.model.Station
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StationBoardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityStationBoardBinding
    private var stationNames: List<String> = emptyList()
    private val screenScope = CoroutineScope(Dispatchers.Main)
    private var renderBoardJob: Job? = null
    private val stationPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val station = result.data?.getSerializableExtra("selectedStation") as? Station
        if (result.resultCode == RESULT_OK && station != null) {
            binding.actBoardStation.setText(station.name)
            renderBoard()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStationBoardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnBack.setOnClickListener { finish() }
        stationNames = RailwayData.stations.map { it.name }.distinct().sorted()
        binding.actBoardStation.setText(stationNames.firstOrNull { it == "北京南" } ?: stationNames.firstOrNull().orEmpty())
        binding.actBoardStation.setOnClickListener { openStationSelection() }
        binding.btnRefreshBoard.setOnClickListener { renderBoard() }
        binding.tvBoardDate.text = SimpleDateFormat("M月d日", Locale.CHINA).format(Date()) + " · 当日到发信息"
        renderBoard()
    }

    private fun openStationSelection() {
        stationPicker.launch(Intent(this, StationSelectionActivity::class.java))
    }

    private fun renderBoard() {
        val station = binding.actBoardStation.text?.toString()?.trim().orEmpty()
        if (station !in stationNames) {
            Toast.makeText(this, "请选择有效车站", Toast.LENGTH_SHORT).show()
            return
        }
        binding.llBoardRows.removeAllViews()
        binding.btnRefreshBoard.isEnabled = false
        binding.btnRefreshBoard.text = "加载中"
        val boardDate = todayDate()
        renderBoardJob?.cancel()
        renderBoardJob = screenScope.launch {
            val entries = withContext(Dispatchers.Default) {
                StationBoardGenerator.forStation(station, boardDate)
            }
            if (isFinishing || isDestroyed) return@launch
            binding.llBoardRows.removeAllViews()
            if (entries.isEmpty()) {
                binding.llBoardRows.addView(TextView(this@StationBoardActivity).apply {
                    text = "暂无可展示的当日班次"
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(this@StationBoardActivity, R.color.text_secondary))
                    gravity = android.view.Gravity.CENTER
                    setPadding(dp(12), dp(28), dp(12), dp(28))
                })
            } else {
                entries.forEach { entry -> binding.llBoardRows.addView(boardRow(entry)) }
            }
            binding.btnRefreshBoard.isEnabled = true
            binding.btnRefreshBoard.text = "查看"
        }
    }

    override fun onDestroy() {
        renderBoardJob?.cancel()
        screenScope.cancel()
        super.onDestroy()
    }

    private fun boardRow(entry: com.railway.ticketsystem.data.StationBoardEntry): MaterialCardView {
        val card = MaterialCardView(this).apply {
            radius = dp(16).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(Color.WHITE)
            strokeWidth = dp(1)
            strokeColor = ContextCompat.getColor(this@StationBoardActivity, R.color.divider)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        row.addView(column("${entry.trainNumber}\n${entry.direction} · ${entry.terminal}", 1.1f, android.view.Gravity.START, R.color.text_primary, true))
        row.addView(column("${entry.arrivalTime}\n${entry.departureTime}", 1f, android.view.Gravity.CENTER, R.color.text_primary, false))
        row.addView(column("${entry.platform}\n${entry.status}", .8f, android.view.Gravity.END, if (entry.status == "正点") R.color.success else R.color.railway_blue, false))
        card.addView(row)
        return card
    }

    private fun column(text: String, weight: Float, gravity: Int, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(ContextCompat.getColor(this@StationBoardActivity, color))
        this.gravity = gravity
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
        setLineSpacing(dp(3).toFloat(), 1f)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
