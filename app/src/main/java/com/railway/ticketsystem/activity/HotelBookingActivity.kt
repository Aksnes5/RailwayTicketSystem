package com.railway.ticketsystem.activity

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.railway.ticketsystem.R
import com.railway.ticketsystem.data.NearbyHotel
import com.railway.ticketsystem.data.NearbyHotelCatalog
import com.railway.ticketsystem.data.RailwayData
import com.railway.ticketsystem.databinding.ActivityHotelBookingBinding
import com.railway.ticketsystem.model.Station
import kotlin.math.max

class HotelBookingActivity : ImmersiveActivity() {
    private lateinit var binding: ActivityHotelBookingBinding
    private var stationNames: Set<String> = emptySet()

    private val stationPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val station = result.data?.getSerializableExtra("selectedStation") as? Station
        if (result.resultCode == RESULT_OK && station != null) {
            binding.actHotelStation.setText(station.name)
            renderHotels(station.name)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHotelBookingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        stationNames = RailwayData.stations.map { it.name }.toSet()
        val preferredStation = intent.getStringExtra(EXTRA_STATION)
            ?.takeIf { it in stationNames }
            ?: stationNames.firstOrNull { it == "北京南" }
            ?: stationNames.firstOrNull().orEmpty()

        binding.btnHotelBack.setOnClickListener { finish() }
        binding.btnHotelOrders.setOnClickListener {
            startActivity(Intent(this, HotelOrdersActivity::class.java))
        }
        binding.actHotelStation.setOnClickListener {
            stationPicker.launch(Intent(this, StationSelectionActivity::class.java))
        }
        binding.actHotelStation.setText(preferredStation)
        renderHotels(preferredStation)
    }

    private fun renderHotels(station: String) {
        if (station !in stationNames) {
            Toast.makeText(this, "请选择有效车站", Toast.LENGTH_SHORT).show()
            return
        }
        val hotels = NearbyHotelCatalog.around(station)
        binding.tvHotelSummary.text = "${station}站 5km 内 · ${hotels.size} 家连锁酒店 · 当晚参考价"
        binding.llHotels.removeAllViews()
        hotels.forEach { hotel -> binding.llHotels.addView(hotelCard(hotel)) }
    }

    private fun hotelCard(hotel: NearbyHotel): MaterialCardView {
        val card = MaterialCardView(this).apply {
            radius = dp(24).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(Color.WHITE)
            strokeWidth = dp(1)
            strokeColor = ContextCompat.getColor(this@HotelBookingActivity, R.color.divider)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
            setOnClickListener {
                startActivity(Intent(this@HotelBookingActivity, HotelDetailActivity::class.java).apply {
                    putExtra(HotelDetailActivity.EXTRA_HOTEL, hotel)
                })
            }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
        }
        val lowestRoom = hotel.rooms.minByOrNull { hotel.nightlyPrice(it) } ?: return card
        content.addView(text(hotel.name, 17, R.color.text_primary, true))
        content.addView(text(
            "距车站 ${distanceLabel(hotel.distanceMeters)} · 步行约 ${walkingMinutes(hotel.distanceMeters)} 分钟 · 住客评分 ${"%.1f".format(hotel.rating)}",
            13,
            R.color.text_secondary,
            false
        ).apply { layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) } })

        val footer = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
        }
        footer.addView(text("${lowestRoom.name}\n${lowestRoom.bed} · ${lowestRoom.area}", 13, R.color.text_primary, false).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            setLineSpacing(dp(3).toFloat(), 1f)
        })
        footer.addView(text("¥${hotel.nightlyPrice(lowestRoom)} 起\n查看房型 ›", 15, R.color.railway_blue, true).apply {
            gravity = Gravity.END
            setLineSpacing(dp(3).toFloat(), 1f)
        })
        content.addView(footer)
        card.addView(content)
        return card
    }

    private fun text(value: String, size: Int, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(ContextCompat.getColor(this@HotelBookingActivity, color))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun distanceLabel(meters: Int): String = "%.1fkm".format(meters / 1000.0)
    private fun walkingMinutes(meters: Int): Int = max(6, meters / 78)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_STATION = "hotel_station"
    }
}
