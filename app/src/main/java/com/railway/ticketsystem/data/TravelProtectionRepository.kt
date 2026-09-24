package com.railway.ticketsystem.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** A durable record for assistance requested around a disrupted trip. */
data class TravelProtectionCase(
    val id: String,
    val userId: String,
    val orderId: String,
    val type: String,
    val status: String,
    val solution: String,
    val createdAt: String
)

class TravelProtectionRepository(context: Context) {
    private val prefs = SecurePreferences.open(context.applicationContext, "secure_travel_protection", "travel_protection")
    private val gson = Gson()

    fun getByUser(userId: String): List<TravelProtectionCase> = read()
        .filter { it.userId == userId }
        .sortedByDescending { it.createdAt }

    fun open(userId: String, orderId: String, type: String, status: String, solution: String): TravelProtectionCase? {
        if (userId.isBlank() || orderId.isBlank()) return null
        val values = read()
        val existing = values.firstOrNull { it.userId == userId && it.orderId == orderId && it.type == type }
        if (existing != null) return existing
        val record = TravelProtectionCase(
            id = "TRP_" + UUID.randomUUID().toString().take(8).uppercase(Locale.ROOT),
            userId = userId,
            orderId = orderId,
            type = type,
            status = status,
            solution = solution,
            createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
        )
        return if (prefs.edit().putString(KEY, gson.toJson((values + record).takeLast(120))).commit()) record else null
    }

    private fun read(): List<TravelProtectionCase> {
        val type = object : TypeToken<List<TravelProtectionCase>>() {}.type
        return runCatching { gson.fromJson<List<TravelProtectionCase>>(prefs.getString(KEY, null), type) ?: emptyList() }
            .getOrDefault(emptyList())
    }

    private companion object { const val KEY = "travel_protection_cases" }
}
