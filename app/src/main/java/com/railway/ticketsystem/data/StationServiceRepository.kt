package com.railway.ticketsystem.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** One locally persisted station-service reservation or registration. */
data class StationServiceRequest(
    val id: String,
    val userId: String,
    val serviceType: String,
    val station: String,
    val schedule: String,
    val contact: String,
    val details: String,
    val status: String,
    val createdAt: String
) : Serializable

/**
 * Stores non-ticket station services independently from orders: a lost-property record or
 * station navigation request must remain available even when no ticket was purchased.
 */
class StationServiceRepository(context: Context) {
    private val prefs = SecurePreferences.open(
        context.applicationContext,
        "secure_station_services",
        "station_services"
    )
    private val gson = Gson()

    fun save(request: StationServiceRequest): Boolean {
        val all = read().filterNot { it.id == request.id }
        return prefs.edit().putString(KEY, gson.toJson((all + request).takeLast(120))).commit()
    }

    fun getByUser(userId: String): List<StationServiceRequest> = read()
        .filter { it.userId == userId }
        .sortedByDescending { it.createdAt }

    private fun read(): List<StationServiceRequest> {
        val type = object : TypeToken<List<StationServiceRequest>>() {}.type
        return runCatching { gson.fromJson<List<StationServiceRequest>>(prefs.getString(KEY, null), type) ?: emptyList() }
            .getOrDefault(emptyList())
    }

    companion object {
        private const val KEY = "station_service_requests"

        fun newRequest(
            userId: String,
            serviceType: String,
            station: String,
            schedule: String,
            contact: String,
            details: String,
            status: String = "已提交"
        ): StationServiceRequest = StationServiceRequest(
            id = "STS_" + UUID.randomUUID().toString().take(8).uppercase(Locale.ROOT),
            userId = userId,
            serviceType = serviceType,
            station = station,
            schedule = schedule,
            contact = contact,
            details = details,
            status = status,
            createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
        )
    }
}
