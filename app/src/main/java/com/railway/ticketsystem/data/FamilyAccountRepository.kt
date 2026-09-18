package com.railway.ticketsystem.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** A family member is also mirrored into the normal passenger directory for checkout. */
data class FamilyMember(
    val id: String,
    val userId: String,
    val passengerId: String,
    val name: String,
    val relation: String,
    val travelerType: String,
    val idCard: String,
    val phone: String,
    val isFrequentCompanion: Boolean,
    val createdAt: Long = System.currentTimeMillis()
) {
    val benefitLabel: String
        get() = when (travelerType) {
            "儿童" -> "儿童随行 · 优先安排相邻座"
            "学生" -> "学生权益 · 购票资格待核验"
            "老人" -> "长者服务 · 优先安排便捷席位"
            else -> "家庭同行 · 自动优先同排"
        }
}

class FamilyAccountRepository(context: Context) {
    private val prefs = SecurePreferences.open(context.applicationContext, "secure_family_account", "family_account")
    private val gson = Gson()

    fun members(userId: String): List<FamilyMember> = read().filter { it.userId == userId }
        .sortedWith(compareByDescending<FamilyMember> { it.isFrequentCompanion }.thenBy { it.createdAt })

    fun memberForPassenger(userId: String, passengerId: String): FamilyMember? =
        members(userId).firstOrNull { it.passengerId == passengerId }

    fun upsert(member: FamilyMember): Boolean {
        val records = read().filterNot { it.id == member.id }.plus(member).takeLast(80)
        return prefs.edit().putString(KEY, gson.toJson(records)).commit()
    }

    fun remove(userId: String, memberId: String): Boolean {
        val records = read()
        if (records.none { it.id == memberId && it.userId == userId }) return false
        return prefs.edit().putString(KEY, gson.toJson(records.filterNot { it.id == memberId && it.userId == userId })).commit()
    }

    private fun read(): List<FamilyMember> {
        val type = object : TypeToken<List<FamilyMember>>() {}.type
        return runCatching { gson.fromJson<List<FamilyMember>>(prefs.getString(KEY, null), type) ?: emptyList() }
            .getOrDefault(emptyList())
    }

    private companion object { const val KEY = "family_members" }
}