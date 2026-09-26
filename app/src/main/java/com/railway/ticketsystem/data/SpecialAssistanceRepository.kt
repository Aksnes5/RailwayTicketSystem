package com.railway.ticketsystem.data

import android.content.Context
import org.json.JSONObject

/**
 * 重点旅客爱心通道与无障碍预约仓储
 */
class SpecialAssistanceRepository(context: Context) {

    private val prefs = context.getSharedPreferences("special_assistance", Context.MODE_PRIVATE)

    data class AssistanceRecord(
        val tripKey: String,
        val trainNumber: String,
        val passengerName: String,
        val contactPhone: String,
        val serviceType: String,
        val note: String,
        val bookedAt: Long,
        val statusText: String
    )

    fun getBooking(tripKey: String): AssistanceRecord? {
        val str = prefs.getString("booking_$tripKey", null) ?: return null
        return runCatching {
            val obj = JSONObject(str)
            AssistanceRecord(
                tripKey = obj.getString("tripKey"),
                trainNumber = obj.getString("trainNumber"),
                passengerName = obj.getString("passengerName"),
                contactPhone = obj.getString("contactPhone"),
                serviceType = obj.getString("serviceType"),
                note = obj.optString("note", ""),
                bookedAt = obj.getLong("bookedAt"),
                statusText = obj.optString("statusText", "车站工作人员将在发车前30分钟电话确认接驳")
            )
        }.getOrNull()
    }

    fun saveBooking(record: AssistanceRecord) {
        val obj = JSONObject().apply {
            put("tripKey", record.tripKey)
            put("trainNumber", record.trainNumber)
            put("passengerName", record.passengerName)
            put("contactPhone", record.contactPhone)
            put("serviceType", record.serviceType)
            put("note", record.note)
            put("bookedAt", record.bookedAt)
            put("statusText", record.statusText)
        }
        prefs.edit().putString("booking_${record.tripKey}", obj.toString()).apply()
    }

    fun cancelBooking(tripKey: String) {
        prefs.edit().remove("booking_$tripKey").apply()
    }
}
