package com.railway.ticketsystem.activity

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.railway.ticketsystem.R
import com.railway.ticketsystem.data.HotelRoom
import com.railway.ticketsystem.data.NearbyHotel
import com.railway.ticketsystem.data.TravelVisualAssets
import com.railway.ticketsystem.databinding.ActivityHotelDetailBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HotelDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHotelDetailBinding
    private lateinit var hotel: NearbyHotel
    private val checkIn = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 14)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, 1)
    }
    private var nights = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHotelDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.statusBarColor = getColor(R.color.surface)
        window.navigationBarColor = getColor(R.color.surface)
        hotel = readHotel() ?: run {
            finish()
            return
        }
        binding.btnHotelDetailBack.setOnClickListener { finish() }
        binding.layoutHotelStayDates.setOnClickListener { chooseCheckIn() }
        binding.btnHotelNightMinus.setOnClickListener { if (nights > 1) { nights--; renderStay() } }
        binding.btnHotelNightPlus.setOnClickListener { if (nights < 30) { nights++; renderStay() } }
        binding.switchHotelBreakfast.setOnCheckedChangeListener { _, _ -> renderBreakfastLabel() }
        renderHotel()
    }

    private fun renderHotel() {
        binding.tvDetailHotelName.text = hotel.name
        binding.tvDetailHotelMeta.text = "${hotel.addressHint} · 评分 ${formatRating(hotel.rating)} · 连锁品牌"
        renderStay()
        renderBreakfastLabel()
        binding.llHotelRoomList.removeAllViews()
        hotel.rooms.forEach { room -> binding.llHotelRoomList.addView(roomCard(room)) }
    }

    private fun renderStay() {
        val checkOut = checkoutCalendar()
        binding.tvHotelCheckIn.text = dateFormat.format(checkIn.time)
        binding.tvHotelCheckOut.text = dateFormat.format(checkOut.time)
        binding.tvHotelNightCount.text = "住 $nights 晚"
        binding.tvHotelNights.text = "$nights 晚"
        binding.btnHotelNightMinus.isEnabled = nights > 1
        binding.btnHotelNightPlus.isEnabled = nights < 30
    }

    private fun renderBreakfastLabel() {
        val lowestBreakfast = hotel.rooms.minOfOrNull { it.breakfastPrice } ?: 28
        binding.switchHotelBreakfast.text = if (binding.switchHotelBreakfast.isChecked) {
            "已加购早餐 · ¥$lowestBreakfast 起/份/晚"
        } else {
            "加购早餐 · ¥$lowestBreakfast 起/份/晚"
        }
    }

    private fun roomCard(room: HotelRoom): MaterialCardView {
        val card = MaterialCardView(this).apply {
            radius = dp(24).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(Color.WHITE)
            strokeWidth = dp(1)
            strokeColor = ContextCompat.getColor(this@HotelDetailActivity, R.color.divider)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(14))
        }
        content.addView(android.widget.ImageView(this).apply {
            contentDescription = "${room.name}图片"
            setImageResource(TravelVisualAssets.roomImage(room))
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            setBackgroundResource(R.drawable.bg_media_frame)
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(-1, dp(166)).apply { bottomMargin = dp(14) }
        })
        val heading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        heading.addView(text(room.name, 17, R.color.text_primary, true).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        heading.addView(text("¥${hotel.nightlyPrice(room)}/晚", 17, R.color.railway_blue, true).apply {
            gravity = Gravity.END
        })
        content.addView(heading)
        content.addView(text("${room.bed} · ${room.area}", 14, R.color.text_primary, false).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) }
        })
        content.addView(text(room.amenities, 13, R.color.text_secondary, false).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) }
        })
        content.addView(text(room.cancellation, 12, R.color.text_secondary, false).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
        })
        content.addView(MaterialButton(this).apply {
            text = "选择此房型"
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(14) }
            setOnClickListener { openReservation(room) }
        })
        card.addView(content)
        return card
    }

    private fun chooseCheckIn() {
        val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }
        DatePickerDialog(this, { _, year, month, day ->
            checkIn.set(year, month, day)
            chooseCheckOut()
        }, checkIn.get(Calendar.YEAR), checkIn.get(Calendar.MONTH), checkIn.get(Calendar.DAY_OF_MONTH)).apply {
            datePicker.minDate = today.timeInMillis
        }.show()
    }

    /** Booking-style date selection: selecting a check-out date sets the actual stay length. */
    private fun chooseCheckOut() {
        val minimum = (checkIn.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        val maximum = (checkIn.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 30) }
        val current = checkoutCalendar().apply {
            if (timeInMillis < minimum.timeInMillis || timeInMillis > maximum.timeInMillis) {
                timeInMillis = minimum.timeInMillis
            }
        }
        DatePickerDialog(this, { _, year, month, day ->
            val selected = (checkIn.clone() as Calendar).apply { set(year, month, day) }
            nights = ((selected.timeInMillis - checkIn.timeInMillis) / DAY_MILLIS).toInt().coerceIn(1, 30)
            renderStay()
        }, current.get(Calendar.YEAR), current.get(Calendar.MONTH), current.get(Calendar.DAY_OF_MONTH)).apply {
            datePicker.minDate = minimum.timeInMillis
            datePicker.maxDate = maximum.timeInMillis
        }.show()
    }

    private fun openReservation(room: HotelRoom) {
        startActivity(Intent(this, HotelReservationConfirmActivity::class.java).apply {
            putExtra(EXTRA_HOTEL, hotel)
            putExtra(EXTRA_ROOM, room)
            putExtra(EXTRA_CHECK_IN, checkIn.timeInMillis)
            putExtra(EXTRA_NIGHTS, nights)
            putExtra(EXTRA_BREAKFAST, binding.switchHotelBreakfast.isChecked)
        })
    }

    @Suppress("DEPRECATION")
    private fun readHotel(): NearbyHotel? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getSerializableExtra(EXTRA_HOTEL, NearbyHotel::class.java)
    } else {
        intent.getSerializableExtra(EXTRA_HOTEL) as? NearbyHotel
    }

    private fun checkoutCalendar() = (checkIn.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, nights) }
    private fun text(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(ContextCompat.getColor(this@HotelDetailActivity, color))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }
    private fun formatRating(value: Double) = String.format(Locale.CHINA, "%.1f", value)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_HOTEL = "hotel_detail_hotel"
        const val EXTRA_ROOM = "hotel_detail_room"
        const val EXTRA_CHECK_IN = "hotel_detail_check_in"
        const val EXTRA_NIGHTS = "hotel_detail_nights"
        const val EXTRA_BREAKFAST = "hotel_detail_breakfast"
        private val dateFormat = SimpleDateFormat("M月d日 E", Locale.CHINA)
        private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}
