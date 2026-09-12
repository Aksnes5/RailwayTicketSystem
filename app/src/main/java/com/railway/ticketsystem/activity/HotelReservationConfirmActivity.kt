package com.railway.ticketsystem.activity

import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.railway.ticketsystem.R
import com.railway.ticketsystem.data.HotelReservationRepository
import com.railway.ticketsystem.data.HotelRoom
import com.railway.ticketsystem.data.MessageRepository
import com.railway.ticketsystem.data.NearbyHotel
import com.railway.ticketsystem.data.PassengerRepository
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.databinding.ActivityHotelReservationConfirmBinding
import com.railway.ticketsystem.model.Passenger
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HotelReservationConfirmActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHotelReservationConfirmBinding
    private lateinit var hotel: NearbyHotel
    private lateinit var room: HotelRoom
    private var checkInMillis = 0L
    private var nights = 1
    private var breakfastSelected = false
    private var totalPrice = 0
    private var selectedGuest: Passenger? = null
    private lateinit var passengerRepository: PassengerRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHotelReservationConfirmBinding.inflate(layoutInflater)
        setContentView(binding.root)
        passengerRepository = PassengerRepository(this)
        window.statusBarColor = getColor(R.color.surface)
        window.navigationBarColor = getColor(R.color.surface)
        val valuesValid = readValues()
        if (!valuesValid) {
            finish()
            return
        }
        binding.btnHotelConfirmBack.setOnClickListener { finish() }
        binding.layoutConfirmGuest.setOnClickListener { chooseGuest() }
        binding.btnSubmitHotelReservation.setOnClickListener { submitReservation() }
        render()
    }

    private fun readValues(): Boolean {
        hotel = readSerializable(HotelDetailActivity.EXTRA_HOTEL, NearbyHotel::class.java) ?: return false
        room = readSerializable(HotelDetailActivity.EXTRA_ROOM, HotelRoom::class.java) ?: return false
        checkInMillis = intent.getLongExtra(HotelDetailActivity.EXTRA_CHECK_IN, 0L)
        nights = intent.getIntExtra(HotelDetailActivity.EXTRA_NIGHTS, 1).coerceIn(1, 30)
        breakfastSelected = intent.getBooleanExtra(HotelDetailActivity.EXTRA_BREAKFAST, false)
        return checkInMillis > 0L
    }

    private fun render() {
        val checkIn = Calendar.getInstance().apply { timeInMillis = checkInMillis }
        val checkOut = (checkIn.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, nights) }
        val nightly = hotel.nightlyPrice(room)
        val roomTotal = nightly * nights
        val breakfastTotal = if (breakfastSelected) room.breakfastPrice * nights else 0
        totalPrice = roomTotal + breakfastTotal
        val user = UserRepository(this).getCurrentUser()

        binding.tvConfirmHotelName.text = hotel.name
        binding.tvConfirmHotelAddress.text = "${hotel.addressHint} · 评分 ${String.format(Locale.CHINA, "%.1f", hotel.rating)}"
        binding.tvConfirmStayDates.text = "${dateFormat.format(checkIn.time)} 入住 · ${dateFormat.format(checkOut.time)} 离店 · 共 $nights 晚"
        binding.tvConfirmRoom.text = "${room.name} · ${room.bed} · ${room.area}"
        binding.tvConfirmBreakfast.text = if (breakfastSelected) {
            "早餐：已选 1 份/晚（¥${room.breakfastPrice}/份/晚）"
        } else {
            "早餐：未选择，可在入住时按酒店政策加购"
        }
        binding.tvConfirmRoomCharge.text = "房费  ¥$nightly × $nights 晚"
        binding.tvConfirmRoomTotal.text = "¥$roomTotal"
        binding.tvConfirmBreakfastCharge.text = if (breakfastSelected) {
            "早餐  ¥${room.breakfastPrice} × $nights 晚"
        } else {
            "早餐"
        }
        binding.tvConfirmBreakfastTotal.text = "¥$breakfastTotal"
        binding.tvConfirmTotal.text = "合计 ¥$totalPrice"
        val guest = selectedGuest
        binding.tvConfirmGuest.text = if (guest != null) {
            "入住人：${guest.name}\n证件号：${maskIdCard(guest.idCard)} · ${guest.phone}"
        } else if (user == null) {
            "入住人：到店办理\n请在到店时出示有效身份证件"
        } else {
            "入住人：${user.realName.ifBlank { user.username }}\n联系电话：${user.phone.ifBlank { "以登记信息为准" }}"
        }
        binding.btnSubmitHotelReservation.text = "提交预订 ¥$totalPrice"
    }

    private fun submitReservation() {
        val user = UserRepository(this).getCurrentUser()
        val checkIn = Calendar.getInstance().apply { timeInMillis = checkInMillis }
        val checkOut = (checkIn.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, nights) }
        val reservation = HotelReservationRepository.newReservation(
            userId = user?.id.orEmpty(),
            hotelName = hotel.name,
            roomName = room.name,
            checkInDate = fullDateFormat.format(checkIn.time),
            checkOutDate = fullDateFormat.format(checkOut.time),
            nights = nights,
            breakfastSelected = breakfastSelected,
            totalPrice = totalPrice,
            guestName = selectedGuest?.name ?: user?.let { it.realName.ifBlank { it.username } }.orEmpty(),
            guestIdCard = selectedGuest?.idCard.orEmpty(),
            guestPhone = selectedGuest?.phone ?: user?.phone.orEmpty()
        )
        if (!HotelReservationRepository(this).save(reservation)) {
            Toast.makeText(this, "预订保存失败，请重试", Toast.LENGTH_SHORT).show()
            return
        }
        user?.let {
            MessageRepository(this).add(
                it.id,
                MessageRepository.TRAVEL,
                "酒店预订成功 · ${hotel.brand}",
                "${hotel.name} ${reservation.checkInDate} 入住，${room.name}，共 ${reservation.nights} 晚，合计 ¥${reservation.totalPrice}。",
                eventKey = "hotel_reservation:${reservation.id}"
            )
        }
        startActivity(Intent(this, HotelBookingSuccessActivity::class.java).apply {
            // Pass plain values as a safe fall-back; the success screen normally re-reads the
            // durable reservation rather than relying on Serializable state during recreation.
            putExtra(HotelBookingSuccessActivity.EXTRA_RESERVATION_ID, reservation.id)
            putExtra(HotelBookingSuccessActivity.EXTRA_HOTEL_NAME, reservation.hotelName)
            putExtra(HotelBookingSuccessActivity.EXTRA_ROOM_NAME, reservation.roomName)
            putExtra(HotelBookingSuccessActivity.EXTRA_CHECK_IN, reservation.checkInDate)
            putExtra(HotelBookingSuccessActivity.EXTRA_CHECK_OUT, reservation.checkOutDate)
            putExtra(HotelBookingSuccessActivity.EXTRA_NIGHTS, reservation.nights)
            putExtra(HotelBookingSuccessActivity.EXTRA_TOTAL_PRICE, reservation.totalPrice)
        })
        finish()
    }

    /** Uses the same saved passenger roster as ticket purchase; a room supports one lead guest. */
    private fun chooseGuest() {
        val user = UserRepository(this).getCurrentUser() ?: run {
            Toast.makeText(this, "请先登录后选择入住人", Toast.LENGTH_SHORT).show()
            return
        }
        val passengers = passengerRepository.getPassengersByUserId(user.id)
        if (passengers.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("暂无常用入住人")
                .setMessage("请先在乘车人管理中添加入住人信息。")
                .setNegativeButton("取消", null)
                .setPositiveButton("管理乘车人") { _, _ ->
                    startActivity(Intent(this, PassengerManageActivity::class.java))
                }
                .show()
            return
        }
        var candidate = selectedGuest ?: passengers.first()
        val labels = passengers.map { "${it.name}  ${maskIdCard(it.idCard)}" }.toTypedArray()
        val selectedIndex = passengers.indexOfFirst { it.id == candidate.id }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("选择入住人")
            .setSingleChoiceItems(labels, selectedIndex) { _, which -> candidate = passengers[which] }
            .setNegativeButton("取消", null)
            .setNeutralButton("管理乘车人") { _, _ ->
                startActivity(Intent(this, PassengerManageActivity::class.java))
            }
            .setPositiveButton("确定") { _, _ ->
                selectedGuest = candidate
                render()
            }
            .show()
    }

    private fun maskIdCard(value: String): String =
        if (value.length < 8) value else value.take(3) + "********" + value.takeLast(3)

    @Suppress("DEPRECATION")
    private fun <T : java.io.Serializable> readSerializable(key: String, clazz: Class<T>): T? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent.getSerializableExtra(key, clazz)
        else intent.getSerializableExtra(key) as? T

    companion object {
        private val dateFormat = SimpleDateFormat("M月d日 E", Locale.CHINA)
        private val fullDateFormat = SimpleDateFormat("yyyy年M月d日", Locale.CHINA)
    }
}
