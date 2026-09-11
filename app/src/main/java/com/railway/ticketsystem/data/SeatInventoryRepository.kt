package com.railway.ticketsystem.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.railway.ticketsystem.model.Order
import com.railway.ticketsystem.model.Train
import kotlin.random.Random

/**
 * The single source of truth for a train's seat-class inventory in this local simulation.
 * A snapshot is generated once per train/date/class and then persisted so rendering never
 * changes the inventory and checkout reads exactly what the user saw.
 */
data class SeatAvailability(
    val trainNumber: String,
    val departureDate: String,
    val seatType: String,
    val availableSeats: Int
) {
    val requiresWaitlist: Boolean get() = availableSeats <= 0

    /** Railway-style display while preserving the actual count for booking checks. */
    val displayLabel: String
        get() = when {
            requiresWaitlist -> "候补"
            seatType == "商务座" && availableSeats > 10 -> "有票"
            seatType != "商务座" && availableSeats > 20 -> "有票"
            else -> "${availableSeats}张"
        }
}

class SeatInventoryRepository(context: Context) {
    private val prefs: SharedPreferences = SecurePreferences.open(
        context.applicationContext,
        "secure_seat_inventory_data",
        "seat_inventory_data"
    )
    private val gson = Gson()

    companion object {
        private const val INVENTORY_KEY = "seat_inventory"
        private val inventoryLock = Any()
        val supportedSeatTypes = listOf("二等座", "一等座", "商务座", "硬座", "硬卧", "软卧", "无座")
    }

    fun getAvailabilities(train: Train, departureDate: String): Map<String, SeatAvailability> =
        synchronized(inventoryLock) {
            val records = readRecords().toMutableList()
            var changed = false
            val result = linkedMapOf<String, SeatAvailability>()
            supportedSeatTypesFor(train).forEach { seatType ->
                val index = records.indexOfFirst { matches(it, train.number, departureDate, seatType) }
                val availability = if (index >= 0) {
                    normalize(records[index]).also { normalized ->
                        if (normalized != records[index]) {
                            records[index] = normalized
                            changed = true
                        }
                    }
                } else {
                    SeatAvailability(
                        trainNumber = train.number,
                        departureDate = departureDate,
                        seatType = seatType,
                        availableSeats = generateInitialCount(seatType)
                    ).also {
                        records.add(it)
                        changed = true
                    }
                }
                result[seatType] = availability
            }
            if (changed) persist(records)
            result
        }

    fun getAvailability(train: Train, departureDate: String, seatType: String): SeatAvailability =
        getAvailabilities(train, departureDate)[seatType]
            ?: SeatAvailability(train.number, departureDate, seatType, 0)

    /** Atomically decrements a seat-class snapshot when every requested ticket is available. */
    fun reserveSeats(train: Train, departureDate: String, seatType: String, count: Int): Boolean {
        if (count <= 0 || seatType !in supportedSeatTypesFor(train)) return false
        return synchronized(inventoryLock) {
            val records = readRecords().toMutableList()
            val index = records.indexOfFirst { matches(it, train.number, departureDate, seatType) }
            val availability = if (index >= 0) {
                normalize(records[index]).also { records[index] = it }
            } else {
                SeatAvailability(train.number, departureDate, seatType, generateInitialCount(seatType)).also(records::add)
            }
            if (availability.availableSeats < count) return@synchronized false
            records[records.indexOfFirst { matches(it, train.number, departureDate, seatType) }] =
                availability.copy(availableSeats = availability.availableSeats - count)
            persist(records)
        }
    }

    /**
     * Compensates a reservation when its surrounding order update fails.
     * A rollback is not a refund, so it deliberately creates no release receipt.
     */
    fun rollbackReservedSeats(train: Train, departureDate: String, seatType: String, count: Int = 1): Boolean {
        if (count <= 0 || seatType !in supportedSeatTypesFor(train)) return false
        return synchronized(inventoryLock) {
            val records = readRecords().toMutableList()
            val index = records.indexOfFirst { matches(it, train.number, departureDate, seatType) }
            if (index < 0) return@synchronized false
            records[index] = normalize(records[index].copy(
                availableSeats = records[index].availableSeats + count
            ))
            persist(records)
        }
    }

