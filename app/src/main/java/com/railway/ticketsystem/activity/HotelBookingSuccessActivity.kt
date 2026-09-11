package com.railway.ticketsystem.activity

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.railway.ticketsystem.R
import com.railway.ticketsystem.data.HotelReservation
import com.railway.ticketsystem.data.HotelReservationRepository
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.databinding.ActivityHotelBookingSuccessBinding

class HotelBookingSuccessActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHotelBookingSuccessBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHotelBookingSuccessBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.statusBarColor = getColor(R.color.surface)
        window.navigationBarColor = getColor(R.color.surface)
        val reservation = readReservation()
        if (reservation != null) {
            binding.tvHotelSuccessSummary.text = "${reservation.hotelName}\n${reservation.checkInDate} 入住 · ${reservation.checkOutDate} 离店\n${reservation.roomName} · ${reservation.nights} 晚"
            binding.tvHotelSuccessNumber.text = "预订编号：${reservation.id}\n合计 ¥${reservation.totalPrice}"
        } else {
            // A saved order must never turn a completed booking into a blank or closed screen.
            binding.tvHotelSuccessSummary.text = "预订信息已保存，请在我的酒店订单查看入住详情"
            binding.tvHotelSuccessNumber.text = "酒店预订已确认"
        }
        binding.btnHotelSuccessDone.setOnClickListener { finishAffinity() }
        binding.btnHotelSuccessOrders.setOnClickListener {
            startActivity(Intent(this, HotelOrdersActivity::class.java))
            finish()
        }
    }

    private fun readReservation(): HotelReservation? {
        val reservationId = intent.getStringExtra(EXTRA_RESERVATION_ID).orEmpty()
        if (reservationId.isNotBlank()) {
            val userId = UserRepository(this).getCurrentUser()?.id
            HotelReservationRepository(this).getById(reservationId, userId)?.let { return it }
        }
        val hotelName = intent.getStringExtra(EXTRA_HOTEL_NAME).orEmpty()
        if (hotelName.isBlank()) return null
        return HotelReservation(
            id = reservationId.ifBlank { "HTL" },
            userId = "",
            hotelName = hotelName,
            roomName = intent.getStringExtra(EXTRA_ROOM_NAME).orEmpty(),
            checkInDate = intent.getStringExtra(EXTRA_CHECK_IN).orEmpty(),
            checkOutDate = intent.getStringExtra(EXTRA_CHECK_OUT).orEmpty(),
            nights = intent.getIntExtra(EXTRA_NIGHTS, 1),
            breakfastSelected = false,
            totalPrice = intent.getIntExtra(EXTRA_TOTAL_PRICE, 0),
            createdAt = ""
        )
    }

    companion object {
        const val EXTRA_RESERVATION_ID = "hotel_reservation_id"
        const val EXTRA_HOTEL_NAME = "hotel_reservation_hotel_name"
        const val EXTRA_ROOM_NAME = "hotel_reservation_room_name"
        const val EXTRA_CHECK_IN = "hotel_reservation_check_in"
        const val EXTRA_CHECK_OUT = "hotel_reservation_check_out"
        const val EXTRA_NIGHTS = "hotel_reservation_nights"
        const val EXTRA_TOTAL_PRICE = "hotel_reservation_total_price"
    }
}
