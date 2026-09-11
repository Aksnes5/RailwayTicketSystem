package com.railway.ticketsystem.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class HotelReservation(
    val id: String,
    val userId: String,
    val hotelName: String,
    val roomName: String,
    val checkInDate: String,
    val checkOutDate: String,
    val nights: Int,
    val breakfastSelected: Boolean,
    val totalPrice: Int,
    val createdAt: String,
    val guestName: String? = null,
    val guestIdCard: String? = null,
    val guestPhone: String? = null
) : Serializable

/** Keeps local hotel reservations durable across app restarts. */
class HotelReservationRepository(context: Context) {
    private val prefs = SecurePreferences.open(context.applicationContext, "secure_hotel_reservations", "hotel_reservations")
    private val gson = Gson()

    fun save(reservation: HotelReservation): Boolean {
        val existing = read().filterNot { it.id == reservation.id }
        return prefs.edit().putString(KEY, gson.toJson((existing + reservation).takeLast(100))).commit()
    }

    fun getByUser(userId: String): List<HotelReservation> =
        read().filter { it.userId == userId }.sortedByDescending { it.createdAt }

    fun getById(reservationId: String, userId: String? = null): HotelReservation? =
        read().firstOrNull { it.id == reservationId && (userId.isNullOrBlank() || it.userId == userId) }

    private fun read(): List<HotelReservation> {
        val type = object : TypeToken<List<HotelReservation>>() {}.type
        return runCatching { gson.fromJson<List<HotelReservation>>(prefs.getString(KEY, null), type) ?: emptyList() }
            .getOrDefault(emptyList())
    }

    companion object {
        private const val KEY = "hotel_reservations"

        fun newReservation(
            userId: String,
            hotelName: String,
            roomName: String,
            checkInDate: String,
            checkOutDate: String,
            nights: Int,
            breakfastSelected: Boolean,
            totalPrice: Int,
            guestName: String? = null,
            guestIdCard: String? = null,
            guestPhone: String? = null
        ) = HotelReservation(
            id = "HTL_" + UUID.randomUUID().toString().take(8).uppercase(Locale.ROOT),
            userId = userId,
            hotelName = hotelName,
            roomName = roomName,
            checkInDate = checkInDate,
            checkOutDate = checkOutDate,
            nights = nights,
            breakfastSelected = breakfastSelected,
            totalPrice = totalPrice,
            createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date()),
            guestName = guestName,
            guestIdCard = guestIdCard,
            guestPhone = guestPhone
        )
    }
}
