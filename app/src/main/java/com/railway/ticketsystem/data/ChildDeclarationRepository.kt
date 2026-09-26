package com.railway.ticketsystem.data

import android.content.Context
import org.json.JSONObject

/**
 * 免费乘车儿童申明管理
 * 依《铁路旅客运输规程》：成年人旅客携带未满6周岁且不单独占用席位的儿童乘车时，
 * 须在购票后或乘车前完成免费乘车儿童申报，生成通行凭证并与成年人一同刷闸过机进站。
 */
class ChildDeclarationRepository(context: Context) {

    private val prefs = context.getSharedPreferences("child_declaration_prefs", Context.MODE_PRIVATE)

    data class ChildDeclaration(
        val childName: String,
        val idNumber: String,
        val adultPassenger: String,
        val declaredAt: String,
        val certificateCode: String
    )

    fun getDeclaration(tripKey: String): ChildDeclaration? {
        val raw = prefs.getString("declaration_$tripKey", null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            ChildDeclaration(
                childName = json.getString("childName"),
                idNumber = json.getString("idNumber"),
                adultPassenger = json.getString("adultPassenger"),
                declaredAt = json.getString("declaredAt"),
                certificateCode = json.getString("certificateCode")
            )
        }.getOrNull()
    }

    fun saveDeclaration(
        tripKey: String,
        childName: String,
        idNumber: String,
        adultPassenger: String
    ): ChildDeclaration {
        val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA).format(java.util.Date())
        val certCode = "CHD" + System.currentTimeMillis().toString().takeLast(8)
        val record = ChildDeclaration(
            childName = childName,
            idNumber = idNumber,
            adultPassenger = adultPassenger,
            declaredAt = now,
            certificateCode = certCode
        )
        val json = JSONObject().apply {
            put("childName", record.childName)
            put("idNumber", record.idNumber)
            put("adultPassenger", record.adultPassenger)
            put("declaredAt", record.declaredAt)
            put("certificateCode", record.certificateCode)
        }
        prefs.edit().putString("declaration_$tripKey", json.toString()).apply()
        return record
    }

    fun cancelDeclaration(tripKey: String) {
        prefs.edit().remove("declaration_$tripKey").apply()
    }
}