    /**
     * A successful candidate consumes a seat that became available outside this local user.
     * If the snapshot is still zero, the simulated release and immediate assignment cancel out.
     */
    fun consumeWaitlistSeat(order: Order): Boolean {
        if (order.seatType !in supportedSeatTypes) return false
        return synchronized(inventoryLock) {
            val records = readRecords().toMutableList()
            val index = records.indexOfFirst { matches(it, order.trainNumber, order.departureDate, order.seatType) }
            if (index < 0) return@synchronized false
            if (records[index].availableSeats > 0) {
                records[index] = records[index].copy(availableSeats = records[index].availableSeats - 1)
                persist(records)
            } else true
        }
    }
    /** Restores one inventory unit after a paid ticket is successfully refunded. */
    fun releaseSeat(order: Order): Boolean {
        if (order.seatType !in supportedSeatTypes) return false
        return synchronized(inventoryLock) {
            // Order ids survive change-ticket operations. Scope the receipt to the exact
            // ticket snapshot so releasing the old seat cannot suppress a later refund.
            val receiptKey = listOf(
                "released_order",
                order.id,
                order.trainNumber,
                order.departureDate,
                order.seatType,
                order.carNumber,
                order.seatNumber
            ).joinToString("_")
            if (prefs.getBoolean(receiptKey, false)) return@synchronized true
            val records = readRecords().toMutableList()
            val index = records.indexOfFirst { matches(it, order.trainNumber, order.departureDate, order.seatType) }
            if (index < 0) {
                // A legacy paid order may predate the persisted inventory snapshot.
                records.add(SeatAvailability(order.trainNumber, order.departureDate, order.seatType, 1))
            } else {
                records[index] = normalize(records[index].copy(availableSeats = records[index].availableSeats + 1))
            }
            prefs.edit()
                .putString(INVENTORY_KEY, gson.toJson(records))
                .putBoolean(receiptKey, true)
                .commit()
        }
    }

    private fun generateInitialCount(seatType: String): Int = when (seatType) {
        "二等座" -> if (Random.nextInt(100) < 18) 0 else Random.nextInt(1, 101)
        "一等座" -> if (Random.nextInt(100) < 25) 0 else Random.nextInt(1, 61)
        "商务座" -> if (Random.nextInt(100) < 35) 0 else Random.nextInt(1, 21)
        "无座" -> if (Random.nextInt(100) < 15) 0 else Random.nextInt(1, 21)
        "硬座" -> if (Random.nextInt(100) < 12) 0 else Random.nextInt(1, 101)
        "硬卧" -> if (Random.nextInt(100) < 22) 0 else Random.nextInt(1, 61)
        "软卧" -> if (Random.nextInt(100) < 30) 0 else Random.nextInt(1, 31)
        else -> 0
    }

    private fun normalize(availability: SeatAvailability): SeatAvailability =
        if (availability.seatType == "无座" && availability.availableSeats > 20) {
            availability.copy(availableSeats = 20)
        } else {
            availability
        }

    private fun supportedSeatTypesFor(train: Train): List<String> =
        if (train.routeType == com.railway.ticketsystem.model.RouteType.CONVENTIONAL) {
            listOf("硬座", "硬卧", "软卧", "无座")
        } else if (train.number.trim().startsWith("D", ignoreCase = true)) {
            listOf("二等座", "一等座", "无座")
        } else {
            listOf("二等座", "一等座", "商务座", "无座")
        }

    private fun matches(
        record: SeatAvailability,
        trainNumber: String,
        departureDate: String,
        seatType: String
    ): Boolean =
        record.trainNumber == trainNumber &&
            record.departureDate == departureDate &&
            record.seatType == seatType

    private fun readRecords(): List<SeatAvailability> {
        val json = prefs.getString(INVENTORY_KEY, null) ?: return emptyList()
        val type = object : TypeToken<List<SeatAvailability>>() {}.type
        return runCatching { gson.fromJson<List<SeatAvailability>>(json, type) ?: emptyList() }
            .getOrDefault(emptyList())
    }

    private fun persist(records: List<SeatAvailability>): Boolean = prefs.edit()
        .putString(INVENTORY_KEY, gson.toJson(records))
        .commit()
}
