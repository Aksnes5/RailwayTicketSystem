package com.railway.ticketsystem.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.railway.ticketsystem.model.AppMessage
import com.railway.ticketsystem.model.Order

/**
 * Local source of truth for business records.
 *
 * The app historically kept complete order and inbox collections in one JSON preference value.
 * That is convenient for a prototype but not suitable for a payment or itinerary record: a single
 * interrupted write can lose the whole collection and every read has to deserialize every row.
 * This database keeps one immutable snapshot per record and leaves the encrypted preference copy
 * in place during the migration window so existing installations can recover safely.
 */
internal class CoreTravelDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    private val gson = Gson()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $TABLE_ORDERS (" +
                "order_id TEXT PRIMARY KEY NOT NULL," +
                "user_id TEXT NOT NULL," +
                "status TEXT NOT NULL," +
                "departure_date TEXT NOT NULL," +
                "departure_time TEXT NOT NULL," +
                "arrival_date TEXT," +
                "payload_json TEXT NOT NULL," +
                "updated_at INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX order_user_departure ON $TABLE_ORDERS(user_id, departure_date)")
        db.execSQL("CREATE INDEX order_status ON $TABLE_ORDERS(status)")
        db.execSQL(
            "CREATE TABLE $TABLE_MESSAGES (" +
                "message_id TEXT PRIMARY KEY NOT NULL," +
                "user_id TEXT NOT NULL," +
                "category TEXT NOT NULL," +
                "created_at INTEGER NOT NULL," +
                "is_read INTEGER NOT NULL," +
                "payload_json TEXT NOT NULL," +
                "updated_at INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX message_user_created ON $TABLE_MESSAGES(user_id, created_at DESC)")
        db.execSQL(
            "CREATE TABLE $TABLE_AUDIT (" +
                "event_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "user_id TEXT NOT NULL," +
                "entity_type TEXT NOT NULL," +
                "entity_id TEXT NOT NULL," +
                "action TEXT NOT NULL," +
                "created_at INTEGER NOT NULL," +
                "payload_json TEXT)"
        )
        db.execSQL("CREATE INDEX audit_entity ON $TABLE_AUDIT(entity_type, entity_id, created_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun readOrders(): List<Order> = readPayloads(
        table = TABLE_ORDERS,
        idColumn = "order_id",
        type = object : TypeToken<Order>() {}.type
    )

    fun replaceOrders(orders: List<Order>, action: String = "snapshot"): Boolean = runCatching {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete(TABLE_ORDERS, null, null)
            val timestamp = System.currentTimeMillis()
            orders.forEach { order ->
                writableDatabase.insertOrThrow(TABLE_ORDERS, null, ContentValues().apply {
                    put("order_id", order.id)
                    put("user_id", order.userId)
                    put("status", order.status)
                    put("departure_date", order.departureDate)
                    put("departure_time", order.departureTime)
                    put("arrival_date", order.arrivalDate)
                    put("payload_json", gson.toJson(order))
                    put("updated_at", timestamp)
                })
                appendAudit(writableDatabase, order.userId, "order", order.id, action, gson.toJson(order), timestamp)
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
        true
    }.getOrDefault(false)

    fun readMessages(): List<AppMessage> = readPayloads(
        table = TABLE_MESSAGES,
        idColumn = "message_id",
        type = object : TypeToken<AppMessage>() {}.type
    )

    fun replaceMessages(messages: List<AppMessage>, action: String = "snapshot"): Boolean = runCatching {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete(TABLE_MESSAGES, null, null)
            val timestamp = System.currentTimeMillis()
            messages.forEach { message ->
                writableDatabase.insertOrThrow(TABLE_MESSAGES, null, ContentValues().apply {
                    put("message_id", message.id)
                    put("user_id", message.userId)
                    put("category", message.category)
                    put("created_at", message.createdAtMillis)
                    put("is_read", if (message.isRead) 1 else 0)
                    put("payload_json", gson.toJson(message))
                    put("updated_at", timestamp)
                })
                appendAudit(writableDatabase, message.userId, "message", message.id, action, gson.toJson(message), timestamp)
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
        true
    }.getOrDefault(false)

    private fun <T> readPayloads(table: String, idColumn: String, type: java.lang.reflect.Type): List<T> = runCatching {
        readableDatabase.query(table, arrayOf(idColumn, "payload_json"), null, null, null, null, "updated_at ASC").use { cursor ->
            buildList {
                val payloadIndex = cursor.getColumnIndexOrThrow("payload_json")
                while (cursor.moveToNext()) {
                    gson.fromJson<T>(cursor.getString(payloadIndex), type)?.let(::add)
                }
            }
        }
    }.getOrDefault(emptyList())

    private fun appendAudit(
        db: SQLiteDatabase,
        userId: String,
        entityType: String,
        entityId: String,
        action: String,
        payload: String,
        timestamp: Long
    ) {
        db.insert(TABLE_AUDIT, null, ContentValues().apply {
            put("user_id", userId)
            put("entity_type", entityType)
            put("entity_id", entityId)
            put("action", action)
            put("created_at", timestamp)
            put("payload_json", payload)
        })
    }

    companion object {
        private const val DATABASE_NAME = "travel_records.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_ORDERS = "order_snapshots"
        private const val TABLE_MESSAGES = "message_snapshots"
        private const val TABLE_AUDIT = "record_audit"
    }
}
