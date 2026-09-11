package com.railway.ticketsystem.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.railway.ticketsystem.model.AppMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Retains business messages even when the user disables system notifications. */
class MessageRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = SecurePreferences.open(appContext, "secure_message_data", "message_data")
    private val gson = Gson()

    companion object {
        private const val MESSAGES_KEY = "messages"
        private const val EVENT_KEYS = "recorded_event_keys"
        private const val SETTINGS_PREFIX = "reminder_enabled_"
        private val messageLock = Any()
        const val PAYMENT = "payment"
        const val WAITLIST = "waitlist"
        const val TRAVEL = "travel"
        const val TICKET = "ticket"
        val CATEGORIES = listOf(PAYMENT, WAITLIST, TRAVEL, TICKET)
        fun categoryLabel(category: String): String = when (category) {
            PAYMENT -> "支付"
            WAITLIST -> "候补"
            TRAVEL -> "行程"
            else -> "车票"
        }
    }

    fun getMessages(userId: String): List<AppMessage> = synchronized(messageLock) {
        readMessages().filter { it.userId == userId }.sortedByDescending { it.createdAtMillis }
    }

    fun unreadCount(userId: String): Int = getMessages(userId).count { !it.isRead }

    fun add(
        userId: String,
        category: String,
        title: String,
        content: String,
        relatedOrderId: String? = null,
        eventKey: String? = null,
        relatedWaitlistId: String? = null
    ): AppMessage? {
        if (userId.isBlank()) return null
        val saved = synchronized(messageLock) {
            val now = System.currentTimeMillis()
            val messages = readMessages()
            // Explicit keys survive refresh, process death and pruning of the visible inbox.
            // Legacy callers are deduplicated by their complete business payload.
            val key = eventKey ?: listOf(category, title, content, relatedOrderId.orEmpty(), relatedWaitlistId.orEmpty()).joinToString("|")
            val scopedKey = "${userId.length}:${userId}:${key}"
            val keys = prefs.getStringSet(EVENT_KEYS, emptySet()).orEmpty().toMutableSet()
            if (scopedKey in keys || messages.any { it.userId == userId && it.eventKey == key }) return@synchronized null
            val message = AppMessage(
                id = "MSG_${UUID.randomUUID()}",
                userId = userId,
                category = category,
                title = title,
                content = content,
                createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(now)),
                createdAtMillis = now,
                relatedOrderId = relatedOrderId,
                eventKey = key,
                relatedWaitlistId = relatedWaitlistId
            )
            keys.add(scopedKey)
            // Keep other accounts intact; limit only this account's visible inbox.
            val retained = messages.filter { it.userId != userId } +
                (messages.filter { it.userId == userId } + message).takeLast(500)
            if (prefs.edit().putString(MESSAGES_KEY, gson.toJson(retained))
                    .putStringSet(EVENT_KEYS, keys).commit()) message else null
        }
        saved?.let { LocalNotifications.post(appContext, it) }
        return saved
    }

    fun markRead(messageId: String, userId: String): Boolean = synchronized(messageLock) {
        val messages = readMessages().toMutableList()
        val index = messages.indexOfFirst { it.id == messageId && it.userId == userId }
        if (index < 0) return@synchronized false
        messages[index] = messages[index].copy(isRead = true)
        val saved = persist(messages)
        if (saved) LocalNotifications.dismiss(appContext, messageId)
        saved
    }

    fun markAllRead(userId: String): Boolean = synchronized(messageLock) {
        val messages = readMessages()
        val saved = persist(messages.map { if (it.userId == userId) it.copy(isRead = true) else it })
        if (saved) messages.filter { it.userId == userId }.forEach { LocalNotifications.dismiss(appContext, it.id) }
        saved
    }

    fun isReminderEnabled(category: String): Boolean =
        UserRepository(appContext).getCurrentUser()?.id?.let { isReminderEnabled(it, category) } ?: false

    fun isReminderEnabled(userId: String, category: String): Boolean =
        prefs.getBoolean(SETTINGS_PREFIX + userId + "_" + category, prefs.getBoolean(SETTINGS_PREFIX + category, true))

    fun setReminderEnabled(category: String, enabled: Boolean): Boolean =
        UserRepository(appContext).getCurrentUser()?.id?.let { setReminderEnabled(it, category, enabled) } ?: false

    fun setReminderEnabled(userId: String, category: String, enabled: Boolean): Boolean {
        val saved = prefs.edit().putBoolean(SETTINGS_PREFIX + userId + "_" + category, enabled).commit()
        if (saved && !enabled) getMessages(userId).filter { it.category == category }
            .forEach { LocalNotifications.dismiss(appContext, it.id) }
        return saved
    }

    private fun readMessages(): List<AppMessage> {
        val json = prefs.getString(MESSAGES_KEY, null) ?: return emptyList()
        val type = object : TypeToken<List<AppMessage>>() {}.type
        return runCatching { gson.fromJson<List<AppMessage>>(json, type) ?: emptyList() }.getOrDefault(emptyList())
    }

    private fun persist(messages: List<AppMessage>): Boolean =
        prefs.edit().putString(MESSAGES_KEY, gson.toJson(messages)).commit()
}
